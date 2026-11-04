/**
 * POE evidence: API request logging. Every request is logged with a request ID that the Android app
 * also prints in Logcat, so one action can be followed from the phone to the hosted API and the database.
 */
package com.backlogbattlers.api.logging

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import kotlin.uuid.Uuid

/** Logger used for all request lifecycle log events. */
private val requestLogger = LoggerFactory.getLogger("com.backlogbattlers.api.logging.RequestLogging")

/** Attribute key storing the start timestamp in nanoseconds for calculating request durations. */
private val RequestStartTimeKey = AttributeKey<Long>("RequestStartTime")

/**
 * Stores the caller's user UUID for the current request.
 *
 * Populated by authentication handlers once a caller is verified so that
 * the formatted request log line can identify who initiated the call.
 */
val SignedInUserKey = AttributeKey<Uuid>("SignedInUser")

/** The header name used to pass request tracing IDs between the client and API. */
val REQUEST_ID_HEADER: String = HttpHeaders.XRequestId

/**
 * Validates a client-supplied request ID.
 *
 * An acceptable ID must be between 8 and 64 characters long and consist solely of
 * ASCII letters, numbers, or hyphens. This guards against log-injection attacks,
 * embedded newlines, or malicious text originating from untrusted client headers.
 */
fun isValidRequestId(id: String): Boolean {
    if (id.length !in 8..64) return false
    return id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }
}

/**
 * Configures request tracing and CallLogging for the Ktor application.
 *
 * Request IDs are extracted from the `X-Request-Id` header or generated as 12-character
 * lowercase hexadecimal identifiers when missing or invalid. Every request writes one
 * structured log line upon completion, and requests exceeding [slowRequestMillis] trigger
 * an additional warning line.
 *
 * @param slowRequestMillis Elapsed millisecond threshold before a warning is logged.
 */
fun Application.configureRequestLogging(slowRequestMillis: Long = 1000) {
    // Based on the Ktor Call ID docs: https://ktor.io/docs/server-call-id.html
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        // Generate a 12-character lowercase hex identifier for requests without an ID
        generate { Uuid.random().toString().replace("-", "").take(12) }
        // Verify incoming IDs against strict format rules to discard invalid client values
        verify { isValidRequestId(it) }
        // Echo the resolved request ID back in the response headers
        replyToHeader(HttpHeaders.XRequestId)
    }

    // Based on the Ktor Call Logging docs: https://ktor.io/docs/server-call-logging.html
    install(CallLogging) {
        logger = requestLogger
        level = Level.INFO
        // Place the request ID into SLF4J MDC so all log statements for this request carry callId
        callIdMdc("callId")
        // Cloud platform health monitors ping /health every few seconds; filter them to keep logs clean
        filter { it.request.path() != "/health" }
        // Disable ANSI escape codes so logs render cleanly in cloud hosting dashboards
        disableDefaultColors()
        format { call ->
            val status = call.response.status()?.value
            val method = call.request.httpMethod.value
            val path = call.request.path()
            val durationMs = call.processingTimeMillis()
            val user = call.attributes.getOrNull(SignedInUserKey)?.toString() ?: "-"
            "$status $method $path in ${durationMs}ms user=$user"
        }
    }

    // Plugin to identify unexpectedly slow requests, mostly catching IGDB and Steam lookups
    val slowRequestWarning = createApplicationPlugin(name = "SlowRequestWarning") {
        onCall { call ->
            call.attributes.put(RequestStartTimeKey, System.nanoTime())
        }
        on(ResponseSent) { call ->
            val startTime = call.attributes.getOrNull(RequestStartTimeKey) ?: return@on
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            if (durationMs > slowRequestMillis) {
                requestLogger.warn("Slow request: ${call.request.httpMethod.value} ${call.request.path()} took ${durationMs}ms")
            }
        }
    }
    install(slowRequestWarning)
}
//------------------------------EOF------------------------------\\
