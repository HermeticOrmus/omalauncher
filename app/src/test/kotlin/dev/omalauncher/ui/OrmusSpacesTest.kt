package dev.omalauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OrmusSpacesTest {
    @Test fun aLongSwipeGoesTheWayItTravelled() {
        assertEquals(1, swipeDirection(total = 600f, velocity = 900f, threshold = 200f))
        assertEquals(-1, swipeDirection(total = -600f, velocity = -900f, threshold = 200f))
    }

    @Test fun aFingerRollingBackAsItLiftsDoesNotTurnTheSwipeAround() {
        // Down the screen, then a quick roll back up at the end: still down, never Mercurio.
        assertEquals(1, swipeDirection(total = 600f, velocity = -2000f, threshold = 200f))
        assertEquals(-1, swipeDirection(total = -600f, velocity = 2000f, threshold = 200f))
    }

    @Test fun aShortFlickCountsTheWayItMoved() {
        assertEquals(1, swipeDirection(total = 60f, velocity = 2000f, threshold = 200f))
        assertEquals(-1, swipeDirection(total = -60f, velocity = -2000f, threshold = 200f))
    }

    @Test fun aShortFlickAgainstItsOwnMovementIsNothing() {
        assertEquals(0, swipeDirection(total = 60f, velocity = -2000f, threshold = 200f))
    }

    @Test fun aNudgeIsNothing() {
        assertEquals(0, swipeDirection(total = 40f, velocity = 300f, threshold = 200f))
    }
}
