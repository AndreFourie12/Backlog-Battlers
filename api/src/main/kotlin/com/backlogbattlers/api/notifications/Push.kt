package com.backlogbattlers.api.notifications

import com.backlogbattlers.api.db.Users
import com.backlogbattlers.api.settings.SettingsDto
import com.backlogbattlers.api.settings.settingsFor
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

/**
 * The kinds of push the API sends, each with the Settings switch that turns it off. The app
 * shows them in the user's own language from these names, so a name is never renamed, only added.
 */
enum class PushType(val isWanted: (SettingsDto) -> Boolean) {
    /** Someone sent you a friend request. */
    FRIEND_REQUEST_RECEIVED({ it.friendActivityNotifications }),

    /** Someone accepted the friend request you sent. */
    FRIEND_REQUEST_ACCEPTED({ it.friendActivityNotifications }),

    /** A friend marked a game complete. */
    FRIEND_COMPLETED_GAME({ it.friendActivityNotifications }),

    /** Someone passed you on this month's leaderboard. */
    OVERTAKEN({ it.rankChangeNotifications }),
}

/**
 * One push to one user. Sent as an FCM data message (no notification block), so the app always
 * builds the notification itself: in the user's language, on the right channel, and opening the
 * matching screen when tapped.
 */
data class Push(
    val type: PushType,
    /** Who did it: the friend who sent, accepted, completed or overtook. */
    val actorName: String,
    /** The game, for [PushType.FRIEND_COMPLETED_GAME]. */
    val gameTitle: String? = null,
) {
    /** The data payload. [recipient] lets the app drop a push meant for an account that has since signed out. */
    fun data(recipient: Uuid): Map<String, String> = buildMap {
        put("type", type.name)
        put("userId", recipient.toString())
        put("actorName", actorName)
        if (gameTitle != null) put("gameTitle", gameTitle)
    }
}

/**
 * Whether a user with these preferences wants [type]. [settings] is null until the app first
 * saves them; the app ships with every switch on, so no row means yes. [pushEnabled] is the
 * account-wide switch on Users, which turns everything off.
 */
fun allowsPush(settings: SettingsDto?, pushEnabled: Boolean, type: PushType): Boolean =
    pushEnabled && (settings == null || type.isWanted(settings))

/** [allowsPush] for a stored user. False when the user no longer exists. */
fun wantsPush(userId: Uuid, type: PushType): Boolean {
    val pushEnabled = transaction {
        Users.selectAll().where { Users.id eq userId }.singleOrNull()?.get(Users.pushNotificationsEnabled)
    } ?: return false
    return allowsPush(settingsFor(userId), pushEnabled, type)
}

/**
 * Who [mover] just passed by going from [pointsBefore] to [pointsAfter], given everyone's points
 * this month in [standings]. Someone level with or ahead of the mover before, and behind them
 * now, was overtaken. Nobody is overtaken when the mover's points did not go up.
 */
fun overtaken(mover: Uuid, pointsBefore: Int, pointsAfter: Int, standings: Map<Uuid, Int>): List<Uuid> {
    if (pointsAfter <= pointsBefore) return emptyList()
    return standings
        .filter { (userId, points) -> userId != mover && points > 0 && points >= pointsBefore && points < pointsAfter }
        .keys.toList()
}
//------------------------------EOF------------------------------\\
