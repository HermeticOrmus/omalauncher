package dev.omalauncher.widgets.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Ormus build's key bar, assets/ormus/widgets/keybar.owl: an agent page for `hc attach`, the stock keys, and the
 * rest of Mercurio's keys. Its keys are data a component sends, so the compiler can't check their names: this does.
 */
class OrmusKeybarTest {
    private val file = java.io.File("../app/src/main/assets/ormus/widgets/keybar.owl")
    private val program = Compiler.compile(file.readText())

    /** The pages, as the bar draws them: page name -> rows -> keys, each key its fields as text. */
    private val pages: Map<String, List<List<Map<String, Value>>>> by lazy {
        val let = program.globals.first { it.name == "pages" } as Global.Let
        val value = let.value(Env.root(LangTest.TestHost(program))) as Obj
        value.fields.mapValues { (_, rows) -> (rows as Lst).items.map { row -> (row as Lst).items.map { (it as Obj).fields } } }
    }

    private fun Map<String, Value>.text(name: String) = (this[name] as? Str)?.v

    private fun keysOf(page: String) = pages.getValue(page).flatten().mapNotNull { it.text("key") }

    @Test fun isAKeybar() {
        assertEquals(OwlKind.Keybar, program.kind)
        assertEquals("terminal key bar", program.name)
    }

    /** Three pages of two rows of nine, like the stock bar's two rows: the keys stay as wide on every page. */
    @Test fun threePagesOfTwoRowsOfNine() {
        assertEquals(setOf("agent", "keys", "more"), pages.keys)
        for ((name, rows) in pages) {
            assertEquals("$name has two rows", 2, rows.size)
            for (row in rows) assertEquals("$name: nine keys a row", 9, row.size)
        }
    }

    /** Every key names one pressKey sends: a typo here would press nothing on the phone and only report it there. */
    @Test fun everyKeyNameIsAKey() {
        for (page in pages.keys) for (key in keysOf(page)) assertNotNull("$page: \"$key\" names no key", KeyNames.combo(key))
    }

    /**
     * What answering an agent through hc attach takes, on its own page: hc attach detaches on ctrl+\, which outside it
     * quits a shell's program, so detach is a hold key; and the mic.
     */
    @Test fun theAgentPageAnswersAnAgent() {
        val keys = keysOf("agent")
        for (k in listOf("esc", "1", "2", "3", "enter", "shift+tab", "ctrl+c", "up", "down", "left", "right", "tab", "shift+insert")) {
            assertTrue("agent page has $k", k in keys)
        }
        val detach = pages.getValue("agent").flatten().single { it.text("label") == "detach" }
        assertEquals("ctrl+\\", detach.text("key"))
        assertEquals(Bool(true), detach["hold"])
        assertEquals("only detach waits for a hold", listOf("detach"), pages.values.flatMap { it.flatten() }.filter { it["hold"] == Bool(true) }.map { it.text("label") })
        assertEquals("one mic, on the agent page", listOf("agent"), pages.filterValues { rows -> rows.flatten().any { it["mic"] == Bool(true) } }.keys.toList())
        assertEquals(43, KeyNames.combo("ctrl+\\")!!.code)
    }

    /** The stock bar's keys all stay, on the keys page, and the rest of Mercurio's catalog is on the more page. */
    @Test fun theStockAndMercurioKeysStay() {
        val stock = listOf("esc", "/", "-", "home", "up", "end", "pgup", "ctrl+insert", "tab", "left", "down", "right", "pgdn", "shift+insert")
        assertEquals(stock.toSet(), keysOf("keys").toSet())
        val all = pages.keys.flatMap(::keysOf).toSet()
        val mercurio = listOf("ctrl+d", "ctrl+z", "ctrl+l", "ctrl+r", "ctrl+a", "ctrl+e", "ctrl+u", "ctrl+w", "|", "~", "delete", "backspace")
        for (k in mercurio) assertTrue("the bar has $k", k in all)
    }

    /** Each page: one page key, at the end of the top row; ctrl and alt latched; the photo picker on the keys page. */
    @Test fun everyPageTurnsAndLatches() {
        for ((name, rows) in pages) {
            val flat = rows.flatten()
            assertEquals("$name: one page key", 1, flat.count { it["page"] == Bool(true) })
            assertEquals("$name: the page key ends the top row", Bool(true), rows[0].last()["page"])
            assertEquals("$name: ctrl and alt latch", listOf("ctrl", "alt"), flat.mapNotNull { it.text("latch") })
            // Up over down, so the arrows sit the same way on every page that has them.
            val up = rows[0].indexOfFirst { it.text("key") == "up" }
            if (up >= 0) assertEquals("$name: up over down", up, rows[1].indexOfFirst { it.text("key") == "down" })
        }
        assertEquals("photos on the keys page", listOf("keys"), pages.filterValues { rows -> rows.flatten().any { it["images"] == Bool(true) } }.keys.toList())
    }

    /** The page key's tap goes agent, keys, more and round again; an unknown page (a newer file's) goes to agent. */
    @Test fun thePageKeyTurnsThePages() {
        val src = file.readText()
        val tap = Regex("""onTap: (page = .*)""").find(src)?.groupValues?.get(1) ?: error("the page key's onTap is not where this test looks")
        val longPress = Regex("""onLongPress: (page = .*)""").find(src)?.groupValues?.get(1) ?: error("the page key's onLongPress is not where this test looks")

        fun after(
            handler: String,
            from: String,
        ): Value {
            val p = Compiler.compile("widget {\nmeta { owl: 16 }\nstate page = \"$from\"\nonTap: $handler\nText { text: page }\n}")
            val host = LangTest.TestHost(p)
            p.onTap!!.run(Env.root(host), emptyList())
            return host.slots[p.globals.indexOfFirst { it is Global.State }]
        }
        assertEquals(Str("keys"), after(tap, "agent"))
        assertEquals(Str("more"), after(tap, "keys"))
        assertEquals(Str("agent"), after(tap, "more"))
        assertEquals(Str("agent"), after(tap, "gone"))
        for (from in listOf("agent", "keys", "more")) assertEquals(Str("agent"), after(longPress, from))
    }

    /** The page is kept across restarts, and the bar draws the agent page for one it doesn't know. */
    @Test fun thePageIsKeptAndFallsBackToAgent() {
        val src = file.readText()
        assertTrue(src.contains("state page = \"agent\" persist"))
        assertTrue(src.contains("for row in get(pages, page, pages.agent)"))
    }

    /** What the dictation does with what it heard, as the trigger runs on the phone: the actions it asks for, in order. */
    private fun dictated(heard: String): List<String> {
        val trigger = program.triggers.single { it.dictation }
        val host = LangTest.TestHost(program)
        trigger.action.run(Env.root(host), listOf(Str(heard)))
        return host.actions
    }

    private val latchesOff = listOf("latch ctrl, off", "latch alt, off", "latch shift, off")

    /** A menu word alone, in English or Spanish, a digit, any case, with the recognizer's marks: its key, no enter. */
    @Test fun aMenuWordPressesItsKey() {
        val words =
            listOf(
                "one" to "1", "Uno." to "1", "1" to "1", "¿Uno?" to "1", "two" to "2", "Dos" to "2", "2." to "2", "three!" to "3", "tres" to "3",
                "four" to "4", "Cuatro." to "4", "4" to "4", "Escape" to "esc", "enter" to "enter", "Intro." to "enter", "Send." to "enter", "enviar" to "enter",
            )
        for ((heard, key) in words) assertEquals(heard, latchesOff + "pressKey $key", dictated(heard))
    }

    /**
     * A refusal is esc, never enter: at Claude Code's permission menu Enter picks the highlighted "1. Yes" (measured by
     * the review on sg, Claude Code 2.1.329), and esc refuses.
     */
    @Test fun aRefusalIsEscNeverEnter() {
        for (heard in listOf("no", "No.", "¡No!", "nope", "cancel", "Cancelar.", "para", "Stop!")) {
            assertEquals(heard, latchesOff + "pressKey esc", dictated(heard))
        }
    }

    /**
     * Any other sentence is only pasted, whole, as heard: one paste, so neither its letters nor a digit in it reach a
     * menu as keys (the review measured "the 3 files are wrong", typed key by key, picking option 3 and auto mode).
     */
    @Test fun aSentenceIsOnlyPasted() {
        for (heard in listOf("yes go ahead", "Sí, dale con la opción dos.", "two of them", "uno más", "please resend the mail", "no thanks", "the 3 files are wrong", "There are 2 bugs in it")) {
            assertEquals(heard, latchesOff + "pasteText $heard", dictated(heard))
        }
        assertFalse("the bar never types text key by key", file.readText().contains("typeText("))
    }

    /** A sentence that ends with the word send or enviar is sent: the words before it, then enter. */
    @Test fun aSentenceEndingInSendIsSent() {
        val sent =
            listOf(
                "fix the tests, send" to "fix the tests", "Fix it. Send." to "Fix it", "Arregla los tests enviar." to "Arregla los tests",
                "Sí, dale con la opción dos, enviar" to "Sí, dale con la opción dos", "yes go ahead send!" to "yes go ahead",
            )
        for ((heard, typed) in sent) assertEquals(heard, latchesOff + "pasteText $typed" + "pressKey enter", dictated(heard))
        // Only the whole word, at the very end.
        assertEquals(latchesOff + "pasteText please resend", dictated("please resend"))
        assertEquals(latchesOff + "pasteText send it to me", dictated("send it to me"))
    }

    /**
     * Words of the list before send are that key alone, no enter: "No, send." refuses, it doesn't say Yes (the review
     * measured "No" then Enter approving), and "Two, send." picks 2, not the highlighted 1.
     */
    @Test fun aListWordBeforeSendIsItsKeyAlone() {
        val cases =
            listOf(
                "No, send." to "esc", "no send" to "esc", "Cancel, send." to "esc", "Stop. Send." to "esc", "Para, enviar" to "esc", "Escape, send." to "esc",
                "Cuatro, enviar." to "4", "Four, send." to "4", "Two, send." to "2", "Three send" to "3", "¿Uno? enviar" to "1",
            )
        for ((heard, key) in cases) assertEquals(heard, latchesOff + "pressKey $key", dictated(heard))
    }

    /** pasteText is the fork's own action: owl 23, only where keys may go, and listed as what the file does. */
    @Test fun pasteTextIsAnActionOfItsOwn() {
        val p = Compiler.compile("keybar \"k\" {\n meta { owl: 23 }\n Text { text: \"k\"; onPress: pasteText(\"ls\") } }")
        assertEquals(listOf("paste into the Linux window you're typing in: ls"), p.commands)
        val old = runCatching { Compiler.compile("keybar \"k\" {\n meta { owl: 22 }\n Text { text: \"k\"; onPress: pasteText(\"ls\") } }") }
        assertTrue(old.exceptionOrNull()?.message.orEmpty(), old.exceptionOrNull()?.message.orEmpty().contains("owl 23"))
        val host = LangTest.TestHost(p)
        ((p.screen!!.single() as WNode.Element).handlers["onPress"]!!).run(Env.root(host), listOf(Num(0.0), Num(0.0), Num(0.0)))
        assertEquals(listOf("pasteText ls"), host.actions)
    }

    /** The menu words name keys pressKey knows; the mic starts only from a tap, as the launcher requires. */
    @Test fun theSpokenKeysAreKeysAndTheMicStartsOnATap() {
        val let = program.globals.first { it.name == "spoken" } as Global.Let
        val spoken = (let.value(Env.root(LangTest.TestHost(program))) as Obj).fields
        assertEquals(24, spoken.size)
        for ((word, key) in spoken) assertNotNull("$word -> $key", KeyNames.combo((key as Str).v))
        assertTrue(file.readText().contains("onTap: speech(\"toggle\")"))
    }
}
