package com.backlogbattlers.api.games

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

/** Thrown when IGDB cannot be reached, rejects a request, or the API has no IGDB credentials. */
class IgdbException(message: String) : RuntimeException(message)

/** The token Twitch hands back for our client id and secret. */
@Serializable
private data class TwitchToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

/** Builds the HTTP client with JSON decoding that ignores fields we did not declare. */
fun igdbHttpClient(engine: HttpClientEngine = CIO.create()): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

/**
 * Talks to IGDB on behalf of the API.
 *
 * IGDB is authenticated through Twitch: we swap our client id and secret for a short-lived
 * token, then send that token with every request. The token is cached so we do not ask
 * Twitch again for every call.
 *
 * Sources followed:
 *  - IGDB API docs (requests, Apicalypse query language, rate limit of 4 requests per second):
 *    https://api-docs.igdb.com/
 *  - Twitch client-credentials flow (how the token is obtained):
 *    https://dev.twitch.tv/docs/authentication/getting-tokens-oauth/#client-credentials-grant-flow
 */
class IgdbClient(
    private val http: HttpClient,
    private val clientId: String,
    private val clientSecret: String,
    // Injected so tests can move time forward to check that expired tokens are replaced
    private val clock: () -> Instant = Instant::now,
) {
    private val tokenLock = Mutex()
    private var token: String? = null
    private var tokenExpiresAt: Instant = Instant.MIN

    /** Searches games by title. [page] starts at 1. Results carry no time-to-beat data. */
    suspend fun search(text: String, page: Int = 1, pageSize: Int = 10): List<IgdbGame> {
        val cleaned = cleanSearchText(text)
        if (cleaned.isEmpty()) return emptyList()

        val size = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val offset = (page.coerceAtLeast(1) - 1) * size
        return query("games", """search "$cleaned"; $GAME_FIELDS limit $size; offset $offset;""")
    }

    /** One game by its IGDB id, or null when IGDB does not know it. */
    suspend fun game(id: Int): IgdbGame? =
        query<List<IgdbGame>>("games", "$GAME_FIELDS where id = $id; limit 1;").firstOrNull()

    /** The time-to-beat figures for a game, or null when IGDB has none. */
    suspend fun timeToBeat(gameId: Int): IgdbTimeToBeat? =
        query<List<IgdbTimeToBeat>>(
            "game_time_to_beats",
            "fields game_id,normally,completely,count; where game_id = $gameId; limit 1;",
        ).firstOrNull()

    /** The game's Steam app id, or null when IGDB has no Steam listing for it. */
    suspend fun steamAppId(gameId: Int): Int? =
        query<List<IgdbExternalGame>>(
            "external_games",
            "fields uid; where game = $gameId & external_game_source = $STEAM_SOURCE_ID; limit 1;",
        ).firstOrNull()?.uid?.toIntOrNull()

    /** Sends one Apicalypse query to an IGDB endpoint and decodes the JSON list it returns. */
    private suspend inline fun <reified T> query(endpoint: String, apicalypse: String): T {
        val accessToken = accessToken()
        val response = http.post("$API_URL/$endpoint") {
            header("Client-ID", clientId)
            bearerAuth(accessToken)
            contentType(ContentType.Text.Plain)
            setBody(apicalypse)
        }
        if (!response.status.isSuccess()) {
            throw IgdbException("IGDB $endpoint request failed: ${response.status}")
        }
        return response.body()
    }

    /** Returns the cached token, or gets a new one from Twitch when there is none or it has expired. */
    private suspend fun accessToken(): String = tokenLock.withLock {
        val cached = token
        if (cached != null && clock().isBefore(tokenExpiresAt)) return cached

        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw IgdbException("IGDB_CLIENT_ID and IGDB_CLIENT_SECRET are not set")
        }

        val response = http.post(TOKEN_URL) {
            parameter("client_id", clientId)
            parameter("client_secret", clientSecret)
            parameter("grant_type", "client_credentials")
        }
        if (!response.status.isSuccess()) {
            throw IgdbException("Twitch token request failed: ${response.status}")
        }

        val fresh = response.body<TwitchToken>()
        token = fresh.accessToken
        // Renew a minute early so a token never expires while a request is in flight
        tokenExpiresAt = clock().plus(Duration.ofSeconds(fresh.expiresIn)).minusSeconds(TOKEN_MARGIN_SECONDS)
        fresh.accessToken
    }

    /**
     * The search text goes inside quotes in the query. Removing quotes and backslashes means
     * user input can never close the quotes and add its own clauses to the query.
     */
    private fun cleanSearchText(text: String): String =
        text.replace("\"", "").replace("\\", "").trim()

    companion object {
        private const val API_URL = "https://api.igdb.com/v4"
        private const val TOKEN_URL = "https://id.twitch.tv/oauth2/token"
        private const val GAME_FIELDS = "fields name,cover.image_id,platforms.name;"
        private const val MAX_PAGE_SIZE = 50
        private const val STEAM_SOURCE_ID = 1 // IGDB's id for Steam in its external game sources
        private const val TOKEN_MARGIN_SECONDS = 60L

        /**
         * Builds the real client from IGDB_CLIENT_ID and IGDB_CLIENT_SECRET.
         * Missing keys do not stop the API from starting; only game requests fail, with a clear message.
         */
        fun fromEnvironment(): IgdbClient = IgdbClient(
            http = igdbHttpClient(),
            clientId = System.getenv("IGDB_CLIENT_ID").orEmpty(),
            clientSecret = System.getenv("IGDB_CLIENT_SECRET").orEmpty(),
        )
    }
}
