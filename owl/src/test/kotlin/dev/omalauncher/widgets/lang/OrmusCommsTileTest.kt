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

        /** A top-level let once read, kept until a slot changes: the engine keeps its lets per frame the same way. */
        private val frame = HashMap<Int, Value>()

        /** What the tile asked the launcher to do, as "name args". */
        val actions = ArrayList<String>()

        init {
            program.globals.forEachIndexed { i, g -> if (g is Global.State) slots[i] = g.init(Env.root(this)) }
            slots[slot("f")] = f
            slots[slot("now")] = Num(1_794_166_000.0)
        }

        /** Pins a card by hand, as his tap on "show 1 · 2 · 3" does: the card is its chat and the text he read. */
        fun pin(
            k: String,
            text: String = "can you look?",
        ) {
            write(slot("pick"), Str(id(k, text)))
        }

        /** The next read of the feed, as the 60 s poll brings it, and the `on f` trigger it fires. */
        fun feed(f: Value) {
            fire("f", f)
        }

        /** The `on x` trigger whose `x` is the given global, fired as the engine fires it: (the value, the old one). */
        fun fire(
            global: String,
            value: Value,
            old: Value = Null,
        ) {
            write(slot(global), value)
            val on = program.triggers.first { it.kind == TriggerKind.ON && it.expr(Env.root(this)) == value }
            on.action.run(Env.root(this), listOf(value, old))
        }

        fun slot(name: String) = program.globals.indexOfFirst { it.name == name }.also { assertTrue("no global $name", it >= 0) }

        /** A top-level `let`, evaluated. */
        fun let(name: String): Value = budget.run { read(slot(name)) }

        override val budget = Budget()

        override fun read(slot: Int): Value = (program.globals[slot] as? Global.Let)?.value?.let { v -> frame.getOrPut(slot) { v.invoke(Env.root(this)) } } ?: slots[slot]

        override fun write(
            slot: Int,
            value: Value,
        ) {
            slots[slot] = value
            frame.clear()
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
            budget.run { visit(nodes, Env.root(this), false) { n, env, _ -> n.handlers["onTap"]?.run?.invoke(env, emptyList()) } }
            return actions.toList()
        }

        /** The Texts of every element with an onTap that sits under another element with an onTap: a tap on it would reach both. */
        fun tapsUnderTaps(nodes: List<WNode>): List<String> {
            val out = ArrayList<String>()
            budget.run { visit(nodes, Env.root(this), false) { n, env, under -> if (under && n.handlers["onTap"] != null) out += lines(listOf(n), env).joinToString(" / ") } }
            return out
        }

        private fun visit(
            nodes: List<WNode>,
            env: Env,
            underTap: Boolean,
            each: (WNode.Element, Env, Boolean) -> Unit,
        ) {
            for (n in nodes) {
                when (n) {
                    is WNode.Element -> {
                        each(n, env, underTap)
                        visit(n.children, env, underTap || n.handlers["onTap"] != null, each)
                    }
                    is WNode.If -> visit(if (n.cond(env).truthy) n.then else n.orElse, env, underTap, each)
                    is WNode.For -> Builtins.iterable(n.list(env)).forEachIndexed { i, v -> visit(n.body, env.child(v, Num(i.toDouble())), underTap, each) }
                    is WNode.Lets -> visit(n.children, env.lets(n.values), underTap, each)
                    is WNode.Use -> visit(n.body, Env(this, Env.root(this), Array(n.args.size) { i -> { n.args[i](env) } }), underTap, each)
                    is WNode.Draw -> {}
                }
            }
        }

        /** Every Text, in order, as the renderer walks it. */
        fun lines(
            nodes: List<WNode>,
            env: Env = Env.root(this),
        ): List<String> = ArrayList<String>().also { budget.run { walk(nodes, env, it) } }

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

    /** A card's id as the tile keeps it: the chat's `k` and the text shown, so a new text is a new card. */
    private fun id(
        k: String,
        text: String,
    ) = "$k|$text"

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
    private fun keys(lines: List<String>) = lines.filter { Regex("^[1-9] (?!chats? wait|of [0-9]|wait$)").containsMatchIn(it) }

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
        assertEquals("one line of text, his three replies stacked (12 characters do not fit a third of 360 dp)", 247.0, (t.let("cardHeight") as Num).v, 0.0)
        val short = Tile(feed(ana.replace(replies, """[{"n": 1, "label": "Ok"}, {"n": 2, "label": "Yes"}, {"n": 3, "label": "No"}]""")))
        assertEquals("three short replies go side by side", 151.0, (short.let("cardHeight") as Num).v, 0.0)
        val wrapped = Tile(feed(chat("aaaaaaaaaaaa", "Ana", text = "can you look at the invoice before the call with the client?")))
        assertEquals("a second line of their text adds 17 dp", 264.0, (wrapped.let("cardHeight") as Num).v, 0.0)
    }

    /** The heights the emulator measured at 420 dpi: a 6x4 tile is 254 dp tall, a 6x6 one 384. */
    @Test fun aShortTileFoldsTheMetaLineIntoTheHeaderAndPutsTheKeysSideBySideWithNoDots() {
        val t = Tile(feed(ana, team), height = 254)
        t.pin("aaaaaaaaaaaa")
        val lines = t.tile()
        assertEquals("the three keys still show, in a row that wraps the long label", listOf("1 On it.", "2 Yes.", "3 Later today."), keys(lines))
        assertTrue("the meta line rides in the header row, as the age alone: $lines", "12m · customer portals · raven" in lines)
        assertFalse("and not on its own line: $lines", "chat · 12m ago" in lines)
        val group = Tile(feed(team), height = 254)
        assertTrue("a group says so: ${group.tile()}", "1h · group · customer portals · raven" in group.tile())
        assertFalse("no arrows: the header counts the cards and a swipe turns them: $lines", "‹" in lines)
        assertFalse("no room for a row under the card: $lines", "Team" in lines)
        assertFalse("and no more-line either: $lines", lines.any { it.startsWith("+") })
        assertEquals("the folded card with a two-line key label", 150.0, (t.let("cardHeight") as Num).v, 0.0)
        val wrapped = Tile(feed(chat("aaaaaaaaaaaa", "Ana", text = "can you look at the invoice before the call?")), height = 254, width = 377)
        assertEquals("44 characters wrap once at 377 dp: 167 of the 174 dp under the head", 167.0, (wrapped.let("cardHeight") as Num).v, 0.0)
        assertTrue((wrapped.let("below") as Num).v >= 167.0)
        val edge = Tile(feed(chat("aaaaaaaaaaaa", "Ana", text = "can you look at the invoice before noon")), height = 254, width = 377)
        assertEquals("39 characters hold one line at 377 dp", 150.0, (edge.let("cardHeight") as Num).v, 0.0)
        val over = Tile(feed(chat("aaaaaaaaaaaa", "Ana", text = "can you look at the invoice before noon?")), height = 254, width = 377)
        assertEquals("40 wrap", 167.0, (over.let("cardHeight") as Num).v, 0.0)
        val three = Tile(feed(ana, team), height = 254, width = 377)
        three.pin("aaaaaaaaaaaa")
        assertEquals("at 377 dp the three keys show the same", listOf("1 On it.", "2 Yes.", "3 Later today."), keys(three.tile()))
    }

    @Test fun aTallTileStacksTheKeysAndShowsTheDotsOnlyWhenTheyFit() {
        val t = Tile(feed(ana, team), height = 384)
        t.pin("aaaaaaaaaaaa")
        val lines = t.tile()
        assertEquals(listOf("1 On it.", "2 Yes.", "3 Later today."), keys(lines))
        assertTrue("the meta line on its own: $lines", "chat · 12m ago" in lines)
        assertTrue("two cards, 57 dp left under a 247 dp card: the dots show: $lines", "‹" in lines)
        assertFalse("but 17 dp hold no row and no more-line: $lines", "Team" in lines || lines.any { it.startsWith("+") })
        val long = Tile(feed(chat("aaaaaaaaaaaa", "Ana", text = "can you look at the invoice before the call with the client?"), team), height = 384)
        long.pin("aaaaaaaaaaaa")
        assertTrue("a 264 dp card leaves 40 dp: the dots just fit: ${long.tile()}", "‹" in long.tile())
        val longer = Tile(feed(chat("aaaaaaaaaaaa", "Ana", text = "can you look at the invoice before the call with the client?").replace(replies, """[{"n": 1, "label": "On it, give me an hour."}, {"n": 2, "label": "Yes, go ahead with it."}, {"n": 3, "label": "Later today, after the call."}, {"n": 4, "label": "No."}]"""), team), height = 384)
        longer.pin("aaaaaaaaaaaa")
        assertFalse("four stacked keys leave none: no dots: ${longer.tile()}", "‹" in longer.tile())
        val roomy = Tile(feed(ana, team, loose), height = 500)
        roomy.pin("aaaaaaaaaaaa")
        assertTrue("133 dp under the dots hold four rows; the two show: ${roomy.tile()}", "Team" in roomy.tile() && "Bo" in roomy.tile())
        val tight = Tile(feed(ana, team, loose, chat("gggggggggggg", "Dee")), height = 500)
        tight.pin("aaaaaaaaaaaa")
        assertTrue("133 dp hold four slots; three rows fit, no more-line: ${tight.tile()}", "Dee" in tight.tile() && tight.tile().none { it.startsWith("+") })
    }

    @Test fun onAShortTileSgsAnswerTakesThePlaceOfTheirLastLine() {
        val t = Tile(feed(ana), height = 254)
        t.pin("aaaaaaaaaaaa")
        t.write(t.slot("said"), Str("sg refused it"))
        t.write(t.slot("saidFor"), Str(id("aaaaaaaaaaaa", "can you look?")))
        val lines = t.tile()
        assertTrue("$lines", "sg refused it" in lines)
        assertFalse("their line gives way: $lines", "can you look?" in lines)
        val tall = Tile(feed(ana), height = 400)
        tall.pin("aaaaaaaaaaaa")
        tall.write(tall.slot("said"), Str("sg refused it"))
        tall.write(tall.slot("saidFor"), Str(id("aaaaaaaaaaaa", "can you look?")))
        assertTrue("a tall tile shows both: ${tall.tile()}", "sg refused it" in tall.tile() && "can you look?" in tall.tile())
        // He swiped on: the answer is about a card that is not the one shown.
        val away = Tile(feed(ana, team), height = 254)
        away.pin("bbbbbbbbbbbb", "deploy tonight?")
        away.write(away.slot("said"), Str("sg refused it"))
        away.write(away.slot("saidFor"), Str(id("aaaaaaaaaaaa", "can you look?")))
        assertFalse("a short tile keeps its headline to the count: ${away.tile()}", "sg refused it" in away.tile())
        assertTrue("its screen says it under the headline: ${away.screen()}", "sg refused it" in away.screen())
        val awayTall = Tile(feed(ana, team), height = 400)
        awayTall.pin("bbbbbbbbbbbb", "deploy tonight?")
        awayTall.write(awayTall.slot("said"), Str("sg refused it"))
        awayTall.write(awayTall.slot("saidFor"), Str(id("aaaaaaaaaaaa", "can you look?")))
        assertTrue("a tall tile says it under the headline: ${awayTall.tile()}", "sg refused it" in awayTall.tile())
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
        for (part in listOf("\"k\":\"aaaaaaaaaaaa\"", "\"n\":1,", "\"label\":\"On it.\"")) assertTrue("$part in $sent", sent.contains(part))
        assertTrue("sg's answer is read whole, then wrapped with a number of its own and ssh's exit code: $sent", Regex("""r=\${'$'}\(printf %s '\{"k":"aaaaaaaaaaaa","n":1,"label":"On it\."\}' \| ssh .* sg projects/ormus-feeds/bin/reply -\); x=\${'$'}\?; \[ -n "\${'$'}r" \] \|\| r=null; printf '\{"n":%s,"r":%s,"x":%s\}' [0-9]+ "\${'$'}r" \${'$'}x""").containsMatchIn(sent))
        assertFalse("no chat id and no number travels in the call: $sent", sent.contains("5550002") || sent.contains("wa.me"))
        assertTrue("the card's own tap opens the chat in WhatsApp", t.taps(program.root).any { it.startsWith("androidIntent") && it.contains("com.whatsapp") && it.contains("wa.me/5550002") })
    }

    @Test fun aGroupCardOpensByItsIdAndStacksLongKeys() {
        val long = """[{"n": 1, "label": "On it, give me an hour."}, {"n": 2, "label": "Yes, go ahead with it."}, {"n": 3, "label": "Later today, after the call."}]"""
        val t = Tile(feed(chat("bbbbbbbbbbbb", "Team", group = true, text = "deploy tonight?").replace(replies, long)))
        t.pin("bbbbbbbbbbbb", "deploy tonight?")
        val lines = t.tile()
        assertTrue("a group, how long ago: $lines", "group · 12m ago" in lines)
        assertEquals(listOf("1 On it, give me an hour.", "2 Yes, go ahead with it.", "3 Later today, after the call."), keys(lines))
        assertEquals("the stack of three", 247.0, (t.let("cardHeight") as Num).v, 0.0)
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
            t.write(t.slot("busy"), Str(id("aaaaaaaaaaaa", "can you look?")))
            t.write(sent, Str(answer))
            on.action.run(Env.root(t), listOf(Str(answer), Str("the mark")))
            return (t.read(t.slot("said")) as Str).v
        }
        assertEquals("sent", said("""{"n": 1, "r": {"ok": true, "outcome": "sent"}}"""))
        assertEquals("you just replied: wait a minute", said("""{"n": 2, "r": {"ok": false, "outcome": "too soon"}}"""))
        assertEquals("the chat moved on: look again", said("""{"n": 3, "r": {"ok": false, "outcome": "unknown chat"}}"""))
        assertEquals("sg's list is old: open the chat", said("""{"n": 4, "r": {"ok": false, "outcome": "feed stale"}}"""))
        assertEquals("not confirmed: open the chat", said("""{"n": 5, "r": {"ok": false, "outcome": "not confirmed"}}"""))
        assertEquals("sg refused it: look again", said("""{"n": 6, "r": {"ok": false, "outcome": "refused"}}"""))
        assertEquals("it did not go through: open the chat", said("""{"n": 7, "r": {"ok": false, "outcome": "failed"}}"""))
        // ssh dropped after the text may have gone, or printed nothing: never "nothing was sent", or he sends it twice.
        assertEquals("no answer: open the chat to check", said(""))
        assertEquals("no answer: open the chat to check", said("""{"n":1,"r":null,"x":255}"""))
        assertEquals("no answer: open the chat to check", said("""{"n":1,"r":}"""))
        assertEquals("no answer: open the chat to check", said("not json"))
        assertEquals("no answer: open the chat to check", said("""{"n":1,"r":"text","x":0}"""))
        assertEquals("the card is free again", "", (t.read(t.slot("busy")) as Str).v)
        assertEquals("and the line is about that card", id("aaaaaaaaaaaa", "can you look?"), (t.read(t.slot("saidFor")) as Str).v)
    }

    @Test fun aLabelWithQuotesAndAShellWordTravelsAsOneQuotedJsonWord() {
        val tricky = """[{"n": 1, "label": "it's \"ok\" $(id)\nnext"}]"""
        val t = Tile(feed(ana.replace(replies, tricky)))
        t.pin("aaaaaaaaaaaa")
        val sent = t.taps(program.root).first { it.startsWith("run ") }
        assertTrue("the label sits inside one single-quoted word, the quote itself closed and reopened: $sent", sent.contains("""'{"k":"aaaaaaaaaaaa","n":1,"label":"it'\''s \"ok\" $(id)\nnext"}'"""))
    }

    /** After a reply the chat writes again: the card is a new one, with its text, no stale "sent" on it (review M2). */
    @Test fun aStaleAnswerLeavesWhenTheChatWritesAgain() {
        for (height in listOf(254, 400)) {
            val t = Tile(feed(ana), height = height)
            t.pin("aaaaaaaaaaaa")
            t.write(t.slot("said"), Str("sent"))
            t.write(t.slot("saidFor"), Str(id("aaaaaaaaaaaa", "can you look?")))
            assertTrue("$height: ${t.tile()}", "sent" in t.tile())
            t.feed(feed(chat("aaaaaaaaaaaa", "Ana", text = "and the invoice?", age = 1)))
            val lines = t.tile()
            assertTrue("$height: their new line shows: $lines", "and the invoice?" in lines)
            assertFalse("$height: the old answer is gone from the card: $lines", "sent" in lines)
            assertEquals("$height: the feed's read clears an answer about a card that is gone", "", (t.read(t.slot("said")) as Str).v)
            assertFalse("$height: and from under the headline: ${t.screen()}", "sent" in t.screen())
        }
    }

    /** A refresh that changes the text under a pinned card unpins it: its keys were for the text he read (review M4). */
    @Test fun aChangedTextUnpinsTheCard() {
        val t = Tile(feed(ana, team))
        t.pin("aaaaaaaaaaaa")
        assertEquals(listOf("1 On it.", "2 Yes.", "3 Later today."), keys(t.tile()))
        t.feed(feed(chat("aaaaaaaaaaaa", "Ana", text = "actually cancel it", age = 0), team))
        val lines = t.tile()
        assertFalse("${t.let("pinned")}", t.let("pinned").truthy)
        assertEquals("no key is live: $lines", emptyList<String>(), keys(lines))
        assertTrue("the pin key is back: $lines", "show 1 · 2 · 3" in lines)
        assertTrue("on the new text: $lines", "actually cancel it" in lines)
        assertTrue("a tap sends nothing", t.taps(program.root).none { it.startsWith("run ") })
    }

    /** No key, pin or arrow sits under the handler that opens the chat: one tap reaches one handler (review M3). */
    @Test fun noTapHandlerSitsUnderAnother() {
        for (pinned in listOf(false, true)) {
            for (height in listOf(254, 400)) {
                val t = Tile(feed(ana, team, town, replied), height = height)
                if (pinned) t.pin("aaaaaaaaaaaa")
                assertEquals("tile, pinned=$pinned, $height", emptyList<String>(), t.tapsUnderTaps(program.root))
                assertEquals("screen, pinned=$pinned, $height", emptyList<String>(), t.tapsUnderTaps(program.screen!!))
            }
        }
        val t = Tile(feed(ana, team))
        t.pin("aaaaaaaaaaaa")
        assertTrue("the body still opens the chat", t.taps(program.root).any { it.startsWith("androidIntent") && it.contains("wa.me/5550002") })
    }

    /** Long labels on a short tile: the lead reply whole and a "more" key to the screen, never a cut label (review M5). */
    @Test fun longLabelsOnAShortTileShowTheLeadKeyWholeAndMore() {
        val long = """[{"n": 1, "label": "On it, give me an hour."}, {"n": 2, "label": "Yes, go ahead with it."}, {"n": 3, "label": "Later today, after the call."}]"""
        val t = Tile(feed(ana.replace(replies, long), team), height = 254, width = 377)
        t.pin("aaaaaaaaaaaa")
        val lines = t.tile()
        assertEquals("the lead key alone among the numbered ones: $lines", listOf("1 On it, give me an hour."), keys(lines))
        assertTrue("and a key to the rest: $lines", "more" in lines)
        assertEquals("the lead label wraps once at two thirds of the row", 150.0, (t.let("cardHeight") as Num).v, 0.0)
        assertEquals("the screen stacks all three", listOf("1 On it, give me an hour.", "2 Yes, go ahead with it.", "3 Later today, after the call."), keys(t.screen()))
        assertTrue("which opens the screen", "screen " in t.taps(program.root))
        val fits = Tile(feed(ana, team), height = 254, width = 377)
        fits.pin("aaaaaaaaaaaa")
        assertFalse("his defaults fit a third each, two lines: no more-key: ${fits.tile()}", "more" in fits.tile())
        val one = Tile(feed(ana.replace(replies, """[{"n": 1, "label": "Yes, go ahead with it, I am on it now."}]"""), team), height = 254, width = 377)
        one.pin("aaaaaaaaaaaa")
        assertEquals("a lone key has the whole row: $lines", listOf("1 Yes, go ahead with it, I am on it now."), keys(one.tile()))
        assertFalse("more" in one.tile())
    }

    /** The folded card needs 230 dp; a 4x3 tile has about 190: the tile asks for 6x4 at least (review M6). */
    @Test fun theTileNeedsSixByFourAtLeast() {
        assertEquals(6 to 4, program.minSize)
    }

    /** The answer line under the card counts in the height budget: the dots give way to it (review L7). */
    @Test fun theAnswerLineCountsInTheHeightBudget() {
        val t = Tile(feed(ana, team), height = 384)
        t.pin("aaaaaaaaaaaa")
        assertTrue("two cards, 57 dp left: dots: ${t.tile()}", "‹" in t.tile())
        t.write(t.slot("said"), Str("sg refused it: look again"))
        t.write(t.slot("saidFor"), Str(id("aaaaaaaaaaaa", "can you look?")))
        assertTrue("${t.tile()}", "sg refused it: look again" in t.tile())
        assertFalse("the answer takes 23 dp: 34 left, no dots: ${t.tile()}", "‹" in t.tile())
        val other = Tile(feed(ana, team), height = 384)
        other.pin("bbbbbbbbbbbb", "deploy tonight?")
        other.write(other.slot("said"), Str("sg refused it: look again"))
        other.write(other.slot("saidFor"), Str(id("aaaaaaaaaaaa", "can you look?")))
        assertTrue("about the other card, under the headline: ${other.tile()}", "sg refused it: look again" in other.tile())
        assertFalse("18 dp under the headline: 39 left, no dots: ${other.tile()}", "‹" in other.tile())
    }

    /** "sent" retires the keys at once, before the next read shows `replied` (review L8). */
    @Test fun aSentReplyRetiresTheKeysAtOnce() {
        for (height in listOf(254, 400)) {
            val t = Tile(feed(ana), height = height)
            t.pin("aaaaaaaaaaaa")
            t.write(t.slot("said"), Str("sent"))
            t.write(t.slot("saidFor"), Str(id("aaaaaaaaaaaa", "can you look?")))
            val lines = t.tile()
            assertEquals("$height: no key: $lines", emptyList<String>(), keys(lines))
            assertTrue("$height: it says sent, with an open key: $lines", "sent" in lines && "open" in lines)
            assertTrue("$height: their line stays: $lines", "can you look?" in lines)
            assertTrue("$height: no reply tool runs", t.taps(program.root).none { it.startsWith("run ") })
        }
    }

    /** A reply whose answer never comes (the shell died) frees the card on the next minute after 30 s (review L9). */
    @Test fun aReplyWithNoAnswerFreesTheCardAfterHalfAMinute() {
        val t = Tile(feed(ana, team))
        t.pin("aaaaaaaaaaaa")
        val now = 1_794_166_000.0
        t.write(t.slot("busy"), Str(id("aaaaaaaaaaaa", "can you look?")))
        t.write(t.slot("busyN"), Num(2.0))
        // Tapped 10 s before the clock's next tick.
        t.write(t.slot("busyAt"), Num(now + 50))
        assertEquals("the other keys keep their words: ${t.tile()}", listOf("1 On it.", "3 Later today."), keys(t.tile()))
        assertTrue("only the tapped key waits: ${t.tile()}", "…" in t.tile())
        t.fire("now", Num(now + 60), Num(now))
        assertEquals("10 s in, still waiting", id("aaaaaaaaaaaa", "can you look?"), (t.read(t.slot("busy")) as Str).v)
        t.write(t.slot("busyAt"), Num(now - 31))
        t.fire("now", Num(now + 120), Num(now + 60))
        assertEquals("", (t.read(t.slot("busy")) as Str).v)
        assertEquals("no answer: open the chat to check", (t.read(t.slot("said")) as Str).v)
        assertEquals(id("aaaaaaaaaaaa", "can you look?"), (t.read(t.slot("saidFor")) as Str).v)
    }

    /** Rows with fields missing, a board with no projects, a feed with no clock: the tile still draws (review L11). */
    @Test fun missingFieldsDoNotBreakTheTile() {
        val bare = """{"name": "Ana", "group": false, "waiting": true, "mine": false, "k": "aaaaaaaaaaaa"}"""
        val t =
            Tile(
                Json.parse(
                    """{"ok": true, "code": 200, "out": {"boards": [{"name": "raven"}, {"name": "ormus", "projects": [{"name": "X", "chats": [$bare]}]}]}}""",
                ),
                height = 254,
            )
        val lines = t.tile()
        assertTrue("$lines", "1 chat waits" in lines && "Ana" in lines)
        assertFalse("no null text: $lines", lines.any { "null" in it })
        assertTrue("the pin key shows with no replies to count: $lines", "open in WhatsApp" in lines || "show" in lines)
        t.pin("aaaaaaaaaaaa", "")
        assertTrue("no `open`: the body opens WhatsApp itself: ${t.taps(program.root)}", t.taps(program.root).any { it == "androidApp com.whatsapp" })
        val screen = t.screen()
        assertTrue("$screen", "Ana" in screen)
        assertFalse("$screen", screen.any { "null" in it })
    }

    /** One chat under two projects is one card and counts once (review L11). */
    @Test fun aChatNamedTwiceIsOneCard() {
        val twice =
            Json.parse(
                """{"ok": true, "code": 200, "out": {"generated": 1794166000, "errors": {}, "unsorted": [$ana],
                "boards": [{"name": "raven", "projects": [{"name": "A", "chats": [$ana]}, {"name": "B", "chats": [$ana]}]}]}}""",
            )
        val t = Tile(twice)
        val lines = t.tile()
        assertTrue("$lines", "1 chat waits" in lines)
        assertFalse("no row under the card for the same chat: $lines", "1 of 3" in lines || "1 of 2" in lines)
        assertEquals(1, (t.let("cards") as Lst).items.size)
    }
}
