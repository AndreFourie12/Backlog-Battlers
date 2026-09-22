package com.backlogbattlers.app.util

import androidx.annotation.ColorRes
import com.backlogbattlers.app.R
import kotlin.math.abs

// the fixed set of colours initials avatars are drawn from avatar colour stored here
private val AVATAR_COLORS = intArrayOf(
    R.color.accent,
    R.color.success,
    R.color.gold,
    R.color.destructive,
    R.color.medal_silver,
    R.color.medal_bronze,
)

//------------------------------
// returns the first letter of a display name, uppercased, for an initials avatar
fun initialsFor(displayName: String): String {
    val trimmed = displayName.trim()

    // this if statement falls back to a placeholder when theres no usable character
    if (trimmed.isEmpty()) return "?"
    return trimmed.first().uppercaseChar().toString()
}

//------------------------------
// picks a color for an avatar from a seed, usually a user id, so the same person always gets the same color
@ColorRes
fun avatarColorFor(seed: String): Int {
    val index = abs(seed.hashCode()) % AVATAR_COLORS.size
    return AVATAR_COLORS[index]
}
//------------------------------EOF------------------------------\\