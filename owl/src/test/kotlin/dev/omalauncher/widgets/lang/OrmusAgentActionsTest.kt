package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sessions tile's steering of an agent in herdr-core (10-sessions.owl, ormus-feeds bin/agent): on the screen's list a
 * core agent has a screen key, a stop key while it works or is blocked, a field to say something while it is idle or
 * done; what each sends, which keys show in which state, what the answers say.
 */
class OrmusAgentActionsTest {
    private val program = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/10-sessions.owl").readText())

    private fun agent(
        status: String,
        session: String = "core",
        pane: String = "w1:p2",
    ) = """{"kind": "claude", "status": "$status", "title": "RAV-9 portal", "tab": "2 · RAV-9", "machine": "sg", "session": "$session", "pane": "$pane", "seq": 4, "term": "term_t"}"""

    private fun feed(vararg agents: String): Value {
        val counts = { s: String -> agents.count { it.contains("\"status\": \"$s\"") } }
        val agentCounts = """{"blocked": ${counts("blocked")}, "working": ${counts("working")}, "done": ${counts("done")}, "idle": ${counts("idle")}}"""
        return Json.parse(
            """{"ok": true, "code": 200, "out": {"generated": 1794166000, "machines": {"sg": "ok"}, "errors": {}, "needs": ${counts("blocked")},
            "agents": $agentCounts, "waiting": 0, "unsorted": [], "links": {}, "hosts": {}, "game": {"today": 0, "into": 0, "need": 3, "level": 1, "streak": 0}, "moves": [],
            "boards": [{"name": "raven", "agents": $agentCounts, "waiting": 0,
              "projects": [{"name": "PORTAL", "agents": $agentCounts, "chats": [], "top": [${agents.joinToString(",")}]}]}]}}""",
        )
    }

    private fun tile(vararg agents: String) = TileHost(program, feed(*agents))

    /** The n the last request carried, which sg's answer must carry back. */
    private fun TileHost.n(): Int = ((Json.parse(text("go")) as Obj).fields["n"] as Num).v.toInt()

    /** sg's answer to the last request. */
    private fun TileHost.answer(r: String) = fire("acted", Str("""{"n": ${n()}, "r": $r, "x": 0}"""))

    private fun TileHost.screenLines() = lines(program.screen!!)

    private fun body(host: TileHost) = (Json.parse(host.text("go")) as Obj).fields["body"] as Obj

    private fun get(
        o: Obj,
        key: String,
    ) = (o.fields[key] as Str).v

    @Test fun onlyACoreAgentHasActionsAndTheKeysFollowItsStatus() {
        val working = tile(agent("working"))
        assertTrue(working.hasKey(program.screen!!, "screen") && working.hasKey(program.screen!!, "stop"))
        assertEquals("a working agent takes no words: its prompt is busy", 0, working.inputs(program.screen!!))
        val blocked = tile(agent("blocked"))
        assertTrue(blocked.hasKey(program.screen!!, "stop"))
        assertEquals(0, blocked.inputs(program.screen!!))
        for (status in listOf("idle", "done")) {
            val t = tile(agent(status))
            assertTrue("$status: screen", t.hasKey(program.screen!!, "screen"))
            assertFalse("$status: nothing to stop", t.hasKey(program.screen!!, "stop"))
            assertEquals("$status: one field to say something", 1, t.inputs(program.screen!!))
        }
        val other = tile(agent("working", session = "raven"))
        assertFalse(other.hasKey(program.screen!!, "screen"))
        assertEquals(0, other.inputs(program.screen!!))
        assertFalse("the tile itself shows no steering keys", working.hasKey(program.root, "screen"))
    }

    @Test fun theScreenKeyAsksSgForTheScreenOfThatAgentAlone() {
        val t = tile(agent("working"))
        t.tap(program.screen!!, "screen")
        val b = body(t)
        assertEquals(listOf("screen", "sg", "core", "w1:p2", "term_t"), listOf("do", "machine", "session", "pane", "term").map { get(b, it) })
        assertFalse("a screen asks with no text", b.fields.containsKey("text"))
        assertEquals("the screen's view opens at once, on the agent", "sg:core:w1:p2", t.text("peek"))
        t.fire("go", t.let("go"))
        val run = t.actions.single()
        assertTrue(run, run.startsWith("run r=$(printf %s "))
        assertTrue(run, run.contains("ssh -o BatchMode=yes -o ConnectTimeout=8") && run.contains(" sg projects/ormus-feeds/bin/agent -); x=$?"))
        assertTrue("the JSON goes on stdin, quoted for the shell: $run", run.contains("| ssh") && run.contains("\"do\":\"screen\""))
    }

    @Test fun theScreenViewShowsTheTextFromItsEndWithTheAgentsTitleAndARefreshKey() {
        val t = tile(agent("working"))
        t.tap(program.screen!!, "screen")
        t.answer("""{"ok": true, "outcome": "screen", "status": "working", "text": "first line\nmiddle\nlast line"}""")
        assertEquals("RAV-9 portal", t.text("peekHead"))
        val lines = t.screenLines()
        assertEquals("the screen's text from its last line up (the list is drawn from the bottom): $lines", listOf("last line", "middle", "first line"), lines.filter { it.endsWith("line") || it == "middle" })
        assertTrue("$lines", "back" in lines && "refresh" in lines && "working" in lines)
        assertFalse("the list of agents gives way to the view: $lines", "PORTAL" in lines)
        t.tap(program.screen!!, "refresh")
        assertEquals("screen", get(body(t), "do"))
        t.tap(program.screen!!, "back")
        assertEquals("", t.text("peek"))
        assertTrue("PORTAL" in t.screenLines())
    }

    @Test fun aScreenThatChangedOrFailedIsOneLineAndTheViewStays() {
        for ((outcome, line, red) in listOf(Triple("changed", "the agent moved on: look again", false), Triple("failed", "it did not go through", true), Triple("refused", "sg refused it", true))) {
            val t = tile(agent("working"))
            t.tap(program.screen!!, "screen")
            t.answer("""{"ok": false, "outcome": "$outcome"}""")
            assertTrue("$outcome: ${t.screenLines()}", line in t.screenLines())
            assertEquals("$outcome is red only when it is an error", red, t.let("actRed").truthy)
        }
        val silent = tile(agent("working"))
        silent.tap(program.screen!!, "screen")
        silent.fire("acted", Str("""{"n": ${silent.n()}, "r": null, "x": 255}"""))
        assertTrue("sg printed nothing: ${silent.screenLines()}", "no answer: look again" in silent.screenLines())
    }

    @Test fun stopNeedsASecondTapAfterAMomentAndASecondTapTooCloseIsIgnored() {
        val t = tile(agent("working"))
        t.clock = 10.0
        t.tap(program.screen!!, "stop")
        assertEquals("the first tap sends nothing", "", t.text("go"))
        assertEquals("keyed by the machine, the pane and the terminal", "sg:w1:p2:term_t", t.text("stopAsk"))
        assertTrue("the key asks again: ${t.screenLines()}", t.hasKey(program.screen!!, "tap again to stop"))
        t.clock = 10.3
        t.tap(program.screen!!, "tap again to stop")
        assertEquals("a second tap 0.3 s after the first is the same tap landing twice", "", t.text("go"))
        assertTrue("and it is still asking", t.hasKey(program.screen!!, "tap again to stop"))
        t.clock = 12.0
        t.tap(program.screen!!, "tap again to stop")
        val b = body(t)
        assertEquals(listOf("stop", "sg", "core", "w1:p2", "term_t"), listOf("do", "machine", "session", "pane", "term").map { get(b, it) })
        assertEquals("a stop does not open the view", "", t.text("peek"))
        assertEquals("the confirm is spent", "", t.text("stopAsk"))
        t.answer("""{"ok": true, "outcome": "stopped"}""")
        assertEquals("stopped", t.text("actLine"))
        assertFalse("not red", t.let("actRed").truthy)
    }

    @Test fun theStopConfirmNeedsNoClockAndEndsOnTheNextRefreshAnyOtherKeyOrBack() {
        assertTrue("no clock that redraws the tile every few seconds", program.globals.none { it.name == "tick" })
        val t = tile(agent("working"))
        t.clock = 5.0
        t.tap(program.screen!!, "stop")
        assertTrue(t.hasKey(program.screen!!, "tap again to stop"))
        t.fire("f", feed(agent("working")))
        assertTrue("the next read of the feed ends it", t.hasKey(program.screen!!, "stop"))
        t.tap(program.screen!!, "stop")
        t.tap(program.screen!!, "screen")
        assertEquals("another key ends it", "", t.text("stopAsk"))
        t.answer("""{"ok": true, "outcome": "screen", "status": "working", "text": "x"}""")
        t.tap(program.screen!!, "back")
        assertEquals("", t.text("stopAsk"))
    }

    @Test fun sayGoesWithHisTextAndNeverComesBack() {
        val t = tile(agent("idle"))
        assertEquals(1, t.submit(program.screen!!, "say something", "run the tests again"))
        val b = body(t)
        assertEquals(listOf("say", "sg", "core", "w1:p2", "term_t", "run the tests again"), listOf("do", "machine", "session", "pane", "term", "text").map { get(b, it) })
        assertEquals("a say does not open the view", "", t.text("peek"))
        for ((outcome, line, red) in listOf(
            Triple("sent", "sent", false),
            Triple("held", "waits for a draft at the desk", false),
            Triple("not waiting", "not waiting for a word", false),
            Triple("changed", "the agent moved on: look again", false),
            Triple("refused", "sg refused it", true),
            Triple("failed", "it did not go through", true),
        )) {
            t.set("act", Str("sg:core:w1:p2"))
            t.set("actDo", Str("say"))
            t.set("actN", Num(t.n().toDouble()))
            t.answer("""{"ok": ${outcome == "sent"}, "outcome": "$outcome"}""")
            assertEquals(outcome, line, t.text("actLine"))
            assertEquals("$outcome red", red, t.let("actRed").truthy)
            assertTrue("one line under the agent: ${t.screenLines()}", line in t.screenLines())
            assertFalse("his text is never echoed: ${t.screenLines()}", t.screenLines().any { it.contains("run the tests again") })
        }
    }

    @Test fun anEmptySayOrASecondRequestWhileOneIsOutSendsNothing() {
        val t = tile(agent("idle"))
        t.submit(program.screen!!, "say something", "   ")
        assertEquals("", t.text("go"))
        t.submit(program.screen!!, "say something", "first")
        val first = t.text("go")
        t.submit(program.screen!!, "say something", "second")
        assertEquals("one request at a time: the second waits for the first's answer", first, t.text("go"))
        t.tap(program.screen!!, "screen")
        assertEquals(first, t.text("go"))
    }

    @Test fun aRequestIsFreedOnlyAfterSgCouldHaveHeldItAndSaysToLookOnTheDeskNotToRetype() {
        // sg holds a say up to 70 s and ssh takes up to 8 to connect; `now` ticks a minute at a time, so a tap 59 s after a
        // tick has an `actAt` up to a minute older than the tap. Freed too soon, a second tap could type it twice.
        val t = tile(agent("idle"))
        val tick = 1_794_166_000.0
        t.submit(program.screen!!, "say something", "hello")
        for (later in listOf(60, 120, 179)) {
            t.fire("now", Num(tick + later))
            assertEquals("$later s of ticks after the tap: still out", "sg:core:w1:p2", t.text("act"))
        }
        t.fire("now", Num(tick + 180))
        assertEquals("", t.text("act"))
        assertEquals("no answer: look on the desk before typing again", t.text("actLine"))
        assertTrue(t.let("actRed").truthy)
    }

    @Test fun anAnswerForAnotherRequestIsDroppedNotShownUnderTheAgentThatAsksNow() {
        val t = tile(agent("idle", pane = "w1:p2"), agent("idle", pane = "w1:p3"))
        t.submit(program.screen!!, "say something", "for the first")
        val late = t.n()
        t.fire("now", Num(1_794_166_000.0 + 180))
        assertEquals("a request that got no answer is freed", "", t.text("act"))
        t.set("actLine", Str(""))
        t.tap(program.screen!!, "screen")
        val screenN = t.n()
        assertTrue(late != screenN)
        // The late answer of the first, a say, arrives while the second's screen is awaited.
        t.fire("acted", Str("""{"n": $late, "r": {"ok": true, "outcome": "sent"}, "x": 0}"""))
        assertEquals("the second request is still waiting", true, t.text("act") != "")
        assertEquals("nothing of the late answer shows", "", t.text("actLine"))
        assertEquals("and no screen came of it", "", t.text("peekText"))
        t.fire("acted", Str("""{"n": $screenN, "r": {"ok": true, "outcome": "screen", "status": "idle", "text": "its screen"}, "x": 0}"""))
        assertEquals("its own answer is taken", "its screen", t.text("peekText"))
        assertEquals("", t.text("act"))
        // And an answer for nothing in flight is dropped.
        t.fire("acted", Str("""{"n": $screenN, "r": {"ok": true, "outcome": "stopped"}, "x": 0}"""))
        assertEquals("", t.text("actLine"))
    }

    @Test fun hisTextStaysUnlessItWentOrWasHeldAndAnUnfitOneIsRefusedHere() {
        val t = tile(agent("idle"))
        t.change(program.screen!!, "say something", "try again")
        assertEquals("try again", t.text("sayText"))
        t.submit(program.screen!!, "say something", "try again")
        for ((outcome, keeps) in listOf("refused" to true, "failed" to true, "changed" to true, "not waiting" to true, "held" to false, "sent" to false)) {
            t.set("sayText", Str("try again"))
            t.set("act", Str("sg:core:w1:p2"))
            t.set("actDo", Str("say"))
            t.set("actN", Num(t.n().toDouble()))
            t.answer("""{"ok": false, "outcome": "$outcome"}""")
            assertEquals("$outcome: his text ${if (keeps) "stays" else "goes"}", if (keeps) "try again" else "", t.text("sayText"))
        }
        // Words bin/agent would refuse never leave the phone.
        for (bad in listOf("/help", "!ls", "#note", "@file", "?", "ends with a slash\\", "x".repeat(601))) {
            val u = tile(agent("idle"))
            u.change(program.screen!!, "say something", bad)
            u.submit(program.screen!!, "say something", bad)
            assertEquals("not sent: ${bad.take(12)}", "", u.text("go"))
            assertEquals("his text stays to be mended", bad, u.text("sayText"))
            assertTrue("a line says why: ${u.screenLines()}", u.text("actLine").startsWith("not sent"))
            assertEquals("", u.text("act"))
        }
        val ok = tile(agent("idle"))
        ok.submit(program.screen!!, "say something", "x".repeat(600))
        assertTrue("600 characters is the limit and fits", ok.text("go") != "")
    }

    @Test fun theBoardTilesCarryTheSameActions() {
        val raven = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/11-board-raven.owl").readText())
        val t = TileHost(raven, feed(agent("working")))
        assertTrue(t.hasKey(raven.screen!!, "screen") && t.hasKey(raven.screen!!, "stop"))
    }

    /** The command one tap builds, run by a real `sh -c` with a stand-in ssh that keeps its stdin: it must be the intended JSON and nothing may run. */
    @Test fun aHostileTextTravelsAsDataThroughARealShell() {
        val hostile = "it's \"q\" \$(touch PWNED1) `touch PWNED2` \\ back\\n\nnext é ✓"
        val dir = java.nio.file.Files.createTempDirectory("ormus-say").toFile()
        try {
            val ssh = java.io.File(dir, "ssh")
            ssh.writeText("#!/bin/sh\ncat > '${dir.path}/stdin.json'\nprintf '{\"ok\":true,\"outcome\":\"sent\"}'\n")
            ssh.setExecutable(true)
            val t = tile(agent("idle"))
            t.submit(program.screen!!, "say something", hostile)
            t.fire("go", t.let("go"))
            val command = t.actions.single().removePrefix("run ")
            val pb = ProcessBuilder("sh", "-c", command).directory(dir).redirectErrorStream(false)
            pb.environment()["PATH"] = dir.path + ":" + System.getenv("PATH")
            val p = pb.start()
            val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
            assertEquals(0, p.waitFor())
            val stdin = java.io.File(dir, "stdin.json").readBytes().toString(Charsets.UTF_8)
            val expected = Json.write(Json.parse(text(t)), 100_000)
            assertEquals("the JSON sg gets is the one the tap built, byte for byte", expected, stdin)
            val sent = Json.parse(stdin) as Obj
            assertEquals("and its text is his, whole", hostile, (sent.fields["text"] as Str).v)
            assertTrue("nothing in his text ran: ${dir.list()!!.toList()}", dir.list()!!.none { it.startsWith("PWNED") })
            val wrapped = Json.parse(out) as Obj
            assertEquals("what comes back is one JSON object with the request's n, whatever ssh printed", t.n().toDouble(), (wrapped.fields["n"] as Num).v, 0.0)
            assertTrue(out, out.contains("\"outcome\":\"sent\""))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun text(t: TileHost) = Json.write((Json.parse(t.text("go")) as Obj).fields["body"]!!, 100_000)!!

    @Test fun anSshThatPrintsNothingIsStillOneJsonObjectWithItsN() {
        val dir = java.nio.file.Files.createTempDirectory("ormus-say").toFile()
        try {
            val ssh = java.io.File(dir, "ssh")
            ssh.writeText("#!/bin/sh\ncat >/dev/null\nexit 255\n")
            ssh.setExecutable(true)
            val t = tile(agent("idle"))
            t.submit(program.screen!!, "say something", "hello")
            t.fire("go", t.let("go"))
            val pb = ProcessBuilder("sh", "-c", t.actions.single().removePrefix("run "))
            pb.environment()["PATH"] = dir.path + ":" + System.getenv("PATH")
            val out = pb.start().let { it.inputStream.readBytes().toString(Charsets.UTF_8).also { _ -> it.waitFor() } }
            val wrapped = Json.parse(out) as Obj
            assertEquals(t.n().toDouble(), (wrapped.fields["n"] as Num).v, 0.0)
            assertEquals(Null, wrapped.fields["r"])
            t.fire("acted", Str(out))
            assertEquals("no answer: look again", t.text("actLine"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
