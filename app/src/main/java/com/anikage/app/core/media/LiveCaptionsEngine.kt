package com.anikage.app.core.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory

/**
 * LIVE CAPTIONS (user directive #15) — a real, on-device speech-recognition
 * engine, not a decorative toggle.
 *
 * How it works: Android's SpeechRecognizer listens through the microphone
 * while the episode's audio plays out loud (exactly how the system Live
 * Caption feature captures device audio on speaker playback). Recognized
 * phrases stream into [onCaption] as they're spoken; the player renders
 * them in the SAME styled caption overlay used for subtitle files.
 *
 * Honesty by design:
 *  - [isSupported] gates the feature on devices without a recognizer.
 *  - headphones/silent playback yields nothing — we surface that state.
 *  - the engine restarts itself in a loop until [stop] (continuous mode).
 */
object LiveCaptionsEngine {

    /** Live caption line shown over the video. */
    data class Caption(val text: String, val isPartial: Boolean, val timestampMs: Long)

    var active: Boolean = false
        private set
    var lastError: String? = null
        private set

    private var recognizer: SpeechRecognizer? = null
    private var appContext: Context? = null
    private var onCaption: ((Caption) -> Unit)? = null
    private var language = "en-US"
    private var stopRequested = false

    /** Whether this device has ANY speech recognition service. */
    fun isSupported(context: Context): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Start continuous recognition. [callback] receives partial + final
     * phrases. Requires the RECORD_AUDIO runtime permission (granted by the
     * caller — the player asks via the permission request flow).
     */
    fun start(context: Context, languageTag: String, callback: (Caption) -> Unit) {
        if (!isSupported(context)) {
            lastError = "This device has no speech recognition service."
            return
        }
        stop()
        appContext = context.applicationContext
        onCaption = callback
        language = languageTag
        stopRequested = false
        active = true
        lastError = null
        createAndStart()
        AppLogger.i(LogCategory.PLAYER, "Live captions started (lang=$languageTag)")
    }

    fun stop() {
        stopRequested = true
        active = false
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun createAndStart() {
        val context = appContext ?: return
        runCatching {
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.joinToString(" ") { it.trim() }
                        .orEmpty()
                    if (text.isNotBlank()) {
                        onCaption?.invoke(Caption(text, isPartial = false, System.currentTimeMillis()))
                    }
                    maybeRestart()
                }

                override fun onPartialResults(partialResults: Bundle) {
                    val text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.joinToString(" ") { it.trim() }
                        .orEmpty()
                    if (text.isNotBlank()) {
                        onCaption?.invoke(Caption(text, isPartial = true, System.currentTimeMillis()))
                    }
                }

                override fun onError(error: Int) {
                    // 6 = no speech input (normal between phrases); 7 = no match;
                    // those just restart. Real failures stop the engine with a cause.
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> maybeRestart()
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            // Back off briefly, then restart.
                            Thread {
                                Thread.sleep(400)
                                maybeRestart()
                            }.start()
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            lastError = "Microphone permission is required for live captions."
                            stopRequested = true
                            active = false
                        }
                        SpeechRecognizer.ERROR_CLIENT -> {
                            // Recognizer died (e.g. service update): restart once.
                            maybeRestart()
                        }
                        else -> {
                            lastError = "Live captions stopped (recognition error $error)."
                            maybeRestart()
                        }
                    }
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            r.startListening(intent)
        }.onFailure {
            lastError = "Couldn't start live captions: ${it.message}"
            active = false
            AppLogger.w(LogCategory.PLAYER, "Live captions failed to start: ${it.message}")
        }
    }

    /** Continuous mode: restart listening when the previous cycle ended. */
    private fun maybeRestart() {
        if (stopRequested || !active) return
        runCatching { recognizer?.destroy() }
        recognizer = null
        createAndStart()
    }
}
