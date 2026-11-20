package dev.omalauncher.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One loudness reading per chunk, and a failure when the microphone stops. Both arrive on the main thread. */
interface TakeListener {
    fun onChunk(chunkMs: Int)

    /** The microphone stopped giving audio: another app took it, say. */
    fun onFailed()
}

interface Recorder {
    /** Starts a take; false when the microphone doesn't start. */
    fun start(listener: TakeListener): Boolean

    /** Ends the take and hands over its audio, or null when none runs. */
    fun stop(): PcmBuffer?

    /** Ends the take and wipes its audio. */
    fun cancel()
}

/** Records 16 kHz mono PCM into memory; the audio never goes to a file. */
class AudioRecorder(
    private val post: (Runnable) -> Unit,
) : Recorder {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var buffer: PcmBuffer? = null

    @Volatile private var running = false

    @SuppressLint("MissingPermission") // Dictation asks for RECORD_AUDIO first
    override fun start(listener: TakeListener): Boolean {
        if (running) return false
        val rate = OrmusVoiceConfig.SAMPLE_RATE
        val chunkBytes = rate * OrmusVoiceConfig.BYTES_PER_SAMPLE * OrmusVoiceConfig.CHUNK_MS / 1000
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val rec =
            try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, chunkBytes * 4))
            } catch (_: IllegalArgumentException) {
                return false
            } catch (_: SecurityException) {
                return false
            }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        val pcm = PcmBuffer(OrmusVoiceConfig.MAX_TAKE_MS / 1000 * rate * OrmusVoiceConfig.BYTES_PER_SAMPLE)
        try {
            rec.startRecording()
        } catch (_: IllegalStateException) {
            rec.release()
            return false
        }
        record = rec
        buffer = pcm
        running = true
        thread =
            Thread({
                val chunk = ByteArray(chunkBytes)
                while (running) {
                    val n = rec.read(chunk, 0, chunk.size)
                    if (n <= 0) {
                        if (running) post { listener.onFailed() }
                        break
                    }
                    pcm.append(chunk, 0, n)
                    val ms = n * 1000 / (rate * OrmusVoiceConfig.BYTES_PER_SAMPLE)
                    post { listener.onChunk(ms) }
                }
                chunk.fill(0)
            }, "ormus-voice-record").also { it.start() }
        return true
    }

    override fun stop(): PcmBuffer? {
        if (record == null) return null
        halt()
        return buffer.also { buffer = null }
    }

    override fun cancel() {
        if (record == null) return
        halt()
        buffer?.wipe()
        buffer = null
    }

    private fun halt() {
        running = false
        thread?.join(1_000)
        thread = null
        record?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
                // already stopped
            }
            it.release()
        }
        record = null
    }
}

/**
 * The widgets' dictation on Ormus's voice: a tap starts a take, a tap ends it, and the take goes once to sg; its text
 * is what was heard. While sg works on it [busy] is on. Nothing is heard when sg doesn't answer: [error] says so, and
 * no other recognizer is tried. The same surface as [dev.omalauncher.ui.voice.Speech] for [dev.omalauncher.widgets.runtime.Dictation].
 * Main thread only; [work] runs the request off it, [post] brings the answer back.
 */
class OrmusVoice(
    private val recorder: Recorder,
    private val transcriber: Transcriber,
    private val work: (Runnable) -> Unit,
    private val post: (Runnable) -> Unit,
    private val text: (Problem) -> String,
) {
    enum class Problem { NO_MIC, MIC_STOPPED, NOT_REACHABLE, SERVER }

    var listening by mutableStateOf(false)
        private set

    /** The take went to sg and its answer isn't back. */
    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)

    /** The take sent last; an answer for any other one is dropped. */
    private var token: CancelToken? = null
    private var onDone: ((String) -> Unit)? = null
    private var elapsedMs = 0

    /** Starts a take; [onDone] gets its text, once, unless it's cancelled or sg heard nothing. */
    fun start(onDone: (String) -> Unit) {
        cancel()
        error = null
        elapsedMs = 0
        this.onDone = onDone
        val listener =
            object : TakeListener {
                override fun onChunk(chunkMs: Int) {
                    if (!listening) return
                    elapsedMs += chunkMs
                    if (elapsedMs >= OrmusVoiceConfig.MAX_TAKE_MS) stop()
                }

                override fun onFailed() {
                    if (!listening) return
                    cancel()
                    error = text(Problem.MIC_STOPPED)
                }
            }
        if (!recorder.start(listener)) {
            this.onDone = null
            error = text(Problem.NO_MIC)
            return
        }
        listening = true
    }

    /** Ends the take and sends it; a take under [OrmusVoiceConfig.MIN_TAKE_MS] is dropped. */
    fun stop() {
        if (!listening) return
        listening = false
        val pcm = recorder.stop()
        val done = onDone
        onDone = null
        if (pcm == null || done == null || pcm.durationMs() < OrmusVoiceConfig.MIN_TAKE_MS) {
            pcm?.wipe()
            return
        }
        val wav = Wav.encode(pcm)
        pcm.wipe()
        val mine = CancelToken()
        token = mine
        busy = true
        work {
            val result = transcriber.transcribe(wav, mine)
            wav.fill(0)
            post { answer(mine, result, done) }
        }
    }

    /** Ends the take, or drops the answer still coming, and forgets it. */
    fun cancel() {
        onDone = null
        if (listening) {
            listening = false
            recorder.cancel()
        }
        token?.cancel()
        token = null
        busy = false
    }

    private fun answer(
        mine: CancelToken,
        result: TranscribeResult,
        done: (String) -> Unit,
    ) {
        if (token !== mine || mine.isCancelled) return
        token = null
        busy = false
        when (result) {
            is TranscribeResult.Text -> done(result.text)
            TranscribeResult.Empty, TranscribeResult.Cancelled -> {}
            TranscribeResult.NotReachable -> error = text(Problem.NOT_REACHABLE)
            is TranscribeResult.ServerError -> error = text(Problem.SERVER)
        }
    }
}
