package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pull requests tile (77-prs.owl) on made-up answers of sg's prs feed (ormus-feeds feeds/prs.py). */
class OrmusPrsTileTest {
    private val program = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/77-prs.owl").readText())

    private fun pr(
        repo: String,
        number: Int,
        state: String,
        review: String = "none",
        conflict: String = "false",
        title: String = "a change",
        age: Int = 30,
    ) = """{"repo": "$repo", "number": $number, "title": "$title", "url": "https://github.com/$repo/pull/$number", "state": "$state", "review": "$review", "conflict": $conflict, "age_h": $age}"""

    private val failing = pr("acme/portal", 7, "failing", title = "fix the invoice total", age = 5)
    private val conflict = pr("acme/portal", 8, "green", conflict = "true", title = "move the router", age = 52)
    private val changes = pr("beta/site", 9, "green", review = "changes_requested", title = "tidy the footer", age = 200)
    private val both = pr("beta/site", 10, "failing", review = "changes_requested", conflict = "true", title = "rework the cart")

    private fun feed(
        attention: List<String> = listOf(failing, conflict, changes),
        updated: List<String> = listOf(pr("acme/portal", 11, "green", title = "bump a library"), pr("beta/site", 12, "pending", title = "add a test"), pr("beta/site", 13, "none", title = "rename a file")),
        counts: String = """{"open": 10, "drafts": 2, "failing": 2, "green": 3, "changes_requested": 1, "conflicts": 3, "needs": 4}""",
        errors: String = "[]",
        generated: Long = 1_794_166_000,
    ) = Json.parse(
        """{"ok": true, "code": 200, "out": {"generated": $generated, "errors": $errors, "counts": $counts,
        "owners": [{"name": "acme", "open": 6, "failing": 2}, {"name": "beta", "open": 4, "failing": 0}],
        "attention": [${attention.joinToString(",")}], "updated": [${updated.joinToString(",")}]}}""",
    )

    private fun tile(
        f: Value = feed(),
        height: Int = 420,
    ) = TileHost(program, f, height = height)

    @Test fun theHeadCountsWhatNeedsHimAndTheOpenOnesBesideIt() {
        val lines = tile().lines(program.root)
        assertTrue("the feed's own count, each pull request once: $lines", "4 need you" in lines && "10 open" in lines)
        assertTrue("an owner a muted line, its failing count only when it has some: $lines", "acme · 6 open · 2 failing" in lines && "beta · 4 open" in lines)
        val none = tile(feed(counts = """{"open": 3, "drafts": 0, "failing": 0, "green": 3, "changes_requested": 0, "conflicts": 0}""", attention = emptyList())).lines(program.root)
        assertTrue("$none", "nothing needs you" in none && "3 open" in none)
    }

    @Test fun anOlderFeedWithoutNeedsIsSummedAsBefore() {
        val old = tile(feed(counts = """{"open": 10, "drafts": 2, "failing": 2, "green": 3, "changes_requested": 1, "conflicts": 3}""")).lines(program.root)
        assertTrue("$old", "6 need you" in old)
    }

    @Test fun theAllClearIsGoldOnlyOnAFeedWithNoErrorsThatIsOnTime() {
        val clear = """{"open": 3, "drafts": 0, "failing": 0, "green": 3, "changes_requested": 0, "conflicts": 0, "needs": 0}"""
        val good = tile(feed(counts = clear, attention = emptyList()))
        val gold = good.colorOfText(program.root, "nothing needs you")
        val muted = good.colorOfText(program.root, "3 open")
        assertNotEquals("gold is not the muted color", gold, muted)
        val broken = tile(feed(counts = clear, attention = emptyList(), errors = """["github: no answer"]"""))
        assertEquals("a broken feed has nothing to celebrate", muted, broken.colorOfText(program.root, "nothing needs you"))
        val old = tile(feed(counts = clear, attention = emptyList(), generated = 1_794_166_000 - 1_200))
        assertEquals("nor an old one", muted, old.colorOfText(program.root, "nothing needs you"))
    }

    @Test fun eachRowNamesItsPullRequestAndWhyItIsThereWithAMarkAndAWord() {
        val lines = tile().lines(program.root)
        assertTrue("$lines", "✕ failing" in lines && "≠ conflict" in lines && "± changes" in lines)
        assertTrue("repository name and number, the age, the title: $lines", "portal#7" in lines && "5h" in lines && "2d" in lines && "8d" in lines && "fix the invoice total" in lines)
        val both = tile(feed(attention = listOf(both))).lines(program.root)
        assertTrue("failing comes before a conflict before changes: $both", "✕ failing" in both && "≠ conflict" !in both && "± changes" !in both)
    }

    @Test fun theTileHoldsTheOnesThatNeedHimAndTheScreenAddsTheQuietOnes() {
        val tileLines = tile().lines(program.root)
        assertFalse("the fine ones are the screen's: $tileLines", "bump a library" in tileLines)
        val screen = tile().lines(program.screen!!)
        assertTrue("$screen", "NEED YOU" in screen && "UPDATED" in screen && "bump a library" in screen)
        assertTrue("a green, a pending and a plain one, each with its word: $screen", "✓ green" in screen && "… pending" in screen && "· open" in screen)
    }

    @Test fun aPullRequestThatNeedsNothingIsQuieterThanOneThatDoes() {
        val t = tile()
        val loud = t.colorOfText(program.screen!!, "✕ failing")
        val quiet = t.colorOfText(program.screen!!, "✓ green")
        assertNotEquals("$loud vs $quiet", loud, quiet)
    }

    @Test fun aTapOnARowOpensThatPullRequestAndTheHeadOpensTheScreen() {
        val t = tile()
        val opened = t.tap(program.root, "✕ failing portal#7 5h fix the invoice total")
        assertEquals(listOf("url https://github.com/acme/portal/pull/7"), opened)
        assertTrue("the tile itself opens the screen", java.io.File("../app/src/main/assets/ormus/widgets/77-prs.owl").readText().contains("  onTap: screen()"))
        val screen = tile().tap(program.screen!!, "✓ green portal#11 30h bump a library".replace("30h", "1d"))
        assertEquals(listOf("url https://github.com/acme/portal/pull/11"), screen)
    }

    @Test fun theFeedsErrorsAreOneLineAndTheRowsStay() {
        val t = tile(feed(errors = """["gh: not signed in", "github: rate limited"]"""))
        val lines = t.lines(program.root)
        assertTrue("$lines", "gh: not signed in · github: rate limited" in lines)
        assertTrue("the last rows are still shown: $lines", "4 need you" in lines && "fix the invoice total" in lines)
        assertNotEquals("the line is red", t.colorOfText(program.root, "gh: not signed in · github: rate limited"), t.colorOfText(program.root, "10 open"))
    }

    @Test fun aFeedThatIsLoadingOrDownOrOldSaysSo() {
        assertTrue("…" in TileHost(program, Null).lines(program.root))
        val down = TileHost(program, Json.parse("""{"ok": false, "code": 0, "out": null}""")).lines(program.root)
        assertTrue("$down", "sg doesn't answer" in down)
        val old = tile(feed(generated = 1_794_166_000 - 1_200)).lines(program.root)
        assertTrue("$old", "as of 20m ago" in old)
        assertFalse("a feed on time says nothing", tile().lines(program.root).any { it.startsWith("as of") })
    }

    @Test fun aShortTileShowsFewerRowsAndCountsTheRest() {
        val many = feed(attention = (1..6).map { pr("acme/portal", it, "failing", title = "change $it") })
        val short = tile(many, height = 200).lines(program.root)
        assertTrue("$short", short.count { it == "✕ failing" } < 6 && short.any { it.startsWith("+") && it.endsWith("more · tap for the list") })
        val tall = tile(many, height = 700).lines(program.root)
        assertEquals(6, tall.count { it == "✕ failing" })
    }
}
