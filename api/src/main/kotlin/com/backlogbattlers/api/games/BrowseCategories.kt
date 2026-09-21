package com.backlogbattlers.api.games

/**
 * The categories behind the app's "Browse by genre" chips.
 *
 * IGDB files a game under genres, themes and game modes, and the chips the app shows do not
 * all live in the same one: "Action" is a theme, "Co-op" is a game mode, the rest are genres.
 * Each category therefore carries its own filter rather than assuming a single field.
 *
 * Nested field filtering follows the Apicalypse docs: https://api-docs.igdb.com/#filters
 */
enum class BrowseCategory(val key: String, val filter: String) {
    ACTION("action", """themes.name = "Action""""),
    RPG("rpg", """genres.name = "Role-playing (RPG)""""),
    PUZZLE("puzzle", """genres.name = "Puzzle""""),
    COOP("coop", """game_modes.name = "Co-operative""""),
    STRATEGY("strategy", """genres.name = "Strategy""""),
    ;

    companion object {

        /** Looks a category up by the key the app sends, ignoring case. Null when it is not one of ours. */
        fun fromKey(key: String): BrowseCategory? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }

        /** The keys the app may send, for the error message when it sends something else. */
        fun keys(): String = entries.joinToString(", ") { it.key }
    }
}
