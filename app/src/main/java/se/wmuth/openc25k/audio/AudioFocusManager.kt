package se.wmuth.openc25k.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages audio focus to enable music ducking during announcements.
 *
 * When the app needs to play a beep or voice announcement, this manager
 * requests temporary audio focus, which causes other apps (like music players)
 * to lower their volume (duck) during the announcement.
 */
class AudioFocusManager(context: Context) {
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var focusRequest: AudioFocusRequest? = null
    private var focusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    private var hasFocus: Boolean = false
    private var refCount: Int = 0
    private var onFocusLost: (() -> Unit)? = null

    companion object {
        private const val TAG = "AudioFocusManager"
    }

    /**
     * Set a callback to be invoked when audio focus is permanently lost.
     * This allows audio players to stop/pause when another app takes focus.
     */
    fun setOnFocusLostListener(listener: () -> Unit) {
        onFocusLost = listener
    }

    /**
     * Get the current reference count for diagnostics and testing
     */
    fun getRefCount(): Int {
        synchronized(this) {
            return refCount
        }
    }

    /**
     * Check if we currently hold audio focus
     */
    fun hasFocus(): Boolean {
        synchronized(this) {
            return hasFocus
        }
    }

    /**
     * Request audio focus for a short announcement or beep.
     *
     * Uses reference counting to handle overlapping requests:
     * - First call actually requests focus from the system
     * - Subsequent calls increment a counter but don't re-request
     * - abandonFocus() must be called the same number of times
     *
     * Uses AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK which:
     * - Is appropriate for short interruptions
     * - Allows other apps to continue playing at reduced volume
     * - Is the standard for notifications and announcements
     *
     * @param durationHint The expected duration of the audio focus request.
     *                     Use AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK for
     *                     short announcements that allow other audio to duck.
     * @return true if focus was granted or already held, false otherwise
     */
    fun requestFocus(durationHint: Int = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK): Boolean {
        synchronized(this) {
            // If we already have focus, just increment the ref count
            if (hasFocus) {
                refCount++
                Log.d(TAG, "Audio focus already held, refCount=$refCount")
                return true
            }

            // Actually request focus from the system
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requestFocusApi26(durationHint)
            } else {
                requestFocusLegacy(durationHint)
            }

            if (result) {
                hasFocus = true
                refCount = 1
                Log.d(TAG, "Audio focus granted, refCount=$refCount")
            }

            return result
        }
    }

    /**
     * Abandon audio focus when announcement is complete.
     *
     * Uses reference counting to handle overlapping requests:
     * - Decrements the counter
     * - Only actually releases focus when counter reaches 0
     * - This allows multiple sounds to play without fighting for focus
     */
    fun abandonFocus() {
        synchronized(this) {
            if (!hasFocus) {
                Log.w(TAG, "abandonFocus called but we don't have focus - possible double-abandon")
                // Print stack trace to help debug where the extra abandon came from
                Log.w(TAG, "Stack trace:", Exception("abandonFocus without focus"))
                return
            }

            refCount--
            Log.d(TAG, "Decrementing focus refCount=$refCount")

            if (refCount < 0) {
                Log.e(TAG, "FOCUS LEAK: refCount went negative! This indicates more abandons than requests")
                Log.e(TAG, "Stack trace:", Exception("Negative refCount"))
            }

            // Only abandon when all requesters are done
            if (refCount <= 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    focusRequest?.let { request ->
                        val result = audioManager.abandonAudioFocusRequest(request)
                        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            Log.w(TAG, "Failed to abandon audio focus")
                        }
                        focusRequest = null
                    }
                } else {
                    focusChangeListener?.let { listener ->
                        @Suppress("DEPRECATION")
                        val result = audioManager.abandonAudioFocus(listener)
                        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            Log.w(TAG, "Failed to abandon audio focus")
                        }
                        focusChangeListener = null
                    }
                }

                hasFocus = false
                refCount = 0
                Log.d(TAG, "Audio focus abandoned")
            }
        }
    }

    /**
     * Request audio focus using Android O (API 26+) API.
     */
    private fun requestFocusApi26(durationHint: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        Log.d(TAG, "Audio focus gained")
                    }
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        Log.d(TAG, "Audio focus lost permanently")
                        synchronized(this) {
                            hasFocus = false
                            refCount = 0
                            focusRequest = null
                        }
                        onFocusLost?.invoke()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        Log.d(TAG, "Audio focus lost (transient) - another app needs focus temporarily")
                        // Don't reset hasFocus - we'll get it back
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        Log.d(TAG, "Audio focus lost (can duck) - should lower volume")
                        // For short sounds like beeps, we don't need to duck
                    }
                }
            }

            val request = AudioFocusRequest.Builder(durationHint)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(listener)
                .build()

            val result = audioManager.requestAudioFocus(request)

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                focusRequest = request
                Log.d(TAG, "Audio focus granted")
                return true
            } else {
                Log.w(TAG, "Audio focus denied")
                return false
            }
        }
        return false
    }

    /**
     * Request audio focus using legacy API (pre-API 26).
     */
    @Suppress("DEPRECATION")
    private fun requestFocusLegacy(durationHint: Int): Boolean {
        val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    Log.d(TAG, "Audio focus gained (legacy)")
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    Log.d(TAG, "Audio focus lost permanently (legacy)")
                    synchronized(this) {
                        hasFocus = false
                        refCount = 0
                        focusChangeListener = null
                    }
                    onFocusLost?.invoke()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    Log.d(TAG, "Audio focus lost (transient, legacy) - another app needs focus temporarily")
                    // Don't reset hasFocus - we'll get it back
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    Log.d(TAG, "Audio focus lost (can duck, legacy) - should lower volume")
                    // For short sounds like beeps, we don't need to duck
                }
            }
        }

        val result = audioManager.requestAudioFocus(
            listener,
            AudioManager.STREAM_MUSIC,
            durationHint
        )

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusChangeListener = listener
            Log.d(TAG, "Audio focus granted (legacy)")
            return true
        } else {
            Log.w(TAG, "Audio focus denied (legacy)")
            return false
        }
    }
}
