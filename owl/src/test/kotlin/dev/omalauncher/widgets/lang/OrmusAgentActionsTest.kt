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
        val run = t.run { fire("go", it.let("go")); actions.single() }
        assertTrue(run, run.startsWith("run printf '{\"n\":%s,\"r\":' "))
        assertTrue(run, run.contains("ssh -o BatchMode=yes -o ConnectTimeout=8") && run.contains(" sg projects/ormus-feeds/bin/agent -; printf '}'"))
        assertTrue("the JSON goes on stdin, quoted for the shell: $run", run.contains("| ssh") && run.contains("\"do\":\"screen\""))
    }

    private fun <R> TileHost.run(block: TileHost.(TileHost) -> R) = block(this)

    @Test fun theScreenViewShowsTheTextFromItsEndWithTheAgentsTitleAndARefreshKey() {
        val t = tile(agent("working"))
        t.tap(program.screen!!, "screen")
        t.fire("acted", Str("""{"n": 5, "r": {"ok": true, "outcome": "screen", "status": "working", "text": "first line\nmiddle\nlast line"}}"""))
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
            t.fire("acted", Str("""{"n": 6, "r": {"ok": false, "outcome": "$outcome"}}"""))
            assertTrue("$outcome: ${t.screenLines()}", line in t.screenLines())
            assertEquals("$outcome is red only when it is an error", red, t.let("actRed").truthy)
        }
        val silent = tile(agent("working"))
        silent.tap(program.screen!!, "screen")
        silent.fire("acted", Str("""{"n": 7, "r": null}"""))
        assertTrue("sg printed nothing: ${silent.screenLines()}", "no answer: look again" in silent.screenLines())
    }

    @Test fun stopNeedsASecondTapWithinAFewSecondsAndThenSendsOnlyTheStop() {
        val t = tile(agent("working"))
        t.tap(program.screen!!, "stop")
        assertEquals("the first tap sends nothing", "", t.text("go"))
        assertTrue("the key asks again: ${t.screenLines()}", t.hasKey(program.screen!!, "tap again to stop"))
        t.fire("tick", Num(1_794_166_000.0 + 3))
        assertTrue("three seconds later it still asks", t.hasKey(program.screen!!, "tap again to stop"))
        t.fire("tick", Num(1_794_166_000.0 + 8))
        assertTrue("after six it is a plain stop again", t.hasKey(program.screen!!, "stop"))
        t.tap(program.screen!!, "stop")
        t.tap(program.screen!!, "tap again to stop")
        val b = body(t)
        assertEquals(listOf("stop", "sg", "core", "w1:p2", "term_t"), listOf("do", "machine", "session", "pane", "term").map { get(b, it) })
        assertEquals("a stop does not open the view", "", t.text("peek"))
        t.fire("acted", Str("""{"n": 8, "r": {"ok": true, "outcome": "stopped"}}"""))
        assertEquals("stopped", t.text("actLine"))
        assertTrue("stopped" in t.screenLines())
        assertFalse("not red", t.let("actRed").truthy)
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
            t.fire("acted", Str("""{"n": ${outcome.length}, "r": {"ok": ${outcome == "sent"}, "outcome": "$outcome"}}"""))
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

    @Test fun aRequestWithNoAnswerFreesTheAgentAfterAWhile() {
        val t = tile(agent("idle"))
        t.submit(program.screen!!, "say something", "hello")
        t.fire("now", Num(1_794_166_000.0 + 60))
        assertEquals("a say may wait a minute for a draft", "sg:core:w1:p2", t.text("act"))
        t.fire("now", Num(1_794_166_000.0 + 120))
        assertEquals("", t.text("act"))
        assertEquals("no answer: look again", t.text("actLine"))
    }

    @Test fun theBoardTilesCarryTheSameActions() {
        val raven = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/11-board-raven.owl").readText())
        val t = TileHost(raven, feed(agent("working")))
        assertTrue(t.hasKey(raven.screen!!, "screen") && t.hasKey(raven.screen!!, "stop"))
    }
}
