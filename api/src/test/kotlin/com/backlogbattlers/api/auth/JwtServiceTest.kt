package com.backlogbattlers.api.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtServiceTest {

    private val secret = "unit-test-secret-1234567890"
    private val issuer = "unit-test-issuer"
    private val jwt = JwtService(secret, issuer)

    /** Builds a token by hand, so tests can create the forged and expired ones the service would never issue. */
    private fun token(
        signWith: Algorithm = Algorithm.HMAC256(secret),
        issuer: String = this.issuer,
        type: String? = "access",
        userId: String? = "user-1",
        expiresInMs: Long = 60_000,
    ): String {
        val builder = JWT.create().withIssuer(issuer).withExpiresAt(Date(System.currentTimeMillis() + expiresInMs))
        if (userId != null) builder.withClaim("userId", userId)
        if (type != null) builder.withClaim("type", type)
        return builder.sign(signWith)
    }

    @Test
    fun `a genuine access token gives back its user id`() {
        assertEquals("user-1", jwt.verifyAccessToken(jwt.generateAccessToken("user-1")))
    }

    @Test
    fun `a refresh token is not accepted as an access token`() {
        assertNull(jwt.verifyAccessToken(jwt.generateRefreshToken("user-1")))
    }

    @Test
    fun `forged, expired and foreign tokens are all rejected`() {
        val rejected = mapOf(
            "signed with another secret" to token(signWith = Algorithm.HMAC256("someone-elses-secret")),
            "issued by another issuer" to token(issuer = "another-issuer"),
            "expired a minute ago" to token(expiresInMs = -60_000),
            "no type claim" to token(type = null),
            "wrong type claim" to token(type = "something-else"),
            "no user id claim" to token(userId = null),
            // The classic attack: a token that claims to need no signature at all
            "unsigned (alg none)" to token(signWith = Algorithm.none()),
            "not a token" to "this-is-not-a-token",
            "empty" to "",
            "three dots but no content" to "a.b.c",
        )

        rejected.forEach { (why, value) ->
            assertNull(jwt.verifyAccessToken(value), "should reject a token that is: $why")
        }
    }
}
