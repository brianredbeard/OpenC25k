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

    companion object {
        private const val TAG = "AudioFocusManager"
    }

    /**
     * Request audio focus for a short announcement or beep.
     *
     * Uses AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK which:
     * - Is appropriate for short interruptions
     * - Allows other apps to continue playing at reduced volume
     * - Is the standard for notifications and announcements
     *
     * @param durationHint The expected duration of the audio focus request.
     *                     Use AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK for
     *                     short announcements that allow other audio to duck.
     * @return true if focus was granted, false otherwise
     */
    fun requestFocus(durationHint: Int = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestFocusApi26(durationHint)
        } else {
            requestFocusLegacy(durationHint)
        }
    }

    /**
     * Abandon audio focus when announcement is complete.
     * This allows other apps to return to normal volume.
     */
    fun abandonFocus() {
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
                        Log.d(TAG, "Audio focus lost")
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        Log.d(TAG, "Audio focus lost (transient)")
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        Log.d(TAG, "Audio focus lost (can duck)")
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
                    Log.d(TAG, "Audio focus gained")
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    Log.d(TAG, "Audio focus lost")
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    Log.d(TAG, "Audio focus lost (transient)")
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    Log.d(TAG, "Audio focus lost (can duck)")
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
