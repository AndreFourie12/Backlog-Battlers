package com.backlogbattlers.api.games

enum class BrowseCategory(val key: String, val filter: String) {
    ACTION("action", """themes.name = "Action""""),
    RPG("rpg", """genres.name = "Role-playing (RPG)""""),
    PUZZLE("puzzle", """genres.name = "Puzzle""""),
    COOP("coop", """game_modes.name = "Co-operative""""),
    STRATEGY("strategy", """genres.name = "Strategy""""),
    ;

    companion object {

        fun fromKey(key: String): BrowseCategory? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }

        fun keys(): String = entries.joinToString(", ") { it.key }
    }
}
