package dev.omalauncher.widgets.lang

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full screens of the Ormus feed tiles refresh when their list is pulled down past its top (`onPull: refresh()`,
 * owl 17): sessions and each board copy, comms and each copy, usage, pull requests and the kiosk.
 */
class OrmusPullTest {
    private val dir = java.io.File("../app/src/main/assets/ormus/widgets")

    private val screens =
        listOf("10-sessions", "12-comms", "72-usage", "77-prs", "45-kiosk") +
            listOf("raven", "ormus", "alqvimia", "clients").flatMap { listOf("11-board-$it", "13-comms-$it") }

    /** Every element of [nodes], into each branch of an `if` and each body. */
    private fun elements(nodes: List<WNode>): List<WNode.Element> =
        nodes.flatMap { n ->
            when (n) {
                is WNode.Element -> listOf(n) + elements(n.children)
                is WNode.If -> elements(n.then) + elements(n.orElse)
                is WNode.For -> elements(n.body)
                is WNode.Lets -> elements(n.children)
                is WNode.Use -> elements(n.body)
                is WNode.Draw -> emptyList()
            }
        }

    @Test fun eachFeedScreenRefreshesWhenItsListIsPulledDown() {
        for (name in screens) {
            val program = Compiler.compile(java.io.File(dir, "$name.owl").readText())
            val screen = program.screen
            assertTrue("$name has a screen", screen != null)
            val pulled = elements(screen!!).filter { "onPull" in it.handlers }
            assertTrue("$name: one list of its screen takes onPull (found ${pulled.size})", pulled.size == 1 && pulled.single().kind == "Column")
        }
    }
}
