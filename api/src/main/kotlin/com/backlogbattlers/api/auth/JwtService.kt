package com.backlogbattlers.api.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.util.Date

// handles jwt access and refresh token generation and verification with HMAC256.
class JwtService(
    private val secret: String,
    private val issuer: String,
) {

    private val algorithm = Algorithm.HMAC256(secret)

    //------------------------------
    // creates a 1 hour jwt access token with the specified userId claim
    fun generateAccessToken(userId: String): String {
        return JWT.create()
            .withIssuer(issuer)
            .withClaim(CLAIM_USER_ID, userId)
            .withClaim(CLAIM_TYPE, TYPE_ACCESS)
            .withExpiresAt(Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION_MS))
            .sign(algorithm)
    }

    //------------------------------
    // creates a 30 day jwt refresh token with the specified userId claim
    // also a type claim marking it specifically as a refresh token
    fun generateRefreshToken(userId: String): String {
        return JWT.create()
            .withIssuer(issuer)
            .withClaim(CLAIM_USER_ID, userId)
            .withClaim(CLAIM_TYPE, TYPE_REFRESH)
            .withExpiresAt(Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRATION_MS))
            .sign(algorithm)
    }

    //------------------------------
    // verifies a jwt refresh token, make sure its valid, unexpired, signed correctly,
    // also carries the refresh type claim/ returns the associated userId
    fun verifyRefreshToken(token: String): String {
        val verifier = JWT.require(algorithm)
            .withIssuer(issuer)
            .withClaim(CLAIM_TYPE, TYPE_REFRESH)
            .build()

        val decoded = try {
            verifier.verify(token)
        } catch (e: JWTVerificationException) {
            throw IllegalArgumentException("Invalid or expired refresh token: ${e.message}", e)
        }

        return decoded.getClaim(CLAIM_USER_ID).asString()
            ?: throw IllegalArgumentException("Refresh token is missing required userId claim")
    }

    //------------------------------
    // verifies a jwt access token: valid signature, right issuer, unexpired and marked as an access token
    // returns the associated userId, or null for any invalid token so callers can answer 401
    // a refresh token is rejected here, because it carries the refresh type claim
    fun verifyAccessToken(token: String): String? {
        val verifier = JWT.require(algorithm)
            .withIssuer(issuer)
            .withClaim(CLAIM_TYPE, TYPE_ACCESS)
            .build()

        return try {
            verifier.verify(token).getClaim(CLAIM_USER_ID).asString()
        } catch (e: JWTVerificationException) {
            null
        }
    }

    companion object {
        private const val CLAIM_USER_ID = "userId"
        private const val CLAIM_TYPE = "type"
        private const val TYPE_ACCESS = "access"
        private const val TYPE_REFRESH = "refresh"
        private const val ACCESS_TOKEN_EXPIRATION_MS = 3_600_000L
        private const val REFRESH_TOKEN_EXPIRATION_MS = 30L * 24 * 3_600_000L
    }
}
//------------------------------EOF------------------------------\\