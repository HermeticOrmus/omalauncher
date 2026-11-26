package dev.omalauncher.ui.home

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
import dev.omalauncher.apps.AndroidApp
import dev.omalauncher.Runtime
import kotlin.math.exp
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.Dp
import dev.omalauncher.theme.Style
import dev.omalauncher.widgets.WidgetLayout
import kotlinx.coroutines.delay
import dev.omalauncher.ui.EdgeGap
import dev.omalauncher.ui.LauncherStyle
import dev.omalauncher.ui.Mono
import dev.omalauncher.ui.Palette
import dev.omalauncher.ui.TextSize

/** The home screen's right strip the rail takes; Home leaves it free of tiles in portrait. */
internal val HomeRailWidth = 22.dp

private val HomeWaveLean = 88.dp
private const val HomeWaveSpread = 4.6f
private val PanelRow = 40.dp
private val PanelHeader = 48.dp
private const val PanelMaxRows = 9
private const val RailPoll = 400L

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
    val shownGroups by rememberUpdatedState(groups)
    // A short rail shows every other letter or so, so each one stays whole; the touch picks among the ones shown.
    val lineHeight = with(density) { (TextSize.Small * RailLineSpacing).toPx() }
    val shown = railShown(letters.size, height, lineHeight)
    val shownLetters by rememberUpdatedState(shown)
    val allLetters by rememberUpdatedState(letters)
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
                            val all = allLetters
                            val shownNow = shownLetters
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
                            val i = railPick(y, height, shownNow)
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
                        val all = allLetters
                        val target = if (picked in all.indices) shownGroups[all[picked]].orEmpty().getOrNull(hover) else null
                        touch = Float.NaN
                        hover = -1
                        if (target != null) open(target) else pinned = picked >= 0
                    }
                },
        ) {
            shown.forEachIndexed { k, i ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BasicText(
                        letters[i].toString(),
                        Modifier.graphicsLayer {
                            val slot = height.toFloat() / shownLetters.size
                            val d = if (touch.isNaN()) Float.POSITIVE_INFINITY else (touch - slot * (k + 0.5f)) / (slot * HomeWaveSpread)
                            translationX = -HomeWaveLean.toPx() * exp(-d * d / 2f) * wave
                        },
                        style = Mono.copy(color = if (i == picked && (touching || pinned)) Palette.accent else Palette.muted, fontSize = TextSize.Small, textAlign = TextAlign.Center),
                    )
                }
            }
        }
    }
}

/**
 * Where the rail stands on home: from the top of the tiles' first row, as HomeGrid places it (clear of the system bars,
 * then EdgeGap), down the rows the grid has, so the strip of recent apps under them stays the strip's. Until the grid
 * is first laid out, from that top down to the bar.
 */
@Composable
internal fun Modifier.homeRailBounds(
    layout: WidgetLayout,
    barRoom: Dp,
): Modifier {
    val density = LocalDensity.current
    // HomeGrid's keepClear, as Home works it out.
    @OptIn(ExperimentalLayoutApi::class)
    val bars = (if (LauncherStyle.style[Style.STATUS_BAR_SHOWN]) WindowInsets.systemBarsIgnoringVisibility else WindowInsets.systemBars).union(WindowInsets.displayCutout)
    val top = with(density) { bars.getTop(density).toDp() } + EdgeGap
    // The grid's size is plain fields HomeGrid sets when laid out, not state: asked again for as long as the rail is on
    // screen, since the grid can change (keyboard, insets, another app) and the rail goes with it.
    val tall by produceState<Float?>(null, layout) {
        followRail(
            read = { railTall(layout.gridSize()?.second, layout.geometry?.second) },
            pause = { delay(RailPoll) },
            emit = { value = it },
        )
    }
    val h = tall
    return if (h == null) fillMaxHeight().padding(top = top, bottom = barRoom) else padding(top = top).height(with(density) { h.toDp() })
}

/** The height of the grid's rows, or null until HomeGrid has reported both the rows and the cell. */
internal fun railTall(rows: Int?, cell: Float?): Float? = if (rows != null && cell != null) rows * cell else null

/** Reads the rail's height with [read] after each [pause], for as long as it runs, and hands each new height to [emit]. */
internal suspend fun followRail(
    read: () -> Float?,
    pause: suspend () -> Unit,
    emit: (Float) -> Unit,
) {
    var last: Float? = null
    while (true) {
        val now = read()
        if (now != null && now != last) {
            last = now
            emit(now)
        }
        pause()
    }
}

/** A letter's line is about this much taller than its font size. */
private const val RailLineSpacing = 1.25f

/**
 * The letters (indexes into the rail's [count]) a rail [height] px tall shows, each in a slot at least [line] px high so
 * none is squeezed or clipped: all of them when they fit, else evenly spread from the first to the last.
 */
internal fun railShown(
    count: Int,
    height: Int,
    line: Float,
): List<Int> {
    if (count <= 2 || height <= 0 || line <= 0f) return List(count) { it }
    val fit = (height / line).toInt().coerceIn(2, count)
    if (fit >= count) return List(count) { it }
    return List(fit) { k -> (k * (count - 1).toFloat() / (fit - 1)).roundToInt() }
}

/** The letter (an entry of [shown]) under [y] px on a rail [height] px tall whose slots hold the [shown] letters. */
internal fun railPick(
    y: Float,
    height: Int,
    shown: List<Int>,
): Int = shown[(y / height * shown.size).toInt().coerceIn(0, shown.lastIndex)]
