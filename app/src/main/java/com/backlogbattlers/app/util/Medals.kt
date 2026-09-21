package com.backlogbattlers.app.util

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.backlogbattlers.app.R


// the ring drawable a rank carries
@DrawableRes
fun medalRingFor(rank: Int): Int? = when (rank) {
    1 -> R.drawable.bg_ring_gold
    2 -> R.drawable.bg_ring_silver
    3 -> R.drawable.bg_ring_bronze
    else -> null
}
//------------------------------


// the color a rank is written in
@ColorRes
fun medalTierColor(rank: Int): Int = when (rank) {
    1 -> R.color.medal_gold
    2 -> R.color.medal_silver
    3 -> R.color.medal_bronze
    else -> R.color.text_tertiary
}
//------------------------------

//------------------------------End Of File------------------------------\\