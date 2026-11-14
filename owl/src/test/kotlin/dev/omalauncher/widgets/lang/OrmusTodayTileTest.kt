package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The today tile (80-today.owl) run on made-up answers of the today feed (ormus-feeds feeds/today.py): what its lines
 * say and which color they take. A calendar the feed could not read, or an old copy of the feed, must show on the tile:
 * "no meetings through tomorrow" is said only when every calendar was read and the copy is fresh.
 */
class OrmusTodayTileTest {
    private val program = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/80-today.owl").readText())

    /** The feed source [f] (http with `status`: {ok, code, out}) and the clock [now]. */
    private inner class Tile(
        f: Value,
        now: Long?,
    ) : Host {
        private val slots = Array<Value>(program.globals.size) { Null }

        init {
            slots[slot("f")] = f
            slots[slot("now")] = now?.let { Num(it.toDouble()) } ?: Null
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

        override fun tile(name: String): Value = Null

        override fun act(
            name: String,
            args: List<Value>,
        ) {}

        /** Every Text as "text [color]", in order, as the renderer walks them. */
        fun lines(nodes: List<WNode>): List<String> = ArrayList<String>().also { walk(nodes, Env.root(this), it) }

        private fun walk(
            nodes: List<WNode>,
            env: Env,
            out: MutableList<String>,
        ) {
            for (n in nodes) {
                when (n) {
                    is WNode.Element -> {
                        val color = n.props["color"]?.invoke(env)?.let { c -> (c as? Col)?.let { "${it.theme}" } ?: "$c" }
                        if (n.kind == "Text") out += "${n.props["text"]!!(env)} [$color]"
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

    private fun feed(
        events: String = "[]",
        errors: String = "{}",
        generated: Long = NOW,
        practice: String = "null",
    ) = Json.parse("""{"ok": true, "code": 200, "out": {"generated": $generated, "events": $events, "practice": $practice, "errors": $errors}}""")

    private fun tile(
        f: Value,
        now: Long? = NOW,
    ) = Tile(f, now).lines(program.root)

    private val meeting = """{"start_unix": ${NOW + 2 * HOUR}, "title": "Stand-up", "join_url": null}"""

    @Test fun aFreshFeedWithEveryCalendarReadSaysNoMeetings() {
        assertEquals(listOf("today · 0 ahead [muted]", "no meetings through tomorrow [muted]"), tile(feed()))
    }

    @Test fun aFreshFeedListsItsMeetings() {
        val lines = tile(feed(events = "[$meeting]"))
        assertEquals("today · 1 ahead [muted]", lines[0])
        assertTrue(lines.toString(), lines.any { it.startsWith("Stand-up") })
    }

    @Test fun aCalendarThatFailedIsRedAndNeverReadsAsAFreeDay() {
        val errors = """{"calendar:first@example.org": "refresh failed (HTTP 400: invalid_grant)"}"""
        assertEquals(listOf("today · 0 ahead · a calendar unreadable [red]", "meetings may be missing [muted]"), tile(feed(errors = errors)))
    }

    @Test fun twoFailedCalendarsAreCountedAndTheMeetingsThatWereReadStillShow() {
        val errors = """{"calendar:first@example.org": "x", "calendar:second@example.org": "y"}"""
        val lines = tile(feed(events = "[$meeting]", errors = errors))
        assertEquals("today · 1 ahead · 2 calendars unreadable [red]", lines[0])
        assertTrue(lines.toString(), lines.any { it.startsWith("Stand-up") })
    }

    @Test fun aFailedPracticeAloneIsNotACalendarError() {
        assertEquals(listOf("today · 0 ahead [muted]", "no meetings through tomorrow [muted]"), tile(feed(errors = """{"practice": "timeout"}""")))
    }

    @Test fun aCopyOlderThanTwentyMinutesSaysHowOld() {
        assertEquals(
            listOf("today · 0 ahead · as of 25m ago [foreground]", "no meetings in this copy [muted]"),
            tile(feed(generated = NOW - 25 * 60)),
        )
        assertEquals("today · 0 ahead · as of 3h ago [foreground]", tile(feed(generated = NOW - 3 * HOUR - 60))[0])
    }

    @Test fun aCopyOfTwentyMinutesOrLessIsFresh() {
        assertEquals("today · 0 ahead [muted]", tile(feed(generated = NOW - 20 * 60))[0])
    }

    @Test fun aFailedCalendarOutranksAnOldCopy() {
        val errors = """{"calendar:first@example.org": "x"}"""
        assertEquals("today · 0 ahead · a calendar unreadable [red]", tile(feed(errors = errors, generated = NOW - 3 * HOUR))[0])
    }

    @Test fun beforeTheClockTicksNothingIsOld() {
        assertEquals("today · 0 ahead [muted]", tile(feed(generated = NOW - 3 * HOUR), now = null)[0])
    }

    @Test fun sgThatDoesNotAnswerIsRedAndLoadingStaysQuiet() {
        assertEquals(listOf("today · sg doesn't answer [red]"), tile(Json.parse("""{"ok": false, "code": -1, "out": null}""")))
        assertEquals(listOf("today … [muted]"), tile(Null))
    }

    private companion object {
        const val NOW = 1_800_000_000L
        const val HOUR = 3_600L
    }
}
