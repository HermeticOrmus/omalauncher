package dev.omalauncher

import dev.omalauncher.theme.ThemeColors
import java.io.File
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Omalauncher Ormus: the seven planet themes (scripts/ormus-themes.py) carry the brand's planets and read on their ground. */
class OrmusThemesTest {
    private val dir = File("src/main/assets/ormus/themes")

    // The planets group of ormus-brand's tokens.json: accent, ground (deep) and surface (midnight).
    private val planets =
        mapOf(
            "sun" to Triple("#d29e3d", "#0e1830", "#14213d"),
            "moon" to Triple("#9cc0f0", "#0c1226", "#182038"),
            "mercury" to Triple("#6fc9d6", "#0a171b", "#12252b"),
            "venus" to Triple("#5fc497", "#0b1814", "#132620"),
            "mars" to Triple("#e07a48", "#1a0f0e", "#2a1816"),
            "jupiter" to Triple("#c08ae8", "#150d24", "#241838"),
            "saturn" to Triple("#8a97ee", "#0d0e12", "#1a1c24"),
        )

    private fun load(name: String) = ThemeColors.load(File(dir, "$name/colors.toml"))

    private fun luminance(hex: String): Double {
        val c = (0..2).map { Integer.parseInt(hex.substring(1 + it * 2, 3 + it * 2), 16) / 255.0 }.map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
    }

    private fun contrast(
        a: String,
        b: String,
    ): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test fun sunIsTheDefaultAndEveryThemeIsSeededAndShipped() {
        assertEquals("ormus-sun", Ormus.THEME)
        assertEquals("every folder in assets/ormus/themes is seeded", dir.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.toSet(), Ormus.THEMES.toSet())
        assertTrue("aurum stays available", "ormus-aurum" in Ormus.THEMES)
        assertTrue(Ormus.THEME in Ormus.THEMES)
    }

    @Test fun eachPlanetThemeHasItsPlanetsAccentGroundAndSurface() {
        for ((planet, tokens) in planets) {
            val c = load("ormus-$planet")
            assertEquals("$planet accent", tokens.first, c["accent"])
            assertEquals("$planet ground", tokens.second, c["background"])
            assertEquals("$planet surface", tokens.third, c["lighter_background"])
            assertEquals("$planet stays dark", "dark", c["mode"])
        }
    }

    @Test fun stateColorsAreTheSameOnEveryPlanetAsOnTheDeskAndTheAccentIsNeverAStateColor() {
        for (planet in planets.keys) {
            val c = load("ormus-$planet")
            assertEquals("needs you, as in herdr-ormus", "#e27a72", c["red"])
            assertEquals("working, as in herdr-ormus", "#6f93cf", c["blue"])
            assertTrue("$planet: no state color is the accent", listOf("red", "blue", "green", "yellow").none { c[it] == c["accent"] })
        }
    }

    @Test fun everythingAWidgetDrawsReadsOnItsGround() {
        for ((planet, tokens) in planets) {
            val c = load("ormus-$planet")
            for ((role, min) in mapOf("foreground" to 10.0, "dark_foreground" to 6.0, "accent" to 6.0, "red" to 6.0, "blue" to 4.5, "muted" to 3.0)) {
                val ratio = contrast(c.getValue(role), tokens.second)
                assertTrue("$planet $role on its ground is $ratio, wants $min", ratio >= min)
                assertTrue("$planet $role on its surface", contrast(c.getValue(role), tokens.third) >= min * 0.7)
            }
            assertTrue("$planet: a wallpaper", File(dir, "ormus-$planet/backgrounds").listFiles().orEmpty().any { it.extension == "jpg" })
        }
    }
}
