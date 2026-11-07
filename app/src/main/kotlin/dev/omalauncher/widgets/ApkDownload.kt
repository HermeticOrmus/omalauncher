package dev.omalauncher.widgets

import dev.omalauncher.hex
import dev.omalauncher.openHttp
import dev.omalauncher.widgets.lang.Compiler
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest

/**
 * `install(url, sha256)`: the download half, with no Android in it so it runs in a plain test. The APK comes over
 * https (or http from this machine: the lab's `adb reverse`), redirects followed as the widgets' fetches follow them,
 * into [fetch]'s target through a temporary file of its own beside it, its SHA-256 summed as it streams; the hash
 * must be the one given, the bytes as many as the server announced, under the cap and in time. Returns the hash.
 */
object ApkDownload {
    /** An APK over this is refused: the launcher's own is a tenth of it. */
    const val MAX_BYTES = 256L * 1024 * 1024

    /** A download slower than this, whole, is given up. */
    const val MAX_MS = 10L * 60 * 1000
    private val NAME = Regex("""[A-Za-z0-9_-][A-Za-z0-9._-]{0,99}\.apk""")

    /** The file name an APK URL gives, from its path alone (no query, no fragment); `app.apk` for one that gives none. */
    fun apkName(url: String): String = runCatching { URL(url).path.substringAfterLast('/') }.getOrNull()?.takeIf { NAME.matches(it) } ?: "app.apk"

    /** Why a download failed, for the user: a server that does not answer is said as such, the rest as the error says. */
    fun whyNot(
        error: Throwable,
        host: String,
    ): String =
        when (error) {
            is UnknownHostException, is ConnectException, is SocketTimeoutException -> "${host.ifBlank { "the server" }} doesn't answer"
            // The phone's own connection says these when a body ends early, before the length check runs.
            is ProtocolException, is EOFException -> "the download was cut short"
            else -> error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        }

    fun fetch(
        url: String,
        into: File,
        expected: String,
        maxBytes: Long = MAX_BYTES,
        maxMs: Long = MAX_MS,
        open: (URL) -> HttpURLConnection = { openHttp(it.toString(), 15_000, 60_000) },
    ): String {
        val want = expected.trim().lowercase()
        if (!Compiler.HEX64.matches(want)) throw IOException("no SHA-256 to check the file against")
        var at = allowed(URL(url))
        val deadline = System.nanoTime() + maxMs * 1_000_000
        var hops = 0
        while (true) {
            val c = open(at)
            c.instanceFollowRedirects = false
            c.useCaches = false
            var part: File? = null
            try {
                val code =
                    try {
                        c.responseCode
                    } catch (e: UnknownHostException) {
                        throw IOException("${at.host} doesn't answer", e)
                    } catch (e: ConnectException) {
                        throw IOException("${at.host} doesn't answer", e)
                    } catch (e: SocketTimeoutException) {
                        throw IOException("${at.host} doesn't answer", e)
                    }
                if (code in REDIRECTS) {
                    val to = c.getHeaderField("Location") ?: throw IOException("HTTP $code without a Location")
                    if (++hops > MAX_REDIRECTS) throw IOException("too many redirects")
                    at = allowed(redirect(code, at, URL(at, to), "GET", null).url)
                    continue
                }
                if (code != 200) throw IOException("HTTP $code for $at")
                val announced = c.contentLengthLong
                if (announced > maxBytes) throw IOException("over ${maxOf(1, maxBytes / (1024 * 1024))} MB, more than an app this launcher installs")
                into.parentFile?.mkdirs()
                // A file of this download's own: two downloads into one target never share a part.
                val tmp = File.createTempFile(into.name + ".", ".part", into.parentFile)
                part = tmp
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                tmp.outputStream().use { out ->
                    c.inputStream.use { input ->
                        val chunk = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) throw IOException("over ${maxOf(1, maxBytes / (1024 * 1024))} MB, more than an app this launcher installs")
                            if (System.nanoTime() > deadline) throw IOException("the download took over ${maxOf(1, maxMs / 60_000)} minutes")
                            digest.update(chunk, 0, n)
                            out.write(chunk, 0, n)
                        }
                    }
                }
                if (announced >= 0 && total != announced) throw IOException("the download was cut short ($total of $announced bytes)")
                val hash = digest.digest().hex()
                if (hash != want) throw IOException("the file is not the one listed (${hash.take(8)}, not ${want.take(8)})")
                // rename(2) puts it in place whole, over an earlier file if one is there.
                if (!tmp.renameTo(into)) throw IOException("could not keep ${into.name}")
                part = null
                return hash
            } finally {
                c.disconnect()
                part?.delete()
            }
        }
    }

    /** https anywhere; http only from this machine (the lab's `adb reverse`). */
    private fun allowed(u: URL): URL {
        if (u.protocol == "https" || (u.protocol == "http" && u.host.lowercase() in Compiler.LOOPBACK)) return u
        throw IOException("only https, or http from this phone: not ${u.protocol}://${u.host}")
    }
}
