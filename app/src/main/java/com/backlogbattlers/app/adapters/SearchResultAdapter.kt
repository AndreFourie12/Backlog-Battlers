package com.backlogbattlers.app.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.domain.model.Game
import com.backlogbattlers.app.R
import com.backlogbattlers.app.util.gameSubtitle
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.loadArtwork
import com.backlogbattlers.app.util.setVisible

data class SearchResult(
    val game: Game,
    val owned: Boolean,
)

class SearchResultAdapter(
    @param:LayoutRes private val itemLayout: Int,
    private val onClick: (Game) -> Unit,
    private val onAdd: (Game) -> Unit,
) : ListAdapter<SearchResult, SearchResultAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateChild(itemLayout), onClick, onAdd)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        view: View,
        private val onClick: (Game) -> Unit,
        private val onAdd: (Game) -> Unit,
    ) : RecyclerView.ViewHolder(view) {

        private val art: ImageView = view.findViewById(R.id.img_game_art)
        private val title: TextView = view.findViewById(R.id.tv_game_title)
        private val subtitle: TextView = view.findViewById(R.id.tv_game_subtitle)
        private val owned: TextView = view.findViewById(R.id.tv_owned)
        private val add: TextView = view.findViewById(R.id.btn_add_to_library)

        init {
            art.clipToOutline = true
        }

        fun bind(result: SearchResult) {
            val game = result.game

            art.loadArtwork(game.artworkUrl ?: game.coverImageUrl)
            title.text = game.title

            val line = gameSubtitle(game.platforms, game.genres)
            subtitle.text = line
            subtitle.setVisible(line.isNotEmpty())

            owned.setVisible(result.owned)
            add.setVisible(!result.owned)
            add.setOnClickListener { onAdd(game) }

            itemView.setOnClickListener { onClick(game) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return oldItem.game.gameId == newItem.game.gameId
        }

        override fun areContentsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return oldItem == newItem
        }
    }
}
