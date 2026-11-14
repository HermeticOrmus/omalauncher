package dev.omalauncher

import dev.omalauncher.theme.ThemeColors
import java.io.File
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Omalauncher Ormus: the seven planet themes (scripts/ormus-themes.py) carry the brand's planets and read on their ground. */
class OrmusThemesTest {
    private val dir = File("src/main/assets/ormus/themes")

    // The planets group of ormus-brand's tokens.json: accent, ground (deep) and surface (midnight).
    private val copied =
        mapOf(
            "sun" to Triple("#d29e3d", "#0e1830", "#14213d"),
            "moon" to Triple("#9cc0f0", "#0c1226", "#182038"),
            "mercury" to Triple("#6fc9d6", "#0a171b", "#12252b"),
            "venus" to Triple("#5fc497", "#0b1814", "#132620"),
            "mars" to Triple("#e07a48", "#1a0f0e", "#2a1816"),
            "jupiter" to Triple("#c08ae8", "#150d24", "#241838"),
            "saturn" to Triple("#8a97ee", "#0d0e12", "#1a1c24"),
        )

    /**
     * The planets as ormus-brand's tokens.json has them, when this build host has the repo (ORMUS_BRAND_TOKENS, else
     * ~/projects/ormus-brand): accent, ground and surface; else the copy above, which another test holds to the file.
     */
    private val fromTokens: Map<String, Triple<String, String, String>>? =
        listOfNotNull(System.getenv("ORMUS_BRAND_TOKENS"), System.getProperty("user.home") + "/projects/ormus-brand/tokens.json")
            .map(::File)
            .firstOrNull { it.isFile }
            ?.readText()
            ?.let { json ->
                val group = json.substring(json.indexOf("\"planets\""))
                copied.keys.associateWith { planet ->
                    val block = Regex("\"$planet\"\\s*:\\s*\\{([^}]*)}").find(group)!!.groupValues[1]
                    fun v(key: String) = Regex("\"$key\"\\s*:\\s*\"(#[0-9a-fA-F]{6})\"").find(block)!!.groupValues[1].lowercase()
                    Triple(v("gold"), v("deep"), v("midnight"))
                }
            }

    private val planets get() = fromTokens ?: copied

    @get:Rule val tmp = TemporaryFolder()

    private fun load(name: String) = ThemeColors.load(File(dir, "$name/colors.toml"))

    private fun luminance(hex: String): Double {
        val c = (0..2).map { Integer.parseInt(hex.substring(1 + it * 2, 3 + it * 2), 16) / 255.0 }.map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
    }

    /** CIE76: the straight distance between two colors in Lab. */
    private fun deltaE(
        a: String,
        b: String,
    ): Double {
        fun lab(hex: String): List<Double> {
            val c = (0..2).map { Integer.parseInt(hex.substring(1 + it * 2, 3 + it * 2), 16) / 255.0 }.map { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
            val x = 0.4124 * c[0] + 0.3576 * c[1] + 0.1805 * c[2]
            val y = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
            val z = 0.0193 * c[0] + 0.1192 * c[1] + 0.9505 * c[2]
            val f = { t: Double -> if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116 }
            val fx = f(x / 0.95047)
            val fy = f(y)
            val fz = f(z / 1.08883)
            return listOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
        }
        return lab(a).zip(lab(b)).sumOf { (p, q) -> (p - q) * (p - q) }.let(Math::sqrt)
    }

    private fun contrast(
        a: String,
        b: String,
    ): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test fun theCopiedPlanetValuesMatchTheBrandTokensWhenTheyAreHere() {
        val tokens = fromTokens ?: return
        assertEquals(copied, tokens)
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

    /** 30 is where two colors still read as two in a 12 sp word at a glance; scripts/ormus-themes.py explains it and holds the same rule. */
    @Test fun needsYouWorkingAndProfitStayApartFromTheAccentAndEachOtherOnEveryPlanet() {
        for ((planet, tokens) in planets) {
            val c = load("ormus-$planet")
            val states = listOf("red", "blue", "green").associateWith { c.getValue(it) }
            for ((name, hex) in states) {
                assertTrue("$planet $name is ${deltaE(hex, tokens.first)} from the accent", deltaE(hex, tokens.first) >= 30)
                for (ground in listOf(tokens.second, tokens.third)) assertTrue("$planet $name reads ${contrast(hex, ground)}:1", contrast(hex, ground) >= 4.5)
            }
            for ((a, b) in listOf("red" to "blue", "red" to "green", "blue" to "green")) assertTrue("$planet $a and $b are ${deltaE(states.getValue(a), states.getValue(b))} apart", deltaE(states.getValue(a), states.getValue(b)) >= 30)
        }
    }

    @Test fun sunKeepsTheDeskStatesOfHerdrOrmus() {
        val c = load("ormus-sun")
        assertEquals("needs you, as in herdr-ormus", "#e27a72", c["red"])
        assertEquals("working, as in herdr-ormus", "#6f93cf", c["blue"])
    }

    @Test fun everythingAWidgetDrawsReadsOnItsGround() {
        for ((planet, tokens) in planets) {
            val c = load("ormus-$planet")
            for ((role, min) in mapOf("foreground" to 10.0, "dark_foreground" to 6.0, "accent" to 6.0, "muted" to 3.0)) {
                val ratio = contrast(c.getValue(role), tokens.second)
                assertTrue("$planet $role on its ground is $ratio, wants $min", ratio >= min)
                assertTrue("$planet $role on its surface", contrast(c.getValue(role), tokens.third) >= min * 0.7)
            }
            assertTrue("$planet: a wallpaper", File(dir, "ormus-$planet/backgrounds").listFiles().orEmpty().any { it.extension == "jpg" })
        }
    }

    @Test fun aSeededThemeIsReseededWhenItsColorsChangeAndNotBefore() {
        val colors = "accent = \"#d29e3d\"\n".toByteArray()
        val dir = tmp.newFolder("ormus-sun")
        assertTrue("nothing there", Ormus.needsSeed(dir, colors))
        File(dir, "colors.toml").writeBytes(colors)
        assertTrue("colors with no stamp: an install from before the stamp", Ormus.needsSeed(dir, colors))
        File(dir, Ormus.SEED_STAMP).writeText(Ormus.stampOf(colors))
        assertFalse("seeded from these colors", Ormus.needsSeed(dir, colors))
        assertTrue("regenerated colors reach the phone", Ormus.needsSeed(dir, "accent = \"#9cc0f0\"\n".toByteArray()))
        File(dir, "colors.toml").delete()
        assertTrue("a stamp without colors is half a theme", Ormus.needsSeed(dir, colors))
    }

    @Test fun theStampIsADotfileSoLinuxNeverGetsIt() {
        assertTrue(Ormus.SEED_STAMP.startsWith("."))
    }
}
