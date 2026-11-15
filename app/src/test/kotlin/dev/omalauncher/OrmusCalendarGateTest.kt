package dev.omalauncher

import dev.omalauncher.widgets.lang.Json
import dev.omalauncher.widgets.lang.Value
import dev.omalauncher.widgets.lang.sha256Hex
import dev.omalauncher.widgets.runtime.Services
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * Only the today tile exactly as the build ships it reads the phone's calendars (Ormus.calendarGate): the widgets
 * folder is in the Linux guest's home, so any other widget file, or a changed today tile, sees no calendar.
 */
class OrmusCalendarGateTest {
    private val shippedBytes = File("src/main/assets/ormus/widgets/80-today.owl").readBytes()
    private val shipped = sha256Hex(shippedBytes)

    private val events = Json.parse("""{"granted": true, "events": [{"id": "1", "title": "Stand-up"}]}""")

    /** Services that answer android.calendar with [events] and android.battery with a marker; nothing else is used. */
    private val services =
        Proxy.newProxyInstance(Services::class.java.classLoader, arrayOf(Services::class.java)) { _, method, args ->
            when (method.name) {
                "androidUpdates" -> if (args[0] == "calendar") flowOf(events) else flowOf(Json.parse("""{"battery": 1}"""))
                else -> null
            }
        } as Services

    private fun first(flow: Flow<Value>?): Value = runBlocking { flow!!.first() }

    @Test fun theShippedTodayTileAloneMayReadTheCalendar() {
        assertTrue(Ormus.calendarAllowed("80-today.owl", shipped, shipped))
        assertFalse("a changed today tile", Ormus.calendarAllowed("80-today.owl", sha256Hex(shippedBytes + "\n".toByteArray()), shipped))
        assertFalse("another widget with the same text", Ormus.calendarAllowed("zz-agent.owl", shipped, shipped))
        assertFalse("a file not compiled yet", Ormus.calendarAllowed("80-today.owl", null, shipped))
        assertFalse("a build with no today tile", Ormus.calendarAllowed("80-today.owl", shipped, null))
    }

    @Test fun aWidgetThatMayNotReadItSeesNoCalendarAndEveryOtherSourceAsBefore() {
        val gated = Ormus.gate(services, allowed = false)
        assertEquals(Ormus.NO_CALENDAR, first(gated.androidUpdates("calendar")))
        assertEquals(Json.parse("""{"battery": 1}"""), first(gated.androidUpdates("battery")))
    }

    @Test fun theTodayTileGetsTheServicesThemselves() {
        assertSame(services, Ormus.gate(services, allowed = true))
        assertEquals(events, first(Ormus.gate(services, allowed = true).androidUpdates("calendar")))
    }

    @Test fun everyWidgetInstanceIsMadeThroughTheGateAndAdoptsOnlyWhereItMay() {
        val calls =
            File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
                Regex("""(?<!class )\bWidgetInstance\(""").findAll(f.readText()).map { f.name }
            }.toList()
        assertEquals("one WidgetInstance( in the app, in WidgetEngine.kt", listOf("WidgetEngine.kt"), calls)
        val engine = File("src/main/kotlin/dev/omalauncher/widgets/WidgetEngine.kt").readText()
        assertTrue(engine.contains("val calendarOk = Ormus.calendarOk(context, name, hashes[name])\n            val inst =\n                WidgetInstance(\n                    program,\n                    Ormus.gate(services, calendarOk),"))
        assertTrue(engine.contains("prev?.takeIf { Ormus.mayAdopt(it.program, calendarOk) }?.let(inst::adopt)"))
        assertFalse("no adopt that skips the check", engine.contains("prev?.let(inst::adopt)"))
        assertTrue("no picture of a calendar tile", engine.contains("Ormus.readsCalendar(program) -> Ormus.NO_CALENDAR_PREVIEW"))
        assertTrue("no theme pictures of a calendar tile", engine.contains("if (spec.program?.let(Ormus::readsCalendar) == true) return"))
    }

    private val today = dev.omalauncher.widgets.lang.Compiler.compile(String(shippedBytes))
    private val other = dev.omalauncher.widgets.lang.Compiler.compile("widget \"x\" {\n  meta { owl: 12 }\n  source b = android.battery\n  Text { text: \"x\" }\n}\n")

    @Test fun aGatedInstanceNeverTakesTheValuesOfOneThatReadTheCalendar() {
        assertTrue(Ormus.readsCalendar(today))
        assertFalse(Ormus.readsCalendar(other))
        assertFalse("the shipped tile's events into a changed tile", Ormus.mayAdopt(today, calendarOk = false))
        assertTrue("the shipped tile into itself again", Ormus.mayAdopt(today, calendarOk = true))
        assertTrue("a widget with no calendar keeps its values", Ormus.mayAdopt(other, calendarOk = false))
    }

    @Test fun theGatedValueSaysItIsGated() {
        assertEquals(Json.parse("""{"granted": false, "gated": true, "events": []}"""), Ormus.NO_CALENDAR)
    }
}
