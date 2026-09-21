package com.backlogbattlers.api.db

import com.backlogbattlers.api.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Opens the database connection pool and makes sure every table exists.
 *
 * Configuration comes from environment variables or apikey.properties (see [Config]), so no
 * secret is ever committed:
 *   DB_URL, DB_USER, DB_PASSWORD  (e.g. jdbc:postgresql://host/dbname?sslmode=require)
 */
object DatabaseFactory {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    // H2 in PostgreSQL mode behaves like our production database, so tests stay realistic.
    // DB_CLOSE_DELAY=-1 keeps the in-memory database alive between connections.
    private fun inMemoryUrl(name: String) = "jdbc:h2:mem:$name;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"

    /**
     * Connects to [jdbcUrl] (or a throw-away in-memory database when it is null) and creates the tables.
     * Tests pass their own [memoryName] so each test class gets an isolated database.
     */
    fun init(
        jdbcUrl: String? = Config.default.get("DB_URL"),
        user: String? = Config.default.get("DB_USER"),
        password: String? = Config.default.get("DB_PASSWORD"),
        memoryName: String = "backlog",
    ): Database {
        if (jdbcUrl == null) {
            log.warn("DB_URL is not set - using an in-memory database. Data is lost on restart.")
        }

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl ?: inMemoryUrl(memoryName)
            username = user
            this.password = password
            maximumPoolSize = 5 // free-tier Postgres hosts allow very few connections
        }

        val database = Database.connect(HikariDataSource(config))

        transaction(database) {
            // ponytail: create() only adds missing tables, it never alters existing ones.
            // Adding a column to a live table needs a manual ALTER; move to Flyway if that gets painful.
            SchemaUtils.create(*ALL_TABLES)
        }
        return database
    }
}
