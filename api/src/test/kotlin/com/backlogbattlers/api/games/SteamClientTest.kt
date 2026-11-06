package com.backlogbattlers.api.games

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every test uses a fake Steam, so nothing here touches the network. */
class SteamClientTest {

    private fun steamWith(
        apiKey: String = "test-key",
        seen: MutableList<HttpRequestData> = mutableListOf(),
        respondTo: (HttpRequestData) -> Pair<HttpStatusCode, String>,
    ) = SteamClient(
        HttpClient(
            MockEngine { request ->
                seen += request
                val (status, body) = respondTo(request)
                respond(body, status)
            },
        ),
        apiKey,
    )

    @Test
    fun `rarity reads percentages sent as text or numbers and skips unreadable ones`() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val body = """{"achievementpercentages":{"achievements":[
            {"name":"AchA","percent":"81.9"},{"name":"AchB","percent":5.9},{"name":"AchBad","percent":"n/a"}]}}"""
        val steam = steamWith(seen = seen) { HttpStatusCode.OK to body }

        val rarity = steam.globalRarity(1145360)

        assertEquals(mapOf("AchA" to 81.9, "AchB" to 5.9), rarity)
        assertEquals("1145360", seen.single().url.parameters["gameid"])
        assertTrue(seen.single().url.encodedPath.contains("GetGlobalAchievementPercentagesForApp"))
    }

    @Test
    fun `an app with no achievements gives an empty map, other failures are errors`() {
        val noStats = steamWith { HttpStatusCode.Forbidden to "" }
        assertEquals(emptyMap(), runBlocking { noStats.globalRarity(1) })

        val broken = steamWith { HttpStatusCode.InternalServerError to "" }
        assertFailsWith<SteamException> { runBlocking { broken.globalRarity(1) } }
    }

    @Test
    fun `display names need a key, and without one Steam is not even asked`() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val steam = steamWith(apiKey = "", seen = seen) { HttpStatusCode.OK to "{}" }

        assertNull(steam.displayNames(1145360))
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `display names read names, descriptions and icons, and a missing description stays null`() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val body = """{"game":{"gameName":"Hades","availableGameStats":{"achievements":[
            {"name":"AchA","defaultvalue":0,"displayName":"Tartarus Cleared","hidden":0,"description":"Clear Tartarus",
             "icon":"https://cdn.steam/AchA.jpg","icongray":"https://cdn.steam/AchA_gray.jpg"},
            {"name":"AchB","defaultvalue":0,"displayName":"Secret One","hidden":1}]}}}"""
        val steam = steamWith(apiKey = "test-key", seen = seen) { HttpStatusCode.OK to body }

        val names = steam.displayNames(1145360)!!

        assertEquals(
            SteamAchievementInfo("Tartarus Cleared", "Clear Tartarus", "https://cdn.steam/AchA.jpg", "https://cdn.steam/AchA_gray.jpg"),
            names["AchA"],
        )
        assertEquals(SteamAchievementInfo("Secret One", null, null, null), names["AchB"])
        assertEquals("test-key", seen.single().url.parameters["key"])
        assertEquals("1145360", seen.single().url.parameters["appid"])
    }

    @Test
    fun `a game with no stats has an empty schema`() = runBlocking {
        val steam = steamWith { HttpStatusCode.OK to """{"game":{}}""" }

        assertEquals(emptyMap(), steam.displayNames(1))
    }

    @Test
    fun `a schema failure never puts the key in the error message`() {
        val steam = steamWith(apiKey = "super-secret-key") { HttpStatusCode.BadRequest to "" }

        val error = assertFailsWith<SteamException> { runBlocking { steam.displayNames(1) } }

        assertFalse(error.message.orEmpty().contains("super-secret-key"))
    }

    @Test
    fun `the most played chart comes back most played first, without needing a key`() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        // Steam's own order is followed by rank, not by where a row sits in the JSON
        val body = """{"response":{"rollup_date":1793577600,"ranks":[
            {"rank":2,"appid":570,"last_week_rank":2,"peak_in_game":701331},
            {"rank":1,"appid":730,"last_week_rank":1,"peak_in_game":1089723},
            {"rank":3,"appid":1145360,"last_week_rank":-1,"peak_in_game":50000}]}}"""
        val steam = steamWith(apiKey = "", seen = seen) { HttpStatusCode.OK to body }

        assertEquals(listOf(730, 570, 1145360), steam.mostPlayedAppIds())
        assertTrue(seen.single().url.encodedPath.contains("ISteamChartsService/GetMostPlayedGames"))
        assertNull(seen.single().url.parameters["key"])
    }

    @Test
    fun `a player's unlocked achievements are the ones Steam marks achieved, nothing else`() = runBlocking {
        val seen = mutableListOf<HttpRequestData>()
        val body = """{"playerstats":{"steamID":"76561198000000001","gameName":"Hades","success":true,"achievements":[
            {"apiname":"AchA","achieved":1,"unlocktime":1700000000},
            {"apiname":"AchB","achieved":0,"unlocktime":0},
            {"apiname":"AchC","achieved":1,"unlocktime":1700000100}]}}"""
        val steam = steamWith(seen = seen) { HttpStatusCode.OK to body }

        val result = steam.unlockedAchievements("76561198000000001", 1145360)

        assertEquals(
            PlayerAchievements.Unlocked(
                codes = setOf("AchA", "AchC"),
                unlockedAt = mapOf("AchA" to java.time.Instant.ofEpochSecond(1700000000), "AchC" to java.time.Instant.ofEpochSecond(1700000100)),
            ),
            result,
        )
        val url = seen.single().url
        assertTrue(url.encodedPath.contains("GetPlayerAchievements"))
        assertEquals("76561198000000001", url.parameters["steamid"])
        assertEquals("1145360", url.parameters["appid"])
        assertEquals("test-key", url.parameters["key"])
    }

    @Test
    fun `a private profile is reported as private, whether Steam says 403 or says so in the body`() = runBlocking {
        val forbidden = steamWith { HttpStatusCode.Forbidden to """{"playerstats":{"error":"Profile is not public","success":false}}""" }
        assertEquals(PlayerAchievements.PrivateProfile, forbidden.unlockedAchievements("76561198000000001", 1))

        val okButPrivate = steamWith { HttpStatusCode.OK to """{"playerstats":{"error":"Profile is not public","success":false}}""" }
        assertEquals(PlayerAchievements.PrivateProfile, okButPrivate.unlockedAchievements("76561198000000001", 1))
    }

    @Test
    fun `a game the player has no stats for confirms nothing, rather than failing`() = runBlocking {
        val noStats = steamWith { HttpStatusCode.BadRequest to """{"playerstats":{"error":"Requested app has no stats","success":false}}""" }

        assertEquals(PlayerAchievements.Unlocked(emptySet()), noStats.unlockedAchievements("76561198000000001", 1))
    }

    @Test
    fun `a Steam outage is an error and never looks like nothing unlocked`() {
        val down = steamWith { HttpStatusCode.InternalServerError to "" }
        assertFailsWith<SteamException> { runBlocking { down.unlockedAchievements("76561198000000001", 1) } }

        val garbled = steamWith { HttpStatusCode.OK to "<html>maintenance</html>" }
        assertFailsWith<SteamException> { runBlocking { garbled.unlockedAchievements("76561198000000001", 1) } }
    }

    @Test
    fun `verifying needs the key, without one Steam is not asked, and a failure never leaks the key`() {
        val seen = mutableListOf<HttpRequestData>()
        val noKey = steamWith(apiKey = "", seen = seen) { HttpStatusCode.OK to "{}" }
        assertFailsWith<SteamException> { runBlocking { noKey.unlockedAchievements("76561198000000001", 1) } }
        assertTrue(seen.isEmpty())

        val failing = steamWith(apiKey = "super-secret-key") { HttpStatusCode.ServiceUnavailable to "" }
        val error = assertFailsWith<SteamException> { runBlocking { failing.unlockedAchievements("76561198000000001", 1) } }
        assertFalse(error.message.orEmpty().contains("super-secret-key"))
    }

    @Test
    fun `a failed most played request is an error`() {
        val steam = steamWith { HttpStatusCode.ServiceUnavailable to "" }

        assertFailsWith<SteamException> { runBlocking { steam.mostPlayedAppIds() } }
    }
}
