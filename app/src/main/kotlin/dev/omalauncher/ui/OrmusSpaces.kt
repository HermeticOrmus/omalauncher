package dev.omalauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.omalauncher.Ormus
import kotlinx.coroutines.delay
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView

/**
 * Omalauncher Ormus: runs [up] or [down] once for a vertical swipe of 72 dp or a quick fling, for gestures that do
 * something rather than drag the drawer. Widgets that scroll keep their own swipes: they take the drag first.
 */
@Composable
internal fun Modifier.swipeVertical(
    enabled: Boolean,
    up: () -> Unit,
    down: () -> Unit,
): Modifier {
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    var total by remember { mutableFloatStateOf(0f) }
    return draggable(
        orientation = Orientation.Vertical,
        enabled = enabled,
        state = rememberDraggableState { dy -> total += dy },
        onDragStarted = { total = 0f },
        onDragStopped = { velocity: Float ->
            when (swipeDirection(total, velocity, threshold)) {
                -1 -> up()
                1 -> down()
            }
            total = 0f
        },
    )
}

/**
 * Which way a vertical swipe went: -1 up, 1 down, 0 too short for either. The distance travelled decides, and a quick
 * fling only counts the way the finger moved: a finger rolling as it lifts can flick back against the swipe.
 */
internal fun swipeDirection(
    total: Float,
    velocity: Float,
    threshold: Float,
    fling: Float = 1500f,
): Int =
    when {
        total > threshold || (total > 0f && velocity > fling) -> 1
        total < -threshold || (total < 0f && velocity < -fling) -> -1
        else -> 0
    }

/** How far into a space change home is, 0 to 1: the new space's tiles slide in, from the way the swipe went, as it lands. */
@Composable
internal fun spaceArrival(space: Int): Float {
    val progress = remember { Animatable(1f) }
    var landed by remember { mutableIntStateOf(space) }
    LaunchedEffect(space) {
        if (space == landed) return@LaunchedEffect
        progress.snapTo(0f)
        landed = space
        progress.animateTo(1f, tween(220))
    }
    // Hidden from the first frame of the new space, before the effect has run.
    return if (landed != space) 0f else progress.value
}

/** The spaces, the one just reached in the accent, for a moment after each change, like Omarchy's workspace switch. */
@Composable
internal fun SpaceFlash(
    space: Int,
    modifier: Modifier = Modifier,
) {
    var seen by remember { mutableIntStateOf(space) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(space) {
        if (space == seen) return@LaunchedEffect
        seen = space
        shown = true
        delay(900)
        shown = false
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 90 else 400), label = "space flash")
    if (alpha == 0f) return
    Box(modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(Palette.surface, LauncherStyle.shape(12.dp))
                .border(1.dp, Palette.border, LauncherStyle.shape(12.dp))
                .padding(horizontal = 28.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Ormus.SPACES.forEachIndexed { i, name ->
                BasicText(
                    name,
                    style =
                        if (i == space) {
                            Mono.copy(color = Palette.accent, fontSize = TextSize.Title * 1.6f)
                        } else {
                            Mono.copy(color = Palette.muted, fontSize = TextSize.Item)
                        },
                )
            }
        }
    }
}

/**
 * Which space home shows: five dots down the top of the rail's strip, in the order a swipe goes through them, the
 * current one brightest. A long press opens [SpacePicker], to go straight to any of them.
 */
@Composable
internal fun SpaceDots(
    state: LauncherState,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    Column(
        modifier
            .padding(vertical = 8.dp)
            .pointerInput(state) {
                detectTapGestures(onLongPress = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    state.pickingSpace = true
                })
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Ormus.SPACES.indices.forEach { i ->
            val here = i == state.space
            Box(Modifier.size(if (here) 7.dp else 5.dp).background(if (here) Palette.foreground else Palette.muted, CircleShape))
        }
    }
}

/** The spaces by name, as [SpaceFlash] shows them, to tap one and go there; a tap anywhere else or Back closes it. */
@Composable
internal fun SpacePicker(
    state: LauncherState,
    modifier: Modifier = Modifier,
) {
    if (!state.pickingSpace) return
    BackHandler { state.pickingSpace = false }
    Box(
        modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.pickingSpace = false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(200.dp)
                .background(Palette.surface, LauncherStyle.shape(12.dp))
                .border(1.dp, Palette.border, LauncherStyle.shape(12.dp))
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Ormus.SPACES.forEachIndexed { i, name ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            state.goToSpace(i)
                            state.pickingSpace = false
                        }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        name,
                        style =
                            if (i == state.space) {
                                Mono.copy(color = Palette.accent, fontSize = TextSize.Title * 1.6f)
                            } else {
                                Mono.copy(color = Palette.foreground, fontSize = TextSize.Item)
                            },
                    )
                }
            }
        }
    }
}
