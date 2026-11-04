package com.backlogbattlers.app.notifications

import com.backlogbattlers.app.domain.model.UserSettings

// the notification channels, which the user can also turn off in the phone's own settings.
// one per Settings section the pushes belong to
enum class PushChannel(val id: String) {
    FRIENDS("friend_activity"),
    COMPETITION("competition"),
}

// the friends screen tab a push can open on, passed as the friendsFragment "openTab" argument
const val OPEN_TAB_REQUESTS = "REQUESTS"

//------------------------------
// the kinds of push the API sends (names match the API's PushType), with the Settings switch that
// turns each off, its channel, and the screen tapping it opens
enum class PushType(
    val channel: PushChannel,
    // the friendsFragment tab to open on, for the pushes that open the friends screen
    val friendsTab: String? = null,
) {
    FRIEND_REQUEST_RECEIVED(PushChannel.FRIENDS, friendsTab = OPEN_TAB_REQUESTS),
    FRIEND_REQUEST_ACCEPTED(PushChannel.FRIENDS),
    FRIEND_COMPLETED_GAME(PushChannel.FRIENDS),
    OVERTAKEN(PushChannel.COMPETITION),
    ;

    //------------------------------
    // whether the matching switch under Settings > Notifications is on
    fun isWanted(settings: UserSettings): Boolean = when (this) {
        FRIEND_REQUEST_RECEIVED, FRIEND_REQUEST_ACCEPTED, FRIEND_COMPLETED_GAME -> settings.friendActivityNotificationsEnabled
        OVERTAKEN -> settings.rankChangeNotificationsEnabled
    }

    companion object {
        fun fromName(name: String?): PushType? = entries.firstOrNull { it.name == name }
    }
}

//------------------------------
// one push as the API sends it: a data message, so the app always builds the notification itself
data class Push(
    val type: PushType,
    // the account the push was sent to
    val recipientId: String,
    // the friend who sent, accepted, completed or overtook
    val actorName: String,
    val gameTitle: String? = null,
) {

    //------------------------------
    // shown only to the account it was sent to (not after it signed out, or to whoever signed in
    // next), and only while its switch is on. the API checks the switch too; this covers a change
    // the API hasn't been told about yet, e.g. one made offline
    fun shouldShow(signedInUserId: String?, settings: UserSettings): Boolean =
        signedInUserId != null && signedInUserId == recipientId && type.isWanted(settings)

    companion object {
        //------------------------------
        // reads a push from an FCM data payload, or null for one this version of the app doesn't know
        fun from(data: Map<String, String>): Push? {
            val type = PushType.fromName(data["type"]) ?: return null
            val recipientId = data["userId"]?.takeIf { it.isNotBlank() } ?: return null
            val actorName = data["actorName"]?.takeIf { it.isNotBlank() } ?: return null
            return Push(type, recipientId, actorName, data["gameTitle"]?.takeIf { it.isNotBlank() })
        }
    }
}
//------------------------------EOF------------------------------\\
