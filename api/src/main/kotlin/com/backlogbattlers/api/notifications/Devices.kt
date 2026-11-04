package com.backlogbattlers.api.notifications

import com.backlogbattlers.api.db.Devices
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant
import kotlin.uuid.Uuid

/** Real FCM tokens are about 160 characters; this leaves room without letting anything be stored. */
const val MAX_TOKEN_LENGTH = 512

/** Body of `POST /devices`: this phone's Firebase Cloud Messaging token. */
@Serializable
data class RegisterDeviceRequest(val token: String)

/** What is wrong with [token], or null when it can be stored. */
fun problemWithToken(token: String): String? = when {
    token.isBlank() -> "token is required"
    token.length > MAX_TOKEN_LENGTH -> "token must be at most $MAX_TOKEN_LENGTH characters"
    else -> null
}

/**
 * Remembers that [token] reaches [userId]. A token belongs to one app install, so if it was
 * stored for someone else (another account signed in on the same phone) it moves to [userId].
 */
fun registerDevice(userId: Uuid, token: String): Unit = transaction {
    Devices.upsert {
        it[Devices.token] = token
        it[Devices.userId] = userId
        it[updatedAt] = Instant.now()
    }
}

/** Forgets [token] for [userId], on sign-out. Another user's token is left alone. */
fun removeDevice(userId: Uuid, token: String): Unit = transaction {
    Devices.deleteWhere { (Devices.token eq token) and (Devices.userId eq userId) }
}

/** Every token that reaches [userId], one per phone they are signed in on. */
fun tokensFor(userId: Uuid): List<String> = transaction {
    Devices.selectAll().where { Devices.userId eq userId }.map { it[Devices.token] }
}

/** Drops tokens FCM says no longer exist (the app was uninstalled, or the token was replaced). */
fun forgetTokens(tokens: Collection<String>) {
    if (tokens.isEmpty()) return
    transaction { Devices.deleteWhere { Devices.token inList tokens } }
}
//------------------------------EOF------------------------------\\
