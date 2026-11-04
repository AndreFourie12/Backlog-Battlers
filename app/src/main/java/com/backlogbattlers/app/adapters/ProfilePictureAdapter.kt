package com.backlogbattlers.app.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.util.ProfilePicture
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.setVisible
import com.google.android.material.imageview.ShapeableImageView

// the grid of pictures in the edit profile picker. the one already picked is ringed and ticked;
// tapping any picture hands it back and the sheet closes
class ProfilePictureAdapter(
    private val pictures: List<ProfilePicture>,
    private val selectedId: String?,
    private val onPick: (ProfilePicture) -> Unit,
) : RecyclerView.Adapter<ProfilePictureAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateChild(R.layout.item_profile_picture))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(pictures[position], position)
    }

    override fun getItemCount(): Int = pictures.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ShapeableImageView = view.findViewById(R.id.ivProfilePictureOption)
        private val check: ImageView = view.findViewById(R.id.ivProfilePictureCheck)

        fun bind(picture: ProfilePicture, position: Int) {
            val selected = picture.id == selectedId
            image.setImageResource(picture.drawable)
            image.strokeWidth = if (selected) itemView.resources.getDimension(R.dimen.profile_picture_ring) else 0f
            image.contentDescription = itemView.context.getString(R.string.cd_profile_picture_option, position + 1)
            image.isSelected = selected
            check.setVisible(selected)
            image.setOnClickListener { onPick(picture) }
        }
    }
}
//------------------------------EOF------------------------------\\
