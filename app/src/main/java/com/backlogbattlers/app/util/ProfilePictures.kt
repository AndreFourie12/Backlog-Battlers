package com.backlogbattlers.app.util

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.backlogbattlers.app.R

// one picture a user can pick for their profile. the id is what gets saved, so it must never
// change once released; the drawable behind it can
data class ProfilePicture(
    val id: String,
    @DrawableRes val drawable: Int,
)

// the pictures users choose from on the edit profile screen, in the order the picker shows them.
// to add one: put a square image (320px, cropped to a circle on screen) in drawable-nodpi and list it here
val PROFILE_PICTURES: List<ProfilePicture> = listOf(
    ProfilePicture("pfp_01", R.drawable.pfp_01),
    ProfilePicture("pfp_02", R.drawable.pfp_02),
    ProfilePicture("pfp_03", R.drawable.pfp_03),
    ProfilePicture("pfp_04", R.drawable.pfp_04),
    ProfilePicture("pfp_05", R.drawable.pfp_05),
    ProfilePicture("pfp_06", R.drawable.pfp_06),
    ProfilePicture("pfp_07", R.drawable.pfp_07),
    ProfilePicture("pfp_08", R.drawable.pfp_08),
    ProfilePicture("pfp_09", R.drawable.pfp_09),
    ProfilePicture("pfp_10", R.drawable.pfp_10),
    ProfilePicture("pfp_11", R.drawable.pfp_11),
    ProfilePicture("pfp_12", R.drawable.pfp_12),
    ProfilePicture("pfp_13", R.drawable.pfp_13),
    ProfilePicture("pfp_14", R.drawable.pfp_14),
    ProfilePicture("pfp_15", R.drawable.pfp_15),
    ProfilePicture("pfp_16", R.drawable.pfp_16),
    ProfilePicture("pfp_17", R.drawable.pfp_17),
    ProfilePicture("pfp_18", R.drawable.pfp_18),
    ProfilePicture("pfp_19", R.drawable.pfp_19),
    ProfilePicture("pfp_20", R.drawable.pfp_20),
    ProfilePicture("pfp_21", R.drawable.pfp_21),
    ProfilePicture("pfp_22", R.drawable.pfp_22),
    ProfilePicture("pfp_23", R.drawable.pfp_23),
    ProfilePicture("pfp_24", R.drawable.pfp_24),
    ProfilePicture("pfp_25", R.drawable.pfp_25),
    ProfilePicture("pfp_26", R.drawable.pfp_26),
    ProfilePicture("pfp_27", R.drawable.pfp_27),
    ProfilePicture("pfp_28", R.drawable.pfp_28),
    ProfilePicture("pfp_29", R.drawable.pfp_29),
    ProfilePicture("pfp_30", R.drawable.pfp_30),
    ProfilePicture("pfp_31", R.drawable.pfp_31),
    ProfilePicture("pfp_32", R.drawable.pfp_32),
)

//------------------------------
// the picture saved under id, or null when none was picked or it is no longer offered
fun profilePictureFor(id: String?): ProfilePicture? = PROFILE_PICTURES.firstOrNull { it.id == id }

//------------------------------
// fills a partial_profile_avatar: the picked picture in the circle, or else the first letter of
// the user's name on the accent disc
fun View.bindProfileAvatar(pictureId: String?, displayName: String) {
    val picture = profilePictureFor(pictureId)
    val pictureView = findViewById<ImageView>(R.id.ivAvatarPicture)
    val initialView = findViewById<TextView>(R.id.tvAvatarInitial)

    initialView.text = initialsFor(displayName)
    if (picture != null) pictureView.setImageResource(picture.drawable) else pictureView.setImageDrawable(null)
    pictureView.setVisible(picture != null)
    initialView.setVisible(picture == null)
}
//------------------------------EOF------------------------------\\
