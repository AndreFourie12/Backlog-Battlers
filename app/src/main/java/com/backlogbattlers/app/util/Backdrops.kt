package com.backlogbattlers.app.util

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.HomeBackdrop


// the artwork each home backdrop choice maps to.
// the domain enum stays free of resource ids, the mapping lives here with the
// rest of the ui helpers


// the drawable drawn behind the home screen header
@DrawableRes
fun backdropArt(backdrop: HomeBackdrop): Int = when (backdrop) {
    HomeBackdrop.HILLS -> R.drawable.img_home_backdrop
    HomeBackdrop.MOUNTAIN -> R.drawable.img_login_backdrop
    HomeBackdrop.MEADOW -> R.drawable.img_backdrop_meadow
    HomeBackdrop.FOREST -> R.drawable.img_backdrop_forest
    HomeBackdrop.COAST -> R.drawable.img_backdrop_coast
}
//------------------------------


// the name shown under the thumbnail on the appearance screen
@StringRes
fun backdropLabel(backdrop: HomeBackdrop): Int = when (backdrop) {
    HomeBackdrop.HILLS -> R.string.backdrop_hills
    HomeBackdrop.MOUNTAIN -> R.string.backdrop_mountain
    HomeBackdrop.MEADOW -> R.string.backdrop_meadow
    HomeBackdrop.FOREST -> R.string.backdrop_forest
    HomeBackdrop.COAST -> R.string.backdrop_coast
}
//------------------------------

//------------------------------End Of File------------------------------\\
