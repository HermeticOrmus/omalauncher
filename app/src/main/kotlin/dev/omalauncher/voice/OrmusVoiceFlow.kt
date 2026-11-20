package dev.omalauncher.voice

import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ormus's own voice for the launcher's dictation: one take, recorded in memory, sent once to `voice-flow-api` on sg,
 * the engine of his desktop dictation (voice-flow) and of Ormus Keys Voice, ported from that app (`ormus-keys-voice`,
 * his code). No other server and no fallback: when sg doesn't answer, nothing is heard. Omalauncher Ormus only.
 */
object OrmusVoiceConfig {
    /** `voice-flow-api` on sg, over HTTPS by `tailscale serve`: tailnet only. The only server address of the voice. */
    const val BASE_URL = "https://sg.tailb43a3a.ts.net/voice"

    /** Sent on every request. The API's default is `prompt`, which rewrites the text with a model. */
    const val STYLE = "verbatim"

    const val SAMPLE_RATE = 16_000
    const val BYTES_PER_SAMPLE = 2
    const val CHUNK_MS = 100

    /** A take ends here and is sent. 120 s of 16 kHz mono 16-bit audio is 3.84 MB. */
    const val MAX_TAKE_MS = 120_000

    /** A take shorter than this is dropped without a request: a tap and a tap again. */
    const val MIN_TAKE_MS = 300

    const val CONNECT_TIMEOUT_MS = 5_000
    const val READ_TIMEOUT_MS = 60_000
    const val MAX_RESPONSE_BYTES = 1 shl 20

    fun dictateUrl(base: String = BASE_URL) = "$base/v1/dictate?style=$STYLE"
}

/** The audio of one take, in memory only, never in a file. [wipe] overwrites it with zeros. */
class PcmBuffer(
    private val maxBytes: Int,
) {
    private var data = ByteArray(64 * 1024)

    var size = 0
        private set

    /** Whether the take reached [maxBytes]: what comes after is dropped. */
    val full get() = size >= maxBytes

    /** Appends up to [len] bytes and returns how many fit under the cap. */
    fun append(
        src: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        val take = minOf(len, maxBytes - size)
        if (take <= 0) return 0
        if (size + take > data.size) {
            val bigger = ByteArray(minOf(maxBytes, maxOf(data.size * 2, size + take)))
            System.arraycopy(data, 0, bigger, 0, size)
            data.fill(0)
            data = bigger
        }
        System.arraycopy(src, off, data, size, take)
        size += take
        return take
    }

    fun durationMs(): Long = size * 1000L / (OrmusVoiceConfig.SAMPLE_RATE * OrmusVoiceConfig.BYTES_PER_SAMPLE)

    fun copyTo(
        out: ByteArray,
        offset: Int,
    ) = System.arraycopy(data, 0, out, offset, size)

    /** For the tests: every byte is zero. */
    internal fun isZeroed() = data.all { it == 0.toByte() }

    fun wipe() {
        data.fill(0)
        size = 0
    }
}

/** A 16 kHz mono 16-bit PCM WAV, built in memory: what `voice-flow-api` takes. */
object Wav {
    const val HEADER_BYTES = 44

    fun encode(
        pcm: PcmBuffer,
        sampleRate: Int = OrmusVoiceConfig.SAMPLE_RATE,
    ): ByteArray {
        val dataLen = pcm.size
        val out = ByteArray(HEADER_BYTES + dataLen)

        fun ascii(
            at: Int,
            s: String,
        ) = s.forEachIndexed { i, c -> out[at + i] = c.code.toByte() }

        fun le(
            at: Int,
            v: Int,
            bytes: Int,
        ) = repeat(bytes) { out[at + it] = (v shr (8 * it)).toByte() }
        ascii(0, "RIFF")
        le(4, 36 + dataLen, 4)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        le(16, 16, 4)
        le(20, 1, 2) // PCM
        le(22, 1, 2) // mono
        le(24, sampleRate, 4)
        le(28, sampleRate * OrmusVoiceConfig.BYTES_PER_SAMPLE, 4)
        le(32, OrmusVoiceConfig.BYTES_PER_SAMPLE, 2)
        le(34, 16, 2)
        ascii(36, "data")
        le(40, dataLen, 4)
        pcm.copyTo(out, HEADER_BYTES)
        return out
    }
}

/** Lets the main thread stop a request a worker runs: [cancel] runs the registered action, or runs it as it's registered. */
class CancelToken {
    private val lock = Any()
    private var cancelled = false
    private var action: (() -> Unit)? = null

    val isCancelled get() = synchronized(lock) { cancelled }

    fun cancel() {
        val run =
            synchronized(lock) {
                if (cancelled) {
                    null
                } else {
                    cancelled = true
                    action
                }
            }
        run?.invoke()
    }

    fun onCancel(run: () -> Unit) {
        val now =
            synchronized(lock) {
                if (cancelled) {
                    true
                } else {
                    action = run
                    false
                }
            }
        if (now) run()
    }
}

sealed interface TranscribeResult {
    data class Text(
        val text: String,
    ) : TranscribeResult

    /** The take was cancelled while the request ran; the request was stopped. */
    data object Cancelled : TranscribeResult

    /** sg heard nothing: a junk take, or an empty text. */
    data object Empty : TranscribeResult

    /** sg could not be reached or did not answer. Nothing was sent anywhere else. */
    data object NotReachable : TranscribeResult

    /** sg answered, but not with a text: an error status, or an answer that isn't valid. */
    data class ServerError(
        val code: Int,
    ) : TranscribeResult
}

fun interface Transcriber {
    /** Sends the take; once [cancel] is cancelled the request stops and the result is [TranscribeResult.Cancelled]. */
    fun transcribe(
        wav: ByteArray,
        cancel: CancelToken,
    ): TranscribeResult
}

/** Posts one take to `voice-flow-api` and reads its text. One request per take: no retry, no second server, no redirect. */
class VoiceFlowClient(
    private val url: String = OrmusVoiceConfig.dictateUrl(),
) : Transcriber {
    override fun transcribe(
        wav: ByteArray,
        cancel: CancelToken,
    ): TranscribeResult {
        if (cancel.isCancelled) return TranscribeResult.Cancelled
        var conn: HttpURLConnection? = null
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            cancel.onCancel { c.disconnect() } // closes the socket: a blocked read or write ends at once
            c.requestMethod = "POST"
            c.doOutput = true
            c.instanceFollowRedirects = false // a redirect must never carry the audio elsewhere
            c.useCaches = false
            c.connectTimeout = OrmusVoiceConfig.CONNECT_TIMEOUT_MS
            c.readTimeout = OrmusVoiceConfig.READ_TIMEOUT_MS
            c.setFixedLengthStreamingMode(wav.size)
            c.setRequestProperty("Content-Type", "audio/wav")
            c.outputStream.use { it.write(wav) }
            val code = c.responseCode
            if (code != HttpURLConnection.HTTP_OK) return TranscribeResult.ServerError(code)
            val body = c.inputStream.use { readCapped(it, OrmusVoiceConfig.MAX_RESPONSE_BYTES) }
            return parse(String(body, Charsets.UTF_8))
        } catch (_: IOException) {
            return if (cancel.isCancelled) TranscribeResult.Cancelled else TranscribeResult.NotReachable
        } finally {
            conn?.disconnect()
        }
    }

    private fun readCapped(
        input: InputStream,
        cap: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (out.size() < cap) {
            val n = input.read(chunk, 0, minOf(chunk.size, cap - out.size()))
            if (n < 0) break
            out.write(chunk, 0, n)
        }
        return out.toByteArray()
    }

    private fun parse(body: String): TranscribeResult {
        val answer =
            try {
                JSONObject(body)
            } catch (_: JSONException) {
                return TranscribeResult.ServerError(HttpURLConnection.HTTP_OK)
            }
        val text = answer.optString("text", "").trim()
        val dropped = answer.has("dropped") && !answer.isNull("dropped")
        return if (dropped || text.isEmpty()) TranscribeResult.Empty else TranscribeResult.Text(text)
    }
}
