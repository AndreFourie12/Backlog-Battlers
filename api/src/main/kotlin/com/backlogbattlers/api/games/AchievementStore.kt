package com.backlogbattlers.api.games

import com.backlogbattlers.api.db.Achievements
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

/** One achievement as the app receives it. `rarityPercent` is the share of players who have unlocked it. */
@Serializable
data class AchievementDto(
    val achievementId: String,
    val name: String,
    val description: String?,
    val rarityPercent: Double?,
)

// Column sizes in the Achievements table
private const val MAX_ID_LENGTH = 64
private const val MAX_NAME_LENGTH = 255

/**
 * The achievements of a game, rarest first. They are fetched from Steam the first time
 * they are asked for and served from our own database afterwards.
 *
 * A game with no Steam listing, or no achievements on Steam, simply has none: the result
 * is empty and nothing is stored. The game must already be in the Games table.
 */
suspend fun achievementsFor(gameId: Int, igdb: IgdbClient, steam: SteamClient): List<AchievementDto> {
    val stored = storedAchievements(gameId)
    if (stored.isNotEmpty()) return stored

    val appId = igdb.steamAppId(gameId) ?: return emptyList()
    val rarity = steam.globalRarity(appId)
    if (rarity.isEmpty()) return emptyList()

    // Names are looked up once and stored with the rest. Without a key they stay as Steam's codes.
    val names = steam.displayNames(appId)

    transaction {
        rarity.forEach { (code, percent) ->
            if (code.length > MAX_ID_LENGTH) return@forEach // would not fit the column
            val info = names?.get(code)
            // upsert: two first-time requests at once cannot collide on the same achievement
            Achievements.upsert {
                it[Achievements.gameId] = gameId
                it[achievementId] = code
                it[name] = (info?.displayName ?: code).take(MAX_NAME_LENGTH)
                it[description] = info?.description
                it[rarityPercent] = percent
            }
        }
    }
    return storedAchievements(gameId)
}

private fun storedAchievements(gameId: Int): List<AchievementDto> = transaction {
    Achievements.selectAll()
        .where { Achievements.gameId eq gameId }
        .map {
            AchievementDto(
                achievementId = it[Achievements.achievementId],
                name = it[Achievements.name],
                description = it[Achievements.description],
                rarityPercent = it[Achievements.rarityPercent],
            )
        }
        // Rarest first; achievements with no known rarity go last
        .sortedWith(compareBy<AchievementDto> { it.rarityPercent == null }.thenBy { it.rarityPercent }.thenBy { it.name })
}
