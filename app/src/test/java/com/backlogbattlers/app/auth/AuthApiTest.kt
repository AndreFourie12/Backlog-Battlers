package com.backlogbattlers.app.auth

import com.backlogbattlers.app.data.remote.AuthApi
import com.backlogbattlers.app.data.remote.SessionExpiredException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
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
                respond(body, status)
            },
        )
        return AuthApi(client, baseUrl = "http://10.0.2.2:8080")
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
}
//------------------------------EOF------------------------------\\
