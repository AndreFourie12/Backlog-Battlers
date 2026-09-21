package com.backlogbattlers.api.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

//------------------------------
// sealed exception hierarchy for Google ID token verification failures.
sealed class GoogleTokenException(message: String) : RuntimeException(message) {

    // thrown when the token's aud claim doesn;t match the expected OAuth client ID
    class AudienceMismatch(message: String = "Google ID token audience mismatch") : GoogleTokenException(message)

    // thrown when the token's exp claim indicates it has expired
    class Expired(message: String = "Google ID token expired") : GoogleTokenException(message)

    // thrown when the response from google's tokeninfo endpoint is invalid.
    class MalformedResponse(message: String = "Google ID token response malformed/ invalid") : GoogleTokenException(message)
}

// represents a verified Google user identity extracted from valid ID token
data class GoogleIdentity(
    val subject: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
)

// response payload from google's tokeninfo endpoint
@Serializable
private data class TokenInfoResponse(
    @SerialName("sub") val sub: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("picture") val picture: String? = null,
    @SerialName("aud") val aud: String? = null,
    @SerialName("exp") val exp: Long? = null,
)

// verifies google ID tokens using google's OAuth2 tokeninfo endpoint
class GoogleIdTokenVerifier(
    private val http: HttpClient,
    private val expectedAudience: String,
) {

    //------------------------------
    // verifies a raw google ID token against google's tokeninfo endpoint and
    // returns the caller's subject, email and display name if valid.
    suspend fun verify(idToken: String): GoogleIdentity {
        val sanitizedToken = cleanTokenInput(idToken)
        if (sanitizedToken.isBlank()) {
            throw GoogleTokenException.MalformedResponse("ID token cannot be blank")
        }

        val response = runCatching {
            http.get(TOKEN_INFO_URL) {
                parameter("id_token", sanitizedToken)
            }
        }.getOrElse { e ->
            throw GoogleTokenException.MalformedResponse("Failed to connect to google tokeninfo: ${e.message}")
        }

        if (!response.status.isSuccess()) {
            throw GoogleTokenException.MalformedResponse("Google tokeninfo returned status ${response.status.value}")
        }

        val tokenInfo = runCatching {
            response.body<TokenInfoResponse>()
        }.getOrElse {
            throw GoogleTokenException.MalformedResponse("Failed to parse google tokeninfo response")
        }

        val sub = tokenInfo.sub
        val email = tokenInfo.email
        val aud = tokenInfo.aud
        val exp = tokenInfo.exp

        if (sub.isNullOrBlank() || email.isNullOrBlank()) {
            throw GoogleTokenException.MalformedResponse("Google tokeninfo response missing sub or email")
        }

        if (aud != expectedAudience) {
            throw GoogleTokenException.AudienceMismatch("Audience '$aud' does not match expected '$expectedAudience'")
        }

        val currentEpochSeconds = System.currentTimeMillis() / 1000
        if (exp != null && exp <= currentEpochSeconds) {
            throw GoogleTokenException.Expired("Token expired at $exp (current time: $currentEpochSeconds)")
        }

        return GoogleIdentity(
            subject = sub,
            email = email,
            displayName = tokenInfo.name ?: email,
            avatarUrl = tokenInfo.picture,
        )
    }

    //------------------------------
    // Removes leading/trailing whitespace and control characters from token input.
    private fun cleanTokenInput(token: String): String {
        return token.trim().filterNot { it.isISOControl() }
    }

    companion object {
        private const val TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo"
    }
}
//------------------------------EOF------------------------------\\