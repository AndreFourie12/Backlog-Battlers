package com.backlogbattlers.app.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.Game
import com.backlogbattlers.app.util.formatHours
import com.backlogbattlers.app.util.formatPoints
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.loadArtwork
import com.backlogbattlers.app.util.platformLabel
import com.backlogbattlers.app.util.primaryPlatform
import com.backlogbattlers.app.util.setVisible
import com.backlogbattlers.app.viewmodels.GameDetailUiState

// the game detail screen's single-item header: hero art through the stat tiles and the
// "Achievements" label. Rides as item 0 of the screen's ConcatAdapter, ahead of the achievement
// rows - see fragment_game_detail.xml for why this is not a separate scrolling header
class GameDetailHeaderAdapter(
    private val onBack: () -> Unit,
    private val onFavouriteToggle: () -> Unit,
) : RecyclerView.Adapter<GameDetailHeaderAdapter.ViewHolder>() {

    private var state = GameDetailUiState()
    private var isFavourite = false

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateChild(R.layout.partial_detail_header), onBack, onFavouriteToggle)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(state, isFavourite)
    }

    fun submitState(newState: GameDetailUiState) {
        state = newState
        notifyItemChanged(0)
    }

    fun setFavourite(value: Boolean) {
        isFavourite = value
        notifyItemChanged(0)
    }

    class ViewHolder(
        view: View,
        onBack: () -> Unit,
        onFavouriteToggle: () -> Unit,
    ) : RecyclerView.ViewHolder(view) {

        private val backdrop: ImageView = view.findViewById(R.id.iv_detail_backdrop)
        private val cover: ImageView = view.findViewById(R.id.iv_detail_cover)
        private val favouriteButton: ImageView = view.findViewById(R.id.btn_detail_favourite)
        private val title: TextView = view.findViewById(R.id.tv_detail_title)
        private val platformChip: TextView = view.findViewById(R.id.tv_detail_platform)
        private val genreChip: TextView = view.findViewById(R.id.tv_detail_genre)
        private val completionPercent: TextView = view.findViewById(R.id.tv_detail_completion_percent)
        private val completionBar: ProgressBar = view.findViewById(R.id.progress_detail_completion)
        private val statPoints: TextView = view.findViewById(R.id.tv_stat_points)
        private val statAchievements: TextView = view.findViewById(R.id.tv_stat_achievements)
        private val statCompletionTime: TextView = view.findViewById(R.id.tv_stat_completion_time)

        init {
            view.findViewById<View>(R.id.btn_detail_back).setOnClickListener { onBack() }
            favouriteButton.setOnClickListener { onFavouriteToggle() }
        }

        fun bind(state: GameDetailUiState, isFavourite: Boolean) {
            val context = itemView.context

            renderGame(state.game)

            completionPercent.text = context.getString(R.string.detail_completion_value, state.completionPercent)
            completionBar.progress = state.completionPercent

            statPoints.text = context.getString(
                R.string.detail_stat_ratio,
                formatPoints(state.earnedPoints),
                formatPoints(state.totalPoints),
            )
            statAchievements.text = context.getString(
                R.string.detail_stat_ratio,
                state.unlockedCount.toString(),
                state.totalCount.toString(),
            )
            statCompletionTime.text = formatHours(state.game?.avgCompletionHours)

            val color = if (isFavourite) R.color.destructive else R.color.white
            favouriteButton.imageTintList = ContextCompat.getColorStateList(context, color)
        }

        private fun renderGame(game: Game?) {
            game ?: return
            backdrop.loadArtwork(game.artworkUrl ?: game.coverImageUrl)
            cover.loadArtwork(game.coverImageUrl ?: game.artworkUrl)
            title.text = game.title

            val platform = primaryPlatform(game.platforms)
            platformChip.text = platform?.let { platformLabel(it.name) }
            platformChip.setVisible(platform != null)

            val genre = game.genres.firstOrNull()
            genreChip.text = genre
            genreChip.setVisible(genre != null)
        }
    }
}
//------------------------------EOF------------------------------\\
