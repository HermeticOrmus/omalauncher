package dev.omalauncher

import android.util.Log
import dev.omalauncher.widgets.Cell
import dev.omalauncher.widgets.Folder
import dev.omalauncher.widgets.HomeItem
import dev.omalauncher.widgets.Shortcut
import dev.omalauncher.widgets.WidgetEngine
import dev.omalauncher.widgets.WidgetItem
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Omalauncher Ormus: the home layout as files next to style.toml, so an agent on Sun can read and arrange the screens.
 * layout.json is rewritten whenever the arrangement changes; a page number carries its space (Ormus.SPACES). A layout.apply.json, written by anyone, is checked as a
 * whole (known keys, inside the grid, no overlaps, no tile under its minimum size) and applied all at once or not at
 * all; it then becomes layout.applied.json, or layout.rejected.json with the reasons in layout.errors.txt.
 */
class OrmusLayout(
    private val widgets: WidgetEngine,
    private val dir: File,
    private val scope: CoroutineScope,
) {
    private val current = File(dir, "layout.json")
    private val incoming = File(dir, "layout.apply.json")
    private val errors = File(dir, "layout.errors.txt")
    private var written: String? = null

    fun start() {
        scope.launch {
            while (true) {
                runCatching {
                    if (withContext(Dispatchers.IO) { incoming.isFile }) applyIncoming()
                    export()
                }.onFailure { Log.w(TAG, "layout sync failed", it) }
                // Cheap: a few dozen tiles. A FileObserver would need re-arming whenever the style dir is replaced.
                delay(3000)
            }
        }
    }

    /** Every tile on every page, with its place; written only when it differs from what's on disk. */
    private suspend fun export() {
        val (cols, rows) = widgets.layout.gridSize() ?: return
        val items = JSONArray()
        for (page in widgets.pages) {
            for (item in widgets.itemsOn(page)) {
                val c = widgets.layout.cell(item.key) ?: continue
                items.put(
                    JSONObject()
                        .put("key", item.key)
                        .put("kind", kind(item))
                        .put("label", label(item))
                        .put("page", page)
                        .put("space", Ormus.SPACES[Ormus.spaceOf(page)])
                        .put("x", c.x)
                        .put("y", c.y)
                        .put("w", c.w)
                        .put("h", c.h)
                        .put("minW", item.minSize.first)
                        .put("minH", item.minSize.second),
                )
            }
        }
        val text = JSONObject().put("columns", cols).put("rows", rows).put("items", items).toString(2) + "\n"
        if (text == written) return
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val tmp = File(dir, ".layout.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(current)
        }
        written = text
    }

    private suspend fun applyIncoming() {
        val text = withContext(Dispatchers.IO) { runCatching { incoming.readText() }.getOrNull() } ?: return
        val problems = ArrayList<String>()
        val grid = widgets.layout.gridSize()
        if (grid == null) {
            // Not laid out yet (the launcher started in the background): try again once home has been on screen.
            return
        }
        val (cols, rows) = grid
        val items = widgets.pages.flatMap { page -> widgets.itemsOn(page) }.associateBy { it.key }
        // Where every tile ends up: today's place, then whatever the file moves.
        val target = HashMap<String, Pair<Int, Cell>>()
        // Pinned apps show as 1x1 whatever an old saved cell says, as in WidgetLayout.freeSpot.
        for (item in items.values) widgets.layout.cell(item.key)?.let { target[item.key] = item.page to if (item.resizable) it else it.copy(w = 1, h = 1) }
        val moves = ArrayList<Triple<HomeItem, Int, Cell>>()
        val requested =
            runCatching {
                val json = text.trim()
                if (json.startsWith("[")) JSONArray(json) else JSONObject(json).getJSONArray("items")
            }.getOrElse {
                problems += "not JSON with an items list: ${it.message}"
                JSONArray()
            }
        for (i in 0 until requested.length()) {
            val o = requested.optJSONObject(i)
            val key = o?.optString("key").orEmpty()
            val item = items[key]
            if (item == null) {
                problems += "no tile called \"$key\" on home"
                continue
            }
            val now = target[key]
            val page = o!!.optInt("page", now?.first ?: item.page)
            var cell = Cell(o.optInt("x", now?.second?.x ?: 0), o.optInt("y", now?.second?.y ?: 0), o.optInt("w", now?.second?.w ?: 1), o.optInt("h", now?.second?.h ?: 1))
            if (!item.resizable) cell = cell.copy(w = 1, h = 1)
            when {
                page < 1 -> problems += "$key: page $page, pages start at 1"
                cell.x < 0 || cell.y < 0 || cell.right > cols || cell.bottom > rows -> problems += "$key: ${cell.w}x${cell.h} at ${cell.x},${cell.y} leaves the ${cols}x$rows grid"
                cell.w < item.minSize.first || cell.h < item.minSize.second -> problems += "$key: ${cell.w}x${cell.h} is under its minimum ${item.minSize.first}x${item.minSize.second}"
                else -> {
                    target[key] = page to cell
                    moves += Triple(item, page, cell)
                }
            }
        }
        val byPage = target.entries.groupBy({ it.value.first }, { it.key to it.value.second })
        for ((page, tiles) in byPage) {
            for (a in tiles.indices) for (b in a + 1 until tiles.size) {
                if (tiles[a].second.overlaps(tiles[b].second)) problems += "page $page: ${tiles[a].first} and ${tiles[b].first} overlap"
            }
        }
        if (problems.isEmpty()) {
            for ((item, page, cell) in moves) {
                if (page != item.page) widgets.moveToPage(item, page)
                widgets.layout.set(item.key, cell)
            }
            widgets.layout.save()
        }
        withContext(Dispatchers.IO) {
            if (problems.isEmpty()) {
                errors.delete()
                incoming.renameTo(File(dir, "layout.applied.json"))
            } else {
                errors.writeText(problems.joinToString("\n", postfix = "\n"))
                incoming.renameTo(File(dir, "layout.rejected.json"))
            }
        }
        Log.i(TAG, if (problems.isEmpty()) "applied ${moves.size} tiles" else "rejected: ${problems.size} problems")
    }

    private fun kind(item: HomeItem) =
        when (item) {
            is WidgetItem -> "widget"
            is Shortcut -> "app"
            is Folder -> "folder"
        }

    private fun label(item: HomeItem) =
        when (item) {
            is WidgetItem -> item.spec.name
            is Shortcut -> item.label
            is Folder -> item.name ?: item.apps.joinToString(", ") { it.label }
        }

    private companion object {
        const val TAG = "ormus-layout"
    }
}
