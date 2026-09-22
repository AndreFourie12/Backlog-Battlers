package com.backlogbattlers.api

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigTest {

    @Test
    fun `the example file lists every setting the API reads and holds no real values`() {
        val example = Config.readFile(Config.findConfigFile("apikey.properties.example"))

        // If the code starts needing a new setting, this fails until the example file mentions it
        val expected = setOf(
            "JWT_SECRET", "JWT_ISSUER", "GOOGLE_OAUTH_CLIENT_ID",
            "IGDB_CLIENT_ID", "IGDB_CLIENT_SECRET", "STEAM_API_KEY",
        )
        assertEquals(expected, example.keys)
        assertTrue(example.values.all { it.isBlank() }, "the example file must never contain real keys")
    }

    private val file = mapOf("IGDB_CLIENT_ID" to "from-file", "ONLY_IN_FILE" to "file-value")

    private fun configWith(env: Map<String, String>) = Config({ env[it] }, file)

    @Test
    fun `an environment variable wins over the file`() {
        val config = configWith(mapOf("IGDB_CLIENT_ID" to "from-environment"))

        assertEquals("from-environment", config.get("IGDB_CLIENT_ID"))
    }

    @Test
    fun `the file is used when there is no environment variable, and a blank one counts as missing`() {
        assertEquals("from-file", configWith(emptyMap()).get("IGDB_CLIENT_ID"))
        assertEquals("from-file", configWith(mapOf("IGDB_CLIENT_ID" to "   ")).get("IGDB_CLIENT_ID"))
        assertEquals("file-value", configWith(emptyMap()).get("ONLY_IN_FILE"))
    }

    @Test
    fun `a setting that is nowhere, or blank in the file, is null`() {
        val config = Config({ null }, mapOf("BLANK_IN_FILE" to ""))

        assertNull(config.get("NOT_ANYWHERE"))
        assertNull(config.get("BLANK_IN_FILE"))
    }

    @Test
    fun `parsing skips comments and blank lines and removes spaces and quotes`() {
        val text = """
            # a comment
            IGDB_CLIENT_ID = abc123

            IGDB_CLIENT_SECRET="quoted-secret"
            STEAM_API_KEY=
        """.trimIndent()

        val values = Config.parse(text)

        assertEquals("abc123", values["IGDB_CLIENT_ID"])
        assertEquals("quoted-secret", values["IGDB_CLIENT_SECRET"])
        assertEquals("", values["STEAM_API_KEY"])
        assertEquals(3, values.size)
    }

    @Test
    fun `a database address keeps its colons and equals signs`() {
        val values = Config.parse("DB_URL=jdbc:postgresql://host:5432/db?sslmode=require&user=me")

        assertEquals("jdbc:postgresql://host:5432/db?sslmode=require&user=me", values["DB_URL"])
    }

    @Test
    fun `a file that does not exist gives no settings and a real file is read`() {
        assertEquals(emptyMap(), Config.readFile(File("no-such-apikey-file.properties")))

        val real = File.createTempFile("apikey", ".properties")
        try {
            real.writeText("STEAM_API_KEY=abc\n")
            assertEquals(mapOf("STEAM_API_KEY" to "abc"), Config.readFile(real))
        } finally {
            real.delete()
        }
    }
}
