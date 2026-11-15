package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The today tile (80-today.owl) run on made-up phone calendars (`android.calendar`: {granted, events}), the phone's
 * list of calendars to show, and the today feed (for the practice only): what its lines say, their colors, and what a
 * tap asks the launcher to do. Meetings come from the phone alone, so a sign-in that ran out elsewhere cannot hide one.
 */
class OrmusTodayTileTest {
    private val program = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/80-today.owl").readText())

    /** The calendar source [cal], the phone's list of calendar names [shown], the feed [f] and the clock [now]. */
    private inner class Tile(
        cal: Value,
        shown: Value,
        f: Value,
        now: Long?,
    ) : Host {
        private val slots = Array<Value>(program.globals.size) { Null }

        /** What the tile asked the launcher to do, as "name args". */
        val actions = ArrayList<String>()

        init {
            slots[slot("cal")] = cal
            slots[slot("shown")] = shown
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
        ) {
            actions += "$name ${args.joinToString { if (it is Obj) "event ${it.fields["id"]}" else "$it" }}".trim()
        }

        /** Every Text as "text [color]", in order, as the renderer walks them. */
        fun lines(): List<String> = ArrayList<String>().also { walk(program.root, Env.root(this), it) }

        /** Every onTap in render order, run once each; the actions they asked for. */
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

    private fun event(
        id: String,
        start: Long,
        title: String = id,
        calendar: String = "main@example.org",
        allDay: Boolean = false,
        location: String = "",
    ) = """{"id": "$id", "eventId": "$id", "title": "$title", "start": $start, "end": ${start + HOUR}, "allDay": $allDay,
        "calendar": "$calendar", "color": "#336699", "location": "$location"}"""

    private fun calendar(
        vararg events: String,
        granted: Boolean = true,
    ) = Json.parse("""{"granted": $granted, "events": [${events.joinToString()}]}""")

    private fun feed(practice: String = "null") = Json.parse("""{"ok": true, "code": 200, "out": {"generated": $NOW, "events": [], "practice": $practice, "errors": {}}}""")

    private val down = Json.parse("""{"ok": false, "code": -1, "out": null}""")

    private val onlyMain = Json.parse("""["main@example.org", "work@example.org"]""")

    private fun tile(
        cal: Value,
        shown: Value = onlyMain,
        f: Value = feed(),
        now: Long? = NOW,
    ) = Tile(cal, shown, f, now)

    @Test fun beforeTheCalendarIsAllowedATapAsksForIt() {
        val t = tile(calendar(granted = false))
        assertEquals(listOf("today · tap to read the calendar [foreground]"), t.lines())
        assertEquals(listOf("calendar request"), t.taps())
    }

    @Test fun whileTheCalendarLoadsTheTileIsQuiet() {
        assertEquals(listOf("today … [muted]"), tile(Null).lines())
    }

    @Test fun anAllowedCalendarWithNothingAheadSaysSo() {
        assertEquals(listOf("today · 0 ahead [muted]", "no meetings through tomorrow [muted]"), tile(calendar()).lines())
    }

    @Test fun meetingsOfTheShownCalendarsTodayAndTomorrowShowWithACountdown() {
        val lines =
            tile(
                calendar(
                    event("soon", NOW + 10 * MINUTE, "Stand-up"),
                    event("later", NOW + 2 * HOUR, "Review", calendar = "work@example.org"),
                ),
            ).lines()
        assertEquals("today · 2 ahead [muted]", lines[0])
        assertTrue(lines.toString(), lines.contains("Stand-up [null]") && lines.contains("in 10m [foreground]"))
        assertTrue(lines.toString(), lines.contains("Review [null]") && lines.contains("in 2h [muted]"))
    }

    @Test fun allDayEventsOtherCalendarsAndLaterDaysAreLeftOut() {
        val lines =
            tile(
                calendar(
                    event("holiday", NOW, "Holiday", allDay = true),
                    event("family", NOW + HOUR, "Dinner", calendar = "Family"),
                    event("far", NOW + 3 * DAY, "Next week"),
                    event("kept", NOW + HOUR, "Call"),
                ),
            ).lines()
        assertEquals("today · 1 ahead [muted]", lines[0])
        assertTrue(lines.toString(), lines.contains("Call [null]"))
        assertTrue(lines.toString(), lines.none { it.startsWith("Holiday") || it.startsWith("Dinner") || it.startsWith("Next week") })
    }

    @Test fun withoutThePhonesListEveryCalendarShows() {
        val lines = tile(calendar(event("family", NOW + HOUR, "Dinner", calendar = "Family")), shown = Null).lines()
        assertEquals("today · 1 ahead [muted]", lines[0])
    }

    @Test fun aMeetingOnNowSaysNowAndAnUntitledOneSaysSo() {
        val lines = tile(calendar(event("on", NOW - 5 * MINUTE, ""))).lines()
        assertTrue(lines.toString(), lines.contains("(no title) [null]") && lines.contains("now [foreground]"))
    }

    @Test fun aTapJoinsALinkPlaceAndOpensAnyOtherInTheCalendarApp() {
        val t = tile(calendar(event("zoom", NOW + HOUR, location = "https://zoom.us/j/1"), event("room", NOW + 2 * HOUR, location = "Room 4")))
        assertEquals(listOf("calendar open", "url https://zoom.us/j/1", "calendar open, event room"), t.taps())
    }

    @Test fun thePracticeComesFromTheFeedAndAFeedThatIsDownIsNoError() {
        val withPractice = tile(calendar(), f = feed("""{"title": "Respiración"}""")).lines()
        assertEquals("practice · Respiración [accent]", withPractice.last())
        assertEquals(listOf("today · 0 ahead [muted]", "no meetings through tomorrow [muted]"), tile(calendar(), f = down).lines())
    }

    @Test fun theTileShowsAsManyMeetingsAsItHasRoomFor() {
        val many = (1..9).map { event("m$it", NOW + it * MINUTE * 20, "M$it") }.toTypedArray()
        val lines = tile(calendar(*many)).lines()
        assertEquals("today · 9 ahead [muted]", lines[0])
        // The default height of 204 dp has room for five rows: (204 - 74) / 25.
        assertEquals(5, lines.count { Regex("""^M\d \[null]$""").matches(it) })
    }

    private companion object {
        const val NOW = 1_800_000_000L
        const val MINUTE = 60L
        const val HOUR = 3_600L
        const val DAY = 86_400L
    }
}
