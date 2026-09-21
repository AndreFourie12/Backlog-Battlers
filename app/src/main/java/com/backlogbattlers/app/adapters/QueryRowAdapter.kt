package com.backlogbattlers.app.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.setVisible

data class QueryRow(
    val label: String,
    val kind: Kind,
    val gameId: Int? = null,
) {
    enum class Kind { RECENT, TRENDING }
}

class QueryRowAdapter(
    private val onClick: (QueryRow) -> Unit,
    private val onRemove: (QueryRow) -> Unit = {},
) : ListAdapter<QueryRow, QueryRowAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateChild(R.layout.item_query_row), onClick, onRemove)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        view: View,
        private val onClick: (QueryRow) -> Unit,
        private val onRemove: (QueryRow) -> Unit,
    ) : RecyclerView.ViewHolder(view) {

        private val icon: ImageView = view.findViewById(R.id.img_query_icon)
        private val label: TextView = view.findViewById(R.id.tv_query_label)
        private val remove: ImageView = view.findViewById(R.id.btn_remove_query)

        fun bind(row: QueryRow) {
            label.text = row.label

            val isRecent = row.kind == QueryRow.Kind.RECENT
            icon.setImageResource(if (isRecent) R.drawable.ic_history else R.drawable.ic_trending_up)
            icon.imageTintList = ContextCompat.getColorStateList(
                icon.context,
                if (isRecent) R.color.text_tertiary else R.color.success,
            )

            remove.setVisible(isRecent)
            remove.setOnClickListener { onRemove(row) }

            itemView.setOnClickListener { onClick(row) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<QueryRow>() {
        override fun areItemsTheSame(oldItem: QueryRow, newItem: QueryRow): Boolean {
            return oldItem.kind == newItem.kind && oldItem.label == newItem.label
        }

        override fun areContentsTheSame(oldItem: QueryRow, newItem: QueryRow): Boolean {
            return oldItem == newItem
        }
    }
}
