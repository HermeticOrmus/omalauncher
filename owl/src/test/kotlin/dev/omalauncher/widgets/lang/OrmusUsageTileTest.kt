package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AI usage tile (72-usage.owl) run on made-up answers of sg's usage feed (ormus-feeds feeds/usage.py): what each
 * line says and which color it takes, on the tile and on its screen. The phone's preview shows one answer, the live one.
 */
class OrmusUsageTileTest {
    private val program = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/72-usage.owl").readText())

    /** The feed source [f] (http with `status`: {ok, code, out}), the clock [now] and the tile's height and width in dp. */
    private inner class Tile(
        f: Value,
        now: Long?,
        private val height: Int?,
        private val width: Int? = null,
    ) : Host {
        private val slots = Array<Value>(program.globals.size) { Null }

        /** What the tile asked the launcher to do (url, screen), as "name args". */
        val actions = ArrayList<String>()

        init {
            slots[slot("f")] = f
            slots[slot("now")] = now?.let { Num(it.toDouble()) } ?: Null
        }

        /** A new answer of the feed source. */
        fun answer(f: Value) {
            slots[slot("f")] = f
        }

        /** The `on fresh first` trigger, as the runtime runs it for a new feed (a null one is skipped there). */
        fun fetched() {
            val fresh = read(slot("fresh"))
            if (fresh != Null) program.triggers.single { it.kind == TriggerKind.ON }.action.run(Env.root(this), listOf(fresh, Null))
        }

        fun slot(name: String) = program.globals.indexOfFirst { it.name == name }.also { assertTrue("no global $name", it >= 0) }

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

        /** Every Text as "text [color]" and every Progress as "bar value color", in order, as the renderer walks them. */
        fun lines(nodes: List<WNode>): List<String> = ArrayList<String>().also { walk(nodes, Env.root(this), it) }

        private fun walk(
            nodes: List<WNode>,
            env: Env,
            out: MutableList<String>,
        ) {
            for (n in nodes) {
                when (n) {
                    is WNode.Element -> {
                        val color = n.props["color"]?.invoke(env)?.let { c -> (c as? Col)?.let { if (it.alpha < 1f) "${it.theme}@${it.alpha}" else "${it.theme}" } ?: "$c" }
                        when (n.kind) {
                            "Text" -> out += "${n.props["text"]!!(env)} [$color]"
                            "Progress" -> out += "bar ${n.props["value"]!!(env)} $color"
                        }
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

    private fun answer(out: String) = Json.parse("""{"ok": true, "code": 200, "out": $out}""")

    private fun feed(
        vararg accounts: String,
        top: String = "null",
        machines: String = "{}",
        errors: String = "[]",
        generated: Long = NOW,
    ) = answer("""{"generated": $generated, "accounts": [${accounts.joinToString()}], "top": $top, "machines": $machines, "errors": $errors}""")

    private fun tile(
        f: Value,
        now: Long? = NOW,
        height: Int? = null,
        width: Int? = null,
    ) = Tile(f, now, height, width).lines(program.root)

    private fun screen(
        f: Value,
        now: Long? = NOW,
    ) = Tile(f, now, null).lines(program.screen!!)

    private fun account(
        key: String,
        service: String,
        name: String,
        windows: String = "[]",
        plan: String = "null",
        note: String = "null",
        state: String = "ok",
        read: Long? = NOW,
        error: String = "null",
        limited: Boolean = false,
        peak: String = "null",
    ) = """{"key": "$key", "service": "${service.lowercase()}", "service_name": "$service", "name": "$name", "open": "https://x.test/$key",
        "plan": $plan, "windows": $windows, "note": $note, "limited": $limited, "state": "$state", "read": $read, "error": $error, "peak": $peak}"""

    private val full = account("claude-0", "Claude", "Ormus", """[{"label": "week", "percent": 100, "resets": ${NOW + 90_000}, "active": true}, {"label": "week Fable", "percent": 17, "resets": ${NOW + 90_000}}]""", "\"Ultra\"", limited = true, peak = "100")
    private val half = account("claude-1", "Claude", "Raven", """[{"label": "week", "percent": 57, "resets": ${NOW + 2 * DAY}}, {"label": "week Fable", "percent": 73, "resets": ${NOW + 2 * DAY}}]""", "\"Ultra\"", peak = "73")
    private val high = account("codex-2", "ChatGPT", "Raven", """[{"label": "week", "percent": 85, "resets": ${NOW + 5 * DAY}}]""", "\"Pro Lite\"", peak = "85")
    private val credits = account("grok-3", "Grok", "Ormus", note = "\"180 credits left\"")
    private val noUsage = account("grok-4", "Grok", "Raven", plan = "\"SuperGrok Pro\"", note = "\"xAI gives no usage for this plan\"")
    private val topFull = """{"key": "claude-0", "name": "Claude Ormus", "label": "week", "percent": 100, "resets": ${NOW + 90_000}}"""

    @Test fun eachAccountShowsItsFullestWindowAndWhenItResets() {
        assertEquals(
            listOf(
                "ai usage [null]",
                "5 accounts [muted]",
                "Claude Ormus [accent]",
                "week 100% · resets in 1d [foreground]",
                "bar 100 red",
                "Claude Raven [foreground]",
                "week Fable 73% · resets in 2d [foreground]",
                "bar 73 foreground@0.55",
                "ChatGPT Raven [foreground]",
                "week 85% · resets in 5d [foreground]",
                "bar 85 yellow",
                "Grok Ormus [foreground]",
                "180 credits left [muted]",
                "Grok Raven [foreground]",
                "xAI gives no usage for this plan [muted]",
            ),
            tile(feed(full, half, high, credits, noUsage, top = topFull)),
        )
    }

    @Test fun theHeaderSaysWhenTheFeedCannotBeShown() {
        assertEquals(listOf("ai usage [null]", "… [muted]"), tile(Null))
        assertEquals(listOf("ai usage [null]", "sg doesn't answer [red]"), tile(Json.parse("""{"ok": false, "code": 502, "out": null}""")))
        assertEquals(listOf("ai usage [null]", "off on sg [red]"), tile(answer("""{"off": "no usage registry on this host"}""")))
        assertEquals(listOf("ai usage [null]", "0 accounts [muted]", "no account listed on sg [muted]"), tile(feed()))
        assertEquals(listOf("ai usage [null]", "registry not readable [yellow]"), tile(feed(errors = """["registry not readable"]""")))
        // A page in the feed's place (json gives its text), or JSON that is not the feed: said, never "0 accounts".
        for (out in listOf("\"<html>sign in</html>\"", "null", "[1, 2]", "{\"generated\": 1}")) {
            assertEquals(out, listOf("ai usage [null]", "not the usage feed [red]"), tile(answer(out)))
            assertEquals(out, listOf("sg's answer is not the usage feed [muted]"), screen(answer(out)))
        }
    }

    @Test fun anOldFeedSaysHowOldAndMachinesNotReachedAreNamed() {
        assertEquals("as of 1h ago [yellow]", tile(feed(half, generated = NOW - 3_600))[1])
        // Under 30 minutes old is one or two missed runs of a 10-minute feed: no word.
        assertEquals("1 account [muted]", tile(feed(half, generated = NOW - 1_700))[1])
        assertEquals("moon, mercury not reached [muted]", tile(feed(half, machines = """{"moon": "no answer", "mercury": "no answer"}"""))[1])
        assertEquals("state not saved [yellow]", tile(feed(half, machines = """{"moon": "no answer"}""", errors = """["state not saved"]"""))[1])
    }

    @Test fun rowsThatDoNotFitAreCounted() {
        val five = arrayOf(full, half, high, credits, noUsage)
        // (100 - 40) / 32: one row of accounts; a height not known yet counts as 272 dp, room for 7.
        val small = tile(feed(*five, top = topFull), height = 100)
        assertEquals("+4 more [muted]", small[1])
        assertEquals(listOf("Claude Ormus [accent]", "week 100% · resets in 1d [foreground]", "bar 100 red"), small.drop(2))
        assertEquals("+2 more [muted]", tile(feed(*five, top = topFull), height = 140)[1])
        assertEquals("5 accounts [muted]", tile(feed(*five, top = topFull), height = 272)[1])
        val eight = five + arrayOf(credits, noUsage, credits).mapIndexed { i, a -> a.replace("\"key\": \"grok-", "\"key\": \"more$i-") }
        assertEquals("+1 more [muted]", tile(feed(*eight, top = topFull))[1])
        assertEquals("+1 more [muted]", tile(feed(*eight, top = topFull), height = 272)[1])
    }

    @Test fun oldAndUnreadAccountsSayWhy() {
        val stale = account("claude-5", "Claude", "Corvin", """[{"label": "week", "percent": 40, "resets": ${NOW + 3 * DAY}}]""", state = "stale", read = NOW - 3 * 3_600, error = "\"no answer\"", peak = "40")
        val unread = account("claude-6", "Claude", "Dev", state = "unread", read = null, error = "\"token ended\"")
        val reset = account("claude-7", "Claude", "Old", """[{"label": "week", "percent": null, "resets": ${NOW - 60}, "reset": true}]""", state = "stale", read = NOW - 2 * DAY, error = "\"no answer\"")
        assertEquals(
            listOf(
                "Claude Corvin [muted]",
                "week 40% · resets in 3d · as of 3h ago [yellow]",
                "bar 40 foreground@0.55",
                "Claude Dev [muted]",
                "token ended [yellow]",
                "Claude Old [muted]",
                "week reset · as of 2d ago [yellow]",
            ),
            tile(feed(stale, unread, reset)).drop(2),
        )
    }

    @Test fun aWindowPastItsResetOnThePhonesClockShowsNoNumberAndNoGold() {
        // The feed was written before the reset; the phone's clock is past it now.
        val later = NOW + 90_000 + 60
        assertEquals(
            listOf("Claude Ormus [foreground]", "week reset [muted]", "Claude Raven [foreground]", "week Fable 73% · resets in 22h [foreground]", "bar 73 foreground@0.55"),
            tile(feed(full, half, top = topFull), now = later).drop(2),
        )
        // Of two windows, the one not reset yet shows.
        val two = account("claude-8", "Claude", "Two", """[{"label": "session", "percent": 90, "resets": ${NOW + 600}}, {"label": "week", "percent": 30, "resets": ${NOW + DAY}}]""", peak = "90")
        assertEquals(listOf("week 30% · resets in 23h [foreground]", "bar 30 foreground@0.55"), tile(feed(two), now = NOW + 700).drop(3))
    }

    @Test fun aLimitBelowAHundredIsRedOnlyOnTheFullestWindow() {
        val limited = account("codex-9", "ChatGPT", "Team", """[{"label": "5h", "percent": 90, "resets": ${NOW + 3_600}}, {"label": "week", "percent": 40, "resets": ${NOW + 4 * DAY}}]""", limited = true, peak = "90")
        assertEquals("bar 90 red", tile(feed(limited))[4])
        assertEquals(listOf("5h [null]", "90% [foreground]"), screen(feed(limited)).subList(2, 4))
        assertEquals("bar 90 red", screen(feed(limited))[5])
        assertEquals("bar 40 foreground@0.55", screen(feed(limited))[9])
        assertEquals(listOf("bar 85 yellow"), screen(feed(high)).filter { it.startsWith("bar") })
        assertEquals(listOf("bar 100 red", "bar 17 foreground@0.55"), screen(feed(full, top = topFull)).filter { it.startsWith("bar") })
    }

    @Test fun theScreenListsEveryWindowNoteAndWhatWasNotRead() {
        val stale = account("claude-5", "Claude", "Corvin", """[{"label": "week", "percent": 40, "resets": ${NOW + 3 * DAY}}, {"label": "week Fable", "percent": null, "resets": ${NOW - 60}, "reset": true}]""", "\"Ultra\"", state = "stale", read = NOW - 3 * 3_600, error = "\"no answer\"", peak = "40")
        val lines = screen(feed(stale, credits, machines = """{"moon": "no answer"}""", errors = """["state not saved"]""", generated = NOW - 300))
        assertEquals(listOf("Claude Corvin [foreground]", "Ultra [muted]", "as of 3h ago · now: no answer [yellow]", "week [null]", "40% [foreground]"), lines.take(5))
        assertEquals("resets ${at(NOW + 3 * DAY)} [muted]", lines[5])
        assertEquals(listOf("bar 40 foreground@0.55", "week Fable [null]", "reset [muted]"), lines.subList(6, 9))
        assertEquals("reset ${at(NOW - 60)} [muted]", lines[9])
        assertEquals(
            listOf("Grok Ormus [foreground]", " [muted]", "180 credits left [muted]", "read on sg 5m ago, every 10 min [muted]", "moon: no answer [muted]", "state not saved [yellow]", "Tap an account for the service's own usage page. [muted]"),
            lines.drop(10),
        )
        assertEquals(listOf("the usage feed is off on sg [muted]"), screen(answer("""{"off": "no usage registry on this host"}""")))
        assertEquals(listOf("sg doesn't answer [muted]"), screen(Json.parse("""{"ok": false, "code": 502, "out": null}""")))
    }

    @Test fun aFullWindowIsRedAndFromEightyYellowOnTheTileAndTheScreen() {
        fun one(percent: Int) = account("codex-$percent", "ChatGPT", "P$percent", """[{"label": "week", "percent": $percent, "resets": ${NOW + DAY}}]""", peak = "$percent")
        // Not limited (the service lets it go on), still red at 100; 79 is plain, 80 is yellow.
        for ((percent, color) in listOf(100 to "red", 80 to "yellow", 79 to "foreground@0.55")) {
            val f = feed(one(percent))
            assertEquals("$percent", "bar $percent $color", tile(f)[4])
            assertEquals("$percent", listOf("bar $percent $color"), screen(f).filter { it.startsWith("bar") })
        }
    }

    @Test fun theAccountWithTheFullestWindowHasItsNameInGold() {
        val titles = listOf("Claude Ormus [accent]", "Claude Raven [foreground]", "ChatGPT Raven [foreground]")
        assertEquals(titles, screen(feed(full, half, high, top = topFull)).filter { it.startsWith("Claude") || it.startsWith("ChatGPT") })
        // A top window with no reset time stays gold; so does an account read 3 h ago (its row says how old).
        val noReset = """{"key": "claude-0", "name": "Claude Ormus", "label": "week", "percent": 100, "resets": null}"""
        assertEquals("Claude Ormus [accent]", tile(feed(full, half, top = noReset))[2])
        val staleFull = full.replace("\"state\": \"ok\"", "\"state\": \"stale\"").replace("\"read\": $NOW", "\"read\": ${NOW - 3 * 3_600}")
        assertEquals(listOf("Claude Ormus [accent]", "week 100% · resets in 1d · as of 3h ago [yellow]"), tile(feed(staleFull, top = topFull)).subList(2, 4))
        assertEquals("Claude Ormus [accent]", screen(feed(staleFull, top = topFull))[0])
    }

    @Test fun aWindowPastItsResetOnThePhonesClockSaysResetOnTheScreen() {
        val later = NOW + 90_000 + 60
        val lines = screen(feed(full, top = topFull), now = later)
        assertEquals(listOf("Claude Ormus [foreground]", "Ultra [muted]", "week [null]", "reset [muted]", "reset ${at(NOW + 90_000)} [muted]"), lines.take(5))
        assertEquals(emptyList<String>(), lines.filter { it.startsWith("bar") })
    }

    @Test fun aWindowTheFeedSaysHasResetSaysResetWhenThePhonesClockIsBehind() {
        // sg's clock passed the reset and the feed says so; the phone's clock is 90 s behind sg's and has not.
        val behind = account("claude-7", "Claude", "Behind", """[{"label": "week", "percent": null, "resets": ${NOW - 30}, "reset": true}]""")
        assertEquals(listOf("Claude Behind [foreground]", "week reset [muted]"), tile(feed(behind), now = NOW - 90).drop(2))
        assertEquals(
            listOf("Claude Behind [foreground]", " [muted]", "week [null]", "reset [muted]", "reset ${at(NOW - 30)} [muted]"),
            screen(feed(behind), now = NOW - 90).take(5),
        )
    }

    @Test fun theFooterSaysHowOldTheFeedIs() {
        assertEquals("read on sg just now, every 10 min [muted]", screen(feed(half)).first { it.startsWith("read on sg") })
        assertEquals("read on sg 29m ago, every 10 min [muted]", screen(feed(half, generated = NOW - 1_740)).first { it.startsWith("read on sg") })
        assertEquals("read on sg 2h ago, every 10 min [yellow]", screen(feed(half, generated = NOW - 7_200)).first { it.startsWith("read on sg") })
        // 30 min is one late run and no word; one second more is late.
        assertEquals("1 account [muted]", tile(feed(half, generated = NOW - 1_800))[1])
        assertEquals("as of 30m ago [yellow]", tile(feed(half, generated = NOW - 1_801))[1])
    }

    @Test fun beforeTheClockIsReadNoLineSaysNull() {
        val five = feed(full, half, high, credits, noUsage, top = topFull)
        val lines = tile(five, now = null) + screen(five, now = null)
        assertTrue(lines.toString(), lines.none { "null" in it.substringBefore(" [") })
        assertEquals(listOf("Claude Ormus [accent]", "week 100% [foreground]", "bar 100 red"), tile(five, now = null).subList(2, 5))
        assertEquals("read on sg …, every 10 min [muted]", screen(five, now = null).first { it.startsWith("read on sg") })
    }

    @Test fun theHeaderCountsHiddenRowsNextToItsMessage() {
        val five = arrayOf(full, half, high, credits, noUsage)
        assertEquals("+4 · as of 2h ago [yellow]", tile(feed(*five, top = topFull, generated = NOW - 7_200), height = 100)[1])
        assertEquals("+4 · moon not reached [muted]", tile(feed(*five, top = topFull, machines = """{"moon": "no answer"}"""), height = 100)[1])
        assertEquals("+4 · state not saved [yellow]", tile(feed(*five, top = topFull, errors = """["state not saved"]"""), height = 100)[1])
        // A late feed is said before its problems, and problems before machines not reached.
        assertEquals("as of 2h ago [yellow]", tile(feed(half, errors = """["state not saved"]""", machines = """{"moon": "no answer"}""", generated = NOW - 7_200))[1])
        assertEquals("1 account [muted]", tile(feed(half))[1])
        assertEquals("2 accounts [muted]", tile(feed(half, high))[1])
    }

    @Test fun aTapOpensTheServicesUsagePageAndTheTileItsScreen() {
        val t = Tile(feed(full, half, credits, top = topFull), NOW, null)
        assertEquals(listOf("url https://x.test/claude-0", "url https://x.test/claude-1", "url https://x.test/grok-3"), t.taps(program.root))
        assertEquals(listOf("url https://x.test/claude-0", "url https://x.test/claude-1", "url https://x.test/grok-3"), t.taps(program.screen!!))
        t.actions.clear()
        program.onTap!!.run(Env.root(t), emptyList())
        assertEquals(listOf("screen "), t.actions)
    }

    @Test fun whenSgDoesNotAnswerTheLastNumbersStayWithTheirAge() {
        val t = Tile(feed(full, half, top = topFull, generated = NOW - 7_200), NOW, null)
        t.fetched()
        val rows = t.lines(program.root).drop(2)
        // No answer: the same rows, the header in red with the numbers' age.
        t.answer(Json.parse("""{"ok": false, "code": 502, "out": "bad gateway"}"""))
        assertEquals("sg doesn't answer · as of 2h ago [red]", t.lines(program.root)[1])
        assertEquals(rows, t.lines(program.root).drop(2))
        val screen = t.lines(program.screen!!)
        assertEquals("sg doesn't answer; these are its last numbers [yellow]", screen[0])
        assertEquals("Claude Ormus [accent]", screen[1])
        assertEquals("read on sg 2h ago, every 10 min [yellow]", screen.first { it.startsWith("read on sg") })
        // A page in the feed's place: the same.
        t.answer(answer("\"<html>sign in</html>\""))
        assertEquals("not the usage feed · as of 2h ago [red]", t.lines(program.root)[1])
        assertEquals(rows, t.lines(program.root).drop(2))
        // A fresh feed under 30 min old: no age next to the failure.
        val u = Tile(feed(half), NOW, null)
        u.fetched()
        u.answer(Json.parse("""{"ok": false, "code": 502, "out": null}"""))
        assertEquals(listOf("ai usage [null]", "sg doesn't answer [red]", "Claude Raven [foreground]"), u.lines(program.root).take(3))
        // "Off" is said and is not kept: the last feed with accounts stays the one kept.
        t.answer(answer("""{"off": "no usage registry on this host"}"""))
        assertEquals(listOf("ai usage [null]", "off on sg [red]"), t.lines(program.root))
        t.fetched()
        t.answer(Json.parse("""{"ok": false, "code": 502, "out": null}"""))
        assertEquals(rows, t.lines(program.root).drop(2))
    }

    @Test fun aWindowWithNoNumberAndNoResetIsUnknownNotReset() {
        val unknown = account("claude-9", "Claude", "Far", """[{"label": "week", "percent": null, "resets": null}]""", state = "stale", read = NOW - 2 * DAY, error = "\"no answer\"")
        assertEquals(listOf("Claude Far [muted]", "use unknown · as of 2d ago [yellow]"), tile(feed(unknown)).drop(2))
        val lines = screen(feed(unknown))
        assertEquals(listOf("Claude Far [foreground]", " [muted]", "as of 2d ago · now: no answer [yellow]", "week [null]", "unknown [muted]", " [muted]"), lines.take(6))
    }

    @Test fun aReadingWithNoTimeOrAheadOfThePhonesClockSaysNoAge() {
        val week = """[{"label": "week", "percent": 40, "resets": ${NOW + 3 * DAY}}]"""
        val noTime = account("claude-5", "Claude", "Corvin", week, state = "stale", read = null, error = "\"no answer\"", peak = "40")
        assertEquals("week 40% · resets in 3d [yellow]", tile(feed(noTime))[3])
        assertEquals("now: no answer [yellow]", screen(feed(noTime))[2])
        val ahead = account("claude-5", "Claude", "Corvin", week, state = "stale", read = NOW + 120, error = "\"no answer\"", peak = "40")
        assertEquals("week 40% · in 3d · as of just now [yellow]", tile(feed(ahead))[3])
        assertEquals("as of just now · now: no answer [yellow]", screen(feed(ahead))[2])
    }

    @Test fun aRowTooLongForTheTilesWidthDropsTheWordResetsThenTheResetTime() {
        // 238 dp is the 4x3 size on the phone (654 px at density 2.75); about 6.25 dp a character, 12 dp padding a side.
        assertEquals(
            listOf("week 100% · in 1d [foreground]", "week Fable 73% [foreground]", "week 85% · in 5d [foreground]"),
            tile(feed(full, half, high, top = topFull), width = 238).filter { it.startsWith("week") },
        )
        // The edges: 262 dp has room for "resets", 218 dp for the time alone.
        assertEquals("week 100% · resets in 1d [foreground]", tile(feed(full), width = 262)[3])
        assertEquals("week 100% · in 1d [foreground]", tile(feed(full), width = 261)[3])
        assertEquals("week 100% · in 1d [foreground]", tile(feed(full), width = 218)[3])
        assertEquals("week 100% [foreground]", tile(feed(full), width = 217)[3])
    }

    @Test fun resetTimesAreInEnglishWhateverThePhonesLanguage() {
        val before = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es"))
            assertEquals("resets ${at(NOW + 2 * DAY)} [muted]", screen(feed(half))[4])
        } finally {
            java.util.Locale.setDefault(before)
        }
    }

    /** The screen's reset time: the phone's zone, in English. */
    private fun at(t: Long) =
        java.time.format.DateTimeFormatter
            .ofPattern("EEE d HH:mm", java.util.Locale.ENGLISH)
            .format(java.time.Instant.ofEpochSecond(t).atZone(java.time.ZoneId.systemDefault()))

    private companion object {
        const val NOW = 1_800_000_000L
        const val DAY = 86_400L
    }
}
