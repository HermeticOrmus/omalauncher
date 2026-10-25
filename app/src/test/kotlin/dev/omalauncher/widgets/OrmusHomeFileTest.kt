package dev.omalauncher.widgets

import dev.omalauncher.theme.Toml
import org.junit.Assert.assertEquals
import org.junit.Test

/** Omalauncher Ormus: layout.toml keeps each page's space, so an edit to it can't fold the spaces into now. */
class OrmusHomeFileTest {
    private val layout =
        HomeLayout(
            6,
            listOf(
                TilePage(listOf(WidgetTile("80-today.owl", Cell(0, 0, 6, 2))), space = 0),
                FacePage("money-face.owl", space = 1),
                TilePage(listOf(WidgetTile("60-owed.owl", Cell(0, 0, 6, 4))), space = 1),
                TilePage(listOf(WidgetTile("62-people.owl", Cell(0, 0, 6, 5))), space = 1),
                TilePage(listOf(WidgetTile("75-raven.owl", Cell(0, 0, 6, 7))), space = 2),
            ),
        )

    @Test fun readsTheSpacesItWrites() {
        val text = Toml.write(HomeFile.encode(emptyMap(), layout), HomeFile.HEADER)
        val decoded = HomeFile.decode(Toml.parse(text), 6)
        assertEquals(emptyList<String>(), decoded.problems)
        assertEquals(layout, decoded.layout)
        assertEquals(listOf(1, 100, 101, 102, 201), HomeFile.numbers(decoded.layout.pages))
    }

    @Test fun aPageWithoutASpaceIsInThePreviousPagesSpace() {
        val text =
            """
            [[page]]
            tiles = [ { widget = "80-today.owl" } ]
            [[page]]
            space = "money"
            tiles = [ { widget = "60-owed.owl" } ]
            [[page]]
            tiles = [ { widget = "62-people.owl" } ]
            """.trimIndent()
        val decoded = HomeFile.decode(Toml.parse(text), 6)
        assertEquals(emptyList<String>(), decoded.problems)
        assertEquals(listOf(1, 101, 102), HomeFile.numbers(decoded.layout.pages))
    }

    @Test fun anUnknownSpaceIsSaidAndThePageStaysWhereItWas() {
        val text =
            """
            [[page]]
            tiles = [ { widget = "80-today.owl" } ]
            [[page]]
            space = "fun"
            tiles = [ { widget = "60-owed.owl" } ]
            """.trimIndent()
        val decoded = HomeFile.decode(Toml.parse(text), 6)
        assertEquals(listOf(1, 2), HomeFile.numbers(decoded.layout.pages))
        assertEquals(1, decoded.problems.size)
    }
}
