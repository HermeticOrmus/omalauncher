package dev.omalauncher.voice

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** The widgets' dictation on Ormus's voice: one take, sent once to voice-flow-api on sg, with no fallback. */
class OrmusVoiceTest {
    private class Seen(
        val method: String,
        val path: String,
        val query: String?,
        val type: String?,
        val body: ByteArray,
    )

    /** A local voice-flow-api that records what it gets. */
    private class Fake(
        val status: Int,
        val answer: String,
        val location: String? = null,
        val hold: Boolean = false,
    ) {
        val seen = CopyOnWriteArrayList<Seen>()
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        val server: HttpServer =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { s ->
                s.createContext("/") { ex ->
                    seen.add(Seen(ex.requestMethod, ex.requestURI.path, ex.requestURI.rawQuery, ex.requestHeaders.getFirst("Content-Type"), ex.requestBody.readBytes()))
                    arrived.countDown()
                    if (hold) release.await(10, TimeUnit.SECONDS)
                    val bytes = answer.toByteArray()
                    if (location != null) ex.responseHeaders.add("Location", location)
                    ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
                    if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
                    ex.close()
                }
                s.start()
            }
        val base get() = "http://127.0.0.1:${server.address.port}/voice"

        fun stop() {
            release.countDown()
            server.stop(0)
        }
    }

    private val fakes = ArrayList<Fake>()

    private fun fake(
        status: Int = 200,
        answer: String = """{"text":"dos"}""",
        location: String? = null,
        hold: Boolean = false,
    ) = Fake(status, answer, location, hold).also { fakes.add(it) }

    @After fun stopAll() = fakes.forEach { it.stop() }

    private val wav = ByteArray(200) { it.toByte() }

    private fun send(
        f: Fake,
        cancel: CancelToken = CancelToken(),
    ) = VoiceFlowClient(OrmusVoiceConfig.dictateUrl(f.base)).transcribe(wav, cancel)

    @Test fun theTakeIsPostedOnceAsAWavForAVerbatimText() {
        val f = fake()
        assertEquals(TranscribeResult.Text("dos"), send(f))
        val s = f.seen.single()
        assertEquals("POST", s.method)
        assertEquals("/voice/v1/dictate", s.path)
        assertEquals("style=verbatim", s.query)
        assertEquals("audio/wav", s.type)
        assertArrayEquals(wav, s.body)
    }

    @Test fun nothingHeardIsEmpty() {
        assertEquals(TranscribeResult.Empty, send(fake(answer = """{"text":"  "}""")))
        assertEquals(TranscribeResult.Empty, send(fake(answer = """{"text":"thank you","dropped":"junk"}""")))
        assertEquals(TranscribeResult.Text("hola"), send(fake(answer = """{"text":" hola ","dropped":null,"more":1}""")))
    }

    @Test fun anErrorOrABadAnswerIsAServerError() {
        assertEquals(TranscribeResult.ServerError(500), send(fake(status = 500, answer = "boom")))
        assertEquals(TranscribeResult.ServerError(200), send(fake(answer = "not json")))
    }

    /** A redirect would carry the audio elsewhere: it isn't followed, and the other server gets nothing. */
    @Test fun aRedirectIsNotFollowed() {
        for (status in listOf(301, 302, 303, 307, 308)) {
            val other = fake()
            val f = fake(status = status, answer = "", location = "${other.base}/v1/dictate")
            assertEquals(TranscribeResult.ServerError(status), send(f))
            assertTrue("$status", other.seen.isEmpty())
        }
    }

    @Test fun sgOutOfReachIsNotReachable() {
        val port = ServerSocket(0).use { it.localPort } // free, and nobody listens on it
        val result = VoiceFlowClient(OrmusVoiceConfig.dictateUrl("http://127.0.0.1:$port/voice")).transcribe(wav, CancelToken())
        assertEquals(TranscribeResult.NotReachable, result)
    }

    @Test fun aCancelEndsTheRequestAtOnce() {
        val f = fake(hold = true)
        val token = CancelToken()
        var result: TranscribeResult? = null
        val t = thread { result = send(f, token) }
        assertTrue(f.arrived.await(5, TimeUnit.SECONDS))
        token.cancel()
        t.join(5_000)
        assertFalse(t.isAlive)
        assertEquals(TranscribeResult.Cancelled, result)
        // Cancelled before it starts: no request at all.
        val g = fake()
        assertEquals(TranscribeResult.Cancelled, send(g, CancelToken().apply { cancel() }))
        assertTrue(g.seen.isEmpty())
    }

    @Test fun theWavIs16kMono16bit() {
        val pcm = PcmBuffer(1_000).apply { append(ByteArray(10) { 7 }, 0, 10) }
        val w = Wav.encode(pcm)
        assertEquals(44 + 10, w.size)
        assertEquals("RIFF", String(w, 0, 4))
        assertEquals("WAVE", String(w, 8, 4))
        assertEquals("data", String(w, 36, 4))
        fun le(at: Int, n: Int) = (0 until n).sumOf { (w[at + it].toInt() and 0xff) shl (8 * it) }
        assertEquals(1, le(20, 2)) // PCM
        assertEquals(1, le(22, 2)) // mono
        assertEquals(16_000, le(24, 4))
        assertEquals(16, le(34, 2))
        assertEquals(10, le(40, 4))
    }

    /** Only sg's voice: the one server address in the source, and no phone recognizer in the widgets' dictation. */
    @Test fun theOnlyServerIsSgAndThereIsNoFallback() {
        assertEquals("https://sg.tailb43a3a.ts.net/voice/v1/dictate?style=verbatim", OrmusVoiceConfig.dictateUrl())
        val root = File("src/main/kotlin/dev/omalauncher")
        val flow = File(root, "voice/OrmusVoiceFlow.kt").readText() + File(root, "voice/OrmusVoice.kt").readText()
        assertEquals(listOf("https://sg.tailb43a3a.ts.net/voice"), Regex("""https?://[^\s"]+""").findAll(flow).map { it.value }.toList())
        val dictation = File(root, "widgets/runtime/Dictation.kt").readText()
        assertTrue(dictation.contains("OrmusVoice("))
        assertFalse("no phone recognizer to fall back to", dictation.contains("Speech(context)"))
        assertFalse(dictation.contains("speech.start(hold"))
    }

    // The engine, with a recorder that hands over what the test gave it.

    private class FakeRecorder : Recorder {
        var starts = true
        var listener: TakeListener? = null
        var pcm: PcmBuffer? = null
        var cancelled = 0

        override fun start(listener: TakeListener): Boolean {
            if (!starts) return false
            this.listener = listener
            pcm = PcmBuffer(OrmusVoiceConfig.MAX_TAKE_MS / 1000 * 32_000)
            return true
        }

        fun speak(ms: Int) {
            val n = ms * 32
            pcm!!.append(ByteArray(n) { 1 }, 0, n)
            listener!!.onChunk(ms)
        }

        override fun stop() = pcm.also { pcm = null }

        override fun cancel() {
            cancelled++
            pcm?.wipe()
            pcm = null
        }
    }

    private class Sent(
        val wav: ByteArray,
        val token: CancelToken,
    )

    private val recorder = FakeRecorder()
    private val sent = ArrayList<Sent>()
    private var answer: TranscribeResult = TranscribeResult.Text("fix the tests")

    /** The request runs when the test says so: [queued] holds it, as a worker thread would. */
    private val queued = ArrayList<Runnable>()

    /** What the engine asked to run later, and after how many ms: [later] runs them as their time comes. */
    private val timers = ArrayList<Pair<Long, Runnable>>()

    private fun later() = timers.toList().also { timers.clear() }.forEach { it.second.run() }

    private fun voice(sync: Boolean = true) =
        OrmusVoice(
            recorder = recorder,
            transcriber = { w, t ->
                sent.add(Sent(w.copyOf(), t))
                answer
            },
            work = { if (sync) it.run() else queued.add(it) },
            post = { it.run() },
            postDelayed = { ms, r -> timers.add(ms to r) },
            text = { it.name },
        )

    @Test fun aTakeIsSentOnceAsItEndsAndItsTextIsHeard() {
        val v = voice()
        val heard = ArrayList<String>()
        v.start { heard.add(it) }
        assertTrue(v.listening)
        recorder.speak(400)
        recorder.speak(600)
        assertTrue("nothing goes out while it listens", sent.isEmpty())
        val pcm = recorder.pcm!!
        v.stop()
        assertFalse(v.listening)
        assertFalse(v.busy)
        assertEquals(listOf("fix the tests"), heard)
        assertEquals(44 + 1_000 * 32, sent.single().wav.size)
        assertTrue("the take's audio is wiped", pcm.isZeroed())
        assertNull(v.error)
        v.stop() // a second stop sends nothing
        assertEquals(1, sent.size)
    }

    @Test fun aTakeTooShortIsNotSent() {
        val v = voice()
        var heard = false
        v.start { heard = true }
        recorder.speak(200)
        v.stop()
        assertTrue(sent.isEmpty())
        assertFalse(heard)
        assertFalse(v.busy)
    }

    @Test fun sgOutOfReachHearsNothingAndSaysSo() {
        answer = TranscribeResult.NotReachable
        val v = voice()
        var heard = false
        v.start { heard = true }
        recorder.speak(1_000)
        v.stop()
        assertFalse(heard)
        assertEquals("NOT_REACHABLE", v.error)
        assertEquals(1, sent.size)
        answer = TranscribeResult.ServerError(500)
        v.start { heard = true }
        assertNull("a new take clears the error", v.error)
        recorder.speak(1_000)
        v.stop()
        assertEquals("SERVER", v.error)
        answer = TranscribeResult.Empty
        v.start { heard = true }
        recorder.speak(1_000)
        v.stop()
        assertFalse(heard)
        assertNull(v.error)
    }

    @Test fun busyUntilSgAnswersAndACancelDropsTheAnswer() {
        val v = voice(sync = false)
        val heard = ArrayList<String>()
        v.start { heard.add(it) }
        recorder.speak(1_000)
        v.stop()
        assertTrue(v.busy)
        queued.removeAt(0).run()
        assertFalse(v.busy)
        assertEquals(listOf("fix the tests"), heard)
        // Cancelled while sg works on it: the request is stopped, and its answer is never heard.
        v.start { heard.add(it) }
        recorder.speak(1_000)
        v.stop()
        v.cancel()
        assertFalse(v.busy)
        queued.removeAt(0).run()
        assertTrue(sent.last().token.isCancelled)
        assertEquals(1, heard.size)
    }

    /**
     * A late answer is dropped, never heard: past the deadline from the stop the request is stopped and the error says
     * so (the review: "one" said to agent A, answered after a detach, would press 1 at agent B's menu).
     */
    @Test fun anAnswerPastTheDeadlineIsDropped() {
        val v = voice(sync = false)
        val heard = ArrayList<String>()
        v.start { heard.add(it) }
        recorder.speak(1_000)
        v.stop()
        assertEquals(listOf(OrmusVoiceConfig.ANSWER_DEADLINE_MS.toLong()), timers.map { it.first })
        later()
        assertFalse(v.busy)
        assertEquals("TOO_SLOW", v.error)
        queued.removeAt(0).run()
        assertTrue(sent.single().token.isCancelled)
        assertTrue(heard.isEmpty())
        // In time, the deadline does nothing.
        v.start { heard.add(it) }
        recorder.speak(1_000)
        v.stop()
        queued.removeAt(0).run()
        assertEquals(listOf("fix the tests"), heard)
        later()
        assertNull(v.error)
        assertEquals(listOf("fix the tests"), heard)
    }

    /** A tap on the mic while sg works drops the take: its answer is never heard. */
    @Test fun aTapWhileBusyDropsTheTake() {
        val v = voice(sync = false)
        val heard = ArrayList<String>()
        v.tap { heard.add(it) }
        assertTrue(v.listening)
        recorder.speak(1_000)
        v.tap { heard.add("again") }
        assertTrue(v.busy)
        v.tap { heard.add("again") }
        assertFalse(v.busy)
        assertFalse("a tap while busy only drops, it starts no take", v.listening)
        queued.removeAt(0).run()
        assertTrue(heard.isEmpty())
        v.tap { heard.add(it) }
        assertTrue("then a tap starts a take", v.listening)
    }

    /** A crash in the client is an error, not the end of the send thread and the launcher. */
    @Test fun aClientThatThrowsIsAnError() {
        val v =
            OrmusVoice(recorder, { _, _ -> throw IllegalStateException("boom") }, { it.run() }, { it.run() }, { _, _ -> }, { it.name })
        var heard = false
        v.start { heard = true }
        recorder.speak(1_000)
        v.stop()
        assertFalse(heard)
        assertEquals("SERVER", v.error)
        assertFalse(v.busy)
    }

    @Test fun aNewTakeDropsTheAnswerOfTheOldOne() {
        val v = voice(sync = false)
        val heard = ArrayList<String>()
        v.start { heard.add("old: $it") }
        recorder.speak(1_000)
        v.stop()
        v.start { heard.add("new: $it") }
        queued.removeAt(0).run()
        assertTrue(sent.single().token.isCancelled)
        assertTrue(heard.isEmpty())
        recorder.speak(1_000)
        v.stop()
        queued.removeAt(0).run()
        assertEquals(listOf("new: fix the tests"), heard)
    }

    @Test fun aCancelWhileListeningSendsNothing() {
        val v = voice()
        var heard = false
        v.start { heard = true }
        recorder.speak(1_000)
        v.cancel()
        assertFalse(v.listening)
        assertEquals(1, recorder.cancelled)
        assertTrue(sent.isEmpty())
        assertFalse(heard)
    }

    @Test fun aMicThatDoesNotStartOrStopsSaysSo() {
        val v = voice()
        recorder.starts = false
        v.start { }
        assertFalse(v.listening)
        assertEquals("NO_MIC", v.error)
        recorder.starts = true
        var heard = false
        v.start { heard = true }
        recorder.speak(1_000)
        recorder.listener!!.onFailed()
        assertFalse(v.listening)
        assertEquals("MIC_STOPPED", v.error)
        assertTrue(sent.isEmpty())
        assertFalse(heard)
    }

    @Test fun aTakeEndsAndIsSentAtItsLongest() {
        val v = voice()
        val heard = ArrayList<String>()
        v.start { heard.add(it) }
        repeat(OrmusVoiceConfig.MAX_TAKE_MS / 1_000 - 1) { recorder.speak(1_000) }
        assertTrue(v.listening)
        recorder.speak(1_000)
        assertFalse(v.listening)
        assertEquals(1, sent.size)
        assertEquals(listOf("fix the tests"), heard)
    }
}
