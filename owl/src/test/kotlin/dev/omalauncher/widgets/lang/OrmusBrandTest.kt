package dev.omalauncher.widgets.lang

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Omalauncher Ormus: the Ormus widgets follow the brand (ormus-brand STYLE.md): state reads through intensity (accent,
 * foreground, muted) and through a word beside it; the two state colors are the soft red of "needs you" (red) and the
 * blue of an agent at work (blue), as in herdr-ormus; no color is typed in a widget, the theme's names carry them.
 */
class OrmusBrandTest {
    private val dir = java.io.File("../app/src/main/assets/ormus/widgets")

    // Profit and loss is the brand's page-scoped exception: data that needs a semantic pair, with its sign beside it.
    private val semantic = setOf("55-money-face.owl", "90-markets.owl")

    private fun code(file: java.io.File) = file.readLines().filterNot { it.trimStart().startsWith("//") }.map { it.replace(Regex("meta \\{.*"), "") }

    private fun widgets() = dir.listFiles().orEmpty().filter { it.extension == "owl" }.sortedBy { it.name }

    @Test fun noWidgetTypesAColorOrDrawsInTheDecorativeOnes() {
        assertTrue(widgets().size > 20)
        for (file in widgets()) for ((i, line) in code(file).withIndex()) {
            assertTrue("${file.name}:${i + 1} types a hex color: the theme's names carry the palette", !Regex("""(?<![\w"'&$])#[0-9a-fA-F]{6}\b""").containsMatchIn(line.replace(Regex("\"[^\"]*\""), "\"\"")))
            for (name in listOf("yellow", "magenta", "cyan", "orange", "purple", "brown")) {
                assertTrue("${file.name}:${i + 1} colors with $name: state reads through intensity", !Regex("""color: [^;}]*\b$name\b""").containsMatchIn(line))
            }
        }
    }

    @Test fun greenIsOnlyTheProfitSideOfTheProfitAndLossPages() {
        for (file in widgets().filter { it.name !in semantic }) for ((i, line) in code(file).withIndex()) {
            assertTrue("${file.name}:${i + 1} uses green outside the profit and loss pages", !Regex("""\bcolor: [^;}]*\bgreen\b""").containsMatchIn(line) && !Regex("""color: green\b""").containsMatchIn(line))
        }
    }

    @Test fun theStrongKeyOfACardIsTheOneGoldFillAndItsTextReadsOnIt() {
        for (name in listOf("10-sessions.owl", "12-comms.owl", "31-needs.owl")) {
            val text = java.io.File(dir, name).readText()
            assertTrue("$name: strong key is gold", text.contains("background: strong ? accent : alpha(foreground, 0.09)"))
            assertTrue("$name: the text on gold is the ground", text.contains("color: strong ? background : foreground"))
        }
    }
}
