package dev.omalauncher.widgets

import dev.omalauncher.widgets.lang.Null
import dev.omalauncher.widgets.lang.Num
import dev.omalauncher.widgets.lang.Str
import org.junit.Assert.assertEquals
import org.junit.Test

/** `android.apps` carries each app's installed version, so a widget can tell what is out of date. */
class AppVersionsTest {
    @Test
    fun versionNameAndCodeBecomeAStringAndANumber() {
        assertEquals(mapOf("version" to Str("v1.27.20"), "versionCode" to Num(12720000.0)), versionFields("v1.27.20", 12720000L))
    }

    @Test
    fun anUnreadableVersionIsNullNotMissing() {
        // Another profile's packages can't be read; the fields stay so `a.versionCode ?? 0` works in OWL.
        assertEquals(mapOf("version" to Null, "versionCode" to Null), versionFields(null, null))
    }
}
