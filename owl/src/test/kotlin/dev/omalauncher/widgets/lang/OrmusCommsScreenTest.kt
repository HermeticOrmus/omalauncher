package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comms tile's screen card (12-comms.owl): the messages before their last one, a reaction on it, and a text he types,
 * each sent through sg's reply tool (ormus-feeds bin/reply) as the feed allows (`send`, `react`). Ormus, 2026-11-15:
 * "give me more context of the chat", "type and send without going into whatsapp", emojis as options.
 */
class OrmusCommsScreenTest {
    private val program = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/12-comms.owl").readText())
    private val replies = """[{"n": 1, "label": "On it."}, {"n": 2, "label": "Yes."}]"""
    private val context = """[{"me": false, "text": "hola, una pregunta"}, {"me": true, "text": "dime"}]"""
    private val card = "aaaaaaaaaaaa|can you look?"

    private fun chat(extra: String = """, "send": true, "react": ["👍", "❤️", "🙏", "😂"], "context": $context, "sig": "3f9a1c2b"""") =
        """{"name": "Ana", "group": false, "waiting": true, "mine": false, "age_min": 12, "text": "can you look?",
        "open": {"url": "https://wa.me/5550002"}, "k": "aaaaaaaaaaaa", "replies": $replies$extra}"""

    private fun feed(row: String) =
        Json.parse(
            """{"ok": true, "code": 200, "out": {"generated": 1794166000, "machines": {"sg": "ok"}, "errors": {}, "needs": 0,
            "agents": {"blocked": 0, "working": 0, "done": 0, "idle": 0}, "waiting": 1, "unsorted": [],
            "links": {}, "hosts": {}, "game": {"today": 0, "into": 0, "need": 3, "level": 1, "streak": 0}, "moves": [],
            "boards": [{"name": "raven", "agents": {"blocked": 0, "working": 0, "done": 0, "idle": 0}, "waiting": 1,
              "projects": [{"name": "PORTALS", "agents": {"blocked": 0, "working": 0, "done": 0, "idle": 0}, "top": [], "chats": [$row]}]}]}}""",
        )

    private fun pinned(row: String = chat()) = TileHost(program, feed(row)).also { it.set("pick", Str(card)) }

    private val screen get() = program.screen!!

    @Test fun theScreenCardShowsTheMessagesBeforeTheirLastOneAndTheTileDoesNot() {
        val t = pinned()
        val lines = t.lines(screen)
        val before = lines.indexOf("Ana: hola, una pregunta")
        assertTrue("theirs by name, oldest first: $lines", before >= 0 && lines[before + 1] == "you: dime")
        assertTrue("their last line comes after them: $lines", lines.indexOf("can you look?") > before + 1)
        assertFalse("the tile keeps its one line: ${t.lines(program.root)}", "you: dime" in t.lines(program.root))
        val group = pinned(chat(""", "group": true, "context": [{"me": false, "text": "quién va?"}], "send": true""").replace(""""group": false, """, ""))
        assertTrue("in a group the sender is not known: ${group.lines(screen)}", "them: quién va?" in group.lines(screen))
    }

    @Test fun aReactionGoesToSgsReplyToolWithTheChatsKeyAndWord() {
        val t = pinned()
        for (e in listOf("👍", "❤️", "🙏", "😂")) assertTrue("a key for $e", t.hasKey(screen, e))
        val sent = t.tap(screen, "🙏").single()
        assertTrue(sent, sent.startsWith("run ") && sent.contains("sg projects/ormus-feeds/bin/reply -"))
        assertTrue("the JSON is {k, react, sig}: $sent", sent.contains("""'{"k":"aaaaaaaaaaaa","react":"🙏","sig":"3f9a1c2b"}'"""))
        assertEquals(card, t.text("busy"))
        assertTrue("the key tapped waits: ${t.lines(screen)}", "…" in t.lines(screen))
        assertTrue("and no other send goes meanwhile", t.tap(screen, "👍").isEmpty())
    }

    @Test fun aTypedTextGoesAsHeTypedItAndStaysInTheFieldUntilSgSaysItWent() {
        val t = pinned()
        assertEquals("one field on the screen's card", 1, t.inputs(screen))
        assertEquals("none on the tile", 0, t.inputs(program.root))
        t.change(screen, "write to Ana", "mañana a las 3 👍")
        assertEquals(1, t.submit(screen, "write to Ana", "mañana a las 3 👍"))
        val sent = t.actions.single()
        assertTrue("the JSON is {k, text, sig}: $sent", sent.contains("""'{"k":"aaaaaaaaaaaa","text":"mañana a las 3 👍","sig":"3f9a1c2b"}'"""))
        assertEquals(Num(20.0), t.let("busyN"))
        // A text that did not go stays in the field.
        t.fire("sent", Str("""{"n": 1, "r": {"ok": false, "outcome": "not confirmed"}}"""))
        assertEquals("mañana a las 3 👍", t.text("draft"))
        assertEquals("not confirmed: open the chat", t.text("said"))
        // One that went leaves it empty.
        t.set("said", Str(""))
        t.submit(screen, "write to Ana", "mañana a las 3 👍")
        t.fire("sent", Str("""{"n": 2, "r": {"ok": true, "outcome": "sent"}}"""))
        assertEquals("", t.text("draft"))
        assertEquals("sent", t.text("said"))
    }

    @Test fun anEmptyTextOrASecondSendWhileOneIsOnTheWaySendsNothing() {
        val t = pinned()
        t.submit(screen, "write to Ana", "   ")
        assertTrue(t.actions.isEmpty())
        t.set("busy", Str("bbbbbbbbbbbb|other"))
        t.submit(screen, "write to Ana", "hola")
        assertTrue("busy: ${t.actions}", t.actions.isEmpty())
    }

    @Test fun aChatSgTakesNothingOnOrOneHeAnsweredOrACardNotPickedShowsNoFieldAndNoReactions() {
        for ((why, t) in listOf(
            "no send" to pinned(chat(""", "context": $context""")),
            "replied" to pinned(chat(""", "send": true, "react": ["👍"], "replied": 2""")),
            "not pinned" to TileHost(program, feed(chat())),
        )) {
            assertEquals(why, 0, t.inputs(screen))
            assertFalse(why, t.hasKey(screen, "👍"))
        }
        val noReact = pinned(chat(""", "send": true"""))
        assertEquals("a field without an id to react to", 1, noReact.inputs(screen))
        assertFalse(noReact.hasKey(screen, "👍"))
    }

    @Test fun anEmojiReplyFromTheModelIsAKeyLikeAnyOther() {
        val t = pinned(chat(""", "send": true""").replace(replies, """[{"n": 1, "label": "Dale 👍"}, {"n": 2, "label": "😂"}]"""))
        assertTrue(t.lines(screen).toString(), t.hasKey(screen, "1 Dale 👍") && t.hasKey(screen, "2 😂"))
    }
}
