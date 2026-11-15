package dev.omalauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Omalauncher Ormus asks nothing of upstream's news (Ormus.UPSTREAM_NEWS): settings neither ask GitHub for upstream's
 * releases nor fetch its developer's announcements. Both checks need a Context, so this pins the guard that opens each
 * one, as a rebase onto a new upstream must keep it: a rebase that drops a guard fails here.
 */
class OrmusUpstreamNewsTest {
    @Test fun upstreamNewsIsOff() {
        assertFalse(Ormus.UPSTREAM_NEWS)
    }

    @Test fun theReleaseCheckAsksNothingWhileUpstreamNewsIsOff() {
        assertEquals("if (!Ormus.UPSTREAM_NEWS) return", firstLineOf("src/foss/kotlin/dev/omalauncher/LauncherUpdates.kt", "fun check(force: Boolean = false) {"))
    }

    @Test fun theAnnouncementsAskNothingWhileUpstreamNewsIsOff() {
        assertEquals("if (!Ormus.UPSTREAM_NEWS) return", firstLineOf("src/main/kotlin/dev/omalauncher/net/Announcements.kt", "fun check() {"))
    }

    /** The first line of the body of the one function in [path] that opens with [signature]. */
    private fun firstLineOf(
        path: String,
        signature: String,
    ): String {
        val lines = File(path).readLines()
        val at = lines.indices.filter { lines[it].trim() == signature }
        assertEquals("$path: one $signature", 1, at.size)
        return lines[at.single() + 1].trim()
    }
}
