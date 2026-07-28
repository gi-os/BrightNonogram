package com.gios.lightnonogram

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.thelightphone.sdk.client.LightScreen
import com.thelightphone.sdk.client.LightViewModel
import com.thelightphone.sdk.client.SealedLightActivity
import com.thelightphone.sdk.client.SimpleLightScreen
import com.thelightphone.sdk.client.annotations.InitialScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Boot screen: how far you've got, and three ways in.
 *
 * Kept to a single column of large tap targets. There's no navigation chrome to
 * build — the SDK draws its own back bar — and no scrolling, which suits both the
 * screen size and the Light ethos.
 */
@InitialScreen
class HomeScreen(activity: SealedLightActivity) : LightScreen<Unit, HomeViewModel>(activity) {

    override val viewModelClass = HomeViewModel::class.java
    override fun createViewModel() = HomeViewModel(ProgressStore(dataStore))

    @Composable
    override fun Content() {
        val progress by viewModel.progress.collectAsState()
        val total = PuzzleLibrary.puzzles.size
        val done = progress.countIn(PuzzleLibrary.puzzles)
        val next = PuzzleLibrary.nextUnsolved(progress)

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(
                "Nonogram",
                style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Black),
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                if (done == total) "All $total solved" else "$done of $total solved",
                style = TextStyle(fontSize = 15.sp, color = Color.Black.copy(alpha = 0.5f)),
            )

            Spacer(Modifier.height(48.dp))

            if (next != null) {
                MenuItem("Continue", next.title?.let { "Puzzle ${done + 1}" } ?: "Next puzzle") {
                    openPuzzle(next.id)
                }
            } else {
                // Nothing left to reveal, so point at the endless mode instead of
                // leaving a dead "Continue" button.
                MenuItem("Random puzzle", "You've finished every picture") { openRandom() }
            }

            MenuItem("Puzzles", "Browse and replay") {
                navigateTo({ act -> GalleryScreen(act) }) { viewModel.refresh() }
            }

            if (next != null) {
                MenuItem("Random", "Endless, generated on the spot") { openRandom() }
            }
        }
    }

    private fun openPuzzle(id: String) {
        navigateTo({ act -> PuzzleScreen(act, id, randomSeed = null) }) { viewModel.refresh() }
    }

    private fun openRandom() {
        // Seed from the clock so consecutive taps give different puzzles, and the
        // seed alone is enough to reproduce or share the result.
        val seed = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        navigateTo({ act -> PuzzleScreen(act, puzzleId = null, randomSeed = seed) }) { }
    }
}

@Composable
private fun MenuItem(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
    ) {
        BasicText(title, style = TextStyle(fontSize = 22.sp, color = Color.Black))
        Spacer(Modifier.height(2.dp))
        BasicText(
            subtitle,
            style = TextStyle(fontSize = 13.sp, color = Color.Black.copy(alpha = 0.45f)),
        )
    }
}

class HomeViewModel(private val store: ProgressStore) : LightViewModel<Unit>() {

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        refresh()
    }

    /** Re-read after returning from a puzzle, so the count is never stale. */
    fun refresh() {
        viewModelScope.launch {
            store.progress.collect { _progress.value = it }
        }
    }
}
