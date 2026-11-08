package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comms tile (12-comms.owl) run on made-up answers of sg's sessions feed (ormus-feeds feeds/sessions.py): the
 * chat card on the tile and on its screen, what its lines say, which keys it offers and what a key sends.
 */
class OrmusCommsTileTest {
    private val program = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/12-comms.owl").readText())

    /** The feed source [f] (http with `status`: {ok, code, out}), the clock [now], the tile's size in dp. */
    private inner class Tile(
        f: Value,
        private val height: Int? = 400,
        private val width: Int? = 360,
    ) : Host {
        private val slots = Array<Value>(program.globals.size) { Null }

        /** What the tile asked the launcher to do, as "name args". */
        val actions = ArrayList<String>()

        init {
            program.globals.forEachIndexed { i, g -> if (g is Global.State) slots[i] = g.init(Env.root(this)) }
            slots[slot("f")] = f
            slots[slot("now")] = Num(1_794_166_000.0)
        }

        /** Pins a card by hand, as his tap on "show 1 · 2 · 3" does. */
        fun pin(k: String) {
            slots[slot("pick")] = Str(k)
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

    private val replies = """[{"n": 1, "label": "On it."}, {"n": 2, "label": "Yes."}, {"n": 3, "label": "Later today."}]"""

    /** A chat row as the feed writes it (feeds/sessions.py chat_row). Invented ids, 7 digits at most. */
    private fun chat(
        k: String,
        name: String,
        waiting: Boolean = true,
        group: Boolean = false,
        text: String = "can you look?",
        age: Int = 12,
        withReplies: Boolean = true,
        extra: String = "",
    ) = """{"name": "$name", "group": $group, "waiting": $waiting, "mine": ${!waiting}, "age_min": $age, "text": "$text",
            "open": ${if (group) """{"jid": "g1@g.us"}""" else """{"url": "https://wa.me/5550002"}"""}, "k": "$k"${if (withReplies && waiting) """, "replies": $replies""" else ""}$extra}"""

    private val ana = chat("aaaaaaaaaaaa", "Ana")
    private val team = chat("bbbbbbbbbbbb", "Team", group = true, text = "deploy tonight?", age = 90)
    private val quiet = chat("cccccccccccc", "Ben", waiting = false, text = "thanks", age = 300)
    private val town = chat("dddddddddddd", "Town", group = true, withReplies = false, text = "who is coming?")
    private val replied = chat("eeeeeeeeeeee", "Cyn", age = 20, extra = """, "replied": 3""")
    private val loose = chat("ffffffffffff", "Bo", text = "hola")

    private fun feed(
        vararg rows: String,
        unsorted: String = "",
    ): Value =
        Json.parse(
            """{"ok": true, "code": 200, "out": {"generated": 1794166000, "machines": {"sg": "ok"}, "errors": {}, "needs": 0,
            "agents": {"blocked": 0, "working": 0, "done": 0, "idle": 0}, "waiting": ${rows.size}, "unsorted": [$unsorted],
            "links": {}, "hosts": {}, "game": {"today": 0, "into": 0, "need": 3, "level": 1, "streak": 0}, "moves": [],
            "boards": [{"name": "raven", "agents": {"blocked": 0, "working": 0, "done": 0, "idle": 0}, "waiting": ${rows.size},
              "projects": [{"name": "CUSTOMER PORTALS", "agents": {"blocked": 0, "working": 0, "done": 0, "idle": 0}, "top": [], "chats": [${rows.joinToString(",")}]}]}]}}""",
        )

    private fun Tile.tile() = lines(program.root)

    private fun Tile.screen() = lines(program.screen!!)

    /** The numbered keys among the lines (not the headline's count nor "1 of 2"). */
    private fun keys(lines: List<String>) = lines.filter { Regex("^[1-9] (?!chats? wait|of [0-9])").containsMatchIn(it) }

    @Test fun theCardIsTheChatItsLastLineAndThePinKeyNamesItsKeys() {
        val t = Tile(feed(ana, team, quiet))
        val lines = t.tile()
        assertTrue("who waits, in gold: $lines", "2 chats wait" in lines)
        assertTrue("the first waiting chat is the card: $lines", "Ana" in lines)
        assertTrue("their last line: $lines", "can you look?" in lines)
        assertTrue("a person, how long ago: $lines", "chat · 12m ago" in lines)
        assertTrue("which card of how many: $lines", "1 of 2" in lines)
        assertTrue("three replies, so the pin key names three: $lines", "show 1 · 2 · 3" in lines)
        assertFalse("no key shows before the card is pinned: $lines", "1 On it." in lines)
        assertTrue("the other waiting chat is a row under the card: $lines", "Team" in lines)
        assertFalse("a chat that does not wait is not on the tile: $lines", "Ben" in lines)
        assertEquals("one line of text, his three replies stacked (12 characters do not fit a third of 360 dp)", 266.0, (t.let("cardHeight") as Num).v, 0.0)
        val short = Tile(feed(ana.replace(replies, """[{"n": 1, "label": "Ok"}, {"n": 2, "label": "Yes"}, {"n": 3, "label": "No"}]""")))
        assertEquals("three short replies go side by side", 190.0, (short.let("cardHeight") as Num).v, 0.0)
        val wrapped = Tile(feed(chat("aaaaaaaaaaaa", "Ana", text = "can you look at the invoice before the call with the client?")))
        assertEquals("a second line of their text adds 18 dp", 284.0, (wrapped.let("cardHeight") as Num).v, 0.0)
    }

    @Test fun aPinnedCardSendsTheChatsKeyTheNumberAndTheLabelOfTheReplyTapped() {
        val t = Tile(feed(ana, team))
        t.pin("aaaaaaaaaaaa")
        val lines = t.tile()
        assertEquals(listOf("1 On it.", "2 Yes.", "3 Later today."), keys(lines))
        val runs = t.taps(program.root).filter { it.startsWith("run ") }
        assertEquals("the first key's tap runs the reply tool; the others find it busy", 1, runs.size)
        val sent = runs[0]
        assertTrue(sent, sent.contains("projects/ormus-feeds/bin/reply -"))
        for (part in listOf("\"k\":\"aaaaaaaaaaaa\"", "\"n\":1", "\"label\":\"On it.\"")) assertTrue("$part in $sent", sent.contains(part))
        assertFalse("no chat id and no number travels in the call: $sent", sent.contains("5550002") || sent.contains("wa.me"))
        assertTrue("the card's own tap opens the chat in WhatsApp", t.taps(program.root).any { it.startsWith("androidIntent") && it.contains("com.whatsapp") && it.contains("wa.me/5550002") })
    }

    @Test fun aGroupCardOpensByItsIdAndStacksLongKeys() {
        val long = """[{"n": 1, "label": "On it, give me an hour."}, {"n": 2, "label": "Yes, go ahead with it."}, {"n": 3, "label": "Later today, after the call."}]"""
        val t = Tile(feed(chat("bbbbbbbbbbbb", "Team", group = true, text = "deploy tonight?").replace(replies, long)))
        t.pin("bbbbbbbbbbbb")
        val lines = t.tile()
        assertTrue("a group, how long ago: $lines", "group · 12m ago" in lines)
        assertEquals(listOf("1 On it, give me an hour.", "2 Yes, go ahead with it.", "3 Later today, after the call."), keys(lines))
        assertEquals("the stack of three", 266.0, (t.let("cardHeight") as Num).v, 0.0)
        assertTrue("a group with no link opens by its id", t.taps(program.root).any { it.startsWith("androidIntent") && it.contains("com.whatsapp.Conversation") && it.contains("g1@g.us") })
    }

    @Test fun aChatHeRepliedToShowsThatAndAnOpenKeyInsteadOfReplies() {
        val t = Tile(feed(replied))
        t.pin("eeeeeeeeeeee")
        val lines = t.tile()
        assertTrue("$lines", "replied 3m ago" in lines)
        assertTrue("$lines", "open" in lines)
        assertEquals(emptyList<String>(), keys(lines))
        assertTrue("no reply tool is run", t.taps(program.root).none { it.startsWith("run ") })
    }

    @Test fun aChatTheFeedOffersNoReplyOnHasTheOpenKeyAlone() {
        val t = Tile(feed(town))
        t.pin("dddddddddddd")
        val lines = t.tile()
        assertTrue("$lines", "open in WhatsApp" in lines)
        assertEquals(emptyList<String>(), keys(lines))
    }

    @Test fun nobodyWaitingIsSaidInGreenAndTheScreenListsEveryChatAndTheLooseOnes() {
        val none = Tile(feed(quiet))
        assertTrue("${none.tile()}", "nobody waits" in none.tile())
        val t = Tile(feed(ana, quiet, unsorted = loose))
        val lines = t.tile()
        assertTrue("the loose chat counts and is a row: $lines", "2 chats wait" in lines && "Bo" in lines)
        val screen = t.screen()
        assertTrue("every chat of the project: $screen", "Ana: can you look?" in screen && "Ben: thanks" in screen)
        assertTrue("the loose ones under their own label: $screen", "WAITS ON YOU, NO PROJECT" in screen && "Bo: hola" in screen)
        assertTrue("the board and its count: $screen", "RAVEN" in screen && "2 wait" in screen)
    }

    @Test fun theLineAfterAReplySaysWhatSgAnswered() {
        val t = Tile(feed(ana))
        t.pin("aaaaaaaaaaaa")
        val sent = t.slot("sent")
        // The `on sent` trigger: the one whose value is the `sent` state.
        t.write(sent, Str("the mark"))
        val on = program.triggers.first { it.kind == TriggerKind.ON && (it.expr(Env.root(t)) as? Str)?.v == "the mark" }
        fun said(answer: String): String {
            t.write(t.slot("busy"), Str("aaaaaaaaaaaa"))
            t.write(sent, Str(answer))
            on.action.run(Env.root(t), listOf(Str(answer), Str("the mark")))
            return (t.read(t.slot("said")) as Str).v
        }
        assertEquals("sent", said("""{"n": 1, "r": {"ok": true, "outcome": "sent"}}"""))
        assertEquals("you just replied: wait a minute", said("""{"n": 2, "r": {"ok": false, "outcome": "too soon"}}"""))
        assertEquals("the chat moved on: look again", said("""{"n": 3, "r": {"ok": false, "outcome": "unknown chat"}}"""))
        assertEquals("sg's list is old: open the chat", said("""{"n": 4, "r": {"ok": false, "outcome": "feed stale"}}"""))
        assertEquals("not confirmed: open the chat", said("""{"n": 5, "r": {"ok": false, "outcome": "not confirmed"}}"""))
        assertEquals("sg refused it", said("""{"n": 6, "r": {"ok": false, "outcome": "refused"}}"""))
        assertEquals("it did not go through: open the chat", said("""{"n": 7, "r": {"ok": false, "outcome": "failed"}}"""))
        assertEquals("sg did not answer", said(""))
        assertEquals("the card is free again", "", (t.read(t.slot("busy")) as Str).v)
    }
}
