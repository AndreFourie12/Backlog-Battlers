// POE evidence: cloud hosting. The API runs on an Azure Web App with an Azure PostgreSQL database; this file is part of how it is deployed and checked.
package com.backlogbattlers.api.db

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DatabaseFactoryProductionTest {

    @Test
    fun `production refuses to start without a database`() {
        val ex = assertFailsWith<IllegalStateException> {
            DatabaseFactory.init(
                jdbcUrl = null,
                appEnv = "production",
                memoryName = "prod-${Uuid.random()}",
            )
        }
        assertTrue(ex.message?.contains("DB_URL") == true, "Exception message must mention DB_URL: ${ex.message}")
    }

    @Test
    fun `development still falls back to the in-memory database`() {
        val database = DatabaseFactory.init(
            jdbcUrl = null,
            appEnv = null,
            memoryName = "dev-${Uuid.random()}",
        )
        assertNotNull(database)
    }
}
//------------------------------EOF------------------------------\\

