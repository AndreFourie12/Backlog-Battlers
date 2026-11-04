package com.backlogbattlers.api

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HealthTest {

    @Test
    fun `health endpoint reports ok`() = testApplication {
        application { module() }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok","database":"ok","version":"dev"}""", response.bodyAsText())
    }

    @Test
    fun `build info reports non-blank dev version in test build`() {
        assertEquals("dev", BuildInfo.version)
        assertFalse(BuildInfo.version.isBlank())
    }
}
