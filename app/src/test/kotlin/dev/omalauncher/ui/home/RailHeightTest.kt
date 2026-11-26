package dev.omalauncher.ui.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RailHeightTest {
    /** Runs followRail over a script: each step is the grid's height at one read (null: not laid out yet). */
    private fun run(vararg steps: Float?): List<Float> {
        val seen = mutableListOf<Float>()
        var at = 0
        runBlocking {
            try {
                followRail(
                    read = { steps[at] },
                    pause = {
                        at++
                        if (at >= steps.size) throw CancellationException("script over")
                    },
                    emit = { seen += it },
                )
            } catch (_: CancellationException) {
            }
        }
        return seen
    }

    @Test fun railTallIsRowsTimesCellOnceBothAreKnown() {
        assertEquals(null, railTall(null, 100f))
        assertEquals(null, railTall(6, null))
        assertEquals(600f, railTall(6, 100f)!!, 0f)
    }

    @Test fun theRailGetsTheHeightOnceTheGridIsLaidOut() {
        assertEquals(listOf(600f), run(null, null, 600f))
    }

    @Test fun theRailFollowsTheGridWhenItChangesSize() {
        assertEquals(listOf(400f, 600f), run(400f, 400f, 600f, 600f))
    }

    @Test fun theRailSaysNothingWhileTheGridStaysTheSame() {
        assertEquals(listOf(600f), run(600f, 600f, 600f, 600f))
    }

    @Test fun allLettersShowWhenEachSlotHoldsItsLine() {
        assertEquals((0 until 26).toList(), railShown(26, height = 26 * 40, line = 40f))
        assertEquals((0 until 26).toList(), railShown(26, height = 2000, line = 40f))
    }

    @Test fun aShortRailShowsFewerLettersEachInASlotThatHoldsItsLine() {
        val shown = railShown(26, height = 400, line = 40f)
        assertEquals(10, shown.size)
        assertEquals(0, shown.first())
        assertEquals(25, shown.last())
        assertEquals(shown.sorted().distinct(), shown)
        assertTrue(400f / shown.size >= 40f)
    }

    @Test fun lessRoomNeverShowsMoreLettersAndNeverFewerThanTwo() {
        var before = 26
        for (h in 2000 downTo 20 step 20) {
            val n = railShown(26, h, 40f).size
            assertTrue(n <= before)
            assertTrue(n >= 2)
            before = n
        }
    }

    @Test fun aRailNotMeasuredYetShowsEveryLetter() {
        assertEquals((0 until 26).toList(), railShown(26, height = 0, line = 40f))
    }

    @Test fun theTouchPicksAmongTheLettersShown() {
        val shown = listOf(0, 8, 17, 25)
        assertEquals(0, railPick(0f, 400, shown))
        assertEquals(8, railPick(150f, 400, shown))
        assertEquals(17, railPick(250f, 400, shown))
        assertEquals(25, railPick(399f, 400, shown))
        assertEquals(25, railPick(900f, 400, shown))
        assertEquals(0, railPick(-5f, 400, shown))
    }
}
