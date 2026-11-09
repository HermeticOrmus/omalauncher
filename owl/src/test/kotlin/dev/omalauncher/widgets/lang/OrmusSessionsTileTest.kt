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
        private val program: Program = this@OrmusSessionsTileTest.program,
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
        assertEquals("one line of label, two keys side by side", 126.0, (t.let("cardHeight") as Num).v, 0.0)
    }

    @Test fun anApprovalWhoseSummaryIsTheAgentsOwnLineShowsTheCommandsFirstLineOnTheTile() {
        val t = Tile(feed(approval(about = "clean the build", summary = "Bash command: clean the build")))
        val lines = t.tile()
        assertTrue("the agent's line is the label: $lines", "Bash command: clean the build" in lines)
        assertTrue("the first line of the command, marked as not all of it: $lines", "rm -rf build/ …" in lines)
        assertEquals("one line of label, the command's line, two keys side by side", 142.0, (t.let("cardHeight") as Num).v, 0.0)
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
        assertEquals("the stack of three", 220.0, (t.let("cardHeight") as Num).v, 0.0)
        assertEquals(listOf("1 Moonlight", "2 Quicksilver", "3 Aurum", "4 Something else"), keys(t.screen()))
        val sent = t.taps(program.screen!!).first { it.startsWith("run ") }
        assertTrue("the screen's key sends the dialog's own key: $sent", sent.contains("\"key\":\"1\""))
        t.actions.clear()
        val unpinned = Tile(feed(pick))
        assertTrue("four keys on the screen, four numbers on its pin key", "show 1 · 2 · 3 · 4" in unpinned.screen())
        assertTrue("three on the tile", "show 1 · 2 · 3" in unpinned.tile())
    }

    @Test fun aLabelOfTwoLinesTakesItsRoomFromTheRowsUnderTheCardAndTheKeysStackOnlyOncePinned() {
        val short = Tile(feed(prompt("the merge word")))
        val long = Tile(feed(prompt("the merge word for the Corvin site PRs")))
        assertEquals("one row of keys before the pin, whatever the labels", 126.0, (short.let("cardHeight") as Num).v, 0.0)
        assertEquals("a second line of label at 17 sp is 22 dp", 148.0, (long.let("cardHeight") as Num).v, 0.0)
        short.pin("sg:raven:w4:p3")
        long.pin("sg:raven:w4:p3")
        assertEquals("pinned, three labels too long for one row stack: 79 + 3 × 47", 220.0, (short.let("cardHeight") as Num).v, 0.0)
        assertEquals(242.0, (long.let("cardHeight") as Num).v, 0.0)
    }

    /**
     * Measured on the emulator (420 dpi, font scale 1) on 2026-11-08: a 6x6 tile is 384 dp tall and 377 wide inside its
     * border (`tileHeight`, `tileWidth`), its title and headline 81 dp, the card with a two-line label and its pin key
     * 148 dp, the same card pinned with its three keys stacked 242 dp; a row of an agent at work 24 dp. The agents at work
     * have room under the card either way; the build before counted the unpinned card as a stack and hid them.
     */
    @Test fun theAgentsAtWorkShowUnderACardOnASixBySixTile() {
        val blocked = prompt("Approve the deploy of the portal fix?")
        val working = """{"kind": "claude", "status": "working", "title": "RAV-4031", "tab": "5 · RAV-4031", "machine": "sg", "session": "raven", "pane": "w4:p2", "seq": 102, "term": "term_t", "ask": null}"""
        val one =
            Json.parse(
                """{"ok": true, "code": 200, "out": {"generated": 1794166000, "machines": {"sg": "ok"}, "errors": {}, "needs": 1,
                "agents": {"blocked": 1, "working": 1, "done": 0, "idle": 0}, "waiting": 0, "unsorted": [],
                "links": {}, "hosts": {}, "game": {"today": 0, "into": 0, "need": 3, "level": 1, "streak": 0}, "moves": [],
                "boards": [{"name": "raven", "agents": {"blocked": 1, "working": 1, "done": 0, "idle": 0}, "waiting": 0,
                  "projects": [{"name": "CUSTOMER PORTALS", "agents": {"blocked": 1, "working": 1, "done": 0, "idle": 0}, "chats": [], "top": [$blocked, $working]}]}]}}""",
            )
        val t = Tile(one, height = 384, width = 377)
        assertEquals("two lines of label, the pin key", 148.0, (t.let("cardHeight") as Num).v, 0.0)
        val lines = t.tile()
        assertTrue("$lines", "1 need you" in lines && "1 working · 0 done" in lines)
        assertTrue("the agent at work has its row: $lines", "RAV-4031" in lines)
        assertFalse("and is not folded into a count: $lines", lines.any { it.startsWith("+1 more") })
        val pinned = Tile(one, height = 384, width = 377)
        pinned.pin("sg:raven:w4:p3")
        assertEquals("the stack of three under two lines", 242.0, (pinned.let("cardHeight") as Num).v, 0.0)
        assertTrue("the row still fits under the stack: ${pinned.tile()}", "RAV-4031" in pinned.tile())
    }

    /** A board copy (11-board-raven.owl, this tile with `only`): its board's agents alone, its own count, its name. */
    @Test fun aBoardCopyShowsItsBoardsAgentsAloneAndSaysWhenTheFeedHasNoSuchBoard() {
        val raven = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/11-board-raven.owl").readText())
        fun board(name: String, project: String, agent: String) =
            """{"name": "$name", "agents": {"blocked": 1, "working": 0, "done": 0, "idle": 0}, "waiting": 0,
               "projects": [{"name": "$project", "agents": {"blocked": 1, "working": 0, "done": 0, "idle": 0}, "chats": [], "top": [$agent]}]}"""
        fun feedOf(vararg boards: String) =
            Json.parse(
                """{"ok": true, "code": 200, "out": {"generated": 1794166000, "machines": {"sg": "ok"}, "errors": {}, "needs": ${boards.size},
                "agents": {"blocked": ${boards.size}, "working": 0, "done": 0, "idle": 0}, "waiting": 0, "unsorted": [],
                "links": {}, "hosts": {}, "game": {"today": 0, "into": 0, "need": 3, "level": 1, "streak": 0}, "moves": [],
                "boards": [${boards.joinToString(",")}]}}""",
            )
        val two = feedOf(board("raven", "CUSTOMER PORTALS", prompt("the merge word")), board("clients", "OA", approval()))
        val all = Tile(two)
        assertTrue("the tile of every workboard counts both: ${all.tile()}", "2 need you" in all.tile() && "sessions" in all.tile())
        val copy = Tile(two, program = raven)
        assertEquals("raven", (copy.let("only") as Str).v)
        val tile = copy.lines(raven.root)
        val screen = copy.lines(raven.screen!!)
        assertTrue("its board's agent alone, counted alone: $tile", "1 need you" in tile && "the merge word" in tile && "raven" in tile)
        assertFalse("not the other board's: $tile $screen", "Bash command: rm -rf build/" in tile || "Bash command: rm -rf build/" in screen || "OA" in screen)
        assertTrue("the screen lists the board's project: $screen", "CUSTOMER PORTALS" in screen)
        val gone = Tile(feedOf(board("clients", "OA", approval())), program = raven)
        assertTrue("a feed without the board says so on the tile: ${gone.lines(raven.root)}", "no raven board in the feed" in gone.lines(raven.root))
        assertTrue("and on the screen: ${gone.lines(raven.screen!!)}", "no raven board in the feed" in gone.lines(raven.screen!!))
    }

    @Test fun aCardWithNoAskOpensTheAgent() {
        val t = Tile(feed("""{"kind": "claude", "status": "blocked", "title": "RAV-1", "tab": "4 · RAV-1", "machine": "sg", "session": "raven", "pane": "w4:p9", "seq": 9, "term": "t", "needs": "his eyes on the diff"}"""))
        val lines = t.tile()
        assertTrue("$lines", "his eyes on the diff" in lines)
        assertTrue("$lines", "open in Mercurio" in lines)
        assertEquals(emptyList<String>(), keys(lines))
    }

    /** The chats are the comms tile's: a chat that waits, in a project or loose, is nowhere on this tile or its screen. */
    @Test fun theTileShowsNoChatsTheyAreTheCommsTiles() {
        val chat = """{"name": "Ana", "group": false, "waiting": true, "mine": false, "age_min": 5, "text": "can you look?", "open": {"url": "https://wa.me/5550002"}, "k": "aaaaaaaaaaaa"}"""
        val f =
            Json.parse(
                """{"ok": true, "code": 200, "out": {"generated": 1794166000, "machines": {"sg": "ok"}, "errors": {}, "needs": 0,
                "agents": {"blocked": 0, "working": 1, "done": 0, "idle": 0}, "waiting": 2, "unsorted": [$chat],
                "links": {"sg": "ssh://ormus@sg:22#sg"}, "hosts": {"sg": ["sg"]}, "game": {"today": 0, "into": 0, "need": 3, "level": 1, "streak": 0}, "moves": [],
                "boards": [{"name": "raven", "agents": {"blocked": 0, "working": 1, "done": 0, "idle": 0}, "waiting": 1,
                  "projects": [{"name": "CUSTOMER PORTALS", "agents": {"blocked": 0, "working": 1, "done": 0, "idle": 0}, "chats": [$chat],
                    "top": [{"kind": "claude", "status": "working", "title": "the portal's invoices", "tab": "", "machine": "sg", "session": "raven", "pane": "w1:p1", "seq": 1}]}]}]}}""",
            )
        val t = Tile(f)
        val tile = t.tile()
        val screen = t.screen()
        assertTrue("the agent at work is a row: $tile", tile.any { "the portal's invoices" in it })
        assertFalse("no chat on the tile: $tile", tile.any { "Ana" in it || "can you look?" in it || "chats wait" in it })
        assertFalse("no chat on the screen: $screen", screen.any { "Ana" in it || "can you look?" in it || "WAITS ON YOU" in it })
        assertTrue("the counts are the agents' alone: $tile", "1 working · 0 done" in tile)
    }
}
