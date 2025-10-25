package se.wmuth.openc25k.track

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import se.wmuth.openc25k.audio.AudioFocusManager
import timber.log.Timber
import java.util.Locale

/**
 * Handles text-to-speech announcements during run tracking
 */
class RunAnnouncer(
    context: Context,
    private val audioFocusManager: AudioFocusManager,
    private val onInitialized: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { textToSpeech ->
                val result = textToSpeech.setLanguage(Locale.getDefault())

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Timber.w("TTS language not supported: ${Locale.getDefault()}")
                    // Try English as fallback
                    textToSpeech.setLanguage(Locale.ENGLISH)
                }

                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        audioFocusManager.requestFocus()
                        Timber.d("TTS started: $utteranceId")
                    }

                    override fun onDone(utteranceId: String?) {
                        audioFocusManager.abandonFocus()
                        Timber.d("TTS done: $utteranceId")
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        audioFocusManager.abandonFocus()
                        Timber.e("TTS error: $utteranceId")
                    }
                })

                isInitialized = true
                onInitialized(true)
                Timber.d("TTS initialized successfully")
            }
        } else {
            Timber.e("TTS initialization failed")
            isInitialized = false
            onInitialized(false)
        }
    }

    /**
     * Announces text via TTS
     */
    fun announce(text: String) {
        if (!isInitialized) {
            Timber.w("TTS not initialized, cannot announce: $text")
            return
        }

        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }

    /**
     * Announces interval change
     */
    fun announceInterval(intervalTitle: String) {
        announce("Now $intervalTitle")
    }

    /**
     * Announces run completion
     */
    fun announceCompletion() {
        announce("Run completed! Great job!")
    }

    /**
     * Announces time remaining (e.g., "1 minute remaining")
     */
    fun announceTimeRemaining(timeString: String) {
        announce("$timeString remaining")
    }

    /**
     * Stops any ongoing speech
     */
    fun stop() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
            audioFocusManager.abandonFocus()
        }
    }

    /**
     * Releases TTS resources
     */
    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Timber.d("TTS released")
    }
}
