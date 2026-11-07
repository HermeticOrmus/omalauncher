package dev.omalauncher.widgets

import com.sun.net.httpserver.HttpServer
import dev.omalauncher.sha256Hex
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** `install(url, sha256)`'s download against a server on this machine. */
class ApkDownloadTest {
    @Rule @JvmField val tmp = TemporaryFolder()

    private val bytes = ByteArray(300_000) { (it % 251).toByte() }
    private val hash = sha256Hex(bytes)
    private val other = ByteArray(300_000) { (it % 13).toByte() }
    private lateinit var server: HttpServer
    private val base get() = "http://127.0.0.1:${server.address.port}"

    /** A slow body: the first bytes (then [started] counts down), the rest once [go] is counted down. */
    private val go = CountDownLatch(1)
    private val started = CountDownLatch(1)
    private lateinit var pool: ExecutorService

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        pool = Executors.newCachedThreadPool()
        server.executor = pool
        server.createContext("/a.apk") { ex ->
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.createContext("/other.apk") { ex ->
            ex.sendResponseHeaders(200, other.size.toLong())
            ex.responseBody.use { it.write(other) }
        }
        server.createContext("/slow.apk") { ex ->
            ex.sendResponseHeaders(200, other.size.toLong())
            ex.responseBody.use {
                it.write(other, 0, 1000)
                it.flush()
                started.countDown()
                go.await(20, TimeUnit.SECONDS)
                it.write(other, 1000, other.size - 1000)
            }
        }
        server.createContext("/trickle.apk") { ex ->
            ex.sendResponseHeaders(200, other.size.toLong())
            ex.responseBody.use {
                var at = 0
                while (at < other.size) {
                    val n = minOf(1024, other.size - at)
                    it.write(other, at, n)
                    it.flush()
                    at += n
                    Thread.sleep(50)
                }
            }
        }
        server.createContext("/moved") { ex ->
            ex.responseHeaders.add("Location", "/a.apk")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        server.createContext("/loop") { ex ->
            ex.responseHeaders.add("Location", "/loop")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        server.createContext("/nowhere") { ex ->
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        server.createContext("/cached") { ex ->
            ex.responseHeaders.add("Location", "/a.apk")
            ex.sendResponseHeaders(304, -1)
            ex.close()
        }
        server.createContext("/gone") { ex ->
            ex.sendResponseHeaders(404, -1)
            ex.close()
        }
        server.start()
    }

    @After fun stop() {
        go.countDown()
        server.stop(0)
        pool.shutdownNow()
    }

    @Test fun theFileLandsWholeWhenItsHashIsTheOneListed() {
        val into = File(tmp.root, "apks/a.apk")
        assertEquals(hash, ApkDownload.fetch("$base/a.apk", into, " ${hash.uppercase()} "))
        assertArrayEquals(bytes, into.readBytes())
        assertEquals("no part file is left", listOf("a.apk"), into.parentFile!!.list()!!.toList())
    }

    @Test fun aFileOfExactlyTheCapIsTaken() {
        assertEquals(hash, ApkDownload.fetch("$base/a.apk", File(tmp.root, "a.apk"), hash, maxBytes = bytes.size.toLong()))
    }

    @Test fun anotherHashIsRefusedAndAnEarlierGoodFileStays() {
        val into = File(tmp.root, "a.apk")
        ApkDownload.fetch("$base/a.apk", into, hash)
        val e = assertThrows(IOException::class.java) { ApkDownload.fetch("$base/other.apk", into, hash) }
        assertTrue(e.message, e.message!!.contains("not the one listed"))
        assertArrayEquals("the earlier file is untouched", bytes, into.readBytes())
        assertEquals(listOf("a.apk"), tmp.root.list()!!.toList())
    }

    @Test fun noHashOrAMalformedOneIsRefusedBeforeAnyConnection() {
        var opened = 0
        for (bad in listOf("", "  ", "abc", "00".repeat(31), hash + "0", "zz".repeat(32))) {
            val e = assertThrows(IOException::class.java) { ApkDownload.fetch("$base/a.apk", File(tmp.root, "x.apk"), bad) { opened++; throw IllegalStateException("never") } }
            assertTrue(e.message, e.message!!.contains("no SHA-256"))
        }
        assertEquals(0, opened)
    }

    @Test fun aRedirectOnTheSameServerIsFollowed_aLoopIsNot() {
        assertEquals(hash, ApkDownload.fetch("$base/moved", File(tmp.root, "m.apk"), hash))
        var opened = 0
        val e = assertThrows(IOException::class.java) { ApkDownload.fetch("$base/loop", File(tmp.root, "l.apk"), hash) { opened++; openHttpFor(it) } }
        assertTrue(e.message, e.message!!.contains("too many redirects"))
        assertEquals("the first request and five hops", 6, opened)
        assertTrue(assertThrows(IOException::class.java) { ApkDownload.fetch("$base/nowhere", File(tmp.root, "n.apk"), hash) }.message!!.contains("without a Location"))
        // 304 is not a redirect the widgets follow: an error, as http() would say.
        assertTrue(assertThrows(IOException::class.java) { ApkDownload.fetch("$base/cached", File(tmp.root, "c.apk"), hash) }.message!!.startsWith("HTTP 304"))
    }

    @Test fun onlyHttpsOrThisMachine_beforeAnyConnection_alsoAfterARedirect() {
        var opened = 0
        for (url in listOf("http://pass.example/a.apk", "ftp://127.0.0.1/a.apk", "http://10.0.2.2/a.apk")) {
            val e = assertThrows(url, IOException::class.java) { ApkDownload.fetch(url, File(tmp.root, "x.apk"), hash) { opened++; throw IllegalStateException("never") } }
            assertTrue(e.message, e.message!!.contains("only https"))
        }
        assertEquals(0, opened)
        // A redirect from this machine to another over http is refused before the second connection.
        val e = assertThrows(IOException::class.java) {
            ApkDownload.fetch("$base/x", File(tmp.root, "x.apk"), hash) { u ->
                opened++
                if (u.path == "/x") fakeRedirect(302, "http://pass.example/a.apk") else throw IllegalStateException("never connects there")
            }
        }
        assertTrue(e.message, e.message!!.contains("only https"))
        assertEquals(1, opened)
        // From https down to http: refused by the shared redirect rule.
        val down = assertThrows(IOException::class.java) { ApkDownload.fetch("https://pass.example/x", File(tmp.root, "x.apk"), hash) { fakeRedirect(302, "http://pass.example/a.apk") } }
        assertTrue(down.message, down.message!!.contains("https to http"))
    }

    @Test fun anAnswerThatIsNotTheFileIsAnError() {
        val e = assertThrows(IOException::class.java) { ApkDownload.fetch("$base/gone", File(tmp.root, "g.apk"), hash) }
        assertTrue(e.message, e.message!!.startsWith("HTTP 404"))
        assertEquals(0, tmp.root.list()!!.size)
    }

    @Test fun aFileOverTheCapIsRefused_andNothingIsLeft() {
        val into = File(tmp.root, "big.apk")
        val e = assertThrows(IOException::class.java) { ApkDownload.fetch("$base/a.apk", into, hash, maxBytes = 1000) }
        assertTrue(e.message, e.message!!.contains("MB"))
        assertEquals(0, tmp.root.list()!!.size)
    }

    @Test fun aDownloadCutShortIsRefused() {
        // A connection whose body ends before the length it announced: the length check says so.
        val half =
            object : java.net.HttpURLConnection(java.net.URL("https://fake.test/a.apk")) {
                override fun connect() {}

                override fun disconnect() {}

                override fun usingProxy() = false

                override fun getResponseCode() = 200

                override fun getContentLengthLong() = bytes.size.toLong()

                override fun getInputStream() = java.io.ByteArrayInputStream(bytes, 0, bytes.size / 2)
            }
        val into = File(tmp.root, "h.apk")
        val e = assertThrows(IOException::class.java) { ApkDownload.fetch("https://fake.test/a.apk", into, hash) { half } }
        assertTrue(e.message, e.message!!.contains("cut short"))
        assertEquals(0, tmp.root.list()!!.size)
        // A real socket that closes early: the connection itself refuses, and the toast reads as cut short.
        val sock = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
        val t =
            thread {
                sock.accept().use { s ->
                    val request = StringBuilder()
                    while (!request.endsWith("\r\n\r\n")) {
                        val b = s.getInputStream().read()
                        if (b < 0) break
                        request.append(b.toChar())
                    }
                    s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    s.getOutputStream().write(bytes, 0, bytes.size / 2)
                    s.getOutputStream().flush()
                }
            }
        try {
            val real = assertThrows(IOException::class.java) { ApkDownload.fetch("http://127.0.0.1:${sock.localPort}/half.apk", into, hash) }
            assertTrue(real.message, listOf("cut short", "eof", "unexpected end").any { real.message!!.lowercase().contains(it) })
            assertEquals("the download was cut short", ApkDownload.whyNot(java.net.ProtocolException("unexpected end of stream"), "h"))
            assertEquals("the download was cut short", ApkDownload.whyNot(java.io.EOFException(), "h"))
            assertEquals(0, tmp.root.list()!!.size)
        } finally {
            t.join(5000)
            sock.close()
        }
    }

    @Test fun aSlowDownloadIsGivenUp_andOneAnnouncedOverTheCapIsRefusedBeforeReading() {
        val e = assertThrows(IOException::class.java) { ApkDownload.fetch("$base/trickle.apk", File(tmp.root, "s.apk"), sha256Hex(other), maxMs = 300) }
        assertTrue(e.message, e.message!!.contains("took over"))
        assertEquals(0, tmp.root.list()!!.size)
        val big = assertThrows(IOException::class.java) { ApkDownload.fetch("$base/a.apk", File(tmp.root, "b.apk"), hash, maxBytes = bytes.size - 1L) }
        assertTrue(big.message, big.message!!.contains("MB"))
    }

    @Test fun twoDownloadsIntoOneTargetNeverMixTheirBytes() {
        val into = File(tmp.root, "one.apk")
        val results = arrayOfNulls<Any>(2)
        // The slow one starts first and holds with its part file open; the quick one lands whole beside it; then the slow one finishes.
        val slow = thread { results[0] = runCatching { ApkDownload.fetch("$base/slow.apk", into, sha256Hex(other)) }.getOrElse { it } }
        assertTrue("the slow body started", started.await(10, TimeUnit.SECONDS))
        val end = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < end && tmp.root.list()!!.none { it.endsWith(".part") }) Thread.sleep(20)
        val slowPart = tmp.root.list()!!.single { it.endsWith(".part") }
        results[1] = runCatching { ApkDownload.fetch("$base/a.apk", into, hash) }.getOrElse { it }
        assertEquals(hash, results[1])
        assertArrayEquals(bytes, into.readBytes())
        assertEquals("the slow one's part is still beside the quick one's file", setOf("one.apk", slowPart), tmp.root.list()!!.toSet())
        go.countDown()
        slow.join(10_000)
        assertEquals(sha256Hex(other), results[0])
        assertArrayEquals("the last one to finish is in place, whole", other, into.readBytes())
        assertEquals("no part file is left", listOf("one.apk"), tmp.root.list()!!.toList())
    }

    @Test fun theNameComesFromThePathAlone() {
        assertEquals("a.apk", ApkDownload.apkName("https://h/x/a.apk?v=1/2"))
        assertEquals("a.apk", ApkDownload.apkName("https://h/a.apk#frag"))
        assertEquals("mercurio-v1.27.38-20261107.apk", ApkDownload.apkName("https://h/pass/mercurio/mercurio-v1.27.38-20261107.apk"))
        for (bad in listOf("https://h/", "https://h/..", "https://h/.apk", "https://h/a.zip", "https://h/" + "a".repeat(120) + ".apk", "not a url")) assertEquals(bad, "app.apk", ApkDownload.apkName(bad))
    }

    @Test fun whyNotSaysWhoDoesNotAnswer() {
        assertEquals("pass.test doesn't answer", ApkDownload.whyNot(UnknownHostException("pass.test"), "pass.test"))
        assertEquals("the server doesn't answer", ApkDownload.whyNot(ConnectException(), ""))
        assertEquals("HTTP 404 for x", ApkDownload.whyNot(IOException("HTTP 404 for x"), "h"))
        assertEquals("IllegalStateException", ApkDownload.whyNot(IllegalStateException(), "h"))
    }

    private fun openHttpFor(u: java.net.URL) = (u.openConnection() as java.net.HttpURLConnection).also { it.connectTimeout = 5000; it.readTimeout = 5000 }

    /** A connection that answers [code] and [location] without a network. */
    private fun fakeRedirect(
        code: Int,
        location: String,
    ): java.net.HttpURLConnection =
        object : java.net.HttpURLConnection(java.net.URL("https://fake.test/")) {
            override fun connect() {}

            override fun disconnect() {}

            override fun usingProxy() = false

            override fun getResponseCode() = code

            override fun getHeaderField(name: String?) = if (name == "Location") location else null
        }
}
