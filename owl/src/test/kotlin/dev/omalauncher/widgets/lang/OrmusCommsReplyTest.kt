package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The comms tile (12-comms.owl) and sg's reply tool: the chat's last word travels with the key, a chat that moved on is a quiet line. */
class OrmusCommsReplyTest {
    private val program = Compiler.compile(java.io.File("../app/src/main/assets/ormus/widgets/12-comms.owl").readText())
    private val replies = """[{"n": 1, "label": "On it."}, {"n": 2, "label": "Yes."}]"""

    private fun chat(
        k: String,
        extra: String = "",
        withReplies: Boolean = true,
    ) = """{"name": "Ana", "group": false, "waiting": true, "mine": false, "age_min": 12, "text": "can you look?",
        "open": {"url": "https://wa.me/5550002"}, "k": "$k"${if (withReplies) """, "replies": $replies""" else ""}$extra}"""

    private fun feed(row: String) =
        Json.parse(
            """{"ok": true, "code": 200, "out": {"generated": 1794166000, "machines": {"sg": "ok"}, "errors": {}, "needs": 0,
            "agents": {"blocked": 0, "working": 0, "done": 0, "idle": 0}, "waiting": 1, "unsorted": [],
            "links": {}, "hosts": {}, "game": {"today": 0, "into": 0, "need": 3, "level": 1, "streak": 0}, "moves": [],
            "boards": [{"name": "raven", "agents": {"blocked": 0, "working": 0, "done": 0, "idle": 0}, "waiting": 1,
              "projects": [{"name": "PORTALS", "agents": {"blocked": 0, "working": 0, "done": 0, "idle": 0}, "top": [], "chats": [$row]}]}]}}""",
        )

    private fun pinned(row: String) = TileHost(program, feed(row)).also { it.set("pick", Str("aaaaaaaaaaaa|can you look?")) }

    @Test fun aKeySendsTheChatsLastWordWithTheReply() {
        val t = pinned(chat("aaaaaaaaaaaa", """, "sig": "3f9a1c2b" """))
        val sent = t.tap(program.root, "1 On it.").single()
        assertTrue(sent, sent.contains("projects/ormus-feeds/bin/reply -"))
        assertTrue("the JSON is {k, n, label, sig}: $sent", sent.contains("""'{"k":"aaaaaaaaaaaa","n":1,"label":"On it.","sig":"3f9a1c2b"}'"""))
    }

    @Test fun aRowWithNoSigSendsNoSigNotANull() {
        val t = pinned(chat("aaaaaaaaaaaa"))
        val sent = t.tap(program.root, "1 On it.").single()
        assertTrue("sg refuses a sig that is not text, so none goes: $sent", sent.contains("""'{"k":"aaaaaaaaaaaa","n":1,"label":"On it."}'"""))
    }

    @Test fun aChatThatMovedOnIsOneQuietLineNotRed() {
        val t = pinned(chat("aaaaaaaaaaaa", """, "sig": "3f9a1c2b" """))
        val line = "the chat moved on: look again"
        t.set("busy", Str("aaaaaaaaaaaa|can you look?"))
        t.fire("sent", Str("""{"n": 1, "r": {"ok": false, "outcome": "changed"}}"""))
        assertEquals(line, t.text("said"))
        assertTrue("${t.lines(program.root)}", line in t.lines(program.root))
        val quiet = t.colorOfText(program.root, line)
        t.set("said", Str("sg refused it: look again"))
        val red = t.colorOfText(program.root, "sg refused it: look again")
        assertNotEquals("a refusal is not drawn in the quiet color", quiet, red)
        t.set("said", Str("sent"))
        assertEquals("the quiet color is the one a sent reply has", t.colorOfText(program.root, "sent"), quiet)
    }

    @Test fun aRepliedChatOffersNoKeysAndANewWordBringsThemBack() {
        val replied = pinned(chat("aaaaaaaaaaaa", """, "replied": 3""", withReplies = false))
        val lines = replied.lines(program.root)
        assertTrue("$lines", "replied 3m ago" in lines)
        assertFalse(replied.hasKey(program.root, "1 On it."))
        // Even a feed that still listed replies on a replied row (an older one) shows none.
        val old = pinned(chat("aaaaaaaaaaaa", """, "replied": 3"""))
        assertFalse(old.hasKey(program.root, "1 On it."))
    }
}
