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

//------------------------------
// one row of the resting search screen. a recent row replays a search the user
// ran before and can be removed, a trending row opens the game it names
data class QueryRow(
    val label: String,
    val kind: Kind,
    // set on trending rows, so tapping one goes straight to that game
    val gameId: Int? = null,
) {
    enum class Kind { RECENT, TRENDING }
}

// adapter for the recent and trending lists under the search box
class QueryRowAdapter(
    private val onClick: (QueryRow) -> Unit,
    private val onRemove: (QueryRow) -> Unit = {},
) : ListAdapter<QueryRow, QueryRowAdapter.ViewHolder>(DiffCallback) {

    //------------------------------
    // inflates one item_query_row
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateChild(R.layout.item_query_row), onClick, onRemove)
    }

    //------------------------------
    // fills the row at position with its query
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // holds the views of one row
    class ViewHolder(
        view: View,
        private val onClick: (QueryRow) -> Unit,
        private val onRemove: (QueryRow) -> Unit,
    ) : RecyclerView.ViewHolder(view) {

        private val icon: ImageView = view.findViewById(R.id.img_query_icon)
        private val label: TextView = view.findViewById(R.id.tv_query_label)
        private val remove: ImageView = view.findViewById(R.id.btn_remove_query)

        //------------------------------
        // shows the query, and the icon and trailing x that suit its kind
        fun bind(row: QueryRow) {
            label.text = row.label

            val isRecent = row.kind == QueryRow.Kind.RECENT
            icon.setImageResource(if (isRecent) R.drawable.ic_history else R.drawable.ic_trending_up)
            icon.imageTintList = ContextCompat.getColorStateList(
                icon.context,
                if (isRecent) R.color.text_tertiary else R.color.success,
            )

            // only a search of the user's own can be forgotten
            remove.setVisible(isRecent)
            remove.setOnClickListener { onRemove(row) }

            itemView.setOnClickListener { onClick(row) }
        }
    }

    // a row is the same row when it names the same thing, and rows carry nothing else that can change
    private object DiffCallback : DiffUtil.ItemCallback<QueryRow>() {
        override fun areItemsTheSame(oldItem: QueryRow, newItem: QueryRow): Boolean {
            return oldItem.kind == newItem.kind && oldItem.label == newItem.label
        }

        override fun areContentsTheSame(oldItem: QueryRow, newItem: QueryRow): Boolean {
            return oldItem == newItem
        }
    }
}
//------------------------------EOF------------------------------\
