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

    @Test fun everyWidgetInstanceIsMadeThroughTheGate() {
        val engine = File("src/main/kotlin/dev/omalauncher/widgets/WidgetEngine.kt").readText()
        assertEquals("one WidgetInstance(", 1, Regex("""\bWidgetInstance\(""").findAll(engine).count())
        assertTrue(engine.contains("WidgetInstance(\n                    program,\n                    Ormus.calendarGate(context, services, name, hashes[name]),"))
    }
}
