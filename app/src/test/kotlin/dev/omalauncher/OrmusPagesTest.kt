package dev.omalauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrmusPagesTest {
    @Test fun aSpacesTilePagesStartAtItsFirstPage() {
        assertEquals(listOf(0, 0, 1, 1, 2, 4), listOf(1, 2, 101, 102, 201, 402).map(Ormus::spaceOf))
    }

    @Test fun theFiftyPagesLeftOfASpacesFirstAreItsFaces() {
        assertEquals(listOf(0, 0, 1, 1, 2), listOf(0, -7, 100, 51, 200).map(Ormus::spaceOf))
        assertTrue(listOf(0, -7, 100, 51, 151, 400).all(Ormus::isFacePage))
        assertFalse(listOf(1, 50, 101, 150, 201, 401).any(Ormus::isFacePage))
    }

    @Test fun pageNumbersAsUpstreamWithinOneSpace() {
        // A face, two tile pages: upstream's 0, 1, 2.
        assertEquals(listOf(0, 1, 2), Ormus.pageNumbers(listOf(0, 0, 0), listOf(false, true, true)))
    }

    @Test fun eachSpaceIsNumberedFromItsOwnFirstPage() {
        val spaces = listOf(0, 0, 1, 1, 1, 2, 4)
        val tiles = listOf(true, true, false, true, true, true, true)
        assertEquals(listOf(1, 2, 100, 101, 102, 201, 401), Ormus.pageNumbers(spaces, tiles))
    }

    @Test fun aSpaceWrittenApartStillCountsOnFromItsOwnPages() {
        assertEquals(listOf(101, 1, 102), Ormus.pageNumbers(listOf(1, 0, 1), listOf(true, true, true)))
    }
}
