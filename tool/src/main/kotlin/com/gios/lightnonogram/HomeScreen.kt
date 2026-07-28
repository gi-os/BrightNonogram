package com.gios.lightnonogram

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.gios.lightnonogram.data.ProgressStore
import com.gios.lightnonogram.data.PuzzleLibrary
import com.gios.lightnonogram.game.Board
import com.gios.lightnonogram.game.Progress
import com.gios.lightnonogram.game.Puzzle
import com.gios.lightnonogram.game.Tool
import com.gios.lightnonogram.gen.Generate
import com.gios.lightnonogram.ui.PicrossGrid
import com.gios.lightnonogram.ui.PuzzleThumbnail
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Room at the bottom of the display for the LightOS back button. */
private val BACK_BUTTON_INSET = 44.dp

/** Which view the single screen is showing. */
sealed interface View {
    object Menu : View
    object Gallery : View
    data class Play(val board: Board, val puzzle: Puzzle?) : View
}

/**
 * The whole tool, in one screen.
 *
 * Deliberately not three SDK screens. Menu, gallery and board are cheap Compose
 * state, and keeping them in one screen means the SDK's own back bar is the only
 * back affordance — one stack, not two that can disagree.
 */
@InitialScreen
class HomeScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<Unit, NonogramViewModel>(sealedActivity) {

    override val viewModelClass: Class<NonogramViewModel>
        get() = NonogramViewModel::class.java

    override fun createViewModel(): NonogramViewModel {
        // Reaching DataStore is the other thing that can fail before any UI
        // exists. Capture the failure instead of letting it kill the process;
        // the tool is still playable without saved progress.
        val store = runCatching { ProgressStore(lightContext.dataStore) }
        return NonogramViewModel(store.getOrNull(), store.exceptionOrNull())
    }

    @Composable
    override fun Content() {
        val view by viewModel.view.collectAsState()
        val progress by viewModel.progress.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        val failure by viewModel.startupError.collectAsState()

        // Drawn without LightTheme or LightText on purpose: if the failure is in
        // the theme or the SDK's text stack, a reporter built on them would die
        // too and we'd be back to a blank crash.
        failure?.let { StartupFailure(it); return@Content }

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
                    .padding(bottom = BACK_BUTTON_INSET),
            ) {
                when (val v = view) {
                    is View.Menu -> Menu(progress, viewModel)
                    is View.Gallery -> Gallery(progress, viewModel)
                    is View.Play -> Play(v, viewModel)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------

/**
 * Last-resort diagnostic: put the stack trace on the display.
 *
 * Sideloaded on a phone with no adb to hand, "it crashes" is all the feedback
 * there is. Rendering the trace turns one install into an actual bug report.
 */
@Composable
private fun StartupFailure(trace: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(12.dp)
            .padding(bottom = BACK_BUTTON_INSET)
            .verticalScroll(rememberScrollState()),
    ) {
        BasicText(
            "Nonogram failed to start",
            style = TextStyle(fontSize = 15.sp, color = Color.Black),
        )
        Spacer(Modifier.height(8.dp))
        BasicText(trace, style = TextStyle(fontSize = 9.sp, color = Color.Black))
    }
}

@Composable
private fun Menu(progress: Progress, vm: NonogramViewModel) {
    val puzzles = vm.puzzles
    val done = progress.countIn(puzzles)
    val next = puzzles.firstOrNull { !progress.has(it.id) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        LightText(text = "Nonogram", variant = LightTextVariant.Heading)
        Spacer(Modifier.height(6.dp))
        LightText(
            text = if (done == puzzles.size) "All ${puzzles.size} solved" else "$done of ${puzzles.size} solved",
            variant = LightTextVariant.Detail,
            lighten = true,
        )

        Spacer(Modifier.height(40.dp))

        if (next != null) {
            MenuRow("Continue", "Puzzle ${puzzles.indexOf(next) + 1}") { vm.play(next) }
        }
        MenuRow("Puzzles", "Browse and replay") { vm.showGallery() }
        MenuRow(
            "Random",
            if (next == null) "You've finished every picture" else "Endless, generated on the spot",
        ) { vm.playRandom() }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 16.dp),
    ) {
        LightText(text = title, variant = LightTextVariant.Copy)
        LightText(text = subtitle, variant = LightTextVariant.Detail, lighten = true)
    }
}

@Composable
private fun Gallery(progress: Progress, vm: NonogramViewModel) {
    val puzzles = vm.puzzles
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(text = "Puzzles", variant = LightTextVariant.Copy)
            LightText(
                text = "${progress.countIn(puzzles)}/${puzzles.size}",
                variant = LightTextVariant.Detail,
                lighten = true,
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(puzzles) { index, puzzle ->
                Box(
                    modifier = Modifier.size(62.dp).lightClickable { vm.play(puzzle) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (progress.has(puzzle.id)) {
                        PuzzleThumbnail(puzzle.solution(), puzzle.width, puzzle.height, side = 50.dp)
                    } else {
                        LightText(
                            text = (index + 1).toString(),
                            variant = LightTextVariant.Detail,
                            lighten = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Play(view: View.Play, vm: NonogramViewModel) {
    val board = view.board
    val tool by vm.tool.collectAsState()
    val solved by vm.solved.collectAsState()
    val title by vm.revealedTitle.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The picture's name is the reward, so it stays hidden until the grid is
        // finished. Showing it up front spoils the only surprise the puzzle has.
        LightText(
            text = if (solved) (title ?: "Solved") else "· · ·",
            variant = LightTextVariant.Copy,
            lighten = !solved,
        )

        Spacer(Modifier.height(12.dp))

        PicrossGrid(
            board = board,
            tool = tool,
            modifier = Modifier.fillMaxWidth(),
            onChanged = { vm.onBoardChanged() },
        )

        Spacer(Modifier.height(18.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (solved) {
                LightText(
                    text = "Next",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.lightClickable { vm.nextAfterWin() },
                )
            } else {
                // One toggle rather than two buttons, and the label names the
                // *current* tool so there's never a question what a tap will do.
                LightText(
                    text = if (tool == Tool.FILL) "Fill" else "Mark",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier.lightClickable { vm.toggleTool() },
                )
                LightText(
                    text = "Undo",
                    variant = LightTextVariant.Detail,
                    lighten = !board.canUndo,
                    modifier = Modifier.lightClickable { vm.undo() },
                )
                LightText(
                    text = "Clear",
                    variant = LightTextVariant.Detail,
                    modifier = Modifier.lightClickable { vm.clearBoard() },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------

class NonogramViewModel(
    private val store: ProgressStore?,
    storeFailure: Throwable? = null,
) : LightViewModel<Unit>() {

    val view = MutableStateFlow<View>(View.Menu)
    val progress = MutableStateFlow(Progress())
    val tool = MutableStateFlow(Tool.FILL)
    val solved = MutableStateFlow(false)
    val revealedTitle = MutableStateFlow<String?>(null)

    /** Non-null means the tool shows a trace instead of the game. */
    val startupError = MutableStateFlow<String?>(null)

    /**
     * Parsed once, here rather than in a composable, so a bad pack surfaces as a
     * readable message rather than a crash inside composition.
     */
    val puzzles: List<Puzzle> = PuzzleLibrary.load().fold(
        onSuccess = { it },
        onFailure = { report("Parsing the bundled puzzle pack failed", it); emptyList() },
    )

    init {
        storeFailure?.let { report("Opening DataStore failed", it) }
        val s = store
        if (s != null) {
            viewModelScope.launch {
                // A throw in here would otherwise be an uncaught coroutine
                // exception, which takes the whole process down.
                runCatching { s.progress.collect { progress.value = it } }
                    .onFailure { report("Reading saved progress failed", it) }
            }
        }
    }

    private fun report(what: String, e: Throwable) {
        startupError.value = "$what\n\n" + e.stackTraceToString()
    }

    fun showGallery() { view.value = View.Gallery }

    fun play(puzzle: Puzzle) {
        solved.value = false
        revealedTitle.value = null
        tool.value = Tool.FILL
        view.value = View.Play(puzzle.newBoard(), puzzle)
    }

    fun playRandom() {
        solved.value = false
        revealedTitle.value = null
        tool.value = Tool.FILL
        // Seeded from the clock so consecutive taps differ, and the seed alone is
        // enough to reproduce the puzzle later.
        val seed = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        val solution = Generate.fromSeed(seed, size = 10) ?: return
        view.value = View.Play(Board(10, 10, solution), null)
    }

    fun toggleTool() {
        tool.value = if (tool.value == Tool.FILL) Tool.CROSS else Tool.FILL
    }

    fun undo() {
        (view.value as? View.Play)?.board?.undo()
    }

    fun clearBoard() {
        (view.value as? View.Play)?.board?.clear()
        solved.value = false
    }

    /** Called after every stroke. Records the win exactly once. */
    fun onBoardChanged() {
        val playing = view.value as? View.Play ?: return
        if (solved.value || !playing.board.isSolved) return
        solved.value = true
        revealedTitle.value =
            playing.puzzle?.title?.replaceFirstChar { it.uppercase() } ?: "Solved"
        val id = playing.puzzle?.id ?: return   // generated puzzles aren't part of the set
        val s = store ?: return
        viewModelScope.launch { runCatching { s.markSolved(id) } }
    }

    /** After a win, go straight to the next unsolved picture — or back to the menu. */
    fun nextAfterWin() {
        val next = puzzles.firstOrNull { !progress.value.has(it.id) }
        if (next != null) play(next) else view.value = View.Menu
    }

    /**
     * Intercept the LightOS back button so it walks the in-screen views back to
     * the menu first, instead of dumping the player straight out of a puzzle.
     *
     * @return true when we handled it, false to let LightOS close the tool.
     */
    override fun onBackPressed(): Boolean = when (view.value) {
        is View.Menu -> false
        else -> { view.value = View.Menu; true }
    }
}
