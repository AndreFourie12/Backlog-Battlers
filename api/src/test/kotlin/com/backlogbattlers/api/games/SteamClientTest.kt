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
}
