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
import com.backlogbattlers.app.domain.model.Platform
import com.backlogbattlers.app.util.formatHours
import com.backlogbattlers.app.util.formatPoints
import com.backlogbattlers.app.util.inflateChild
import com.backlogbattlers.app.util.loadArtwork
import com.backlogbattlers.app.util.loadLandscapeArt
import com.backlogbattlers.app.util.platformLabel
import com.backlogbattlers.app.util.setVisible
import com.backlogbattlers.app.viewmodels.GameDetailUiState

// the game detail screen's single-item header: hero art through the stat tiles and the
// "Achievements" label. Rides as item 0 of the screen's ConcatAdapter, ahead of the achievement
// rows - see fragment_game_detail.xml for why this is not a separate scrolling header
class GameDetailHeaderAdapter(
    private val onBack: () -> Unit,
    private val onFavouriteToggle: () -> Unit,
    private val onComplete: () -> Unit,
) : RecyclerView.Adapter<GameDetailHeaderAdapter.ViewHolder>() {

    private var state = GameDetailUiState()
    private var isFavourite = false

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent.inflateChild(R.layout.partial_detail_header), onBack, onFavouriteToggle, onComplete)
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
        onComplete: () -> Unit,
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
        private val completeIcon: ImageView = view.findViewById(R.id.iv_detail_complete_icon)
        private val completeTitle: TextView = view.findViewById(R.id.tv_detail_complete_title)
        private val completeHint: TextView = view.findViewById(R.id.tv_detail_complete_hint)
        private val completeButton: TextView = view.findViewById(R.id.btn_detail_complete)

        init {
            view.findViewById<View>(R.id.btn_detail_back).setOnClickListener { onBack() }
            favouriteButton.setOnClickListener { onFavouriteToggle() }
            completeButton.setOnClickListener { onComplete() }
        }

        fun bind(state: GameDetailUiState, isFavourite: Boolean) {
            val context = itemView.context

            renderGame(state.game, state.platform)

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
            statCompletionTime.text = context.formatHours(state.game?.avgCompletionHours)

            val color = if (isFavourite) R.color.destructive else R.color.white
            favouriteButton.imageTintList = ContextCompat.getColorStateList(context, color)

            renderCompletion(state)
        }

        // where the game stands: not completed, its story completed, or 100%. there is nothing left to do
        // at 100%, so the button goes
        private fun renderCompletion(state: GameDetailUiState) {
            val context = itemView.context
            val (title, hint) = when {
                state.hundredPercentCompleted -> R.string.detail_complete_hundred_title to R.string.detail_complete_hundred_hint
                state.storyCompleted -> R.string.detail_complete_story_title to R.string.detail_complete_story_hint
                else -> R.string.detail_complete_none_title to R.string.detail_complete_none_hint
            }
            completeTitle.setText(title)
            completeHint.setText(hint)
            completeIcon.imageTintList = ContextCompat.getColorStateList(context, if (state.storyCompleted) R.color.gold else R.color.text_tertiary)
            completeButton.setVisible(!state.hundredPercentCompleted)
            completeButton.isEnabled = !state.completing
            completeButton.setText(if (state.completing) R.string.detail_completing else R.string.detail_complete_button)
        }

        private fun renderGame(game: Game?, platform: Platform?) {
            game ?: return
            backdrop.loadLandscapeArt(game.landscapeArtUrls)
            cover.loadArtwork(game.coverImageUrl ?: game.artworkUrl)
            title.text = game.title

            platformChip.text = platform?.let { itemView.context.platformLabel(it.name) }
            platformChip.setVisible(platform != null)

            val genre = game.genres.firstOrNull()
            genreChip.text = genre
            genreChip.setVisible(genre != null)
        }
    }
}
//------------------------------EOF------------------------------\\
