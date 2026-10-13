package dev.omalauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterRailTest {
    @Test fun lettersAreUpperCaseFirstLetters() {
        assertEquals('C', railLetter("calendar"))
        assertEquals('M', railLetter("  Maps"))
    }

    @Test fun accentsFileUnderTheirLetter() {
        assertEquals('E', railLetter("Éclair"))
        assertEquals('A', railLetter("ádvent"))
    }

    @Test fun digitsSymbolsAndBlanksFileUnderHash() {
        assertEquals('#', railLetter("1Password"))
        assertEquals('#', railLetter("[ai] studio"))
        assertEquals('#', railLetter(""))
    }

    @Test fun otherScriptsKeepTheirOwnLetter() {
        assertEquals('Б', railLetter("банк"))
    }

    @Test fun eachLetterPointsAtTheRowItFirstShowsUpIn() {
        val labels = listOf("1Password", "Calendar", "Camera", "Chrome", "Maps", "Messages", "Notes", "Photos")
        assertEquals(listOf('#' to 0, 'C' to 0, 'M' to 1, 'N' to 2, 'P' to 2), letterRows(labels, columns = 3))
    }

    @Test fun aLetterComingBackLaterKeepsItsFirstRow() {
        // Sorted by lowercase label, é comes after z.
        val labels = listOf("Email", "Zoom", "Éclair")
        assertEquals(listOf('E' to 0, 'Z' to 1), letterRows(labels, columns = 1))
    }

    @Test fun noAppsNoLetters() {
        assertEquals(emptyList<Pair<Char, Int>>(), letterRows(emptyList(), columns = 4))
    }
}
