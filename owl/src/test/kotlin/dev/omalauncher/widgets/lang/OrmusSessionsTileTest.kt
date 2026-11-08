package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sessions tile (10-sessions.owl) run on made-up answers of sg's sessions feed (ormus-feeds feeds/sessions.py):
 * the needs-you card on the tile and on its screen, what its lines say, which keys it offers and what a key sends.
 */
class OrmusSessionsTileTest {
    private val program = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/10-sessions.owl").readText())

    /** The feed source [f] (http with `status`: {ok, code, out}), the clock [now], the tile's size in dp, the phone's apps. */
    private inner class Tile(
        f: Value,
        private val height: Int? = 600,
        private val width: Int? = 360,
    ) : Host {
        private val slots = Array<Value>(program.globals.size) { Null }

        /** What the tile asked the launcher to do, as "name args". */
        val actions = ArrayList<String>()

        init {
            program.globals.forEachIndexed { i, g -> if (g is Global.State) slots[i] = g.init(Env.root(this)) }
            slots[slot("f")] = f
            slots[slot("now")] = Num(1_794_166_000.0)
            slots[slot("apps")] = Json.parse("""[{"package": "solutions.ormus.logos.debug", "label": "Mercurio"}]""")
        }

        /** Pins a card by hand, as his tap on "show 1 · 2 · 3" does. */
        fun pin(id: String) {
            slots[slot("pick")] = Str(id)
        }

        fun slot(name: String) = program.globals.indexOfFirst { it.name == name }.also { assertTrue("no global $name", it >= 0) }

        /** A top-level `let`, evaluated. */
        fun let(name: String): Value = read(slot(name))

        override val budget = Budget()

        override fun read(slot: Int): Value = (program.globals[slot] as? Global.Let)?.value?.invoke(Env.root(this)) ?: slots[slot]

        override fun write(
            slot: Int,
            value: Value,
        ) {
            slots[slot] = value
        }

        override fun theme(name: String) = null

        override fun time() = 0.0

        override fun tile(name: String): Value =
            when {
                name == "tileHeight" && height != null -> Num(height.toDouble())
                name == "tileWidth" && width != null -> Num(width.toDouble())
                else -> Null
            }

        override fun act(
            name: String,
            args: List<Value>,
        ) {
            actions += "$name ${args.joinToString()}"
        }

        override fun runInto(
            command: String,
            slot: Int,
        ) {
            actions += "run $command"
        }

        /** Runs every element's onTap in the env it renders in, in order; the actions it asked for. */
        fun taps(nodes: List<WNode>): List<String> {
            actions.clear()
            visit(nodes, Env.root(this)) { n, env -> n.handlers["onTap"]?.run?.invoke(env, emptyList()) }
            return actions.toList()
        }

        private fun visit(
            nodes: List<WNode>,
            env: Env,
            each: (WNode.Element, Env) -> Unit,
        ) {
            for (n in nodes) {
                when (n) {
                    is WNode.Element -> {
                        each(n, env)
                        visit(n.children, env, each)
                    }
                    is WNode.If -> visit(if (n.cond(env).truthy) n.then else n.orElse, env, each)
                    is WNode.For -> Builtins.iterable(n.list(env)).forEachIndexed { i, v -> visit(n.body, env.child(v, Num(i.toDouble())), each) }
                    is WNode.Lets -> visit(n.children, env.lets(n.values), each)
                    is WNode.Use -> visit(n.body, Env(this, Env.root(this), Array(n.args.size) { i -> { n.args[i](env) } }), each)
                    is WNode.Draw -> {}
                }
            }
        }

        /** Every Text, in order, as the renderer walks it. */
        fun lines(nodes: List<WNode>): List<String> = ArrayList<String>().also { walk(nodes, Env.root(this), it) }

        private fun walk(
            nodes: List<WNode>,
            env: Env,
            out: MutableList<String>,
        ) {
            for (n in nodes) {
                when (n) {
                    is WNode.Element -> {
                        if (n.kind == "Text") out += n.props["text"]?.invoke(env)?.let { (it as? Str)?.v ?: "$it" } ?: ""
                        walk(n.children, env, out)
                    }
                    is WNode.If -> walk(if (n.cond(env).truthy) n.then else n.orElse, env, out)
                    is WNode.For -> Builtins.iterable(n.list(env)).forEachIndexed { i, v -> walk(n.body, env.child(v, Num(i.toDouble())), out) }
                    is WNode.Lets -> walk(n.children, env.lets(n.values), out)
                    is WNode.Use -> walk(n.body, Env(this, Env.root(this), Array(n.args.size) { i -> { n.args[i](env) } }), out)
                    is WNode.Draw -> {}
                }
            }
        }
    }

    // Three blocked agents as the feed writes them (feeds/sessions.py, tests/test_asks.py): a permission prompt, a
    // pick list of four, an agent at its free prompt with his three replies.
    private fun agent(
        pane: String,
        seq: Int,
        ask: String,
        extra: String = "",
    ) = """{"kind": "claude", "status": "blocked", "title": "RAV-1", "tab": "4 · RAV-1", "machine": "sg", "session": "raven", "pane": "$pane", "seq": $seq, "term": "term_t"$extra, "ask": $ask}"""

    private fun approval(
        about: String = "",
        summary: String = "Bash command: rm -rf build/",
    ) = agent(
        "w4:p1", 101,
        """{"question": "Do you want to proceed?", "why": "This command removes files", "what": "rm -rf build/", "lines": 2, "cut": false,
           "about": "$about", "tool": "Bash command", "approval": true, "options": [{"key": "1", "label": "Yes"}, {"key": "2", "label": "No"}],
           "run": "rm -rf build/\nmake all", "more": false, "checked": true, "id": "484b6b02717f",
           "summary": "$summary", "choices": [{"n": 1, "label": "Yes", "key": "1"}, {"n": 2, "label": "No", "key": "2"}]}""",
    )

    private val pick = agent(
        "w4:p2", 102,
        """{"question": "Which look do you want?", "why": "", "what": "", "lines": 0, "cut": false, "about": "", "tool": "", "approval": false,
           "options": [{"key": "1", "label": "Moonlight"}, {"key": "2", "label": "Quicksilver"}, {"key": "3", "label": "Aurum"}, {"key": "4", "label": "Something else"}],
           "run": "", "more": false, "checked": false, "id": "30be825843dd", "summary": "Which look do you want?",
           "choices": [{"n": 1, "label": "Moonlight", "key": "1"}, {"n": 2, "label": "Quicksilver", "key": "2"}, {"n": 3, "label": "Aurum", "key": "3"}]}""",
    )

    private fun prompt(needs: String) = agent(
        "w4:p3", 103,
        """{"question": "", "why": "", "what": "", "lines": 0, "cut": false, "about": "", "tool": "", "approval": false, "options": [],
           "run": "", "more": false, "checked": false, "prompt": true, "id": "a1b2c3d4e5f6", "summary": "$needs",
           "choices": [{"n": 1, "label": "go on"}, {"n": 2, "label": "no, stop"}, {"n": 3, "label": "later"}]}""",
        extra = """, "needs": "$needs"""",
    )

    private fun feed(vararg agents: String): Value {
        val n = agents.size
        return Json.parse(
            """{"ok": true, "code": 200, "out": {"generated": 1794166000, "machines": {"sg": "ok"}, "errors": {}, "needs": $n,
            "agents": {"blocked": $n, "working": 0, "done": 0, "idle": 0}, "waiting": 0, "unsorted": [],
            "links": {"sg": "ssh://ormus@sg:22#sg"}, "hosts": {"sg": ["sg", "sg.example"]}, "game": {"today": 0, "into": 0, "need": 3, "level": 1, "streak": 0}, "moves": [],
            "boards": [{"name": "raven", "agents": {"blocked": $n, "working": 0, "done": 0, "idle": 0}, "waiting": 0,
              "projects": [{"name": "CUSTOMER PORTALS", "agents": {"blocked": $n, "working": 0, "done": 0, "idle": 0}, "chats": [], "top": [${agents.joinToString(",")}]}]}]}}""",
        )
    }

    private fun Tile.tile() = lines(program.root)

    private fun Tile.screen() = lines(program.screen!!)

    private fun keys(lines: List<String>) = lines.filter { Regex("^[1-9] (?!need you)").containsMatchIn(it) }

    @Test fun theCardIsOneLineOfTheBlockageWhereItIsAndThePinKeyNamesItsKeys() {
        val t = Tile(feed(approval()))
        val lines = t.tile()
        assertTrue("the summary is the label: $lines", "Bash command: rm -rf build/" in lines)
        assertTrue("the agent and its machine, not the tool again: $lines", "claude on sg" in lines)
        assertFalse("the tool is in the summary already: $lines", "Bash command on sg" in lines)
        assertTrue("two keys, so the pin key names two: $lines", "show 1 · 2" in lines)
        assertFalse("no key shows before the card is pinned: $lines", "1 Yes" in lines)
        assertFalse("the tile's card shows no command: $lines", lines.any { it.startsWith("rm -rf build/") })
        assertEquals("one line of label, two keys side by side", 215.0, (t.let("cardHeight") as Num).v, 0.0)
    }

    @Test fun anApprovalWhoseSummaryIsTheAgentsOwnLineShowsTheCommandsFirstLineOnTheTile() {
        val t = Tile(feed(approval(about = "clean the build", summary = "Bash command: clean the build")))
        val lines = t.tile()
        assertTrue("the agent's line is the label: $lines", "Bash command: clean the build" in lines)
        assertTrue("the first line of the command, marked as not all of it: $lines", "rm -rf build/ …" in lines)
        assertEquals("one line of label, the command's line, two keys side by side", 231.0, (t.let("cardHeight") as Num).v, 0.0)
        val screen = t.screen()
        assertTrue("the screen shows the whole command: $screen", "rm -rf build/\nmake all" in screen)
        assertFalse("and not its first line twice: $screen", "rm -rf build/ …" in screen)
    }

    @Test fun aPinnedDialogCardSendsTheKeyOfTheChoiceTapped() {
        val t = Tile(feed(approval()))
        t.pin("sg:raven:w4:p1")
        val lines = t.tile()
        assertEquals(listOf("1 Yes", "2 No"), keys(lines))
        val runs = t.taps(program.root).filter { it.startsWith("run ") }
        assertEquals("the first key's tap runs the answer tool; the second finds it busy", 1, runs.size)
        val sent = runs[0]
        assertTrue(sent, sent.contains("projects/ormus-feeds/bin/answer -"))
        for (part in listOf("\"machine\":\"sg\"", "\"session\":\"raven\"", "\"pane\":\"w4:p1\"", "\"seq\":101", "\"id\":\"484b6b02717f\"", "\"key\":\"1\"", "\"term\":\"term_t\"")) {
            assertTrue("$part in $sent", sent.contains(part))
        }
        assertFalse("a dialog's answer carries no reply text: $sent", sent.contains("\"label\""))
        assertTrue("the card's own tap opens the agent in Mercurio", t.taps(program.root).any { it.startsWith("androidIntent") && it.contains("HERD_SESSION") })
    }

    @Test fun aPinnedPromptCardSendsTheNumberAndTheLabelOfTheReplyTapped() {
        val t = Tile(feed(prompt("the merge word")))
        t.pin("sg:raven:w4:p3")
        val lines = t.tile()
        assertTrue("where it is, at its prompt: $lines", "claude on sg · at its prompt" in lines)
        assertEquals(listOf("1 go on", "2 no, stop", "3 later"), keys(lines))
        val sent = t.taps(program.root).first { it.startsWith("run ") }
        for (part in listOf("\"n\":1", "\"label\":\"go on\"", "\"id\":\"a1b2c3d4e5f6\"")) assertTrue("$part in $sent", sent.contains(part))
        assertFalse("a reply sends no key: $sent", sent.contains("\"key\""))
    }

    @Test fun thePickListShowsThreeKeysOnTheTileAndEveryPickOnTheScreen() {
        val t = Tile(feed(pick))
        t.pin("sg:raven:w4:p2")
        assertEquals(listOf("1 Moonlight", "2 Quicksilver", "3 Aurum"), keys(t.tile()))
        assertEquals("the stack of three", 314.0, (t.let("cardHeight") as Num).v, 0.0)
        assertEquals(listOf("1 Moonlight", "2 Quicksilver", "3 Aurum", "4 Something else"), keys(t.screen()))
        val sent = t.taps(program.screen!!).first { it.startsWith("run ") }
        assertTrue("the screen's key sends the dialog's own key: $sent", sent.contains("\"key\":\"1\""))
        t.actions.clear()
        val unpinned = Tile(feed(pick))
        assertTrue("four keys on the screen, four numbers on its pin key", "show 1 · 2 · 3 · 4" in unpinned.screen())
        assertTrue("three on the tile", "show 1 · 2 · 3" in unpinned.tile())
    }

    @Test fun aLabelOfTwoLinesTakesItsRoomFromTheRowsUnderTheCard() {
        val short = Tile(feed(prompt("the merge word")))
        val long = Tile(feed(prompt("the merge word for the Corvin site PRs")))
        assertEquals(314.0, (short.let("cardHeight") as Num).v, 0.0)
        assertEquals("a second line of label at 17 sp is 22 dp", 336.0, (long.let("cardHeight") as Num).v, 0.0)
    }

    @Test fun aCardWithNoAskOpensTheAgent() {
        val t = Tile(feed("""{"kind": "claude", "status": "blocked", "title": "RAV-1", "tab": "4 · RAV-1", "machine": "sg", "session": "raven", "pane": "w4:p9", "seq": 9, "term": "t", "needs": "his eyes on the diff"}"""))
        val lines = t.tile()
        assertTrue("$lines", "his eyes on the diff" in lines)
        assertTrue("$lines", "open in Mercurio" in lines)
        assertEquals(emptyList<String>(), keys(lines))
    }
}
