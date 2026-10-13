package dev.omalauncher.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The drawer's end edge the letters take; the grid leaves it free while the full list shows. */
internal val RailWidth = 24.dp

/** How far the letter under the finger leans out of the rail. */
private val WaveLean = 36.dp

/** How many letters either side of the finger the bulge reaches. */
private const val WaveReach = 3f

private val BubbleSize = 56.dp
private val BubbleGap = 12.dp

/** The letter [label] files under in the rail: its first letter upper-cased without accents (É under E), or # for a digit or symbol. */
internal fun railLetter(label: String): Char {
    val first = label.trimStart().firstOrNull()?.takeIf { it.isLetter() } ?: return '#'
    return Normalizer.normalize(first.toString(), Normalizer.Form.NFD)[0].uppercaseChar()
}

/**
 * The rail's letters for [labels] laid out [columns] to a row, in list order, each with the row it first shows up in.
 * A letter that comes back further down (é after z, as the list sorts by lowercase label) keeps its first row, so the
 * rail reads top to bottom in the same order as the list.
 */
internal fun letterRows(
    labels: List<String>,
    columns: Int,
): List<Pair<Char, Int>> {
    val rows = LinkedHashMap<Char, Int>()
    labels.forEachIndexed { i, label -> rows.getOrPut(railLetter(label)) { i / columns } }
    return rows.toList()
}

/**
 * Niagara's alphabet down the drawer's end edge, over a list that scrolls. Touching or dragging along it scrolls
 * [listState] so the row of the letter under the finger is at the top, with a tick each time the letter changes. The
 * letters next to the finger grow and lean out, and the one under it shows large beside the rail. [itemOf] turns a
 * letter's row into the list's item index. Fills [modifier]'s size without taking touches outside the rail.
 */
@Composable
internal fun LetterRail(
    letters: List<Pair<Char, Int>>,
    listState: LazyListState,
    itemOf: (row: Int) -> Int,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var height by remember { mutableIntStateOf(0) }
    // Where the finger is along the rail, in px from its top; NaN while nothing touches it.
    var touch by remember { mutableFloatStateOf(Float.NaN) }
    var picked by remember { mutableIntStateOf(-1) }
    val touching = !touch.isNaN()
    val wave by animateFloatAsState(if (touching) 1f else 0f, label = "letter wave")
    val shown by rememberUpdatedState(letters)
    val rowItem by rememberUpdatedState(itemOf)
    // A list that fits on screen has nothing to jump to; one being dragged keeps its rail until the finger lifts.
    if (shown.size < 2 || !(touching || listState.canScrollForward || listState.canScrollBackward)) return

    fun follow(y: Float) {
        touch = y
        val all = shown
        val i = (y / height * all.size).toInt().coerceIn(0, all.lastIndex)
        if (i == picked) return
        picked = i
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        scope.launch { listState.scrollToItem(rowItem(all[i].second)) }
    }

    Box(modifier) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .width(RailWidth)
                .fillMaxHeight()
                .onSizeChanged { height = it.height }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        follow(down.position.y)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            follow(change.position.y)
                        }
                        touch = Float.NaN
                        picked = -1
                    }
                },
        ) {
            letters.forEachIndexed { i, (letter, _) ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BasicText(
                        letter.toString(),
                        Modifier.graphicsLayer {
                            // Read here, not in composition, so a drag only redraws the letters.
                            val slot = height.toFloat() / shown.size
                            val center = slot * (i + 0.5f)
                            val near = if (touch.isNaN()) 0f else (1f - abs(touch - center) / (slot * WaveReach)).coerceAtLeast(0f)
                            val bulge = near * near * wave
                            scaleX = 1f + bulge
                            scaleY = 1f + bulge
                            translationX = -WaveLean.toPx() * bulge
                        },
                        style =
                            Mono.copy(
                                color = if (i == picked) Palette.accent else Palette.muted,
                                fontSize = TextSize.Small,
                                textAlign = TextAlign.Center,
                            ),
                    )
                }
            }
        }
        if (touching && picked in letters.indices) {
            // Clear of the leaning letters, centered on the finger and kept inside the list.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val bubble = BubbleSize.toPx()
                        IntOffset(
                            -(RailWidth + WaveLean + BubbleGap).roundToPx(),
                            (touch - bubble / 2).coerceIn(0f, (height - bubble).coerceAtLeast(0f)).roundToInt(),
                        )
                    }.size(BubbleSize)
                    .background(Palette.surface, LauncherStyle.shape(BubbleSize / 2)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(letters[picked].first.toString(), style = Mono.copy(color = Palette.accent, fontSize = TextSize.Title * 1.6f))
            }
        }
    }
}
