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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.gios.lightnonogram.data.ProgressStore
import com.gios.lightnonogram.data.PuzzleLibrary
import com.gios.lightnonogram.data.ToolState
import com.gios.lightnonogram.game.Board
import com.gios.lightnonogram.game.Made
import com.gios.lightnonogram.game.Puzzle
import com.gios.lightnonogram.game.Session
import com.gios.lightnonogram.game.Tool
import com.gios.lightnonogram.gen.Generate
import com.gios.lightnonogram.gen.Names
import com.gios.lightnonogram.ui.PicrossGrid
import com.gios.lightnonogram.ui.PuzzleThumbnail
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
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
    object Collected : View
    object Settings : View

    /**
     * @param puzzle non-null for a bundled picture, null for a generated one.
     * @param made   non-null for a generated one, so a win can be collected.
     * @param reveal the title shown once solved — the whole reward.
     */
    data class Play(
        val board: Board,
        val puzzle: Puzzle?,
        val made: Made?,
        val reveal: String,
    ) : View {
        /** Shown while playing a generated puzzle, so a seed worth keeping can be. */
        val seed: Int? get() = made?.seed
    }
}

/**
 * The whole tool, in one screen.
 *
 * Deliberately not several SDK screens. Menu, gallery, collection, settings and
 * board are cheap Compose state, and one screen means one back stack.
 *
 * Note that LightOS's hardware back cannot be intercepted here: `LightActivity`
 * wires its back dispatcher straight to its own `goBack()`, which pops the SDK's
 * stack and calls `finish()` when it empties. `LightScreen.goBack` — the one that
 * consults `LightViewModel.onBackPressed` — is only reached when a tool calls it
 * itself. With a single screen on the stack, back therefore always closes the
 * tool, which is why every view below carries an explicit Home affordance.
 */
@InitialScreen
class HomeScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<Unit, NonogramViewModel>(sealedActivity) {

    override val viewModelClass: Class<NonogramViewModel>
        get() = NonogramViewModel::class.java

    override fun createViewModel(): NonogramViewModel {
        // Reaching DataStore is one of the two things that can fail before any UI
        // exists. Capture the failure instead of letting it kill the process; the
        // tool is still playable without saved progress.
        val store = runCatching { ProgressStore(lightContext.dataStore) }
        return NonogramViewModel(store.getOrNull(), store.exceptionOrNull())
    }

    /**
     * Push the text editor and play whatever comes back.
     *
     * Text entry on LightOS is a screen of its own (see [SeedEditorScreen]), so
     * this is the one navigation the tool does.
     */
    private fun promptForSeed() {
        navigateTo(
            screenFactory = { SeedEditorScreen(it, "") },
            resultCallback = { typed -> if (typed != null) viewModel.playTypedSeed(typed) },
        )
    }

    @Composable
    override fun Content() {
        val view by viewModel.view.collectAsState()
        val state by viewModel.state.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()
        val failure by viewModel.startupError.collectAsState()

        // Drawn without LightTheme or LightText on purpose: if the failure is in
        // the theme or the SDK's text stack, a reporter built on them would die
        // too and we'd be back to a blank crash.
        failure?.let { StartupFailure(it); return@Content }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
                    .padding(bottom = BACK_BUTTON_INSET),
            ) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (val v = view) {
                        is View.Menu -> Menu(state, viewModel, onEnterSeed = ::promptForSeed)
                        is View.Gallery -> Gallery(state, viewModel)
                        is View.Collected -> CollectedView(state, viewModel)
                        is View.Settings -> Settings(state, viewModel)
                        is View.Play -> Play(v, viewModel)
                    }
                }

                // The bar is the way between the two halves of the tool, and — since
                // hardware back closes the tool outright — the way out of the
                // campaign and settings views too.
                //
                // Hidden while a board is open. It costs about four grid units of
                // height, and on the board every one of those goes to the grid
                // instead; the board carries its own Home instead.
                if (view !is View.Play) {
                    LightBottomBar(
                        items = listOf(
                            LightBarButton.LightIcon(
                                icon = LightIcons.PLAY,
                                contentDescription = "Play",
                                onClick = { viewModel.show(View.Menu) },
                            ),
                            LightBarButton.LightIcon(
                                icon = LightIcons.LARGE_LIST,
                                contentDescription = "Your collection",
                                onClick = { viewModel.show(View.Collected) },
                            ),
                        ),
                    )
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

/**
 * Header for every view except the menu.
 *
 * Hardware back closes the tool outright (see [HomeScreen]), so this is the only
 * way back to the menu. It sits top-left on every view for that reason.
 */
@Composable
private fun Header(title: String, trailing: String? = null, onHome: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = "‹ Home",
            variant = LightTextVariant.Detail,
            modifier = Modifier.lightClickable(onClick = onHome).padding(vertical = 6.dp, horizontal = 2.dp),
        )
        LightText(text = title, variant = LightTextVariant.Detail, lighten = true)
        LightText(
            text = trailing ?: "",
            variant = LightTextVariant.Detail,
            lighten = true,
        )
    }
}

@Composable
private fun Menu(state: ToolState, vm: NonogramViewModel, onEnterSeed: () -> Unit) {
    val puzzles = vm.puzzlesFor(state.size)
    val seedNote by vm.seedMessage.collectAsState()
    val done = state.progress.countIn(puzzles)
    val next = puzzles.firstOrNull { !state.progress.has(it.id) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        LightText(text = "Nonogram", variant = LightTextVariant.Heading)
        Spacer(Modifier.height(4.dp))
        LightText(
            text = "${state.size}×${state.size}  ·  $done of ${puzzles.size} solved",
            variant = LightTextVariant.Detail,
            lighten = true,
        )

        Spacer(Modifier.height(28.dp))

        val resumable = state.session
        if (resumable != null) {
            MenuRow("Continue", vm.describeSession(resumable)) { vm.resume(resumable) }
        } else if (next != null) {
            MenuRow("Continue", "Puzzle ${puzzles.indexOf(next) + 1}") { vm.play(next, state) }
        }
        MenuRow("Puzzle campaign", "All ${puzzles.size} pictures") { vm.show(View.Gallery) }
        MenuRow(
            "Random",
            if (next == null) "You've finished every picture" else "Endless, named, generated here",
        ) { vm.playRandom(state) }
        MenuRow("From a seed", seedNote ?: "A number or a word") { onEnterSeed() }
        MenuRow("Settings", if (state.autoCross) "Auto-mark on" else "Auto-mark off") {
            vm.show(View.Settings)
        }
    }
}

private fun collectionSubtitle(state: ToolState): String = when (state.made.size) {
    0 -> "Nothing yet — solve a random one"
    1 -> "1 piece"
    else -> "${state.made.size} pieces"
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 13.dp),
    ) {
        LightText(text = title, variant = LightTextVariant.Copy)
        LightText(text = subtitle, variant = LightTextVariant.Detail, lighten = true)
    }
}

@Composable
private fun Gallery(state: ToolState, vm: NonogramViewModel) {
    val puzzles = vm.puzzlesFor(state.size)
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Header(
            title = "Puzzle campaign",
            trailing = "${state.progress.countIn(puzzles)}/${puzzles.size}",
        ) { vm.show(View.Menu) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(puzzles) { index, puzzle ->
                Box(
                    modifier = Modifier.size(62.dp).lightClickable { vm.play(puzzle, state) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.progress.has(puzzle.id)) {
                        PuzzleThumbnail(puzzle.solution(), puzzle.width, puzzle.height, side = 52.dp)
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

/**
 * Everything the player has generated and finished.
 *
 * Each entry is a seed and a size; the picture and the name are both recomputed
 * from the seed, so this whole view is rebuilt from a few characters per piece.
 */
@Composable
private fun CollectedView(state: ToolState, vm: NonogramViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Header(title = "Your collection", trailing = "${state.made.size}") { vm.show(View.Menu) }

        if (state.made.entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                LightText(
                    text = "Solve a random puzzle and it lands here, with a name.",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                )
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(state.made.entries) { made ->
                Column(
                    modifier = Modifier.lightClickable { vm.replay(made, state) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val solution = remember(made) { Generate.fromSeed(made.seed, made.size) }
                    if (solution != null) {
                        PuzzleThumbnail(solution, made.size, made.size, side = 78.dp)
                    } else {
                        Box(Modifier.size(78.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    LightText(
                        text = made.label ?: Names.nameFor(made.seed),
                        variant = LightTextVariant.Detail,
                    )
                    LightText(
                        text = "${made.size}×${made.size}",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun Settings(state: ToolState, vm: NonogramViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Header(title = "Settings") { vm.show(View.Menu) }
        Spacer(Modifier.height(8.dp))

        MenuRow(
            title = "Auto-mark  ·  ${if (state.autoCross) "On" else "Off"}",
            subtitle = if (state.autoCross) {
                "Finished lines cross themselves out"
            } else {
                "Only the marks you place yourself"
            },
        ) { vm.setAutoCross(!state.autoCross) }

        MenuRow(
            title = "Size  ·  ${state.size}×${state.size}",
            subtitle = "Tap to switch between 10×10 and 15×15",
        ) { vm.setSize(if (state.size == 10) 15 else 10) }
    }
}

@Composable
private fun Play(view: View.Play, vm: NonogramViewModel) {
    val board = view.board
    val tool by vm.tool.collectAsState()
    val solved by vm.solved.collectAsState()
    val canUndo by vm.canUndo.collectAsState()

    // Chrome on this view is measured in cells: every 40dp spent above or below
    // the board is roughly one whole row of a 10x10 gone. So Home, the hidden
    // title and the seed share one line instead of taking three, the spacers are
    // gone, and the grid area gets the full screen width — its own clue gutter is
    // the only inset it needs.
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(
                text = "‹ Home",
                variant = LightTextVariant.Detail,
                modifier = Modifier
                    .lightClickable { vm.show(View.Menu) }
                    .padding(vertical = 4.dp),
            )
            // Dots while playing: the picture's name is the reward, so it stays
            // hidden. It gets its own line below once solved, where the space no
            // longer costs the player anything.
            LightText(
                text = if (solved) "Solved" else "· · ·",
                variant = LightTextVariant.Detail,
                lighten = true,
            )
            LightText(
                text = view.seed?.let { "#$it" } ?: "",
                variant = LightTextVariant.Detail,
                lighten = true,
            )
        }

        PicrossGrid(
            board = board,
            tool = tool,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onChanged = { vm.onBoardChanged() },
        )

        if (solved) {
            LightText(
                text = view.reveal,
                variant = LightTextVariant.Copy,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
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
                    lighten = !canUndo,
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
    val state = MutableStateFlow(ToolState())
    val tool = MutableStateFlow(Tool.FILL)
    val solved = MutableStateFlow(false)

    /** Non-null means the tool shows a trace instead of the game. */
    val startupError = MutableStateFlow<String?>(null)

    /** Feedback for the seed row when a typed seed can't be used. */
    val seedMessage = MutableStateFlow<String?>(null)

    /**
     * Whether Undo is available.
     *
     * Board is deliberately plain mutable state, which the grid redraws from via
     * its own counter — but nothing outside the grid recomposed, so the Undo label
     * stayed greyed out until some *other* change happened to repaint it. Mirroring
     * it into a flow is what makes the button light up on the stroke that created
     * the undo, rather than the one after.
     */
    val canUndo = MutableStateFlow(false)

    private val packs = HashMap<Int, List<Puzzle>>()

    init {
        storeFailure?.let { report("Opening DataStore failed", it) }
        val s = store
        if (s != null) {
            viewModelScope.launch {
                // A throw in here would otherwise be an uncaught coroutine
                // exception, which takes the whole process down.
                runCatching { s.state.collect { state.value = it } }
                    .onFailure { report("Reading saved state failed", it) }
            }
        }
    }

    /**
     * Parsed lazily per size and cached, outside composition, so a bad pack
     * surfaces as a readable message rather than a crash mid-compose.
     */
    fun puzzlesFor(size: Int): List<Puzzle> = packs.getOrPut(size) {
        PuzzleLibrary.load(size).fold(
            onSuccess = { it },
            onFailure = { report("Parsing the ${size}×$size puzzle pack failed", it); emptyList() },
        )
    }

    private fun report(what: String, e: Throwable) {
        startupError.value = "$what\n\n" + e.stackTraceToString()
    }

    fun show(v: View) { view.value = v }

    private fun begin(board: Board, puzzle: Puzzle?, made: Made?, reveal: String) {
        solved.value = false
        tool.value = Tool.FILL
        view.value = View.Play(board, puzzle, made, reveal)
        refreshUndo()
    }

    fun play(puzzle: Puzzle, s: ToolState) = begin(
        board = Board(puzzle.width, puzzle.height, puzzle.solution(), autoCross = s.autoCross),
        puzzle = puzzle,
        made = null,
        reveal = puzzle.title?.replaceFirstChar { it.uppercase() } ?: "Solved",
    )

    fun playRandom(s: ToolState) {
        // Seeded from the clock so consecutive taps differ. The seed alone
        // determines the picture and the name, so it's all the collection stores.
        val seed = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        val solution = Generate.fromSeed(seed, s.size) ?: return
        begin(
            board = Board(s.size, s.size, solution, autoCross = s.autoCross),
            puzzle = null,
            made = Made(seed, s.size),
            reveal = Names.nameFor(seed),
        )
    }

    /**
     * Play the puzzle for a typed seed.
     *
     * Any Int is a legal seed, so the only real failures are text that isn't a
     * number and the rare case where generation exhausts its attempt budget.
     * Both report on the menu rather than doing nothing, which would look broken.
     */
    fun playTypedSeed(typed: String) {
        val s = state.value
        val cleaned = typed.trim()
        val seed = Names.seedFromText(cleaned)
        if (seed == null) {
            seedMessage.value = "Type a number or a word"
            return
        }
        val solution = Generate.fromSeed(seed, s.size)
        if (solution == null) {
            seedMessage.value = "That seed makes no ${s.size}×${s.size} puzzle — try another"
            return
        }
        // A word is what the player will remember the puzzle by, so keep it as the
        // title. A number carries no meaning, so those get a generated name.
        val label = if (cleaned.toIntOrNull() == null) Made.sanitizeLabel(cleaned) else null
        seedMessage.value = null
        begin(
            board = Board(s.size, s.size, solution, autoCross = s.autoCross),
            puzzle = null,
            made = Made(seed, s.size, label),
            reveal = label ?: Names.nameFor(seed),
        )
    }

    fun replay(made: Made, s: ToolState) {
        val solution = Generate.fromSeed(made.seed, made.size) ?: return
        begin(
            board = Board(made.size, made.size, solution, autoCross = s.autoCross),
            puzzle = null,
            made = made,
            reveal = made.label ?: Names.nameFor(made.seed),
        )
    }

    fun toggleTool() {
        tool.value = if (tool.value == Tool.FILL) Tool.CROSS else Tool.FILL
    }

    fun undo() {
        (view.value as? View.Play)?.board?.undo()
        refreshUndo()
    }

    fun clearBoard() {
        (view.value as? View.Play)?.board?.clear()
        solved.value = false
        refreshUndo()
    }

    private fun refreshUndo() {
        canUndo.value = (view.value as? View.Play)?.board?.canUndo == true
    }

    /** One line for the Continue row, so it says *what* you'd be resuming. */
    fun describeSession(session: Session): String {
        val what = when {
            session.label != null -> session.label
            session.isGenerated -> Names.nameFor(session.seed!!)
            else -> {
                val p = PuzzleLibrary.byId(session.puzzleId.orEmpty())
                // Deliberately not the picture's name — that's the reveal.
                if (p != null) "Puzzle ${runCatching { PuzzleLibrary.numberOf(p) }.getOrDefault(0)}" else "In progress"
            }
        }
        return "$what · ${session.size}×${session.size}"
    }

    /**
     * Reopen a saved board.
     *
     * Anything that doesn't reconstruct — a pack that no longer has that id, a
     * seed that stopped generating, a truncated mask — drops the session rather
     * than failing, and the player just starts something new.
     */
    fun resume(session: Session) {
        val s = state.value
        if (session.isGenerated) {
            val seed = session.seed ?: return dropSession()
            val solution = Generate.fromSeed(seed, session.size) ?: return dropSession()
            val marks = Session.decodeMarks(session.filled, session.crossed, solution.size)
                ?: return dropSession()
            begin(
                board = Board(session.size, session.size, solution, s.autoCross, marks),
                puzzle = null,
                made = Made(seed, session.size, session.label),
                reveal = session.label ?: Names.nameFor(seed),
            )
        } else {
            val puzzle = PuzzleLibrary.byId(session.puzzleId.orEmpty()) ?: return dropSession()
            val solution = puzzle.solution()
            val marks = Session.decodeMarks(session.filled, session.crossed, solution.size)
                ?: return dropSession()
            begin(
                board = Board(puzzle.width, puzzle.height, solution, s.autoCross, marks),
                puzzle = puzzle,
                made = null,
                reveal = puzzle.title?.replaceFirstChar { it.uppercase() } ?: "Solved",
            )
        }
    }

    private fun dropSession() {
        val s = store ?: return
        viewModelScope.launch { runCatching { s.clearSession() } }
    }

    fun setAutoCross(enabled: Boolean) {
        val s = store ?: return
        viewModelScope.launch { runCatching { s.setAutoCross(enabled) } }
    }

    fun setSize(size: Int) {
        val s = store ?: return
        viewModelScope.launch { runCatching { s.setSize(size) } }
    }

    /** Called after every stroke. Records the win exactly once. */
    fun onBoardChanged() {
        refreshUndo()
        val playing = view.value as? View.Play ?: return
        val s = store
        if (solved.value) return

        if (!playing.board.isSolved) {
            // Save after every stroke. LightSolitaire persists its deal the same
            // way; the payload is under a hundred characters and preferences
            // coalesce the writes.
            if (s != null) {
                val session = Session.of(
                    board = playing.board,
                    puzzleId = playing.puzzle?.id,
                    seed = playing.made?.seed,
                    label = playing.made?.label,
                )
                viewModelScope.launch { runCatching { s.saveSession(session) } }
            }
            return
        }

        solved.value = true
        if (s == null) return
        viewModelScope.launch {
            runCatching {
                playing.puzzle?.let { s.markSolved(it.id) }
                playing.made?.let { s.addMade(it) }
                // Finished, so there's nothing left to continue.
                s.clearSession()
            }
        }
    }

    /** After a win: the next unsolved picture, another generated one, or the menu. */
    fun nextAfterWin() {
        val s = state.value
        val playing = view.value as? View.Play
        if (playing?.made != null) { playRandom(s); return }
        val next = puzzlesFor(s.size).firstOrNull { !s.progress.has(it.id) }
        if (next != null) play(next, s) else view.value = View.Menu
    }
}
