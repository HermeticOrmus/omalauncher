package dev.omalauncher

import dev.omalauncher.OrmusWorkboards.Item
import dev.omalauncher.OrmusWorkboards.Move
import dev.omalauncher.widgets.Cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Omalauncher Ormus: the one move from the old spaces to the workboards, on a copy of his real layout (names only). */
class OrmusWorkboardsTest {
    private fun tile(
        key: String,
        page: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        widget: Boolean = true,
    ) = Item(key, page, Cell(x, y, w, h), widget)

    /** His home on 2026-11-06, under now, money, work, mind, world, plus the four board tiles as the new build seeds them. */
    private val his =
        listOf(
            tile("10-sessions.owl", 1, 0, 0, 6, 6), tile("45-kiosk.owl", 1, 0, 6, 6, 4),
            tile("30-mercurio.owl", 2, 0, 0, 6, 5), tile("15-waiting.owl", 2, 0, 5, 6, 5),
            tile("80-today.owl", 3, 0, 0, 6, 2), tile("20-notifications.owl", 3, 0, 2, 6, 4), tile("16-projects.owl", 3, 0, 6, 6, 4),
            Item("55-money-face.owl", 100, null, face = true),
            tile("60-owed.owl", 101, 0, 0, 6, 4), tile("90-markets.owl", 101, 0, 4, 6, 6),
            tile("62-people.owl", 102, 0, 0, 6, 5), tile("64-pipeline.owl", 102, 0, 5, 6, 5),
            tile("50-pass.owl", 201, 0, 0, 6, 3), tile("70-fleet.owl", 201, 0, 3, 6, 3), tile("05-sun.owl", 201, 0, 6, 4, 1),
            tile("app:one", 201, 4, 6, 1, 1, widget = false), tile("app:two", 201, 5, 6, 1, 1, widget = false),
            tile("75-raven.owl", 202, 0, 0, 6, 7),
            tile("40-trainer.owl", 301, 0, 0, 6, 6),
            tile("market-kingdompaulo-ai-stars.owl", 302, 0, 0, 6, 5), tile("market-simonschubert-github-trending.owl", 302, 0, 5, 6, 5),
            tile("market-simonschubert-picture-of-the-day.owl", 401, 0, 0, 6, 7),
            tile("market-jdblackwell-spacex.owl", 402, 0, 0, 6, 4), tile("market-simonschubert-earthquakes.owl", 402, 0, 4, 6, 6),
            Item("11-board-raven.owl", 101, null), Item("11-board-ormus.owl", 201, null), Item("11-board-alqvimia.owl", 301, null), Item("11-board-clients.owl", 401, null),
        )

    @Test fun hisHomeMovesByWorkboardAndNothingIsLostButWhatLeaves() {
        val plan = OrmusWorkboards.plan(his)
        val expected =
            setOf(
                // now keeps its three pages, the kiosk, today and the communications tile gone from them; the rows close up.
                Move("10-sessions.owl", 1, Cell(0, 0, 6, 6)),
                Move("30-mercurio.owl", 2, Cell(0, 0, 6, 5)),
                Move("20-notifications.owl", 3, Cell(0, 0, 6, 4)), Move("16-projects.owl", 3, Cell(0, 4, 6, 4)),
                // each workboard's board tile on its first page; raven's old tile does not fit under it.
                Move("11-board-raven.owl", 101, Cell(0, 0, 6, 6)), Move("75-raven.owl", 102, Cell(0, 0, 6, 7)),
                Move("11-board-ormus.owl", 201, Cell(0, 0, 6, 6)), Move("11-board-alqvimia.owl", 301, Cell(0, 0, 6, 6)), Move("11-board-clients.owl", 401, Cell(0, 0, 6, 6)),
                // mercury: the kiosk and the trainer share a page (4 + 6 rows), AI stars take the next.
                Move("45-kiosk.owl", 501, Cell(0, 0, 6, 4)), Move("40-trainer.owl", 501, Cell(0, 4, 6, 6)),
                Move("market-kingdompaulo-ai-stars.owl", 502, Cell(0, 0, 6, 5)),
                // more: today, then the work page's tiles under it, together as they were.
                Move("80-today.owl", 601, Cell(0, 0, 6, 2)),
                Move("50-pass.owl", 601, Cell(0, 2, 6, 3)), Move("70-fleet.owl", 601, Cell(0, 5, 6, 3)), Move("05-sun.owl", 601, Cell(0, 8, 4, 1)),
                Move("app:one", 601, Cell(4, 8, 1, 1)), Move("app:two", 601, Cell(5, 8, 1, 1)),
            )
        assertEquals(expected, plan.moves.toSet())
        assertEquals(expected.size, plan.moves.size)
        assertEquals(
            setOf(
                "15-waiting.owl", "55-money-face.owl", "60-owed.owl", "90-markets.owl", "62-people.owl", "64-pipeline.owl",
                "market-simonschubert-github-trending.owl", "market-simonschubert-picture-of-the-day.owl",
                "market-jdblackwell-spacex.owl", "market-simonschubert-earthquakes.owl",
            ),
            plan.removed.toSet(),
        )
        assertEquals("every tile is moved or leaves", his.size, plan.moves.size + plan.removed.size)
        // Each page keeps its tiles inside the grid and apart.
        for ((page, moves) in plan.moves.filter { it.cell != null }.groupBy { it.page }) {
            val cells = moves.map { it.cell!! }
            assert(cells.all { it.y >= 0 && it.y + it.h <= OrmusWorkboards.ROWS }) { "page $page: a tile outside the rows" }
            for (i in cells.indices) for (j in cells.indices) if (i < j) assert(!cells[i].overlaps(cells[j])) { "page $page: ${moves[i].key} over ${moves[j].key}" }
        }
    }

    @Test fun aTileNotNamedStaysWithItsSpaceWhenThatIsStillOneElseGoesToMore() {
        assertEquals("now", OrmusWorkboards.home(tile("some-widget.owl", 2, 0, 0, 6, 2)))
        assertEquals("more", OrmusWorkboards.home(tile("some-widget.owl", 102, 0, 0, 6, 2)))
        assertEquals("more", OrmusWorkboards.home(tile("app:x", 301, 0, 0, 1, 1, widget = false)))
        assertEquals("now", OrmusWorkboards.home(tile("app:x", 1, 0, 0, 1, 1, widget = false)))
        assertNull(OrmusWorkboards.home(tile("60-owed.owl", 1, 0, 0, 6, 4)))
        assertEquals("mercury", OrmusWorkboards.home(tile("45-kiosk.owl", 1, 0, 0, 6, 4)))
    }

    @Test fun aGroupWithATileThatHasNoCellTakesAPageOfItsOwnAndAKeptFaceGoesLeftOfItsSpace() {
        val plan =
            OrmusWorkboards.plan(
                listOf(
                    tile("30-mercurio.owl", 1, 0, 0, 6, 5),
                    Item("loose.owl", 2, null),
                    tile("16-projects.owl", 3, 0, 0, 6, 4),
                    Item("a-face.owl", 0, null, face = true),
                ),
            )
        assertEquals(
            setOf(Move("30-mercurio.owl", 1, Cell(0, 0, 6, 5)), Move("loose.owl", 2, null), Move("16-projects.owl", 3, Cell(0, 0, 6, 4)), Move("a-face.owl", 0, null)),
            plan.moves.toSet(),
        )
        assertEquals(emptyList<String>(), plan.removed)
    }

    @Test fun facesKeepTheirOrderAndAFaceOfASpaceThatIsGoneGoesLeftOfMore() {
        val plan = OrmusWorkboards.plan(listOf(Item("near.owl", 0, null, face = true), Item("far.owl", -1, null, face = true), Item("old-money-face.owl", 100, null, face = true)))
        assertEquals(setOf(Move("near.owl", 0, null), Move("far.owl", -1, null), Move("old-money-face.owl", 600, null)), plan.moves.toSet())
    }

    @Test fun aGroupThatDoesNotFitUnderThePageTakesTheNextAndATileTallerThanTheRowsStandsAlone() {
        val plan = OrmusWorkboards.plan(listOf(tile("45-kiosk.owl", 1, 0, 6, 6, 4), tile("40-trainer.owl", 301, 0, 0, 6, 6)), rows = 8)
        assertEquals(setOf(Move("45-kiosk.owl", 501, Cell(0, 0, 6, 4)), Move("40-trainer.owl", 502, Cell(0, 0, 6, 6))), plan.moves.toSet())
        val tall = OrmusWorkboards.plan(listOf(tile("45-kiosk.owl", 1, 0, 0, 6, 12), tile("40-trainer.owl", 301, 0, 0, 6, 6)))
        assertEquals(setOf(Move("45-kiosk.owl", 501, Cell(0, 0, 6, 12)), Move("40-trainer.owl", 502, Cell(0, 0, 6, 6))), tall.moves.toSet())
    }

    @Test fun anEmptyHomeMovesNothingAndTheBoardsStillHaveTheirPages() {
        assertEquals(OrmusWorkboards.Plan(emptyList(), emptyList()), OrmusWorkboards.plan(emptyList()))
        assertEquals(listOf(101, 201, 301, 401), OrmusWorkboards.BOARDS.keys.map { Ormus.firstPage(Ormus.SPACES.indexOf(it)) })
    }
}
