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

//------------------------------
// one game the search turned up, together with whether it is already in the backlog
data class SearchResult(
    val game: Game,
    val owned: Boolean,
)

// adapter for the games a search found: artwork, the name, what it runs on and
// either the green Owned tick or the blue pill that puts it in the backlog.
//
// the suggestions under the search box and the full results are the same row in
// two sizes, so both pass their own layout rather than duplicating this class
class SearchResultAdapter(
    @param:LayoutRes private val itemLayout: Int,
    private val onClick: (Game) -> Unit,
    private val onAdd: (Game) -> Unit,
) : ListAdapter<SearchResult, SearchResultAdapter.ViewHolder>(DiffCallback) {

    //------------------------------
    // inflates one row in whichever of the two sizes this adapter was built for
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateChild(itemLayout), onClick, onAdd)
    }

    //------------------------------
    // fills the row at position with its result
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // holds the views of one row
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
            // rounds the artwork against the tile behind it. the xml attribute for this
            // only takes effect from API 31, and the app runs from 26
            art.clipToOutline = true
        }

        //------------------------------
        // shows the game, and whichever of the two trailing actions applies to it
        fun bind(result: SearchResult) {
            val game = result.game

            // Steam's artwork is landscape and fits these wide tiles, IGDB's portrait
            // cover is the fallback for the games Steam does not carry
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

    // a row is the same row when it shows the same game, and needs redrawing when it
    // has just been added to the backlog
    private object DiffCallback : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return oldItem.game.gameId == newItem.game.gameId
        }

        override fun areContentsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return oldItem == newItem
        }
    }
}
//------------------------------EOF------------------------------\
