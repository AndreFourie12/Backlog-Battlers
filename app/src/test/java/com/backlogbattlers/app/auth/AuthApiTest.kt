package com.backlogbattlers.app.auth

import com.backlogbattlers.app.data.remote.AuthApi
import com.backlogbattlers.app.data.remote.ProfileRejectedException
import com.backlogbattlers.app.data.remote.SessionExpiredException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

// the delete account call: what it sends, and how each backend answer is read
class AuthApiTest {

    private val seen = mutableListOf<HttpRequestData>()

    private fun api(status: HttpStatusCode, body: String = ""): AuthApi {
        val client = HttpClient(
            MockEngine { request ->
                seen += request
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) {
            // the refresh call sends a JSON body
            install(ContentNegotiation) { json() }
        }
        return AuthApi(client, baseUrl = "https://api.example.test")
    }

    @Test
    fun `deleting the account sends DELETE users me with the access token`() = runBlocking {
        api(HttpStatusCode.NoContent).deleteAccount("token-123")

        val request = seen.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/users/me", request.url.encodedPath)
        assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `an account that is already gone counts as deleted`() = runBlocking {
        // no exception: the 404 means there is nothing left to delete
        api(HttpStatusCode.NotFound, """{"error":"Account was not found"}""").deleteAccount("token-123")
    }

    @Test
    fun `a rejected session asks the user to sign in again`() {
        assertThrows(SessionExpiredException::class.java) {
            runBlocking { api(HttpStatusCode.Unauthorized).deleteAccount("expired-token") }
        }
    }

    @Test
    fun `any other failure is an error`() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { api(HttpStatusCode.InternalServerError).deleteAccount("token-123") }
        }
    }

    private val savedMe = """{"userId":"u1","displayName":"Sam","avatarUrl":null,"pictureId":"pfp_03"}"""

    @Test
    fun `saving the profile sends PATCH users me with the token, and only the fields that were given`() = runBlocking {
        val saved = api(HttpStatusCode.OK, savedMe).updateProfile("token-123", displayName = "Sam", pictureId = null)

        val request = seen.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/users/me", request.url.encodedPath)
        assertEquals("Bearer token-123", request.headers[HttpHeaders.Authorization])
        // a field that is not given goes as null, which the backend reads as "leave it"
        assertEquals("""{"displayName":"Sam","pictureId":null}""", (request.body as TextContent).text)
        assertEquals("Sam", saved.displayName)
        assertEquals("pfp_03", saved.pictureId)
    }

    @Test
    fun `a picture is sent by id, and an empty id takes it away`() = runBlocking {
        api(HttpStatusCode.OK, savedMe).updateProfile("token-123", displayName = null, pictureId = "pfp_03")
        api(HttpStatusCode.OK, savedMe).updateProfile("token-123", displayName = null, pictureId = "")

        assertEquals("""{"displayName":null,"pictureId":"pfp_03"}""", (seen[0].body as TextContent).text)
        assertEquals("""{"displayName":null,"pictureId":""}""", (seen[1].body as TextContent).text)
    }

    @Test
    fun `a refused profile, a dead session and a server error are told apart`() {
        assertThrows(ProfileRejectedException::class.java) {
            runBlocking { api(HttpStatusCode.BadRequest, """{"error":"A display name is 1 to 20 characters"}""").updateProfile("t", "x", null) }
        }
        assertThrows(SessionExpiredException::class.java) {
            runBlocking { api(HttpStatusCode.Unauthorized).updateProfile("t", "x", null) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { api(HttpStatusCode.InternalServerError).updateProfile("t", "x", null) }
        }
    }

    @Test
    fun `a refresh token the backend rejects ends the session, a server error does not`() {
        // 401 is the backend saying this refresh token is no good: the user has to sign in again
        assertThrows(SessionExpiredException::class.java) {
            runBlocking { api(HttpStatusCode.Unauthorized).refresh("old-refresh-token") }
        }
        // anything else may pass, so it must not look like a dead session
        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { api(HttpStatusCode.InternalServerError).refresh("old-refresh-token") }
        }
        assertEquals(false, failure is SessionExpiredException)
    }
}
//------------------------------EOF------------------------------\\
