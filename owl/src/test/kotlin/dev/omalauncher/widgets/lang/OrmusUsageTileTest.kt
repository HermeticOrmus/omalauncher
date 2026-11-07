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

    /** The feed source [f] (http with `status`: {ok, code, out}), the clock [now] and the tile's height in dp. */
    private inner class Tile(
        f: Value,
        now: Long?,
        private val height: Int?,
    ) : Host {
        private val slots = Array<Value>(program.globals.size) { Null }

        init {
            slots[slot("f")] = f
            slots[slot("now")] = now?.let { Num(it.toDouble()) } ?: Null
        }

        private fun slot(name: String) = program.globals.indexOfFirst { it.name == name }.also { assertTrue("no global $name", it >= 0) }

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

        override fun tile(name: String): Value = if (name == "tileHeight" && height != null) Num(height.toDouble()) else Null

        override fun act(
            name: String,
            args: List<Value>,
        ) {}

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
    ) = Tile(f, now, height).lines(program.root)

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
        assertEquals("1 accounts [muted]", tile(feed(half, generated = NOW - 1_700))[1])
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
                "reset, not read since · as of 2d ago [yellow]",
            ),
            tile(feed(stale, unread, reset)).drop(2),
        )
    }

    @Test fun aWindowPastItsResetOnThePhonesClockShowsNoNumberAndNoGold() {
        // The feed was written before the reset; the phone's clock is past it now.
        val later = NOW + 90_000 + 60
        assertEquals(
            listOf("Claude Ormus [foreground]", "reset, not read since [muted]", "Claude Raven [foreground]", "week Fable 73% · resets in 22h [foreground]", "bar 73 foreground@0.55"),
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
        val resets = Regex("resets [A-Z][a-z]{2} [0-9]{2}:[0-9]{2} \\[muted]")
        val wasReset = Regex("reset [A-Z][a-z]{2} [0-9]{2}:[0-9]{2} \\[muted]")
        assertEquals(listOf("Claude Corvin [foreground]", "Ultra [muted]", "as of 3h ago · now: no answer [yellow]", "week [null]", "40% [foreground]"), lines.take(5))
        assertTrue(lines[5], resets.matches(lines[5]))
        assertEquals(listOf("bar 40 foreground@0.55", "week Fable [null]", "reset [muted]"), lines.subList(6, 9))
        assertTrue(lines[9], wasReset.matches(lines[9]))
        assertEquals(
            listOf("Grok Ormus [foreground]", " [muted]", "180 credits left [muted]", "read on sg 5m ago, every 10 min [muted]", "moon: no answer [muted]", "state not saved [yellow]", "Tap an account for the service's own usage page. [muted]"),
            lines.drop(10),
        )
        assertEquals(listOf("the usage feed is off on sg [muted]"), screen(answer("""{"off": "no usage registry on this host"}""")))
        assertEquals(listOf("sg doesn't answer [muted]"), screen(Json.parse("""{"ok": false, "code": 502, "out": null}""")))
    }

    private companion object {
        const val NOW = 1_800_000_000L
        const val DAY = 86_400L
    }
}
