package com.backlogbattlers.app.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.BadgeProgress
import com.backlogbattlers.app.util.badgeInfo
import com.backlogbattlers.app.util.setVisible

// the profile's badge grid. an earned badge is filled in gold; one still to earn is a dashed
// outline with how far along it is (when it takes more than one thing). tapping one calls onClick
class BadgeAdapter(private val onClick: (BadgeProgress) -> Unit) : ListAdapter<BadgeProgress, BadgeAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.ivBadgeIcon)
        val tvLabel: TextView = itemView.findViewById(R.id.tvBadgeLabel)
        val tvProgress: TextView = itemView.findViewById(R.id.tvBadgeProgress)
    }

    private object DiffCallback : DiffUtil.ItemCallback<BadgeProgress>() {
        override fun areItemsTheSame(oldItem: BadgeProgress, newItem: BadgeProgress): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BadgeProgress, newItem: BadgeProgress): Boolean = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_badge, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val badge = getItem(position)
        val context = holder.itemView.context
        val info = badgeInfo(badge.id)

        holder.ivIcon.setImageResource(info.icon)
        holder.ivIcon.setBackgroundResource(if (badge.earned) R.drawable.bg_circle_gold_tint else R.drawable.bg_circle_dashed)
        ImageViewCompat.setImageTintList(
            holder.ivIcon,
            ColorStateList.valueOf(ContextCompat.getColor(context, if (badge.earned) R.color.gold else R.color.text_tertiary)),
        )
        holder.tvLabel.setText(info.name)
        holder.tvLabel.setTextColor(ContextCompat.getColor(context, if (badge.earned) R.color.text_primary else R.color.text_tertiary))

        // a badge that is one thing to do needs no "0 / 1" under it
        val showProgress = !badge.earned && badge.target > 1
        holder.tvProgress.setVisible(showProgress)
        if (showProgress) holder.tvProgress.text = context.getString(R.string.profile_badge_progress_short, badge.progress, badge.target)

        holder.itemView.setOnClickListener { onClick(badge) }
    }
}
//------------------------------EOF------------------------------\
