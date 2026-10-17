package app.typix

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Built-in voice typing on top of the phone's speech recogniser.
 * - live partial text while speaking
 * - keeps listening after each sentence until the user taps the mic again (or stays silent for a while)
 * - start/stop beeps are muted while listening so continuous dictation is not a stream of beeps
 */
class VoiceInput(private val ctx: Context, private val cb: Callback) {
    interface Callback {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onListening(on: Boolean)
        fun onLevel(level: Float)                       // 0..1
        fun onMessage(msg: String, fatal: Boolean)
    }

    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var wantOn = false
    private var lang = "si-LK"
    private var silentRounds = 0
    private var muted = false
    private var lastPartial = ""
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val beepStreams = intArrayOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)

    val active: Boolean get() = wantOn
    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start(language: String) {
        lang = language; wantOn = true; silentRounds = 0
        muteBeeps(); cb.onListening(true); begin()
    }

    /** Switch language while listening (e.g. the user flips En / සිං). */
    fun setLanguage(language: String) {
        if (language == lang) return
        lang = language
        if (wantOn) { try { rec?.cancel() } catch (e: Exception) { }; again(150) }
    }

    /** Stop listening. Whatever partial text was showing is handed back through [Callback.onFinal]. */
    fun stop(flushPartial: Boolean = true) {
        if (!wantOn) return
        wantOn = false
        main.removeCallbacksAndMessages(null)
        val p = lastPartial; lastPartial = ""
        try { rec?.cancel() } catch (e: Exception) { }
        release(); unmuteBeeps(); cb.onListening(false)
        if (flushPartial && p.isNotBlank()) cb.onFinal(p) else if (p.isNotEmpty()) cb.onPartial("")
    }

    fun destroy() { wantOn = false; main.removeCallbacksAndMessages(null); release(); unmuteBeeps() }

    private fun release() { try { rec?.destroy() } catch (e: Exception) { }; rec = null }

    private fun begin() {
        if (!wantOn) return
        try {
            if (rec == null) rec = SpeechRecognizer.createSpeechRecognizer(ctx).also { it.setRecognitionListener(listener) }
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }
            rec?.startListening(i)
        } catch (e: Exception) {
            fail("Voice typing is not available", true)
        }
    }

    private fun fail(msg: String, fatal: Boolean) {
        cb.onMessage(msg, fatal)
        if (fatal) stop(false)
    }

    private fun again(delay: Long = 120) { if (!wantOn) return; main.removeCallbacksAndMessages(null); main.postDelayed({ begin() }, delay) }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { cb.onListening(true) }
        override fun onBeginningOfSpeech() { silentRounds = 0 }
        override fun onRmsChanged(rmsdB: Float) { cb.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            if (t.isNotEmpty() && wantOn) { lastPartial = t; cb.onPartial(t) }
        }

        override fun onResults(results: Bundle?) {
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
            lastPartial = ""
            if (t.isNotEmpty()) { silentRounds = 0; cb.onFinal(t) } else silentRounds++
            if (silentRounds >= 3) stop(false) else again()
        }

        override fun onError(error: Int) {
            if (!wantOn) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // nothing heard: keep the partial (if any), try again, give up after a few silent rounds
                    if (lastPartial.isNotBlank()) { val p = lastPartial; lastPartial = ""; cb.onFinal(p); silentRounds = 0 } else silentRounds++
                    if (silentRounds >= 3) { cb.onMessage("Stopped – nothing heard", false); stop(false) } else again()
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> { release(); again(400) }
                SpeechRecognizer.ERROR_CLIENT -> again(300)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Allow the microphone to use voice typing", true)
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                    fail("Voice typing needs an internet connection", true)
                11, 12, 13 -> fail("This language isn't supported by the phone's voice engine", true)   // SERVER_DISCONNECTED / LANGUAGE_NOT_SUPPORTED / LANGUAGE_UNAVAILABLE
                else -> { silentRounds++; if (silentRounds >= 3) fail("Voice typing stopped", true) else { release(); again(400) } }
            }
        }
    }

    private fun muteBeeps() {
        if (muted) return
        try { for (s in beepStreams) am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0); muted = true } catch (e: Exception) { }
    }
    private fun unmuteBeeps() {
        if (!muted) return
        try { for (s in beepStreams) am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0) } catch (e: Exception) { }
        muted = false
    }
}
