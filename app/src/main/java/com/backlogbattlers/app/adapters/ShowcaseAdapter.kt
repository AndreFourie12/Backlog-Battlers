package com.backlogbattlers.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.ShowcaseAchievement

// the profile's fourteen showcase slots, two rows of seven: the user's rarest achievements first,
// rarest at the top left, and a dashed empty slot for each one they have not got yet
class ShowcaseAdapter(private val onClick: (ShowcaseAchievement) -> Unit) : RecyclerView.Adapter<ShowcaseAdapter.ViewHolder>() {

    var achievements: List<ShowcaseAchievement> = emptyList()
        set(value) {
            field = value.take(SLOTS)
            notifyDataSetChanged()
        }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivTile: ImageView = itemView.findViewById(R.id.ivShowcaseTile)
    }

    override fun getItemCount(): Int = SLOTS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_showcase_tile, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val achievement = achievements.getOrNull(position)
        if (achievement == null) {
            holder.ivTile.setImageDrawable(null)
            holder.ivTile.setBackgroundResource(R.drawable.bg_slot_dashed)
            holder.ivTile.contentDescription = null
            holder.ivTile.setOnClickListener(null)
            holder.ivTile.isClickable = false
            return
        }
        holder.ivTile.setBackgroundResource(R.drawable.bg_tile_gold_glow)
        // Steam gives most achievements an icon; the star stands in for the ones it does not, or that fail to load
        holder.ivTile.load(achievement.iconUrl) {
            placeholder(R.drawable.ic_star)
            error(R.drawable.ic_star)
        }
        holder.ivTile.contentDescription = achievement.name
        holder.ivTile.setOnClickListener { onClick(achievement) }
    }

    companion object {
        // the profile shows two rows of seven
        const val COLUMNS = 7
        const val SLOTS = 14
    }
}
//------------------------------EOF------------------------------\
