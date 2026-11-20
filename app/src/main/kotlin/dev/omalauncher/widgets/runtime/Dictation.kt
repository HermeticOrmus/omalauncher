package dev.omalauncher.widgets.runtime

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.omalauncher.R
import dev.omalauncher.ui.voice.Speech
import dev.omalauncher.voice.AudioRecorder
import dev.omalauncher.voice.OrmusVoice
import dev.omalauncher.voice.VoiceFlowClient
import dev.omalauncher.widgets.lang.Null
import dev.omalauncher.widgets.lang.Obj
import dev.omalauncher.widgets.lang.Str
import dev.omalauncher.widgets.lang.Value
import dev.omalauncher.widgets.lang.bool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.Executors
import dev.omalauncher.system.hasPermission

/**
 * Dictation for widgets: `source said = android.speech` and `speech("start")`. One widget listens at a time, the one
 * that started last, and each sees only its own: the others read it as not listening, with nothing heard. It listens
 * across pauses until stopped, and stops as its widget stops reading the source: its screen closed, home went away.
 * The prompt bar has a [Speech] of its own; one starting cancels the other (both ways: [Speech.beforeStart]). Main
 * thread only.
 *
 * Omalauncher Ormus: the widgets hear with Ormus's voice ([OrmusVoice], voice-flow-api on sg), not the phone's
 * recognizer, and with no fallback to it: when sg doesn't answer, nothing is heard and `error` says so.
 */
class Dictation(
    private val context: Context,
) {
    private val main = Handler(Looper.getMainLooper())
    private val speech =
        OrmusVoice(
            recorder = AudioRecorder(main::post),
            transcriber = VoiceFlowClient(),
            work = Executors.newSingleThreadExecutor { Thread(it, "ormus-voice-send") }::execute,
            post = main::post,
            postDelayed = { ms, r -> main.postDelayed(r, ms) },
            text = { context.getString(it.string) },
        )

    init {
        // The phone has one microphone: the prompt bar's recognizer, starting, ends a widget's take.
        Speech.beforeStart = { speech.cancel() }
    }

    private val OrmusVoice.Problem.string
        get() =
            when (this) {
                OrmusVoice.Problem.NO_MIC -> R.string.ormus_voice_no_mic
                OrmusVoice.Problem.MIC_STOPPED -> R.string.ormus_voice_mic_stopped
                OrmusVoice.Problem.NOT_REACHABLE -> R.string.ormus_voice_not_reachable
                OrmusVoice.Problem.SERVER -> R.string.ormus_voice_server
                OrmusVoice.Problem.TOO_SLOW -> R.string.ormus_voice_too_slow
            }

    /**
     * The widget listening, or that listened last; who [heard] and the source's text belong to. State, like the rest:
     * the others' sources read nothing else of it, and must see it become theirs.
     */
    private var owner by mutableStateOf<Any?>(null)

    /** The whole text of [owner]'s last dictation once it ended, "" from its start: `on said.final` runs once for each. */
    private var final by mutableStateOf("")

    /** Asks the activity in front for the microphone, then says whether it was granted; set as it resumes. */
    var askMic: ((granted: (Boolean) -> Unit) -> Unit)? = null

    /** What [owner]'s last dictation heard, once it ended; what lets its `on said.final` type it, see WidgetInstance. */
    fun heard(owner: Any) = final.takeIf { this.owner === owner && it.isNotEmpty() }

    /** android.speech for [owner]; when it stops reading, its dictation stops too. */
    fun updates(owner: Any): Flow<Value> {
        return snapshotFlow { value(owner) }.onCompletion {
            if (this@Dictation.owner !== owner) return@onCompletion
            speech.cancel()
            // Not kept for a widget that may be gone for good: one that reads again starts with nothing heard.
            this@Dictation.owner = null
            final = ""
        }
    }

    /** Ormus's voice is always there to try: whether sg answers shows only as a take is sent. */
    fun value(owner: Any): Value {
        val mine = this.owner === owner
        return Obj(
            mapOf(
                "available" to bool(true),
                "listening" to bool(mine && speech.listening),
                // sg answers once, as the take ends: nothing is heard while it listens.
                "text" to Str(""),
                "busy" to bool(mine && speech.busy),
                "final" to Str(if (mine) final else ""),
                "error" to (speech.error?.takeIf { mine }?.let(::Str) ?: Null),
            ),
        )
    }

    /** speech("start"), "stop", "toggle" or "cancel" from [owner]'s tap. */
    fun act(
        owner: Any,
        word: String,
    ) {
        val mine = this.owner === owner && speech.listening
        when (word) {
            "start" -> start(owner)
            // A tap while it listens ends the take; while sg works on it, drops it (OrmusVoice.tap).
            "toggle" -> if (this.owner === owner && (speech.listening || speech.busy)) speech.tap { } else start(owner)
            "stop" -> if (mine) speech.stop()
            "cancel" -> if (this.owner === owner) speech.cancel()
        }
    }

    private fun start(owner: Any) {
        if (!context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            val ask = askMic
            if (ask != null) {
                ask { granted -> if (granted) listen(owner) else denied(owner) }
            } else {
                denied(owner)
            }
            return
        }
        listen(owner)
    }

    private fun listen(owner: Any) {
        this.owner = owner
        final = ""
        // The phone has one microphone: the prompt bar's recognizer stops for the take.
        Speech.cancelActive()
        speech.start { heard -> if (this.owner === owner) final = heard }
    }

    private fun denied(owner: Any) {
        speech.cancel()
        this.owner = owner
        final = ""
        speech.error = context.getString(R.string.prompt_speech_mic_permission)
    }
}
