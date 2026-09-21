package com.backlogbattlers.api

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ErrorsTest {

    @Test
    fun `an unexpected error becomes a 500 that reveals nothing about the cause`() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            routing { get("/boom") { error("database password is hunter2") } }
        }

        val response = client.get("/boom")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val body = response.bodyAsText()
        assertEquals("""{"error":"Something went wrong on the server"}""", body)
        assertFalse(body.contains("hunter2"))
    }
}
