package dev.omalauncher.ui.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
