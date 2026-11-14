package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Omalauncher Ormus: the Ormus widgets follow the brand (ormus-brand STYLE.md): state reads through intensity (accent,
 * foreground, muted) and through a word beside it; the two state colors are the soft red of "needs you" and errors (red)
 * and the blue of an agent at work (blue), as in herdr-ormus; no color is typed in a widget, the theme's names carry them.
 */
class OrmusBrandTest {
    private val dir = java.io.File("../app/src/main/assets/ormus/widgets")

    // Profit and loss is the brand's page-scoped exception: data that needs a semantic pair, with its sign beside it.
    private val semantic = setOf("55-money-face.owl", "90-markets.owl")

    /** Every property that takes a color, up to its `;` or the closing brace. */
    private val colorProp = Regex("""\b(color|background|fill|track|stroke|border|tint)\s*:\s*([^;}]*)""")

    private fun code(file: java.io.File) = file.readLines().filterNot { it.trimStart().startsWith("//") }.map { it.replace(Regex("meta \\{.*"), "") }

    private fun widgets() = dir.listFiles().orEmpty().filter { it.extension == "owl" }.sortedBy { it.name }

    private fun colorsOf(line: String) = colorProp.findAll(line).map { it.groupValues[2] }.toList()

    @Test fun noWidgetTypesAColorInAnyForm() {
        assertTrue(widgets().size > 20)
        for (file in widgets()) for ((i, line) in code(file).withIndex()) {
            // 3, 4, 6 and 8 digits, in code and in strings alike.
            assertTrue("${file.name}:${i + 1} types a hex color: the theme's names carry the palette", !Regex("""#[0-9a-fA-F]{3,8}\b""").containsMatchIn(line))
            assertTrue("${file.name}:${i + 1} types an rgb() color", !Regex("""\brgba?\(""").containsMatchIn(line))
        }
    }

    @Test fun stateReadsThroughIntensityNotThroughDecorativeHues() {
        for (file in widgets()) for ((i, line) in code(file).withIndex()) for (value in colorsOf(line)) {
            for (name in listOf("yellow", "magenta", "cyan", "orange", "purple", "brown")) {
                assertTrue("${file.name}:${i + 1} colors with $name: state reads through intensity", !Regex("""\b$name\b""").containsMatchIn(value))
            }
            if (file.name !in semantic) assertTrue("${file.name}:${i + 1} uses green outside the profit and loss pages", !Regex("""\bgreen\b""").containsMatchIn(value))
        }
    }

    // Where red may appear: the widgets with a state to name (a feed that did not answer, an agent or a chat that needs him,
    // an invoice late, a limit full). Not a whole-line cue list, which breaks on every reworded message.
    private val mayBeRed = setOf("10-sessions", "11-board-", "12-comms", "13-comms-", "15-waiting", "16-projects", "30-mercurio", "31-needs", "45-kiosk", "50-pass", "55-money-face", "60-owed", "62-people", "64-pipeline", "70-fleet", "72-usage", "75-raven", "90-markets")

    @Test fun redOnlyMarksNeedsYouAndErrorsAndBlueOnlyAWorkingAgent() {
        for (file in widgets()) for ((i, line) in code(file).withIndex()) for (value in colorsOf(line)) {
            if (Regex("""\bred\b""").containsMatchIn(value)) assertTrue("${file.name}:${i + 1} red in a widget with no state to name", mayBeRed.any { file.name.startsWith(it) })
            if (Regex("""\bblue\b""").containsMatchIn(value)) assertTrue("${file.name}:${i + 1} blue on something that is not an agent at work", line.contains("working"))
        }
    }

    @Test fun theStrongKeyOfACardIsTheOneGoldFillAndItsTextReadsOnIt() {
        for (name in listOf("10-sessions.owl", "12-comms.owl", "31-needs.owl")) {
            val text = java.io.File(dir, name).readText()
            assertTrue("$name: strong key is gold", text.contains("background: strong ? accent : alpha(foreground, 0.09)"))
            assertTrue("$name: the text on gold is the ground", text.contains("color: strong ? background : foreground"))
        }
    }

    @Test fun oneGoldElementAViewTheScreensAndTilesShowOneCardOneHeaderAndTheTopRow() {
        for (name in listOf("10-sessions.owl", "12-comms.owl", "31-needs.owl")) {
            val calls = java.io.File(dir, name).readLines().filterNot { it.trimStart().startsWith("//") }.count { Regex("""\bNeedCard \{|\bCard \{""").containsMatchIn(it) }
            assertTrue("$name: one card in focus at a time, so one gold key (found $calls card calls)", calls <= 2)
        }
        val notifications = java.io.File(dir, "20-notifications.owl").readText()
        assertTrue("only the top row may be gold", notifications.contains("k == 0 && x.sc.score >= 80 ? accent"))
        val kiosk = java.io.File(dir, "45-kiosk.owl").readText()
        assertTrue("the kiosk header is ink while the pick's gold line shows", kiosk.contains("opening != \"\" ? foreground : note") && !kiosk.contains("""color: accent }"""))
    }

    @Test fun aLoadingTileIsQuietNotRed() {
        // `!f.ok` is true while `f` is still null: a status color that tests it names `f != null` first.
        for (file in widgets()) for ((i, line) in code(file).withIndex()) {
            for (value in colorsOf(line)) if (value.contains("!f.ok") && value.contains("red")) assertTrue("${file.name}:${i + 1} turns the loading … red", value.contains("f != null"))
        }
    }

    @Test fun theKiosksSessionAnswerIsWrappedSoTwoEqualAnswersStillCount() {
        val kiosk = java.io.File(dir, "45-kiosk.owl").readText()
        assertTrue(kiosk.contains("""printf '{\"n\":%s,\"r\":' ${'$'}{int(random(1000000000))}"""))
        assertTrue(kiosk.contains("let got = wrapped != null ? wrapped.r : null"))
        assertEquals("the kiosk still asks for one session per double tap", 1, Regex("""spun = run\(""").findAll(kiosk).count())
    }
}
