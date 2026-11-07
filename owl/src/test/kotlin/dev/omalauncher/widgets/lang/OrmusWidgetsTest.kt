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

    /** The pass tile installs from The Pass on sg alone, and the reviewer's line says so. */
    @Test fun thePassTileInstallsFromThePassAlone() {
        val p = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/50-pass.owl").readText())
        assertEquals(listOf("install an app from the web: https://sg.tailb43a3a.ts.net/pass/…"), p.commands.filter { it.startsWith("install") })
    }

    /** The pass tile's key: update or get with a hash installs; a row with no hash, or one current, opens the page. The handler is the tile's own text. */
    @Test fun thePassTilesKeyInstallsOnlyWithAHash() {
        val tile = java.io.File("../app/src/main/assets/ormus/widgets/50-pass.owl").readText()
        val handler = Regex("""Button \{ text: r\.st;.*?onTap: (\{.*\}) \}""").find(tile)?.groupValues?.get(1) ?: fail("the key's handler is not where this test looks")
        val p = Compiler.compile("widget {\nmeta { owl: 9 }\nlet page = \"https://sg.tailb43a3a.ts.net/pass/\"\nstate r = null\nonTap: $handler\nText { text: page }\n}")
        val slot = p.globals.indexOfFirst { it is Global.State }
        fun tap(st: String, sha: Value): List<String> {
            // With the program, so the `page` let reads its text in the handler.
            val host = LangTest.TestHost(p)
            host.slots[slot] = Obj(mapOf("st" to Str(st), "b" to Obj(mapOf("apk" to Str("mercurio/x.apk"), "sha256" to sha))))
            p.onTap!!.run(Env.root(host), emptyList())
            return host.actions
        }
        val h = "ab".repeat(32)
        assertEquals(listOf("install https://sg.tailb43a3a.ts.net/pass/mercurio/x.apk, $h"), tap("update", Str(h)))
        assertEquals(listOf("install https://sg.tailb43a3a.ts.net/pass/mercurio/x.apk, $h"), tap("get", Str(h)))
        for ((st, sha) in listOf("get" to Null, "update" to Null, "current" to Str(h), "on phone" to Str(h))) assertEquals("$st without install", listOf("url https://sg.tailb43a3a.ts.net/pass/"), tap(st, sha))
    }

    /** The board tiles are the sessions tile with three lines changed (scripts/ormus-board-tiles.sh); one edited by hand drifts from it. */
    @Test fun boardTilesAreTheSessionsTileWithThreeLinesChanged() {
        val dir = java.io.File("../app/src/main/assets/ormus/widgets")
        val lines = java.io.File(dir, "10-sessions.owl").readText().split("\n")
        val boards = listOf("raven" to 101, "ormus" to 201, "alqvimia" to 301, "clients" to 401)
        assertEquals("every board tile is one the script writes", boards.map { "11-board-${it.first}.owl" }.toSet(), dir.listFiles().orEmpty().map { it.name }.filter { it.startsWith("11-board-") }.toSet())
        for ((board, page) in boards) {
            val swaps = mapOf("widget \"sessions\" {" to "widget \"board-$board\" {", "  page: 1" to "  page: $page", "  let only = \"\"" to "  let only = \"$board\"")
            for (from in swaps.keys) assertEquals("$from once in 10-sessions.owl", 1, lines.count { it == from })
            val expected = lines.joinToString("\n") { swaps[it] ?: it }
            assertEquals("11-board-$board.owl is 10-sessions.owl with three lines changed", expected, java.io.File(dir, "11-board-$board.owl").readText())
        }
    }
}
