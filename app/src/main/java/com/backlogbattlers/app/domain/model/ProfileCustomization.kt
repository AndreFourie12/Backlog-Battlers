package com.backlogbattlers.app.domain.model

// how a user has set up their profile: the picture they picked (null shows the initial of their
// name) and which of the optional sections friends get to see. everything else on the profile
// (level, points, league position, recent activity) is always shown
data class ProfileCustomization(
    val pictureId: String? = null,
    val showBadges: Boolean = true,
    val showShowcase: Boolean = true,
)
//------------------------------EOF------------------------------\\
