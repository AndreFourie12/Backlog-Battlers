package com.backlogbattlers.api

import org.slf4j.LoggerFactory
import java.io.File
import java.io.StringReader
import java.util.Properties

/**
 * Where the API gets its settings and secrets (IGDB and Steam keys, database details).
 *
 * A setting is looked up in two places, in this order:
 *  1. an environment variable, which is what a real server uses;
 *  2. `apikey.properties` in the folder the API runs from, which is convenient on a
 *     developer laptop. That file is ignored by git, so real keys never get committed.
 *
 * A blank value counts as "not set".
 */
class Config(
    private val environment: (String) -> String?,
    private val fileValues: Map<String, String>,
) {
    /** The value of [name], or null when it is not set anywhere. */
    fun get(name: String): String? =
        environment(name)?.takeIf { it.isNotBlank() } ?: fileValues[name]?.takeIf { it.isNotBlank() }

    companion object {
        const val FILE_NAME = "apikey.properties"
        private val log = LoggerFactory.getLogger(Config::class.java)

        /** The real configuration, read once: environment variables, then [FILE_NAME]. */
        val default: Config by lazy { Config(System::getenv, readFile(findConfigFile(FILE_NAME))) }

        /** Resolves configuration files whether running from the api subfolder or repository root. */
        fun findConfigFile(name: String): File =
            listOf(File(name), File("api", name), File("..", name)).firstOrNull { it.isFile } ?: File(name)

        /** The settings in [file], or none when it does not exist. */
        fun readFile(file: File): Map<String, String> {
            if (!file.isFile) return emptyMap()
            val values = parse(file.readText())
            // Only the names are logged, never the values
            log.info("Read {} settings from {}: {}", values.size, file.name, values.keys.sorted().joinToString())
            return values
        }

        /**
         * Reads `NAME=value` lines. Blank lines and `#` comments are skipped, and spaces and
         * surrounding double quotes are removed, so `KEY="abc"` and `KEY=abc` are the same.
         */
        fun parse(text: String): Map<String, String> {
            val properties = Properties().apply { load(StringReader(text)) }
            return properties.stringPropertyNames().associateWith {
                properties.getProperty(it).trim().removeSurrounding("\"")
            }
        }
    }
}
