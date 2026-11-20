package dev.omalauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Ormus build's key bar for terminals: its own keybar.owl (assets/ormus/widgets, with the agent page), and the
 * extra keys a Linux terminal window keeps on with the keyboard down, as Mercurio's bar is. The window and the
 * engine need a Context, so the lines that wire them are pinned here too: a rebase that drops one fails here.
 */
class OrmusKeybarTest {
    @Test fun aTerminalKeepsTheKeysWithTheKeyboardDown() {
        // Over a docked keyboard, as upstream: any window, terminal or not, hardware keyboard or not.
        for (terminal in listOf(true, false)) for (hardware in listOf(true, false)) {
            assertTrue(Ormus.windowKeysShown(keyboardPx = 900, terminal = terminal, hardwareKeyboard = hardware))
        }
        // Keyboard down: a terminal keeps them, unless a hardware keyboard has the keys; other apps don't get them.
        assertTrue(Ormus.windowKeysShown(keyboardPx = 0, terminal = true, hardwareKeyboard = false))
        assertFalse(Ormus.windowKeysShown(keyboardPx = 0, terminal = true, hardwareKeyboard = true))
        assertFalse(Ormus.windowKeysShown(keyboardPx = 0, terminal = false, hardwareKeyboard = false))
    }

    /** A terminal: plain foot, one the launcher started (the attach tiles' hc attach among them), or a tile's. */
    @Test fun whichWindowsAreTerminals() {
        assertTrue(Ormus.isTerminalWindow("foot", launched = false))
        assertTrue(Ormus.isTerminalWindow("omarchy.ssh-t-sg", launched = true))
        assertTrue(Ormus.isTerminalWindow("omarchy.tile-btop", launched = false))
        assertFalse(Ormus.isTerminalWindow("org.mozilla.firefox", launched = false))
        assertFalse(Ormus.isTerminalWindow("footclient-x", launched = false))
    }

    /** The bar is the build's own: upstream's never seeds keybar.owl, and the stock copy is this one. */
    @Test fun theBuildShipsItsOwnBar() {
        assertTrue("keybar.owl" in Ormus.HIDDEN_WIDGETS)
        assertEquals("ormus/widgets/keybar.owl", Ormus.KEYBAR_ASSET)
        assertTrue(File("src/main/assets/${Ormus.KEYBAR_ASSET}").isFile)
        val engine = File("src/main/kotlin/dev/omalauncher/widgets/WidgetEngine.kt").readText()
        assertEquals(1, Regex("""add\(asset\(Ormus\.KEYBAR_ASSET, File\(dir, STOCK_KEYBAR\), Policy\.Owned\)\)""").findAll(engine).count())
        assertFalse("upstream's bar is no longer the stock copy", engine.contains("asset(\"widgets/\$KEYBAR\""))
    }

    @Test fun theWindowAsksTheRule() {
        val window = File("src/main/kotlin/dev/omalauncher/LinuxWindowActivity.kt").readText()
        assertTrue(window.contains("if (Ormus.windowKeysShown(keyboard, runtime.isTerminalWindow(window.appId), hardwareKeyboard)) {"))
        assertFalse("the old keyboard-only guard is gone", window.contains("if (keyboard > 0) ExtraKeys("))
    }
}
