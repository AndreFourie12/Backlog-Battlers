// POE evidence: cloud hosting. The API runs on an Azure Web App with an Azure PostgreSQL database; this file is part of how it is deployed and checked.
package com.backlogbattlers.api

import java.util.Properties

/**
 * Provides build-time metadata such as the application version or commit SHA.
 */
object BuildInfo {
    /**
     * Stamped build version (commit short hash in CI, or "dev" for local builds).
     */
    val version: String = run {
        val stream = BuildInfo::class.java.classLoader.getResourceAsStream("version.properties")
        if (stream == null) {
            "dev"
        } else {
            val props = Properties()
            runCatching {
                stream.use { props.load(it) }
                val v = props.getProperty("version")
                if (v.isNullOrBlank() || v.contains("\${")) "dev" else v
            }.getOrDefault("dev")
        }
    }
}
//------------------------------EOF------------------------------\\

