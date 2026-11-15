package dev.omalauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * The phone's calendars are the today tile's alone (Ormus.GUEST_CALENDAR): the calendar permission is the whole app's,
 * so the Linux guest's `omalauncher calendar` must answer "not allowed" here. PhoneBridge needs a Context, so this pins
 * the guard that opens its calendar command, as a rebase onto a new upstream must keep it.
 */
class OrmusGuestCalendarTest {
    @Test fun theGuestDoesNotReadTheCalendar() {
        assertFalse(Ormus.GUEST_CALENDAR)
    }

    @Test fun theGuestsCalendarCommandAnswersNotAllowedFirst() {
        val lines = File("src/main/kotlin/dev/omalauncher/linux/PhoneBridge.kt").readLines()
        val at = lines.indices.filter { lines[it].trim() == "private suspend fun calendar(days: String?): Answer {" }
        assertEquals("one calendar command", 1, at.size)
        assertEquals("if (!Ormus.GUEST_CALENDAR) return Answer.Denied(GUEST_NO_CALENDAR)", lines[at.single() + 1].trim())
    }
}
