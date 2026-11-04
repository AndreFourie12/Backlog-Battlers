package com.backlogbattlers.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

// body of POST /devices
@Serializable
data class RegisterDeviceBody(val token: String)

/**
 * API client for the phones the signed-in user gets push notifications on. The token is this
 * phone's Firebase Cloud Messaging registration token.
 */
class DevicesApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {

    //------------------------------
    // tells the API pushes for the signed-in user can be sent to this phone
    suspend fun register(accessToken: String, fcmToken: String) {
        httpClient.post("$baseUrl/devices") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(RegisterDeviceBody(fcmToken))
        }.throwIfFailed()
    }

    //------------------------------
    // stops pushes for the signed-in user reaching this phone, on sign-out
    suspend fun unregister(accessToken: String, fcmToken: String) {
        httpClient.delete("$baseUrl/devices/${fcmToken.encodeURLPathPart()}") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }.throwIfFailed()
    }

    private suspend fun HttpResponse.throwIfFailed() {
        if (status == HttpStatusCode.Unauthorized) throw SessionExpiredException()
        if (!status.isSuccess()) {
            val errorText = runCatching { body<String>() }.getOrDefault("")
            throw IllegalStateException("Server returned HTTP ${status.value}: $errorText")
        }
    }
}
//------------------------------EOF------------------------------\\
