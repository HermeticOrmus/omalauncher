package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tiles that reach Ormus's agents use herdr-core alone (Ormus, 2026-11-15: "make sure that mercurio and all widgets
 * are connected properly to herdr core and not to old herdr tui workboard/terminals"): a tap opens the agent's
 * herdr-core terminal in Mercurio (Ormus, 2026-11-26: one app owns alerts, opening and answering agents, and that app
 * is Mercurio), the Mercurio tile reads sg's sessions feed, and the kiosk starts its agent with `hc run` and opens it in Mercurio.
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
        hosts: String = """{"sg": ["sg", "sg.tail.ts.net"], "mars": ["mars", "mars.tail.ts.net"]}""",
    ): Value {
        // The counts by state, as the feed writes them beside the agents.
        val counts = listOf("blocked", "working", "done", "idle").joinToString(", ") { s -> "\"$s\": ${agents.count { it.contains("\"status\": \"$s\"") }}" }
        return Json.parse(
            """{"ok": true, "code": 200, "out": {"generated": 1794166000, "machines": $machines, "errors": {}, "needs": 0,
            "agents": ${totals ?: "{$counts}"}, "waiting": 0, "unsorted": [],
            "links": {"sg": "ssh://ormus@sg:22#sg", "mars": "ssh://ops@mars:22#mars"}, "hosts": $hosts, "game": {"today": 0, "into": 0, "need": 3, "level": 1, "streak": 0}, "moves": [],
            "boards": [{"name": "raven", "agents": {$counts}, "waiting": 0,
              "projects": [{"name": "PORTALS", "agents": {$counts}, "chats": [], "top": [${agents.joinToString(",")}]}]}]}}""",
        )
    }

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
        t.set("apps", Json.parse("""[{"package": "solutions.ormus.logos.debug", "label": "Mercurio", "versionCode": 12797000}]"""))
        val tapped = t.tap(program.screen!!, "working RAV-1 claude · sg")
        assertTrue("$tapped", opensInMercurio(tapped, "sg", "t15", names = "sg,sg.tail.ts.net"))
        assertTrue("the tab names the watch view: $tapped", tapped.any { it.contains("solutions.ormus.logos.extra.CORE_TAB") && it.contains("4 · RAV-1") })
    }

    @Test fun anAgentOnAMachineWithNoLink_opensMercurio_neverTheLaunchersTerminal() {
        val program = compile("10-sessions.owl")
        val t = TileHost(program, feed(agent("working", "t4", machine = "sun", title = "builds")))
        t.set("apps", Json.parse(mercurio))
        val tapped = t.tap(program.screen!!, "working builds claude · sun")
        assertTrue("$tapped", tapped.any { it.startsWith("androidApp") && it.contains("solutions.ormus.logos") })
        assertTrue("$tapped", tapped.none { it.startsWith("terminal") })
    }

    // Mercurio before 1.27.97 (versionCode 12797000) sends this intent down its Herdr TUI path, and so does any build
    // when the names are blank: the tap opens Mercurio itself then.
    private val mercurio = """[{"package": "solutions.ormus.logos", "label": "Mercurio", "versionCode": 12797000}]"""

    @Test fun theCardThatNeedsHim_opensTheAgentInMercurio() {
        val program = compile("10-sessions.owl")
        val t = TileHost(program, feed(agent("blocked", "t21")))
        t.set("apps", Json.parse(mercurio))
        val taps = t.tapAll(program.root)
        assertTrue("$taps", opensInMercurio(taps, "sg", "t21", names = "sg,sg.tail.ts.net"))
        // The card's own tap and its "open in Mercurio" key: both the intent, neither Mercurio's home.
        assertEquals("$taps", 2, taps.count { it.startsWith("androidIntent") })
        assertTrue("$taps", taps.none { it.startsWith("androidApp") })
    }

    @Test fun aLinkWithNoNames_orAMercurioBefore1_27_97_opensMercurioItself() {
        val program = compile("10-sessions.owl")
        val noNames = TileHost(program, feed(agent("working", "t15"), hosts = "{}"))
        noNames.set("apps", Json.parse(mercurio))
        val a = noNames.tap(program.screen!!, "working RAV-1 claude · sg")
        assertTrue("$a", a.any { it.startsWith("androidApp solutions.ormus.logos") } && a.none { it.startsWith("androidIntent") })

        val old = TileHost(program, feed(agent("working", "t15")))
        old.set("apps", Json.parse("""[{"package": "solutions.ormus.logos", "label": "Mercurio", "versionCode": 12796000}]"""))
        val b = old.tap(program.screen!!, "working RAV-1 claude · sg")
        assertTrue("$b", b.any { it.startsWith("androidApp solutions.ormus.logos") } && b.none { it.startsWith("androidIntent") })
    }

    // The card's own tap and its key take the same two ways as a row of the screen's list: the intent, or Mercurio itself.
    @Test fun aCardWithNoNames_orAMercurioBefore1_27_97_opensMercurioItselfOnItsTapAndItsKey() {
        val program = compile("10-sessions.owl")
        val noNames = TileHost(program, feed(agent("blocked", "t21"), hosts = "{}"))
        noNames.set("apps", Json.parse(mercurio))
        val a = noNames.tapAll(program.root)
        assertEquals("$a", 2, a.count { it.startsWith("androidApp solutions.ormus.logos") })
        assertTrue("$a", a.none { it.startsWith("androidIntent") })

        val old = TileHost(program, feed(agent("blocked", "t21")))
        old.set("apps", Json.parse("""[{"package": "solutions.ormus.logos", "label": "Mercurio", "versionCode": 12796000}]"""))
        val b = old.tapAll(program.root)
        assertEquals("$b", 2, b.count { it.startsWith("androidApp solutions.ormus.logos") })
        assertTrue("$b", b.none { it.startsWith("androidIntent") })

        // A card of another agent kind than herdr-core opens Mercurio itself as well, whatever the names.
        val other = TileHost(program, feed(agent("blocked", "t21").replace("\"session\": \"core\"", "\"session\": \"raven\"")))
        other.set("apps", Json.parse(mercurio))
        val c = other.tapAll(program.root)
        assertEquals("$c", 2, c.count { it.startsWith("androidApp solutions.ormus.logos") })
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
        t.set("apps", Json.parse(mercurio))
        val tapped = t.tap(program.root, "waiting needs you portals·mars 5m")
        assertTrue("$tapped", opensInMercurio(tapped, "mars", "t9", names = "mars,mars.tail.ts.net"))
        assertTrue("the project names the watch view's space: $tapped", tapped.any { it.contains("solutions.ormus.logos.extra.CORE_SPACE") && it.contains("PORTALS") })
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

    private val kioskFeed = """{"ok": true, "code": 200, "out": {"generated": 1794166000, "picks": [], "errors": {}}}"""

    /** The kiosk's tile with the sessions feed that names sg (its link and tailnet names) and the apps of the phone. */
    private fun kiosk(
        apps: String,
        sessions: Value = feed(agent("idle", "t1")),
    ): TileHost {
        val t = TileHost(compile("45-kiosk.owl"), Json.parse(kioskFeed))
        t.set("apps", Json.parse(apps))
        t.set("sess", sessions)
        return t
    }

    @Test fun theKioskStartsItsAgentWithHcRunAndOpensItInMercurio() {
        val text = java.io.File("../app/src/main/assets/ormus/widgets/45-kiosk.owl").readText()
        assertTrue("hc run in the script sg runs", text.contains("\"hc\", \"run\", \"--bg\""))
        val t = kiosk(mercurio)
        t.fire("spun", Str("""{"n": 1, "r": {"ok": true, "pane": "t77", "session": "core", "title": "An article"}}"""))
        assertTrue("${t.actions}", opensInMercurio(t.actions, "sg", "t77", names = "sg,sg.tail.ts.net"))
    }

    @Test fun theKioskOpensMercurioItselfWhenItHasNoNamesOrMercurioIsOlder() {
        val answer = Str("""{"n": 1, "r": {"ok": true, "pane": "t77", "session": "core", "title": "An article"}}""")
        val noNames = kiosk(mercurio, sessions = feed(agent("idle", "t1"), hosts = "{}"))
        noNames.fire("spun", answer)
        assertTrue("${noNames.actions}", noNames.actions.any { it.startsWith("androidApp solutions.ormus.logos") } && noNames.actions.none { it.startsWith("androidIntent") || it.startsWith("terminal") })
        val old = kiosk("""[{"package": "solutions.ormus.logos", "label": "Mercurio", "versionCode": 12796000}]""")
        old.fire("spun", answer)
        assertTrue("${old.actions}", old.actions.any { it.startsWith("androidApp solutions.ormus.logos") } && old.actions.none { it.startsWith("androidIntent") || it.startsWith("terminal") })
    }

    @Test fun aSessionThatDidNotStartShowsItsReasonInTheTileAndPostsNoNotification() {
        val program = compile("45-kiosk.owl")
        val t = TileHost(program, Json.parse(kioskFeed))
        t.fire("spun", Str("""{"n": 2, "r": {"ok": false, "err": "hc run failed: no space"}}"""))
        assertEquals("hc run failed: no space", t.text("note"))
        assertTrue("the reason is in the tile's header: ${t.lines(program.root)}", "hc run failed: no space" in t.lines(program.root))
        assertTrue("${t.actions}", t.actions.none { it.startsWith("notify") || it.startsWith("terminal") || it.startsWith("androidIntent") })
    }

    /** The code of a widget: its `//` comment lines taken out, so a comment that names what was cut does not count. */
    private fun code(file: java.io.File) = file.readLines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    @Test fun noOrmusWidgetAttachesATerminalOrPostsANotification_MercurioOwnsBoth() {
        val dir = java.io.File("../app/src/main/assets/ormus/widgets")
        val owls = dir.listFiles()!!.filter { it.extension == "owl" }
        assertTrue("widgets found", owls.size > 5)
        assertEquals("widgets with `hc attach` in their code", emptyList<String>(), owls.filter { code(it).contains("hc attach") }.map { it.name })
        assertEquals("widgets that call notify(", emptyList<String>(), owls.filter { Regex("""\bnotify\(""").containsMatchIn(code(it)) }.map { it.name })
    }

    // Do Not Disturb is Ormus's phone-wide switch, not an alert of the tile: the decision of record does not cover it.
    @Test fun theSessionsScreenKeepsItsDoNotDisturbRow() {
        val text = code(java.io.File("../app/src/main/assets/ormus/widgets/10-sessions.owl"))
        assertTrue("10-sessions.owl reads the phone's modes", text.contains("source modes = android.modes"))
        assertTrue("10-sessions.owl sets Do Not Disturb only from his tap", Regex("""onTap: dnd\(""").containsMatchIn(text))
        for (b in listOf("alqvimia", "clients", "ormus", "raven")) {
            assertTrue("the $b copy keeps it", code(java.io.File("../app/src/main/assets/ormus/widgets/11-board-$b.owl")).contains("source modes = android.modes"))
        }
    }

    // A background activity start can be blocked: a started agent also leaves a line in the tile that opens the same intent.
    @Test fun aStartedAgentLeavesALineInTheTileThatOpensItInMercurio() {
        val program = compile("45-kiosk.owl")
        val t = kiosk(mercurio)
        t.fire("spun", Str("""{"n": 1, "r": {"ok": true, "pane": "t77", "session": "core", "title": "An article"}}"""))
        assertTrue("${t.lines(program.root)}", "started: open in Mercurio" in t.lines(program.root))
        val tapped = t.tap(program.root, "started: open in Mercurio")
        assertTrue("$tapped", opensInMercurio(tapped, "sg", "t77", names = "sg,sg.tail.ts.net"))
    }

    @Test fun withNoLinkToSgTheKioskSaysItOpenedMercurioItself() {
        val program = compile("45-kiosk.owl")
        val t = TileHost(program, Json.parse(kioskFeed))
        t.set("apps", Json.parse(mercurio))
        t.fire("spun", Str("""{"n": 1, "r": {"ok": true, "pane": "t77", "session": "core", "title": "An article"}}"""))
        assertTrue("${t.actions}", t.actions.any { it.startsWith("androidApp solutions.ormus.logos") })
        assertTrue("${t.lines(program.root)}", "opened Mercurio (no link to sg yet)" in t.lines(program.root))
        assertFalse("no \"started\" line without the intent", "started: open in Mercurio" in t.lines(program.root))
    }

    @Test fun aFailedStartLeavesNoStartedLine() {
        val program = compile("45-kiosk.owl")
        val t = kiosk(mercurio)
        t.fire("spun", Str("""{"n": 2, "r": {"ok": false, "err": "hc run failed"}}"""))
        assertTrue("${t.lines(program.root)}", t.lines(program.root).none { it.startsWith("started") || it.startsWith("opened Mercurio") })
    }
}
