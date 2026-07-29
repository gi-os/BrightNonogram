package com.gios.lightnonogram.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gios.lightnonogram.game.Board
import com.gios.lightnonogram.game.Mark
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.gios.lightnonogram.game.Tool
import com.thelightphone.sdk.ui.LightThemeTokens

/**
 * Clue digits are drawn with an explicit size rather than a LightText variant.
 *
 * The SDK's variants are declared in design-pixels and rescaled per screen at
 * runtime, so their real line height isn't a number this file can know. Reserving
 * a guessed number of dp per line is what cropped the third clue off a column.
 * Here the font size and the space reserved for it are set together, so they
 * can't disagree.
 */
private val CLUE_FONT = 11.sp

/** Width reserved per row-clue number — two digits at [CLUE_FONT]. */
private val CLUE_SLOT = 11.dp

/** Height reserved per stacked column-clue number, with room to spare. */
private val CLUE_LINE = 16.dp

/**
 * The playfield: clue gutters plus the grid itself.
 *
 * Cells are drawn in a single [Canvas] rather than as 100 composables — on this
 * hardware, recomposing a hundred nodes on every pointer move during a drag is
 * the difference between smooth and not. Clue numbers *are* composables, since
 * they only change when a line becomes satisfied.
 *
 * [Board] is deliberately plain mutable state, so a version counter drives
 * redraws. That keeps all the game rules in testable non-Compose code instead of
 * spreading them across snapshot state.
 */
@Composable
fun PicrossGrid(
    board: Board,
    tool: Tool,
    modifier: Modifier = Modifier,
    onChanged: () -> Unit = {},
) {
    var version by remember(board) { mutableIntStateOf(0) }
    // Read the palette outside the draw lambda: DrawScope isn't a composable
    // context, so tokens have to be captured here.
    val ink = LightThemeTokens.colors.content

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Size the clue gutters from the clues the puzzle actually has, not from
        // a fixed fraction. A 15x15 needs room for more numbers than a 10x10, and
        // a fixed multiple of the cell size clipped them; sizing to the real
        // maximum also hands spare room back to the cells when clues are short.
        val maxRowClues = remember(board) { board.rowClues.maxOf { it.size } }
        val maxColClues = remember(board) { board.colClues.maxOf { it.size } }

        val rowGutter: Dp = (CLUE_SLOT * maxRowClues).coerceAtLeast(22.dp)
        val colGutter: Dp = (CLUE_LINE * maxColClues).coerceAtLeast(22.dp)

        // Fit the grid to whichever axis runs out first. Without the height
        // constraint a 15x15 is taller than the display and the bottom rows sit
        // under the back bar.
        //
        // Twice the row gutter, because the grid itself is centred (below) and so
        // needs the same slack on the right as the clues take on the left. That
        // costs some size, which is why the gutters above are as tight as the
        // digits allow.
        val side: Dp = minOf(maxWidth - rowGutter * 2, maxHeight - colGutter)
        val cell: Dp = side / board.width
        val gridSide: Dp = cell * board.width
        val gutter: Dp = rowGutter

        // Centre the *grid*, not the grid-plus-clues block. Centring the block
        // left the board sitting half a gutter right of centre, which read as
        // misalignment. Shifting the wrapped block right by half the gutter puts
        // the grid square on the screen's centre line and lets the clues hang off
        // to its left.
        Column(
            Modifier
                .width(gutter + gridSide)
                .offset(x = gutter / 2),
        ) {
            // ---- column clues -------------------------------------------
            Row {
                Spacer(Modifier.width(gutter).height(colGutter))
                for (c in 0 until board.width) {
                    val satisfied = remember(version) { board.isColSatisfied(c) }
                    Column(
                        modifier = Modifier.width(cell).height(colGutter),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        for (n in board.colClues[c]) ClueNumber(n, satisfied)
                    }
                }
            }

            Row {
                // ---- row clues ------------------------------------------
                Column(Modifier.width(gutter)) {
                    for (r in 0 until board.height) {
                        val satisfied = remember(version) { board.isRowSatisfied(r) }
                        Row(
                            modifier = Modifier.width(gutter).height(cell),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            for (n in board.rowClues[r]) {
                                Box(Modifier.width(CLUE_SLOT), Alignment.Center) {
                                    ClueNumber(n, satisfied)
                                }
                            }
                        }
                    }
                }

                // ---- cells ----------------------------------------------
                Canvas(
                    modifier = Modifier
                        .size(gridSide)
                        .pointerInput(board, tool) {
                            val cellPx = size.width.toFloat() / board.width

                            fun cellAt(o: Offset): Pair<Int, Int>? {
                                val c = (o.x / cellPx).toInt()
                                val r = (o.y / cellPx).toInt()
                                return if (r in 0 until board.height && c in 0 until board.width) {
                                    r to c
                                } else null
                            }

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                cellAt(down.position)?.let { (r, c) ->
                                    board.beginStroke(r, c, tool)
                                    version++
                                }
                                down.consume()

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    // Clamp instead of dropping: a finger sliding
                                    // past the grid edge should keep painting to
                                    // the edge, not stop dead.
                                    val clamped = Offset(
                                        change.position.x.coerceIn(0f, size.width - 1f),
                                        change.position.y.coerceIn(0f, size.height - 1f),
                                    )
                                    cellAt(clamped)?.let { (r, c) ->
                                        board.extendStroke(r, c)
                                        version++
                                    }
                                    change.consume()
                                }

                                board.endStroke()
                                version++
                                onChanged()
                            }
                        },
                ) {
                    version.let { }        // read it so the draw re-runs on change
                    val cellPx = size.width / board.width
                    val hair = 1.dp.toPx()
                    val heavy = 2.dp.toPx()

                    for (r in 0 until board.height) {
                        for (c in 0 until board.width) {
                            val x = c * cellPx
                            val y = r * cellPx
                            when (board.markAt(r, c)) {
                                Mark.FILLED -> drawRect(
                                    color = ink,
                                    topLeft = Offset(x, y),
                                    size = Size(cellPx, cellPx),
                                )
                                Mark.CROSSED -> drawCircle(
                                    // A small dot, not an X. Auto-cross marks a
                                    // lot of cells at once, and a grid full of
                                    // X's is visually loud; dots stay quiet and
                                    // still read as "ruled out".
                                    color = ink.copy(alpha = 0.30f),
                                    radius = cellPx * 0.10f,
                                    center = Offset(x + cellPx / 2, y + cellPx / 2),
                                )
                                Mark.EMPTY -> Unit
                            }
                        }
                    }

                    // Grid lines last so they sit on top of filled cells.
                    for (i in 0..board.width) {
                        // Heavier rule every 5 columns — the standard Picross
                        // counting aid, and the main thing that makes a 10-wide
                        // grid scannable at a glance.
                        val w = if (i % 5 == 0) heavy else hair
                        val a = if (i % 5 == 0) 0.55f else 0.20f
                        drawLine(
                            color = ink.copy(alpha = a),
                            start = Offset(i * cellPx, 0f),
                            end = Offset(i * cellPx, size.height),
                            strokeWidth = w,
                        )
                    }
                    for (i in 0..board.height) {
                        val w = if (i % 5 == 0) heavy else hair
                        val a = if (i % 5 == 0) 0.55f else 0.20f
                        drawLine(
                            color = ink.copy(alpha = a),
                            start = Offset(0f, i * cellPx),
                            end = Offset(size.width, i * cellPx),
                            strokeWidth = w,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single clue number, dimmed once its line is satisfied.
 *
 * Dimming is the cheapest readability win available: it tells you at a glance
 * which lines are done, so you stop re-reading them.
 */
@Composable
private fun ClueNumber(n: Int, satisfied: Boolean) {
    // Colour still comes from the theme; only the metrics are pinned locally.
    val ink = LightThemeTokens.colors.content
    BasicText(
        text = n.toString(),
        style = TextStyle(
            fontSize = CLUE_FONT,
            lineHeight = CLUE_FONT,
            color = if (satisfied) ink.copy(alpha = 0.3f) else ink,
            textAlign = TextAlign.Center,
        ),
    )
}

/** Renders a solved puzzle as a small static thumbnail for the gallery. */
@Composable
fun PuzzleThumbnail(solution: IntArray, width: Int, height: Int, side: Dp) {
    val ink = LightThemeTokens.colors.content
    Canvas(Modifier.size(side)) {
        val cellPx = size.width / width
        for (r in 0 until height) for (c in 0 until width) {
            if (solution[r * width + c] == 1) {
                drawRect(
                    color = ink,
                    topLeft = Offset(c * cellPx, r * cellPx),
                    size = Size(cellPx, cellPx),
                )
            }
        }
    }
}
