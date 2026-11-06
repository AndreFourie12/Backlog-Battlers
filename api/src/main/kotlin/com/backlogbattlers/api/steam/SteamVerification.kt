package com.backlogbattlers.api.steam

import com.backlogbattlers.api.db.LibraryEntries
import com.backlogbattlers.api.db.UnlockedAchievements
import com.backlogbattlers.api.db.UserSteamAccounts
import com.backlogbattlers.api.db.VerifiedUnlocks
import com.backlogbattlers.api.games.IgdbClient
import com.backlogbattlers.api.games.PlayerAchievements
import com.backlogbattlers.api.games.SteamClient
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

/** Body and response of `PUT`/`GET /users/me/steam`. */
@Serializable
data class SteamLinkDto(val steamId: String)

/** A SteamID64 is 17 digits and, for a normal player account, starts with 7656. */
private val STEAM_ID_FORMAT = Regex("^7656[0-9]{13}$")

fun isValidSteamId(steamId: String): Boolean = STEAM_ID_FORMAT.matches(steamId)

sealed interface LinkResult {
    data object Linked : LinkResult

    /** Another user has already linked this Steam account. */
    data object TakenByAnotherUser : LinkResult
}

/** The Steam ID this user linked, or null if they have not. */
fun linkedSteamId(owner: Uuid): String? = transaction {
    UserSteamAccounts.selectAll().where { UserSteamAccounts.userId eq owner }.singleOrNull()?.get(UserSteamAccounts.steamId)
}

/**
 * Links [steamId] to [owner], replacing any Steam account they linked before. The caller has
 * already checked the format.
 *
 * shortcut: nothing proves the user owns this Steam account (that needs Steam sign-in, i.e. OpenID),
 * so someone could link another player's public profile and have that player's achievements count.
 * The one-user-per-Steam-account rule limits it; add Steam OpenID if it ever has to be airtight.
 * Two requests for the same new Steam ID at the exact same moment would make the second a 500.
 */
fun linkSteamAccount(owner: Uuid, steamId: String): LinkResult = transaction {
    val takenElsewhere = UserSteamAccounts.selectAll()
        .where { (UserSteamAccounts.steamId eq steamId) and (UserSteamAccounts.userId neq owner) }
        .any()
    if (takenElsewhere) return@transaction LinkResult.TakenByAnotherUser

    val updated = UserSteamAccounts.update({ UserSteamAccounts.userId eq owner }) { it[UserSteamAccounts.steamId] = steamId }
    if (updated == 0) {
        UserSteamAccounts.insert {
            it[userId] = owner
            it[UserSteamAccounts.steamId] = steamId
        }
    }
    LinkResult.Linked
}

/** Removes the user's Steam link. False if they had none. */
fun unlinkSteamAccount(owner: Uuid): Boolean = transaction {
    UserSteamAccounts.deleteWhere { UserSteamAccounts.userId eq owner } > 0
}

/** Why some unlocked achievements were or were not confirmed by Steam. */
@Serializable
enum class SteamCheckStatus {
    /** Nothing was waiting to be paid for, so Steam was not asked. */
    NOT_NEEDED,

    /** Steam was asked; the achievements it confirmed are paid, the rest are not. */
    VERIFIED,

    /** The user has not linked a Steam account, so nothing can be confirmed. */
    NOT_LINKED,

    /** The linked Steam profile is private, so Steam will not say what is unlocked. */
    PRIVATE_PROFILE,

    /** IGDB has no Steam listing for the game, so there is nothing to check it against. */
    NOT_ON_STEAM,
}

/** The outcome of checking an entry's unlocks, and the codes Steam confirmed. */
data class SteamCheck(val status: SteamCheckStatus, val confirmed: Set<String> = emptySet())

/**
 * The unlocks of [entryId] that Steam has confirmed: the ones an import or an earlier check recorded,
 * and the ones already paid for, which were only paid once Steam confirmed them. Call inside a transaction.
 */
fun verifiedIds(entryId: Uuid): Set<String> {
    val recorded = VerifiedUnlocks.selectAll().where { VerifiedUnlocks.libraryEntryId eq entryId }
        .map { it[VerifiedUnlocks.achievementId] }
    val paid = UnlockedAchievements.selectAll()
        .where { (UnlockedAchievements.libraryEntryId eq entryId) and (UnlockedAchievements.countedForPoints eq true) }
        .map { it[UnlockedAchievements.achievementId] }
    return (recorded + paid).toSet()
}

/** Records [ids] as confirmed for [entryId]. Only ids that are unlocked on the entry are recorded. Call inside a transaction. */
fun markVerified(entryId: Uuid, ids: Collection<String>) {
    if (ids.isEmpty()) return
    val unlocked = UnlockedAchievements.selectAll()
        .where { (UnlockedAchievements.libraryEntryId eq entryId) and (UnlockedAchievements.achievementId inList ids.toList()) }
        .map { it[UnlockedAchievements.achievementId] }
        .toSet()
    val already = VerifiedUnlocks.selectAll().where { VerifiedUnlocks.libraryEntryId eq entryId }
        .map { it[VerifiedUnlocks.achievementId] }
        .toSet()
    (unlocked - already).forEach { code ->
        VerifiedUnlocks.insert {
            it[libraryEntryId] = entryId
            it[achievementId] = code
        }
    }
}

/**
 * Asks Steam which of the entry's not-yet-paid achievements [owner] has really unlocked. Only those
 * are checked, and not even those that an import already confirmed, so an entry that was already
 * paid for or confirmed never costs a Steam call. Throws a
 * SteamException or IgdbException when either service is down, so the caller can refuse to go on
 * rather than treat an outage as "nothing unlocked".
 */
suspend fun checkUnlocks(owner: Uuid, entryId: Uuid, igdb: IgdbClient, steam: SteamClient): SteamCheck {
    val (gameId, waiting) = transaction {
        val entry = LibraryEntries.selectAll()
            .where { (LibraryEntries.id eq entryId) and (LibraryEntries.userId eq owner) }
            .singleOrNull() ?: return@transaction null
        val confirmed = verifiedIds(entryId)
        val ids = UnlockedAchievements.selectAll()
            .where { (UnlockedAchievements.libraryEntryId eq entryId) and (UnlockedAchievements.countedForPoints eq false) }
            .map { it[UnlockedAchievements.achievementId] }
            .filter { it !in confirmed }
        entry[LibraryEntries.gameId] to ids
    } ?: return SteamCheck(SteamCheckStatus.NOT_NEEDED) // no such entry: the completion itself answers 404
    if (waiting.isEmpty()) return SteamCheck(SteamCheckStatus.NOT_NEEDED)

    val steamId = linkedSteamId(owner) ?: return SteamCheck(SteamCheckStatus.NOT_LINKED)
    val appId = steamAppIdFor(gameId, igdb) ?: return SteamCheck(SteamCheckStatus.NOT_ON_STEAM)

    return when (val unlocked = steam.unlockedAchievements(steamId, appId)) {
        PlayerAchievements.PrivateProfile -> SteamCheck(SteamCheckStatus.PRIVATE_PROFILE)
        is PlayerAchievements.Unlocked -> SteamCheck(SteamCheckStatus.VERIFIED, waiting.filter { it in unlocked.codes }.toSet())
    }
}
