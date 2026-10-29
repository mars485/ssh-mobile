package dev.sshmobile.ui.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sshmobile.core.terminal.TerminalAttr
import dev.sshmobile.core.terminal.TerminalSnapshot
import dev.sshmobile.ui.theme.GreenAccent
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

data class LineSelection(val startLine: Int, val endLine: Int) {
    val first: Int get() = min(startLine, endLine)
    val last: Int get() = maxOf(startLine, endLine)

    fun contains(line: Int): Boolean = line in first..last
}

private val TerminalBackground = Color(0xFF070A08)
private val TerminalForeground = Color(0xFFE8F2EC)
private val SelectionTint = Color(0x5500E676)

/**
 * Renders the visible terminal grid with a monospace [Paint] for predictable cell metrics, and
 * handles tap-to-focus, drag-to-scroll and long-press line selection.
 */
@Composable
fun TerminalCanvas(
    snapshot: TerminalSnapshot,
    fontSizeSp: Float,
    selection: LineSelection?,
    onResize: (cols: Int, rows: Int) -> Unit,
    onScroll: (lines: Int) -> Unit,
    onSelectLines: (startLine: Int, endLine: Int) -> Unit,
    onTap: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { fontSizeSp.sp.toPx() }
    val paint = remember(textSizePx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = textSizePx
        }
    }
    val metrics = remember(paint) { paint.fontMetrics }
    val cellWidth = remember(paint) { (paint.measureText("MMMMMMMMMM") / 10f).coerceAtLeast(1f) }
    val cellHeight = remember(metrics) {
        ((metrics.descent - metrics.ascent + metrics.leading) * 1.12f).coerceAtLeast(1f)
    }
    val baselineOffset = remember(metrics, cellHeight) {
        (cellHeight - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
    }
    val underlineWidth = with(density) { 1.dp.toPx() }
    val cursorStroke = with(density) { 1.5.dp.toPx() }

    val currentSelection by rememberUpdatedState(selection)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnClear by rememberUpdatedState(onClearSelection)
    val currentOnSelect by rememberUpdatedState(onSelectLines)
    val currentOnScroll by rememberUpdatedState(onScroll)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val cols = (widthPx / cellWidth).toInt().coerceAtLeast(2)
        val rows = (heightPx / cellHeight).toInt().coerceAtLeast(1)

        LaunchedEffect(cols, rows) {
            onResize(cols, rows)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cellWidth, cellHeight, snapshot.firstLine, snapshot.screenRows) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPosition = down.position
                        val slop = viewConfiguration.touchSlop
                        val longPressTimeout = viewConfiguration.longPressTimeoutMillis

                        val gesture = withTimeoutOrNull(longPressTimeout) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@withTimeoutOrNull Gesture.Cancel
                                if (!change.pressed) return@withTimeoutOrNull Gesture.Tap
                                if ((change.position - downPosition).getDistance() > slop) {
                                    return@withTimeoutOrNull Gesture.Scroll
                                }
                            }
                            @Suppress("UNREACHABLE_CODE")
                            Gesture.Cancel
                        } ?: Gesture.LongPress

                        when (gesture) {
                            Gesture.Tap -> {
                                if (currentSelection != null) currentOnClear() else currentOnTap()
                            }

                            Gesture.Cancel -> Unit

                            Gesture.LongPress -> {
                                val startLine = lineAt(
                                    downPosition.y,
                                    cellHeight,
                                    snapshot.firstLine,
                                    snapshot.screenRows,
                                )
                                currentOnSelect(startLine, startLine)
                                var lastLine = startLine
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val line = lineAt(
                                        change.position.y,
                                        cellHeight,
                                        snapshot.firstLine,
                                        snapshot.screenRows,
                                    )
                                    if (line != lastLine) {
                                        lastLine = line
                                        currentOnSelect(startLine, line)
                                    }
                                    change.consume()
                                    if (!change.pressed) break
                                }
                            }

                            Gesture.Scroll -> {
                                var lastY = downPosition.y
                                var accumulated = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val dy = change.position.y - lastY
                                    lastY = change.position.y
                                    accumulated += dy
                                    val lines = (accumulated / cellHeight).toInt()
                                    if (lines != 0) {
                                        currentOnScroll(lines)
                                        accumulated -= lines * cellHeight
                                    }
                                    change.consume()
                                    if (!change.pressed) break
                                }
                            }
                        }
                    }
                },
        ) {
            drawRect(TerminalBackground)
            val selectionRange = currentSelection

            for (rowIndex in 0 until min(rows, snapshot.rows.size)) {
                val row = snapshot.rows[rowIndex]
                val y = rowIndex * cellHeight
                val absoluteLine = snapshot.firstLine + rowIndex
                val selected = selectionRange?.contains(absoluteLine) == true

                for (column in 0 until min(cols, row.size)) {
                    val cell = row[column]
                    if (cell.width == 0) continue

                    var foreground = if (cell.fg == 0) TerminalForeground else Color(cell.fg)
                    var background = if (cell.bg == 0) TerminalBackground else Color(cell.bg)
                    if (cell.attrs and TerminalAttr.REVERSE != 0) {
                        val swap = foreground
                        foreground = background
                        background = swap
                    }
                    if (cell.attrs and TerminalAttr.DIM != 0) {
                        foreground = foreground.copy(alpha = 0.65f)
                    }
                    val cellW = cellWidth * cell.width
                    val x = column * cellWidth
                    if (background != TerminalBackground) {
                        drawRect(background, topLeft = Offset(x, y), size = Size(cellW, cellHeight))
                    }
                    if (selected) {
                        drawRect(SelectionTint, topLeft = Offset(x, y), size = Size(cellW, cellHeight))
                    }

                    if (cell.text.isNotEmpty() && cell.attrs and TerminalAttr.HIDDEN == 0) {
                        paint.color = foreground.toArgb()
                        paint.isFakeBoldText = cell.attrs and TerminalAttr.BOLD != 0
                        val baseline = y + baselineOffset
                        if (cell.width == 2 || cell.text.length > 1) {
                            val native = drawContext.canvas.nativeCanvas
                            val save = native.save()
                            native.clipRect(x, y, x + cellW, y + cellHeight)
                            native.drawText(cell.text, x, baseline, paint)
                            native.restoreToCount(save)
                        } else {
                            drawContext.canvas.nativeCanvas.drawText(cell.text, x, baseline, paint)
                        }
                    }

                    if (cell.attrs and TerminalAttr.UNDERLINE != 0) {
                        drawLine(
                            color = foreground,
                            start = Offset(x, y + cellHeight - underlineWidth),
                            end = Offset(x + cellW, y + cellHeight - underlineWidth),
                            strokeWidth = underlineWidth,
                        )
                    }
                    if (cell.attrs and TerminalAttr.STRIKE != 0) {
                        drawLine(
                            color = foreground,
                            start = Offset(x, y + cellHeight / 2f),
                            end = Offset(x + cellW, y + cellHeight / 2f),
                            strokeWidth = underlineWidth,
                        )
                    }
                }
            }

            if (snapshot.cursorVisible) {
                val cx = snapshot.cursorCol * cellWidth
                val cy = snapshot.cursorRow * cellHeight
                if (cx + cellWidth <= size.width && cy + cellHeight <= size.height) {
                    drawRect(
                        color = GreenAccent.copy(alpha = 0.32f),
                        topLeft = Offset(cx, cy),
                        size = Size(cellWidth, cellHeight),
                    )
                    drawRect(
                        color = GreenAccent.copy(alpha = 0.9f),
                        topLeft = Offset(cx, cy),
                        size = Size(cellWidth, cellHeight),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(cursorStroke),
                    )
                }
            }
        }
    }
}

private fun lineAt(y: Float, cellHeight: Float, firstLine: Int, screenRows: Int): Int {
    val row = (y / cellHeight).toInt().coerceIn(0, screenRows - 1)
    return firstLine + row
}

private enum class Gesture { Tap, LongPress, Scroll, Cancel }

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
