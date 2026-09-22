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
import com.backlogbattlers.app.domain.model.FriendRequest
import com.backlogbattlers.app.util.avatarColorFor
import com.backlogbattlers.app.util.initialsFor

// adapter for displaying incoming friend requests with accept and reject actions
class RequestAdapter(
    private val onAccept: (FriendRequest) -> Unit,
    private val onReject: (FriendRequest) -> Unit,
) : ListAdapter<FriendRequest, RequestAdapter.ViewHolder>(DiffCallback) {

    // viewholder wrapping views for a single friend request item
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAvatarInitial: TextView = itemView.findViewById(R.id.tv_avatar_initial)
        val tvFriendName: TextView = itemView.findViewById(R.id.tv_friend_name)
        val btnAccept: Button = itemView.findViewById(R.id.btn_accept)
        val btnReject: Button = itemView.findViewById(R.id.btn_reject)
    }

    // diffutil callback comparing friend request items for efficient recyclerview updates
    private object DiffCallback : DiffUtil.ItemCallback<FriendRequest>() {
        //------------------------------
        // checks if two friend requests represent the same request by id
        override fun areItemsTheSame(oldItem: FriendRequest, newItem: FriendRequest): Boolean = oldItem.requestId == newItem.requestId

        //------------------------------
        // checks if all properties of two friend requests are identical
        override fun areContentsTheSame(oldItem: FriendRequest, newItem: FriendRequest): Boolean = oldItem == newItem
    }

    //------------------------------
    // inflates the friend request item layout and creates a viewholder instance
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend_request, parent, false)
        return ViewHolder(view)
    }

    //------------------------------
    // binds friend request data to the viewholder and attaches click listeners
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = getItem(position)
        val context = holder.itemView.context

        holder.tvAvatarInitial.text = initialsFor(request.fromDisplayName)
        val colorRes = avatarColorFor(request.fromUserId)
        holder.tvAvatarInitial.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))

        holder.tvFriendName.text = request.fromDisplayName

        holder.btnAccept.setOnClickListener { onAccept(request) }
        holder.btnReject.setOnClickListener { onReject(request) }
    }
}
//------------------------------EOF------------------------------\\