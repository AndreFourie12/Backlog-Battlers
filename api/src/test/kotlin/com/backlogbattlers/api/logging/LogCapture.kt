/**
 * POE evidence: API request logging. Every request is logged with a request ID that the Android app
 * also prints in Logcat, so one action can be followed from the phone to the hosted API and the database.
 */
package com.backlogbattlers.api.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/**
 * Test helper that captures Logback logging events emitted during API tests.
 *
 * Attaches a [ListAppender] to the root logger and exposes captured log messages
 * and MDC attributes for test assertions.
 */
class LogCapture : AutoCloseable {
    private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }

    init {
        rootLogger.addAppender(appender)
    }

    /** All captured logging events in arrival order. */
    val events: List<ILoggingEvent>
        get() = appender.list.toList()

    /** Formatted messages of all captured logging events. */
    val messages: List<String>
        get() = events.map { it.formattedMessage }

    /** Returns all values captured for the given MDC property key. */
    fun capturedMdc(key: String): List<String?> =
        events.map { it.mdcPropertyMap[key] }

    /** Detaches the appender and stops recording log events. */
    fun detach() {
        rootLogger.detachAppender(appender)
        appender.stop()
    }

    override fun close() {
        detach()
    }
}
//------------------------------EOF------------------------------\\
