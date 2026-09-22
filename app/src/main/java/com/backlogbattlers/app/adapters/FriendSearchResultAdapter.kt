package com.backlogbattlers.app.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.FriendRelationshipStatus
import com.backlogbattlers.app.domain.model.FriendSearchResult
import com.backlogbattlers.app.util.avatarColorFor
import com.backlogbattlers.app.util.initialsFor

// adapter for displaying search results when finding new friends to add
class FriendSearchResultAdapter(
    private val onSendRequest: (FriendSearchResult) -> Unit,
) : ListAdapter<FriendSearchResult, FriendSearchResultAdapter.ViewHolder>(DiffCallback) {

    // viewholder wrapping views for a single friend search result row
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAvatarInitial: TextView = itemView.findViewById(R.id.tv_avatar_initial)
        val tvFriendName: TextView = itemView.findViewById(R.id.tv_friend_name)
        val btnSendRequest: Button = itemView.findViewById(R.id.btn_send_request)
    }

    // diffutil callback comparing friend search result items for recyclerview updates
    private object DiffCallback : DiffUtil.ItemCallback<FriendSearchResult>() {

        //------------------------------
        // checks if two search result items represent the same user by id
        override fun areItemsTheSame(oldItem: FriendSearchResult, newItem: FriendSearchResult): Boolean = oldItem.userId == newItem.userId

        //------------------------------
        // checks if all properties of two search result items are identical
        override fun areContentsTheSame(oldItem: FriendSearchResult, newItem: FriendSearchResult): Boolean = oldItem == newItem
    }

    //------------------------------
    // inflates the friend search result item layout and creates a viewholder instance
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend_search_result, parent, false)
        return ViewHolder(view)
    }

    //------------------------------
    // binds friend search result data to the viewholder and configures button state from relationship status
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = getItem(position)
        val context = holder.itemView.context

        holder.tvAvatarInitial.text = initialsFor(result.displayName)
        val colorRes = avatarColorFor(result.userId)
        holder.tvAvatarInitial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))

        holder.tvFriendName.text = result.displayName

        // this when expression configures the action button text, style, and click listener based on relationship status
        when (result.status) {
            FriendRelationshipStatus.NONE -> {
                holder.btnSendRequest.text = context.getString(R.string.add)
                holder.btnSendRequest.isEnabled = true
                holder.btnSendRequest.setBackgroundResource(R.drawable.bg_button_accent)
                holder.btnSendRequest.setTextColor(ContextCompat.getColor(context, R.color.page_background))
                holder.btnSendRequest.setOnClickListener { onSendRequest(result) }
            }
            FriendRelationshipStatus.PENDING_SENT, FriendRelationshipStatus.PENDING_RECEIVED -> {
                holder.btnSendRequest.text = context.getString(R.string.pending)
                holder.btnSendRequest.isEnabled = false
                holder.btnSendRequest.setBackgroundResource(R.drawable.bg_pill_outline)
                holder.btnSendRequest.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                holder.btnSendRequest.setOnClickListener(null)
            }
            FriendRelationshipStatus.FRIENDS -> {
                holder.btnSendRequest.text = context.getString(R.string.already_friends)
                holder.btnSendRequest.isEnabled = false
                holder.btnSendRequest.setBackgroundResource(R.drawable.bg_pill_outline)
                holder.btnSendRequest.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
                holder.btnSendRequest.setOnClickListener(null)
            }
        }
    }
}
//------------------------------EOF------------------------------\\