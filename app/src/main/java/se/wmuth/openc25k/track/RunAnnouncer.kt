package se.wmuth.openc25k.track

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import se.wmuth.openc25k.audio.AudioFocusManager
import se.wmuth.openc25k.utils.DurationFormatter
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
    private var hasFocusForUtterance: Boolean = false
    private var currentUtteranceId: String? = null

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
                        synchronized(this@RunAnnouncer) {
                            // Only request focus if we don't already have it
                            if (!hasFocusForUtterance) {
                                val granted = audioFocusManager.requestFocus()
                                if (granted) {
                                    hasFocusForUtterance = true
                                    currentUtteranceId = utteranceId
                                    Timber.d("TTS started: $utteranceId, focus granted")
                                } else {
                                    Timber.w("TTS started: $utteranceId, focus denied")
                                }
                            } else {
                                Timber.d("TTS started: $utteranceId, reusing existing focus")
                            }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        synchronized(this@RunAnnouncer) {
                            // Only abandon focus if this utterance actually holds it
                            if (hasFocusForUtterance && utteranceId == currentUtteranceId) {
                                audioFocusManager.abandonFocus()
                                hasFocusForUtterance = false
                                currentUtteranceId = null
                                Timber.d("TTS done: $utteranceId, focus abandoned")
                            } else {
                                Timber.d("TTS done: $utteranceId, but focus already released or different utterance")
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        synchronized(this@RunAnnouncer) {
                            // Only abandon focus if this utterance actually holds it
                            if (hasFocusForUtterance && utteranceId == currentUtteranceId) {
                                audioFocusManager.abandonFocus()
                                hasFocusForUtterance = false
                                currentUtteranceId = null
                                Timber.e("TTS error: $utteranceId, focus abandoned")
                            } else {
                                Timber.e("TTS error: $utteranceId, but focus already released or different utterance")
                            }
                        }
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
     *
     * @param text The text to speak
     * @param queueMode QUEUE_FLUSH to interrupt current speech, QUEUE_ADD to queue after current
     */
    fun announce(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!isInitialized) {
            Timber.w("TTS not initialized, cannot announce: $text")
            return
        }

        tts?.speak(text, queueMode, null, text.hashCode().toString())
        Timber.d("TTS announce: $text (queueMode=${if (queueMode == TextToSpeech.QUEUE_FLUSH) "FLUSH" else "ADD"})")
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
     * Announces interval change with duration
     */
    fun announceIntervalWithDuration(intervalTitle: String, durationSeconds: Int) {
        val duration = DurationFormatter.toSpeech(durationSeconds)
        announce("Now $intervalTitle. $duration")
    }

    /**
     * Announces countdown (e.g., "Thirty seconds remaining")
     */
    fun announceCountdown(remainingSeconds: Int) {
        val time = DurationFormatter.toSpeech(remainingSeconds)
        announce("$time remaining")
    }

    /**
     * Announces motivational message
     */
    fun announceMotivation(message: String) {
        announce(message)
    }

    /**
     * Announces workout start with total duration
     */
    fun announceWorkoutStart(totalDurationSeconds: Int) {
        val duration = DurationFormatter.toSpeech(totalDurationSeconds)
        announce("Starting your workout. Total time: $duration")
    }

    /**
     * Stops any ongoing speech and releases audio focus if held
     */
    fun stop() {
        synchronized(this) {
            // Abandon focus BEFORE stopping TTS to prevent race with callbacks
            if (hasFocusForUtterance) {
                audioFocusManager.abandonFocus()
                hasFocusForUtterance = false
                currentUtteranceId = null
                Timber.d("TTS stopped, focus abandoned")
            }

            if (tts?.isSpeaking == true) {
                tts?.stop()
                Timber.d("TTS speech interrupted")
            }
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
