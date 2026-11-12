package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Needs you tile (31-needs.owl) run on made-up answers of sg's store (ormus-platform apps/needs-you,
 * `GET /needs/api/asks` and `POST /needs/api/asks/<id>/answer`): which card it shows, which keys it offers, what a key
 * sends, and what each reply of the store says on the tile.
 */
class OrmusNeedsTileTest {
    private val program = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/31-needs.owl").readText())

    /** The store source [f] (http with `status`: {ok, code, out}), the clock, the tile's size in dp, the phone's apps. */
    private inner class Tile(
        f: Value,
        private val height: Int? = 400,
        private val width: Int? = 360,
    ) : Host {
        private val slots = Array<Value>(program.globals.size) { Null }

        /** What the tile asked the launcher to do, as "name args"; a post as "post <url> <body as JSON>". */
        val actions = ArrayList<String>()

        init {
            program.globals.forEachIndexed { i, g -> if (g is Global.State) slots[i] = g.init(Env.root(this)) }
            slots[slot("f")] = f
            slots[slot("now")] = Num(NOW)
            slots[slot("apps")] = Json.parse("""[{"package": "solutions.ormus.logos.debug", "label": "Mercurio"}]""")
        }

        /** Picks a card by hand, as his tap on "show 1 · 2" does (or the first list does, through the `when`). */
        fun pin(id: String) {
            slots[slot("pick")] = Str(id)
        }

        /** An answer for card [id] sent at [at] (clock seconds), and the store's [reply] as `post` writes it into `sent` (null: on its way). */
        fun replied(
            id: String,
            reply: String?,
            at: Double = NOW,
        ) {
            slots[slot("sentFor")] = Str(id)
            slots[slot("sentTime")] = Num(at)
            slots[slot("sent")] = if (reply == null) Null else Json.parse(reply)
        }

        fun slot(name: String) = program.globals.indexOfFirst { it.name == name }.also { assertTrue("no global $name", it >= 0) }

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

        override fun post(
            url: String,
            body: Value,
            headers: Map<String, String>,
            slot: Int?,
            method: String,
        ) {
            actions += "post $url ${Json.write(body, 10_000)}"
        }

        /** Runs every element's onTap in the env it renders in, in order; the actions it asked for. */
        fun taps(): List<String> {
            actions.clear()
            visit(program.root, Env.root(this)) { n, env -> n.handlers["onTap"]?.run?.invoke(env, emptyList()) }
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
        fun lines(): List<String> = ArrayList<String>().also { walk(program.root, Env.root(this), it) }

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

    private companion object {
        /** 2026-11-12 15:00:00 UTC. */
        const val NOW = 1_794_495_600.0
    }

    // Cards as the store's row_out writes them. The store lists the open ones oldest first.
    private fun ask(
        id: String,
        question: String,
        created: String,
        options: List<String> = listOf("tile", "Mercurio"),
        multi: Boolean = false,
        context: String = "",
    ) = """{"id": "$id", "created": "$created", "expires": "2026-11-19T15:00:00+00:00", "host": "sg", "session": "run321",
        "project": "Tools ledger", "question": "$question", "context": "$context",
        "options": [${options.joinToString(",") { """{"label": "$it", "detail": ""}""" }}], "multi": $multi, "state": "open",
        "answer": null, "closed_at": null, "via": null, "pictures": []}"""

    private fun store(vararg asks: String): Value = Json.parse("""{"ok": true, "code": 200, "out": {"asks": [${asks.joinToString(",")}]}}""")

    private val older = ask("a1old", "Ship the old thing?", "2026-11-10T15:00:00+00:00", listOf("yes", "no"))
    private val middle = ask("b2mid", "Which host?", "2026-11-12T12:00:00+00:00", listOf("sg", "Sun", "Mars"))
    private val newest = ask("c3new", "N-53 · a launcher tile, or a widget inside Mercurio?", "2026-11-12T14:50:00+00:00", context = "Measured: a tile can answer.")

    @Test fun anEmptyStoreSaysNobodyWaits() {
        val lines = Tile(store()).lines()
        assertTrue("$lines", "needs you" in lines)
        assertTrue("$lines", "nobody waits on you" in lines)
    }

    @Test fun theNewestCardShowsFirstWithWhereAndHowOld() {
        val t = Tile(store(older, middle, newest))
        val lines = t.lines()
        assertTrue("the count in the headline: $lines", "3 need you" in lines)
        assertTrue("the newest question: $lines", "N-53 · a launcher tile, or a widget inside Mercurio?" in lines)
        assertTrue("its context: $lines", "Measured: a tile can answer." in lines)
        assertTrue("project and host: $lines", "tools ledger · sg" in lines)
        assertTrue("ten minutes old: $lines", "10m" in lines)
        assertTrue("the first of three: $lines", "1 of 3" in lines)
        assertFalse("the oldest is not shown: $lines", "Ship the old thing?" in lines)
    }

    @Test fun anUnpickedCardOffersOnlyThePickKey() {
        val t = Tile(store(older, newest))
        val lines = t.lines()
        assertTrue("$lines", "show 1 · 2" in lines)
        assertFalse("no answer key before the card is picked: $lines", "1 tile" in lines)
        val taps = t.taps()
        assertTrue("a tap picks it and sends nothing: $taps", taps.none { it.startsWith("post") })
        assertEquals("the pick key picked the card", "c3new", (t.let("pick") as Str).v)
    }

    @Test fun aPickedCardHasOneNumberedKeyPerOptionAndAKeySendsItsLabelViaLauncher() {
        val t = Tile(store(older, newest))
        t.pin("c3new")
        val lines = t.lines()
        assertTrue("$lines", "1 tile" in lines && "2 Mercurio" in lines)
        val posts = t.taps().filter { it.startsWith("post") }
        // taps() presses every key in order: the first one sends, and the second waits while the first is on its way.
        assertEquals(
            listOf("""post https://sg.tailb43a3a.ts.net/needs/api/asks/c3new/answer {"choice":["tile"],"via":"launcher"}"""),
            posts,
        )
        assertTrue("the key shows it is on its way: ${t.lines()}", "…" in t.lines())
    }

    @Test fun threeOptionsWithLongLabelsStack() {
        val long = ask("d4long", "Pick one", "2026-11-12T14:00:00+00:00", listOf("the first rather long option", "the second one", "a third"))
        val t = Tile(store(long))
        t.pin("d4long")
        assertFalse("the keys stack", t.let("side").truthy)
        assertTrue("${t.lines()}", "1 the first rather long option" in t.lines())
    }

    @Test fun severalPicksOrMoreThanFourOptionsOpenTheCardsPage() {
        val multi = ask("e5multi", "Which ones?", "2026-11-12T14:00:00+00:00", listOf("a", "b"), multi = true)
        val many = ask("f6many", "Which of five?", "2026-11-12T14:10:00+00:00", listOf("a", "b", "c", "d", "e"))
        for ((card, id) in listOf(multi to "e5multi", many to "f6many")) {
            val t = Tile(store(card))
            t.pin(id)
            val lines = t.lines()
            assertTrue("$id: $lines", "answer on the cards page" in lines)
            assertFalse("$id: no answer keys: $lines", lines.any { Regex("^[1-9] (?!need you)").containsMatchIn(it) })
            val taps = t.taps()
            assertTrue("$id: $taps", "url https://sg.tailb43a3a.ts.net/needs/" in taps)
            assertTrue("$id: $taps", taps.none { it.startsWith("post") })
        }
    }

    @Test fun aTapOnTheCardOpensMercurio() {
        val t = Tile(store(newest))
        assertTrue("${t.taps()}", "androidApp solutions.ormus.logos.debug" in t.taps())
    }

    @Test fun eachReplyOfTheStoreSaysWhatHappened() {
        val cases =
            mapOf(
                """{"code": 409, "ok": false, "out": {"error": "this ask is answered"}, "err": ""}""" to "already closed",
                """{"code": 403, "ok": false, "out": {"error": "this device may not answer"}, "err": ""}""" to "this phone may not answer",
                """{"code": -1, "ok": false, "out": null, "err": "timeout"}""" to "sg did not answer",
                """{"code": 400, "ok": false, "out": {"error": "via is not allowed here"}, "err": ""}""" to "refused: via is not allowed here",
            )
        for ((reply, line) in cases) {
            val t = Tile(store(newest))
            t.pin("c3new")
            t.replied("c3new", reply)
            val lines = t.lines()
            assertTrue("$reply: $lines", line in lines)
            assertTrue("the keys are live again after a reply: $lines", "1 tile" in lines)
        }
    }

    @Test fun anAnsweredCardThatLeftTheListSaysAnsweredInTheHeadline() {
        val t = Tile(store(older))
        t.replied("c3new", """{"code": 200, "ok": true, "out": {"id": "c3new", "state": "answered", "via": "launcher"}, "err": ""}""")
        val lines = t.lines()
        assertTrue("$lines", "answered" in lines)
        assertTrue("the next card shows, not picked: $lines", "Ship the old thing?" in lines && "show 1 · 2" in lines)
    }

    @Test fun aStoreThatRefusesOrDoesNotAnswerSaysSoAndShowsNoCard() {
        val refused = Tile(Json.parse("""{"ok": false, "code": 403, "out": {"error": "this device may not answer"}}""")).lines()
        assertTrue("$refused", "this phone may not read the cards" in refused)
        assertFalse("$refused", "nobody waits on you" in refused)
        val down = Tile(Json.parse("""{"ok": false, "code": -1, "out": null}""")).lines()
        assertTrue("$down", "sg doesn't answer" in down)
        assertFalse("$down", "nobody waits on you" in down)
    }

    private val ok = """{"code": 200, "ok": true, "out": {"id": "c3new", "state": "answered", "via": "launcher"}, "err": ""}"""

    @Test fun anAnsweredCardStillListedOffersNoKeys() {
        val t = Tile(store(older, newest))
        t.pin("c3new")
        t.replied("c3new", ok)
        val lines = t.lines()
        assertTrue("$lines", "answered" in lines)
        assertFalse("no key after the store said answered: $lines", "1 tile" in lines || "2 Mercurio" in lines)
        assertTrue("${t.taps()}", t.taps().none { it.startsWith("post") })
    }

    @Test fun theHeadlineForgetsAReplyAfterTwoMinutes() {
        val t = Tile(store(older))
        t.replied("c3new", ok, at = NOW - 300)
        assertFalse("${t.lines()}", "answered" in t.lines())
    }

    @Test fun aStoreThatDoesNotAnswerWinsOverARecentReply() {
        val t = Tile(Json.parse("""{"ok": false, "code": -1, "out": null}"""))
        t.replied("c3new", ok)
        val lines = t.lines()
        assertTrue("$lines", "sg doesn't answer" in lines)
        assertFalse("$lines", "answered" in lines)
    }

    @Test fun aReplyForACardSwipedAwayShowsInTheHeadline() {
        val t = Tile(store(older, newest))
        t.pin("a1old")
        t.replied("c3new", """{"code": 409, "ok": false, "out": {"error": "this ask is answered"}, "err": ""}""")
        val lines = t.lines()
        assertTrue("$lines", "Ship the old thing?" in lines)
        assertTrue("$lines", "already closed" in lines)
    }

    @Test fun anAnswerWithNoReplyForTwoMinutesFreesTheKeys() {
        val t = Tile(store(newest))
        t.pin("c3new")
        t.replied("c3new", null)
        assertTrue("on its way: ${t.lines()}", "…" in t.lines())
        t.replied("c3new", null, at = NOW - 300)
        assertTrue("the keys come back: ${t.lines()}", "1 tile" in t.lines())
    }
}
