package se.wmuth.openc25k.both

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
import android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
import android.media.MediaPlayer
import se.wmuth.openc25k.R
import se.wmuth.openc25k.audio.AudioFocusManager
import se.wmuth.openc25k.data.model.SoundType
import timber.log.Timber

/**
 * Used to create the beeping noise in the application
 * Uses MediaPlayer to play the raw mp3-file in the project
 *
 * @param pCon the context of the parent of the beeper
 * @param vol the initial volume of the beeper, 0.0 to 1.0
 * @param audioFocusManager manages audio focus for music ducking
 * @param soundType the type of sound to play (defaults to BEEP)
 * @constructor Creates beeper with standard attributes
 */
class Beeper(
    pCon: Context,
    vol: Float,
    private val audioFocusManager: AudioFocusManager,
    private val soundType: SoundType = SoundType.BEEP
) : MediaPlayer.OnCompletionListener {
    private var mp: MediaPlayer? = null
    private var playCount: UInt = 0u

    init {
        // Only initialize MediaPlayer if soundType is not NONE
        if (soundType != SoundType.NONE && soundType.resourceId != null) {
            try {
                val file: AssetFileDescriptor = pCon.resources.openRawResourceFd(soundType.resourceId)
                mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setContentType(CONTENT_TYPE_SONIFICATION)
                            .setUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .build()
                    )
                    setVolume(vol, vol)
                    setDataSource(file.fileDescriptor, file.startOffset, file.length)
                }
                mp?.prepare()
                mp?.setOnCompletionListener(this)
                file.close()
                Timber.d("Beeper initialized with sound: ${soundType.displayName}, volume: $vol")
            } catch (e: Exception) {
                Timber.e(e, "Error initializing Beeper with sound: ${soundType.displayName}")
            }
        } else {
            Timber.d("Beeper initialized with silent mode (NONE)")
        }
    }

    /**
     * Makes the Beeper beep
     * Does nothing if soundType is NONE
     */
    fun beep() {
        if (soundType == SoundType.NONE || mp == null) {
            Timber.d("Beep skipped (silent mode or no MediaPlayer)")
            return
        }

        try {
            audioFocusManager.requestFocus()
            mp?.start()
        } catch (e: Exception) {
            Timber.e(e, "Error playing beep")
        }
    }

    /**
     * Makes the Beeper beep a [number] of times
     */
    fun beepMultiple(number: UInt) {
        playCount = number - 1u
        beep()
    }

    override fun onCompletion(p0: MediaPlayer?) {
        if (playCount > 0u) {
            playCount--
            beep()
        } else {
            // Abandon focus when all beeps complete
            audioFocusManager.abandonFocus()
        }
    }

    /**
     * Releases the MediaPlayer resources
     * MUST be called when the Beeper is no longer needed to prevent memory leaks
     */
    fun release() {
        try {
            audioFocusManager.abandonFocus()
            mp?.release()
            mp = null
            Timber.d("Beeper released")
        } catch (e: Exception) {
            Timber.e(e, "Error releasing Beeper")
        }
    }
}