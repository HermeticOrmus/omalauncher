package dev.omalauncher.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.omalauncher.AndroidApp
import dev.omalauncher.Runtime
import kotlin.math.exp
import kotlin.math.roundToInt

/** The home screen's right strip the rail takes; Home leaves it free of tiles in portrait. */
internal val HomeRailWidth = 22.dp

private val HomeWaveLean = 88.dp
private const val HomeWaveSpread = 4.6f
private val PanelRow = 40.dp
private val PanelHeader = 48.dp
private const val PanelMaxRows = 9

/**
 * Omalauncher Ormus: Niagara's alphabet on the home screen. Sliding along the right edge bends the letters toward the
 * finger, as the drawer's rail does, and lists that letter's apps by name on the left. Slide over to one and let go to
 * open it; let go anywhere else and the list stays until an app or the space around it is tapped.
 */
@Composable
internal fun HomeLetterRail(
    runtime: Runtime,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    // The drawer's personal list, in its order (by lowercase label), grouped by the rail's letter.
    val groups = remember(runtime.androidApps.apps.toList()) {
        runtime.androidApps.apps.filter { !it.work }.groupBy { railLetter(it.label) }
    }
    val letters = groups.keys.toList()
    var height by remember { mutableIntStateOf(0) }
    var touch by remember { mutableFloatStateOf(Float.NaN) }
    var picked by remember { mutableIntStateOf(-1) }
    var hover by remember { mutableIntStateOf(-1) }
    var pinned by remember { mutableStateOf(false) }
    val touching = !touch.isNaN()
    val wave by animateFloatAsState(if (touching) 1f else 0f, label = "home letter wave")
    val shownLetters by rememberUpdatedState(letters)
    val shownGroups by rememberUpdatedState(groups)
    if (letters.size < 2) return

    val apps: List<AndroidApp> = if (picked in letters.indices) groups[letters[picked]].orEmpty().take(PanelMaxRows) else emptyList()
    val rowPx = with(density) { PanelRow.toPx() }
    val headerPx = with(density) { PanelHeader.toPx() }
    var panelTop by remember { mutableFloatStateOf(0f) }

    fun open(app: AndroidApp) {
        runtime.usage.record("a:" + app.key)
        runtime.androidApps.open(app)
        pinned = false
        picked = -1
    }

    Box(modifier) {
        if (pinned) {
            // Tapping anywhere but the list closes it.
            Box(
                Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    pinned = false
                    picked = -1
                },
            )
        }
        if ((touching || pinned) && apps.isNotEmpty()) {
            Column(
                Modifier
                    .padding(start = 16.dp)
                    .offset { IntOffset(0, panelTop.roundToInt()) }
                    .width(220.dp)
                    .background(Palette.surface, LauncherStyle.shape(12.dp))
                    .border(1.dp, Palette.border, LauncherStyle.shape(12.dp))
                    .padding(horizontal = 12.dp),
            ) {
                BasicText(letters[picked].toString(), Modifier.height(PanelHeader).padding(top = 8.dp), style = Mono.copy(color = Palette.accent, fontSize = TextSize.Title * 1.6f))
                apps.forEachIndexed { i, app ->
                    BasicText(
                        app.label,
                        Modifier
                            .fillMaxWidth()
                            .height(PanelRow)
                            .clickable { open(app) }
                            .padding(top = 10.dp),
                        style = Mono.copy(color = if (i == hover) Palette.accent else Palette.foreground, fontSize = TextSize.Item),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .width(HomeRailWidth)
                .fillMaxHeight()
                .onSizeChanged { height = it.height }
                .pointerInput(Unit) {
                    val lean = HomeWaveLean.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pinned = false
                        var x = down.position.x
                        var y = down.position.y
                        fun follow() {
                            val all = shownLetters
                            // Left of the wave the finger is over the list: pick the row under it instead of a letter.
                            if (x < -lean - 16.dp.toPx() && picked in all.indices) {
                                val row = ((y - panelTop - headerPx) / rowPx).toInt()
                                val count = shownGroups[all[picked]].orEmpty().take(PanelMaxRows).size
                                val next = if (row in 0 until count) row else -1
                                if (next != hover && next >= 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                hover = next
                                return
                            }
                            hover = -1
                            touch = y
                            val i = (y / height * all.size).toInt().coerceIn(0, all.lastIndex)
                            if (i != picked) {
                                picked = i
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                val count = shownGroups[all[i]].orEmpty().take(PanelMaxRows).size
                                val h = headerPx + count * rowPx
                                panelTop = (y - h / 2).coerceIn(0f, (height - h).coerceAtLeast(0f))
                            }
                        }
                        follow()
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            x = change.position.x
                            y = change.position.y
                            follow()
                        }
                        val all = shownLetters
                        val target = if (picked in all.indices) shownGroups[all[picked]].orEmpty().getOrNull(hover) else null
                        touch = Float.NaN
                        hover = -1
                        if (target != null) open(target) else pinned = picked >= 0
                    }
                },
        ) {
            letters.forEachIndexed { i, letter ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BasicText(
                        letter.toString(),
                        Modifier.graphicsLayer {
                            val slot = height.toFloat() / shownLetters.size
                            val d = if (touch.isNaN()) Float.POSITIVE_INFINITY else (touch - slot * (i + 0.5f)) / (slot * HomeWaveSpread)
                            translationX = -HomeWaveLean.toPx() * exp(-d * d / 2f) * wave
                        },
                        style = Mono.copy(color = if (i == picked && (touching || pinned)) Palette.accent else Palette.muted, fontSize = TextSize.Small, textAlign = TextAlign.Center),
                    )
                }
            }
        }
    }
}

