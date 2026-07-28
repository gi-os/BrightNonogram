package com.gios.lightnonogram

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gios.lightnonogram.data.ProgressStore
import com.gios.lightnonogram.data.PuzzleLibrary
import com.gios.lightnonogram.game.Board
import com.gios.lightnonogram.game.Tool
import com.gios.lightnonogram.gen.Generate
import com.gios.lightnonogram.ui.PicrossGrid
import com.thelightphone.sdk.client.LightScreen
import com.thelightphone.sdk.client.LightViewModel
import com.thelightphone.sdk.client.SealedLightActivity
import com.thelightphone.sdk.client.SimpleLightScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The game.
 *
 * Handles both a bundled picture ([puzzleId]) and an on-device generated one
 * ([randomSeed]). Exactly one should be non-null.
 *
 * Returns `true` if the puzzle was solved, so the caller can refresh its counts.
 */
class PuzzleScreen(
    activity: SealedLightActivity,
    private val puzzleId: String?,
    private val randomSeed: Int?,
) : LightScreen<Boolean, PuzzleViewModel>(activity) {

    override val viewModelClass = PuzzleViewModel::class.java
    override fun createViewModel() =
        PuzzleViewModel(ProgressStore(dataStore), puzzleId, randomSeed)

    @Composable
    override fun Content() {
        val board = viewModel.board ?: run {
            // Only reachable if generation exhausted its attempt budget.
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                BasicText("Couldn't make a puzzle", style = TextStyle(fontSize = 16.sp))
            }
            return
        }
        val tool by viewModel.tool.collectAsState()
        val solved by viewModel.solved.collectAsState()
        val revealed by viewModel.revealedTitle.collectAsState()

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The title is the reward — hidden until solved, or the picture is
            // spoiled before you start.
            BasicText(
                text = if (solved) (revealed ?: "Solved") else "· · ·",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black.copy(alpha = if (solved) 0.9f else 0.3f),
                ),
            )

            Spacer(Modifier.height(16.dp))

            PicrossGrid(
                board = board,
                tool = tool,
                modifier = Modifier.fillMaxWidth(),
                onChanged = { viewModel.onBoardChanged() },
            )

            Spacer(Modifier.height(20.dp))

            if (solved) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ActionButton("Next", primary = true) { goBack(true) }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A single toggle rather than two buttons: one tap to switch,
                    // and the label always states the *current* tool so there's no
                    // guessing what a tap will do.
                    ActionButton(
                        if (tool == Tool.FILL) "Fill" else "Mark",
                        primary = true,
                    ) { viewModel.toggleTool() }

                    ActionButton("Undo", enabled = board.canUndo) { viewModel.undo() }
                    ActionButton("Clear") { viewModel.clearBoard() }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.25f)
            .background(
                color = if (primary) Color.Black else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        BasicText(
            label,
            style = TextStyle(
                fontSize = 16.sp,
                color = if (primary) Color.White else Color.Black,
            ),
        )
    }
}

class PuzzleViewModel(
    private val store: ProgressStore,
    private val puzzleId: String?,
    private val randomSeed: Int?,
) : LightViewModel<Boolean>() {

    private val puzzle = puzzleId?.let { PuzzleLibrary.byId(it) }

    /**
     * Built once, eagerly. A 10x10 generate is sub-millisecond, so there's no
     * reason to complicate this with a loading state.
     */
    val board: Board? = when {
        puzzle != null -> puzzle.newBoard()
        randomSeed != null -> Generate.fromSeed(randomSeed, size = 10)
            ?.let { Board(10, 10, it) }
        else -> null
    }

    private val _tool = MutableStateFlow(Tool.FILL)
    val tool: StateFlow<Tool> = _tool

    private val _solved = MutableStateFlow(false)
    val solved: StateFlow<Boolean> = _solved

    private val _revealedTitle = MutableStateFlow<String?>(null)
    val revealedTitle: StateFlow<String?> = _revealedTitle

    override fun onScreenShow(screen: SimpleLightScreen<Boolean>) {
        onBoardChanged()
    }

    fun toggleTool() {
        _tool.value = if (_tool.value == Tool.FILL) Tool.CROSS else Tool.FILL
    }

    fun undo() {
        board?.undo()
        onBoardChanged()
    }

    fun clearBoard() {
        board?.clear()
        _solved.value = false
        onBoardChanged()
    }

    /** Called after every stroke. Records the win exactly once. */
    fun onBoardChanged() {
        val b = board ?: return
        if (_solved.value || !b.isSolved) return
        _solved.value = true
        _revealedTitle.value = puzzle?.title?.replaceFirstChar { it.uppercase() } ?: "Solved"
        val id = puzzle?.id ?: return    // generated puzzles aren't part of the set
        viewModelScope.launch { store.markSolved(id) }
    }
}
