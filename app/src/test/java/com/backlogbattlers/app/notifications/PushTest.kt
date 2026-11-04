package com.backlogbattlers.app.notifications

import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// reading pushes from the API's data payload, and deciding whether to show them
class PushTest {

    private val me = "6f1c0d2e-8a4b-4c7d-9e10-2b3c4d5e6f70"
    private val everythingOn = UserSettings()

    private fun payload(type: String, vararg extra: Pair<String, String>) =
        mapOf("type" to type, "userId" to me, "actorName" to "Ana") + extra

    @Test
    fun `reads each of the four pushes the API sends`() {
        assertEquals(PushType.FRIEND_REQUEST_RECEIVED, Push.from(payload("FRIEND_REQUEST_RECEIVED"))?.type)
        assertEquals(PushType.FRIEND_REQUEST_ACCEPTED, Push.from(payload("FRIEND_REQUEST_ACCEPTED"))?.type)
        assertEquals(PushType.OVERTAKEN, Push.from(payload("OVERTAKEN"))?.type)

        val completed = Push.from(payload("FRIEND_COMPLETED_GAME", "gameTitle" to "Hades"))
        assertEquals(Push(PushType.FRIEND_COMPLETED_GAME, me, "Ana", "Hades"), completed)
    }

    @Test
    fun `a push this version doesn't know, or one missing its fields, is ignored`() {
        assertNull(Push.from(payload("SEASON_RESET")))
        assertNull(Push.from(mapOf("type" to "OVERTAKEN", "actorName" to "Ana")))
        assertNull(Push.from(mapOf("type" to "OVERTAKEN", "userId" to me, "actorName" to " ")))
        assertNull(Push.from(emptyMap()))
    }

    @Test
    fun `a push is shown only to the signed-in account it was sent to`() {
        val push = Push(PushType.OVERTAKEN, me, "Ana")

        assertTrue(push.shouldShow(me, everythingOn))
        assertFalse(push.shouldShow(null, everythingOn))
        assertFalse(push.shouldShow("someone-else", everythingOn))
    }

    @Test
    fun `the friend activity switch hides friend pushes, and rank changes hides overtaken`() {
        val friendsOff = everythingOn.copy(friendActivityNotificationsEnabled = false)
        val rankOff = everythingOn.copy(rankChangeNotificationsEnabled = false)

        assertFalse(Push(PushType.FRIEND_REQUEST_RECEIVED, me, "Ana").shouldShow(me, friendsOff))
        assertFalse(Push(PushType.FRIEND_REQUEST_ACCEPTED, me, "Ana").shouldShow(me, friendsOff))
        assertFalse(Push(PushType.FRIEND_COMPLETED_GAME, me, "Ana").shouldShow(me, friendsOff))
        assertTrue(Push(PushType.OVERTAKEN, me, "Ana").shouldShow(me, friendsOff))

        assertFalse(Push(PushType.OVERTAKEN, me, "Ana").shouldShow(me, rankOff))
        assertTrue(Push(PushType.FRIEND_REQUEST_RECEIVED, me, "Ana").shouldShow(me, rankOff))
    }

    @Test
    fun `tapping a push opens its matching screen`() {
        assertEquals(R.id.friendsFragment, PushNotifications.destinationOf(PushType.FRIEND_REQUEST_RECEIVED))
        assertEquals(R.id.friendsFragment, PushNotifications.destinationOf(PushType.FRIEND_REQUEST_ACCEPTED))
        assertEquals(R.id.activityFragment, PushNotifications.destinationOf(PushType.FRIEND_COMPLETED_GAME))
        assertEquals(R.id.standingsFragment, PushNotifications.destinationOf(PushType.OVERTAKEN))

        // a friend request opens the friends screen on its requests tab
        assertEquals(OPEN_TAB_REQUESTS, PushType.FRIEND_REQUEST_RECEIVED.friendsTab)
        assertNull(PushType.FRIEND_REQUEST_ACCEPTED.friendsTab)
    }

    @Test
    fun `friend pushes and competition pushes go to separate channels`() {
        assertEquals(PushChannel.FRIENDS, PushType.FRIEND_REQUEST_RECEIVED.channel)
        assertEquals(PushChannel.FRIENDS, PushType.FRIEND_COMPLETED_GAME.channel)
        assertEquals(PushChannel.COMPETITION, PushType.OVERTAKEN.channel)
    }
}
//------------------------------EOF------------------------------\\
