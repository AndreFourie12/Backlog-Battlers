package com.backlogbattlers.app.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** Request payload for Google Single Sign-On. */
@Serializable
data class SsoGoogleRequest(
    val idToken: String,
)

/** User object payload returned from the authentication backend. */
@Serializable
data class UserDto(
    val userId: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String?,
    val isNewUser: Boolean,
)

/** Response payload for Google Single Sign-On authentication. */
@Serializable
data class SsoGoogleResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto,
)

/** Request payload for refreshing an access token. */
@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

/** Response payload containing a newly issued access token. */
@Serializable
data class RefreshResponse(
    val accessToken: String,
    val expiresIn: Long,
)

/**
 * Dedicated API client for backend authentication endpoints.
 */
class AuthApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {

    //------------------------------
    // POSTs Google ID token to the backend SSO endpoint and returns authentication payload.
    suspend fun ssoGoogle(idToken: String): SsoGoogleResponse {
        val response = httpClient.post("$baseUrl/auth/sso/google") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(SsoGoogleRequest(idToken))
        }
        if (!response.status.isSuccess()) {
            val errorText = runCatching { response.body<String>() }.getOrDefault("HTTP ${response.status.value}")
            Log.e("AuthApi", "SSO Google request failed [${response.status.value}]: $errorText")
            throw IllegalStateException("Server returned HTTP ${response.status.value}: $errorText")
        }
        return response.body()
    }

    //------------------------------
    // POSTs refresh token to the backend refresh endpoint and returns new access token response.
    suspend fun refresh(refreshToken: String): RefreshResponse {
        val response = httpClient.post("$baseUrl/auth/refresh") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }
        if (!response.status.isSuccess()) {
            val errorText = runCatching { response.body<String>() }.getOrDefault("HTTP ${response.status.value}")
            Log.e("AuthApi", "Refresh token request failed [${response.status.value}]: $errorText")
            throw IllegalStateException("Server returned HTTP ${response.status.value}: $errorText")
        }
        return response.body()
    }
}
