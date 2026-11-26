package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tiles that reach Ormus's agents use herdr-core alone (Ormus, 2026-11-15: "make sure that mercurio and all widgets
 * are connected properly to herdr core and not to old herdr tui workboard/terminals"): a tap opens the agent's
 * herdr-core terminal in Mercurio (Ormus, 2026-11-26: one app owns alerts, opening and answering agents, and that app
 * is Mercurio), the Mercurio tile reads sg's sessions feed, and the kiosk starts its agent with `hc run`.
 */
class OrmusHerdrCoreTest {
    private fun compile(name: String) = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/$name").readText())

    private fun agent(
        status: String,
        pane: String,
        machine: String = "sg",
        title: String = "RAV-1",
        seq: Long = 1_794_165_700_000,
    ) = """{"kind": "claude", "status": "$status", "title": "$title", "tab": "4 · RAV-1", "machine": "$machine", "session": "core", "pane": "$pane", "seq": $seq, "term": "$pane-1"}"""

    private fun feed(
        vararg agents: String,
        machines: String = """{"sg": "ok", "sun": "ok", "mars": "ok"}""",
        totals: String? = null,
    ): Value {
        // The counts by state, as the feed writes them beside the agents.
        val counts = listOf("blocked", "working", "done", "idle").joinToString(", ") { s -> "\"$s\": ${agents.count { it.contains("\"status\": \"$s\"") }}" }
        return Json.parse(
            """{"ok": true, "code": 200, "out": {"generated": 1794166000, "machines": $machines, "errors": {}, "needs": 0,
            "agents": ${totals ?: "{$counts}"}, "waiting": 0, "unsorted": [],
            "links": {"sg": "ssh://ormus@sg:22#sg", "mars": "ssh://ops@mars:22#mars"}, "hosts": {"sg": ["sg", "sg.tail.ts.net"], "mars": ["mars"]}, "game": {"today": 0, "into": 0, "need": 3, "level": 1, "streak": 0}, "moves": [],
            "boards": [{"name": "raven", "agents": {$counts}, "waiting": 0,
              "projects": [{"name": "PORTALS", "agents": {$counts}, "chats": [], "top": [${agents.joinToString(",")}]}]}]}}""",
        )
    }

    private fun attaches(
        actions: List<String>,
        machine: String,
        pane: String,
    ) = actions.any { it.startsWith("terminal ssh -t ") && it.contains(machine) && it.contains("exec hc attach $pane") }

    // Mercurio's core watch from a launcher's tap (Mercurio #350): the machine's link and tailnet names, the terminal's
    // address, and no session (a session name is its Herdr TUI path).
    private fun opensInMercurio(
        actions: List<String>,
        machine: String,
        pane: String,
        names: String = machine,
    ) = actions.any {
        it.startsWith("androidIntent") && it.contains("solutions.ormus.logos") && it.contains("org.connectbot.ui.MainActivity") &&
            it.contains("ssh://") && it.contains("@$machine:22") && it.contains("solutions.ormus.logos.extra.CORE_ADDRESS") &&
            it.contains(pane) && it.contains("solutions.ormus.logos.extra.HERD_HOSTS") && it.contains(names) && !it.contains("HERD_SESSION")
    } && actions.none { it.startsWith("terminal") }

    @Test fun noOrmusTileCallsTheOldHerdrTui() {
        val dir = java.io.File("../app/src/main/assets/ormus/widgets")
        // A command line or an argument list that starts the TUI; "herdr" alone is a word (the trainer has a deck of that name).
        val tui = Regex("""herdr (--session|agent |workspace |tab |pane |session )|\["herdr", |exec herdr""")
        val hits = dir.listFiles()!!.filter { it.extension == "owl" }.filter { tui.containsMatchIn(it.readText()) }.map { it.name }
        assertEquals("tiles that still call the herdr TUI", emptyList<String>(), hits)
        // Mercurio's Herdr path (a session name on its intent) attaches the TUI: no tile takes it.
        val herd = dir.listFiles()!!.filter { it.extension == "owl" && it.readText().contains("HERD_SESSION") }.map { it.name }
        assertEquals("tiles that open Mercurio's Herdr path", emptyList<String>(), herd)
    }

    @Test fun aHerdrCoreAgentsRowOnTheSessionsScreenOpensItInMercurio() {
        val program = compile("10-sessions.owl")
        val t = TileHost(program, feed(agent("working", "t15")))
        t.set("apps", Json.parse("""[{"package": "solutions.ormus.logos.debug", "label": "Mercurio"}]"""))
        val tapped = t.tap(program.screen!!, "working RAV-1 claude · sg")
        assertTrue("$tapped", opensInMercurio(tapped, "sg", "t15", names = "sg,sg.tail.ts.net"))
        assertTrue("the tab names the watch view: $tapped", tapped.any { it.contains("solutions.ormus.logos.extra.CORE_TAB") && it.contains("4 · RAV-1") })
    }

    @Test fun anAgentOnAMachineWithNoLink_opensMercurio_neverTheLaunchersTerminal() {
        val program = compile("10-sessions.owl")
        val t = TileHost(program, feed(agent("working", "t4", machine = "sun", title = "builds")))
        t.set("apps", Json.parse("""[{"package": "solutions.ormus.logos", "label": "Mercurio"}]"""))
        val tapped = t.tap(program.screen!!, "working builds claude · sun")
        assertTrue("$tapped", tapped.any { it.startsWith("androidApp") && it.contains("solutions.ormus.logos") })
        assertTrue("$tapped", tapped.none { it.startsWith("terminal") })
    }

    @Test fun theSessionsTilePostsNoNotification_MercurioOwnsTheAlerts() {
        // Ormus, 2026-11-26 ("Adopt it"): only Mercurio alerts; the launcher keeps the home-screen glance.
        val text = java.io.File("../app/src/main/assets/ormus/widgets/10-sessions.owl").readText()
        assertFalse("10-sessions.owl still posts a notification", Regex("""\bnotify\(""").containsMatchIn(text))
        assertFalse("10-sessions.owl still takes an alert from sg", text.contains("on alert"))
    }

    @Test fun theMercurioTileListsEveryMachinesAgentsFromTheFeedAndOpensThemInMercurio() {
        val program = compile("30-mercurio.owl")
        val t = TileHost(program, feed(agent("idle", "t3", title = "old one"), agent("blocked", "t9", machine = "mars", title = "needs you"), agent("working", "t4", machine = "sun", title = "builds")))
        val lines = t.lines(program.root)
        assertTrue("$lines", "mercurio · 3" in lines && "1 waiting · 1 working · 0 done" in lines)
        val order = listOf("needs you", "builds", "old one").map { lines.indexOf(it) }
        assertTrue("waiting first, then working, then idle: $lines", order.all { it >= 0 } && order == order.sorted())
        assertTrue("project and machine: $lines", "portals·mars" in lines)
        assertTrue("how long ago, from the agent's state time: $lines", "5m" in lines)
        t.set("apps", Json.parse("""[{"package": "solutions.ormus.logos", "label": "Mercurio"}]"""))
        val tapped = t.tap(program.root, "waiting needs you portals·mars 5m")
        assertTrue("$tapped", opensInMercurio(tapped, "mars", "t9"))
    }

    @Test fun theMercurioTilesCountsAreTheFeedsTotalsNotTheShortLists() {
        val program = compile("30-mercurio.owl")
        val f = feed(agent("blocked", "t9"), totals = """{"blocked": 12, "working": 3, "done": 1, "idle": 40}""")
        val lines = TileHost(program, f).lines(program.root)
        assertTrue("a project lists 8 at most; the counts are the feed's: $lines", "mercurio · 56" in lines && "12 waiting · 3 working · 1 done" in lines)
    }

    @Test fun theMercurioTileNamesAMachineTheFeedCouldNotReadAndAFeedThatIsDown() {
        val program = compile("30-mercurio.owl")
        val t = TileHost(program, feed(agent("idle", "t3"), machines = """{"sg": "ok", "sun": "no answer", "mars": "ok"}"""))
        assertTrue(t.lines(program.root).toString(), "sun didn't answer: their agents are missing" in t.lines(program.root))
        val down = TileHost(program, Json.parse("""{"ok": false, "code": -1, "out": null}"""))
        assertTrue(down.lines(program.root).toString(), "sg doesn't answer" in down.lines(program.root))
    }

    @Test fun theKioskStartsItsAgentWithHcRunAndAttachesToIt() {
        val program = compile("45-kiosk.owl")
        val text = java.io.File("../app/src/main/assets/ormus/widgets/45-kiosk.owl").readText()
        assertTrue("hc run in the script sg runs", text.contains("\"hc\", \"run\", \"--bg\""))
        val t = TileHost(program, Json.parse("""{"ok": true, "code": 200, "out": {"generated": 1794166000, "picks": [], "errors": {}}}"""))
        t.fire("spun", Str("""{"n": 1, "r": {"ok": true, "pane": "t77", "session": "core", "title": "An article"}}"""))
        assertTrue("${t.actions}", attaches(t.actions, "sg", "t77"))
    }
}
