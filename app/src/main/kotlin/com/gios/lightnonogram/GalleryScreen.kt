package com.gios.lightnonogram

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gios.lightnonogram.data.ProgressStore
import com.gios.lightnonogram.data.PuzzleLibrary
import com.gios.lightnonogram.game.Progress
import com.gios.lightnonogram.game.Puzzle
import com.gios.lightnonogram.ui.PuzzleThumbnail
import com.thelightphone.sdk.client.LightScreen
import com.thelightphone.sdk.client.LightViewModel
import com.thelightphone.sdk.client.SealedLightActivity
import com.thelightphone.sdk.client.SimpleLightScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The collection.
 *
 * Solved puzzles show their picture as a thumbnail; unsolved ones show a number.
 * That's the whole point of having a fixed set rather than only random puzzles —
 * finishing one adds something to a shelf.
 */
class GalleryScreen(activity: SealedLightActivity) :
    LightScreen<Unit, GalleryViewModel>(activity) {

    override val viewModelClass = GalleryViewModel::class.java
    override fun createViewModel() = GalleryViewModel(ProgressStore(dataStore))

    @Composable
    override fun Content() {
        val progress by viewModel.progress.collectAsState()
        val puzzles = PuzzleLibrary.puzzles
        val done = progress.countIn(puzzles)

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    "Puzzles",
                    style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black),
                )
                BasicText(
                    "$done/${puzzles.size}",
                    style = TextStyle(fontSize = 14.sp, color = Color.Black.copy(alpha = 0.45f)),
                )
            }

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(puzzles) { puzzle ->
                    GalleryTile(
                        puzzle = puzzle,
                        index = puzzles.indexOf(puzzle) + 1,
                        solved = progress.has(puzzle.id),
                    ) {
                        navigateTo({ act -> PuzzleScreen(act, puzzle.id, randomSeed = null) }) {
                            viewModel.refresh()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryTile(puzzle: Puzzle, index: Int, solved: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(64.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (solved) {
            PuzzleThumbnail(puzzle.solution(), puzzle.width, puzzle.height, side = 52.dp)
        } else {
            BasicText(
                index.toString(),
                style = TextStyle(fontSize = 16.sp, color = Color.Black.copy(alpha = 0.3f)),
            )
        }
    }
}

class GalleryViewModel(private val store: ProgressStore) : LightViewModel<Unit>() {

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) = refresh()

    fun refresh() {
        viewModelScope.launch {
            store.progress.collect { _progress.value = it }
        }
    }
}
