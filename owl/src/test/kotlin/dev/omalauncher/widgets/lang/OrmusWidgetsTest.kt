package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The widgets only the Ormus build ships. LangTest.bundledWidgetsCompile reads the upstream folder. */
class OrmusWidgetsTest {
    /** The Ormus build seeds these on home: one that does not compile would show an error tile there. */
    @Test fun ormusWidgetsCompile() {
        val dir = java.io.File("../app/src/main/assets/ormus/widgets")
        val files = dir.walk().filter { it.extension == "owl" }.toList()
        assertTrue("no widgets found in ${dir.absolutePath}", files.size > 5)
        for (f in files) {
            try {
                Compiler.compile(f.readText())
            } catch (e: CompileError) {
                fail("${f.name}: ${Source(f.readText()).format(e)}")
            }
        }
    }

    /** The board tiles are the sessions tile with three lines changed (scripts/ormus-board-tiles.sh); one edited by hand drifts from it. */
    @Test fun boardTilesAreTheSessionsTileWithThreeLinesChanged() {
        val dir = java.io.File("../app/src/main/assets/ormus/widgets")
        val lines = java.io.File(dir, "10-sessions.owl").readText().split("\n")
        for ((board, page) in listOf("raven" to 101, "ormus" to 201, "alqvimia" to 301, "clients" to 401)) {
            val swaps = mapOf("widget \"sessions\" {" to "widget \"board-$board\" {", "  page: 1" to "  page: $page", "  let only = \"\"" to "  let only = \"$board\"")
            for (from in swaps.keys) assertEquals("$from once in 10-sessions.owl", 1, lines.count { it == from })
            val expected = lines.joinToString("\n") { swaps[it] ?: it }
            assertEquals("11-board-$board.owl is 10-sessions.owl with three lines changed", expected, java.io.File(dir, "11-board-$board.owl").readText())
        }
    }
}
