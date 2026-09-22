package com.backlogbattlers.app.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.Achievement
import com.backlogbattlers.app.util.formatRarity
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.loadArtwork
import com.backlogbattlers.app.viewmodels.AchievementUiItem

// adapter for the game detail screen's achievement list: one row per achievement, showing Steam's
// own icon art when the server has some, or an empty ring/check when it does not. Tapping the
// icon unlocks (or locks back) whichever achievement its row is showing
class AchievementAdapter(
    private val onToggle: (achievementId: String) -> Unit,
) : ListAdapter<AchievementUiItem, AchievementAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateChild(R.layout.item_achievement), onToggle)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        view: View,
        private val onToggle: (achievementId: String) -> Unit,
    ) : RecyclerView.ViewHolder(view) {

        private val icon: ImageView = view.findViewById(R.id.ivAchievementIcon)
        private val name: TextView = view.findViewById(R.id.tvAchievementName)
        private val description: TextView = view.findViewById(R.id.tvAchievementDescription)
        private val rarity: TextView = view.findViewById(R.id.tvAchievementRarity)
        private val status: TextView = view.findViewById(R.id.tvAchievementDate)
        private val checkbox: CheckBox = view.findViewById(R.id.cbAchievementUnlock)

        fun bind(item: AchievementUiItem) {
            val context = itemView.context
            val achievement = item.achievement

            name.text = achievement.name
            description.text = achievement.description
            description.visibility = if (achievement.description.isNullOrBlank()) View.GONE else View.VISIBLE
            rarity.text = achievement.rarityPercent?.let { formatRarity(it) } ?: context.getString(R.string.rarity_unknown)

            bindIcon(icon, achievement, unlocked = item.unlocked)

            status.setText(if (item.unlocked) R.string.unlocked else R.string.locked)
            icon.contentDescription = status.text

            icon.setOnClickListener { onToggle(achievement.achievementId) }

            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = item.unlocked
            checkbox.setOnClickListener { onToggle(achievement.achievementId) }
        }

        // Steam's own icon art when the server has some (needs a STEAM_API_KEY), the plain
        // check/ring otherwise. A locked achievement prefers its grayscale icon, falling back to
        // the colour one rather than showing nothing if Steam only ever sent one of the two
        private fun bindIcon(icon: ImageView, achievement: Achievement, unlocked: Boolean) {
            val context = icon.context
            val artUrl = if (unlocked) achievement.iconUrl else achievement.iconGrayUrl ?: achievement.iconUrl

            if (artUrl != null) {
                icon.scaleType = ImageView.ScaleType.CENTER_CROP
                icon.setPadding(0, 0, 0, 0)
                icon.imageTintList = null
                icon.setBackgroundResource(R.drawable.bg_square_surface)
                icon.loadArtwork(artUrl)
                return
            }

            val padding = context.resources.getDimensionPixelSize(R.dimen.achievement_icon_padding)
            icon.scaleType = ImageView.ScaleType.FIT_CENTER
            icon.setPadding(padding, padding, padding, padding)
            if (unlocked) {
                icon.setImageResource(R.drawable.ic_check)
                icon.setBackgroundResource(R.drawable.bg_square_success_tint)
                icon.imageTintList = ContextCompat.getColorStateList(context, R.color.success)
            } else {
                icon.setImageDrawable(null)
                icon.imageTintList = null
                icon.setBackgroundResource(R.drawable.bg_square_muted)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<AchievementUiItem>() {
        override fun areItemsTheSame(oldItem: AchievementUiItem, newItem: AchievementUiItem): Boolean {
            return oldItem.achievement.achievementId == newItem.achievement.achievementId
        }

        override fun areContentsTheSame(oldItem: AchievementUiItem, newItem: AchievementUiItem): Boolean {
            return oldItem == newItem
        }
    }
}
//------------------------------EOF------------------------------\\
