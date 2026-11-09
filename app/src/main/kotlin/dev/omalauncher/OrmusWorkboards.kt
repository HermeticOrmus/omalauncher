package dev.omalauncher

import dev.omalauncher.widgets.Cell

/**
 * Omalauncher Ormus: home's spaces went from areas of life (now, money, work, mind, world) to workboards (now, raven,
 * ormus, alqvimia, clients, mercury, more: [Ormus.SPACES]). A home laid out under the old names is moved over once, at
 * the first start of a build with the new ones (the widget engine's migrateWorkboards): every kept tile keeps its size
 * and its page mates, the tiles of money and the world and the communications tile leave the home (their files stay,
 * in widgets/.removed; the comms tile is the chats' place now), and each workboard space starts with its board tile. Pure: [plan] says where everything goes; the engine moves it.
 */
object OrmusWorkboards {
    /** The marker file in widgets/ once the move is done; a build that changes the spaces again takes another name. */
    const val DONE = ".workboards-2"

    /** The spaces of the builds before, in their old order, each a hundred pages from its first (as today). */
    val OLD_SPACES = listOf("now", "money", "work", "mind", "world")

    /**
     * Rows of his home grid, when the engine does not know the grid yet: a page's tiles are stacked within them. A tile
     * with a cell is never moved by the engine, so on a grid with fewer rows a stacked tile could sit past the bottom;
     * his layout reaches row 10.
     */
    const val ROWS = 10

    /** The board tile each workboard space holds on its first page, from the top. */
    val BOARDS = linkedMapOf("raven" to "11-board-raven.owl", "ormus" to "11-board-ormus.owl", "alqvimia" to "11-board-alqvimia.owl", "clients" to "11-board-clients.owl")
    const val BOARD_HEIGHT = 6

    /** Where a widget goes, by its file; one not named here stays with its page's space when that is still one, else goes to more. */
    private val HOMES =
        mapOf(
            "45-kiosk.owl" to "mercury", "40-trainer.owl" to "mercury", "market-kingdompaulo-ai-stars.owl" to "mercury",
            "50-pass.owl" to "more", "70-fleet.owl" to "more", "80-today.owl" to "more", "05-sun.owl" to "more",
            "75-raven.owl" to "raven",
        ) + BOARDS.entries.associate { (space, file) -> file to space }

    /**
     * Widgets that leave the home: money's face, owed, people, pipeline and markets, GitHub trending and the world's, and the
     * communications tile (the comms tile under the sessions tile is where the chats that wait are now).
     */
    private val LEAVE =
        setOf(
            "15-waiting.owl", "55-money-face.owl", "60-owed.owl", "62-people.owl", "64-pipeline.owl", "90-markets.owl",
            "market-simonschubert-github-trending.owl", "market-simonschubert-picture-of-the-day.owl",
            "market-jdblackwell-spacex.owl", "market-simonschubert-earthquakes.owl",
        )

    /** A tile on home before the move: a widget's file name or an app's or folder's key, its page, its cell when it has one. */
    data class Item(
        val key: String,
        val page: Int,
        val cell: Cell?,
        val widget: Boolean = true,
        val face: Boolean = false,
    )

    data class Move(
        val key: String,
        val page: Int,
        val cell: Cell?,
    )

    /** Every kept tile's new page and cell, and the widgets that leave. */
    data class Plan(
        val moves: List<Move>,
        val removed: List<String>,
    )

    private fun oldSpaceOf(page: Int) = Math.floorDiv(page - 1 + Ormus.FACE_PAGES, Ormus.SPACE_PAGES).coerceIn(0, OLD_SPACES.lastIndex)

    /** The space [item] goes to, or null when it leaves the home. */
    fun home(item: Item): String? {
        if (item.widget && item.key in LEAVE) return null
        if (item.widget) HOMES[item.key]?.let { return it }
        val old = OLD_SPACES[oldSpaceOf(item.page)]
        return if (old in Ormus.SPACES) old else "more"
    }

    /**
     * The move for [items], the tiles on home under the old spaces. A page's kept tiles that go to one space stay together,
     * moved up so the first starts at row 0, and stack under what is on their new page when they fit in [rows]; else they
     * take the next page. Each workboard space keeps the first [BOARD_HEIGHT] rows of its first page for its board tile. A
     * kept face takes the page left of its space's first. Nothing is lost but what leaves.
     */
    fun plan(
        items: List<Item>,
        rows: Int = ROWS,
        columns: Int = 6,
    ): Plan {
        val removed = items.filter { home(it) == null }.map { it.key }
        val moves = ArrayList<Move>()
        val used = HashMap<Int, Int>() // page -> rows taken
        val last = HashMap<String, Int>() // space -> the page its tiles last went to
        val faces = HashMap<String, Int>() // space -> faces placed
        for ((space, file) in BOARDS) {
            val first = Ormus.firstPage(Ormus.SPACES.indexOf(space))
            used[first] = BOARD_HEIGHT
            last[space] = first
            if (items.any { it.widget && it.key == file }) moves += Move(file, first, Cell(0, 0, columns, BOARD_HEIGHT))
        }
        val kept = items.filter { home(it) != null && it.key !in BOARDS.values }
        // Faces keep their order: the one nearest its space's first page stays nearest.
        for (face in kept.filter { it.face }.sortedByDescending { it.page }) {
            val space = home(face)!!
            val n = faces.getOrDefault(space, 0)
            faces[space] = n + 1
            moves += Move(face.key, Ormus.firstPage(Ormus.SPACES.indexOf(space)) - 1 - n, null)
        }
        val groups =
            kept.filter { !it.face }.groupBy { it.page }.toSortedMap().values.flatMap { page ->
                page.groupBy { home(it)!! }.entries.map { it.key to it.value }
            }
        for ((space, tiles) in groups) {
            val first = Ormus.firstPage(Ormus.SPACES.indexOf(space))
            val top = tiles.mapNotNull { it.cell?.y }.minOrNull() ?: 0
            val shifted = tiles.map { it to it.cell?.let { c -> c.copy(y = c.y - top) } }
            val placed = tiles.all { it.cell != null }
            val height = if (placed) shifted.maxOf { (_, c) -> c!!.y + c.h } else rows
            var page = last[space] ?: first
            if (used.getOrDefault(page, 0) > 0 && (!placed || used.getValue(page) + height > rows)) page += 1
            val offset = used.getOrDefault(page, 0)
            for ((item, c) in shifted) moves += Move(item.key, page, c?.copy(y = c.y + offset))
            used[page] = offset + height
            last[space] = page
        }
        return Plan(moves, removed)
    }
}
