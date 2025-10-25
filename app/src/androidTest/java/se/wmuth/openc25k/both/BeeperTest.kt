package se.wmuth.openc25k.both

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import se.wmuth.openc25k.audio.AudioFocusManager
import se.wmuth.openc25k.data.model.SoundType

/**
 * Integration tests for Beeper
 */
@RunWith(AndroidJUnit4::class)
class BeeperTest {

    private lateinit var context: Context
    private lateinit var audioFocusManager: AudioFocusManager
    private lateinit var beeper: Beeper

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        audioFocusManager = mock()

        // Mock audio focus to always grant
        whenever(audioFocusManager.requestFocus()).thenReturn(true)
    }

    @After
    fun tearDown() {
        // Only release if beeper was initialized in the test
        if (::beeper.isInitialized) {
            beeper.release()
        }
    }

    @Test
    fun initializesWithBeepSound() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        // Should initialize without crashing
        // Can't verify MediaPlayer state directly, but no exception = success
    }

    @Test
    fun initializesWithBellSound() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BELL_1)

        // Should initialize without crashing
    }

    @Test
    fun initializesWithNoneSound() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.NONE)

        // Should initialize without crashing, MediaPlayer should not be created
    }

    @Test
    fun singleBeepRequestsAudioFocus() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        beeper.beep()
        Thread.sleep(500) // Give beep time to start

        verify(audioFocusManager, times(1)).requestFocus()
    }

    @Test
    fun singleBeepAbandonsAudioFocusAfterCompletion() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        beeper.beep()
        Thread.sleep(1500) // Wait for beep to complete (~800ms + buffer)

        verify(audioFocusManager, times(1)).abandonFocus()
    }

    @Test
    fun multipleBeepsRequestFocusOnce() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        beeper.beepMultiple(3u)
        Thread.sleep(3000) // Wait for all beeps

        // Should only request focus once at the start of the sequence
        verify(audioFocusManager, times(1)).requestFocus()
    }

    @Test
    fun multipleBeepsAbandonFocusAfterSequence() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        beeper.beepMultiple(3u)
        Thread.sleep(3500) // Wait for all beeps to complete

        // Should abandon focus once after all beeps finish
        verify(audioFocusManager, times(1)).abandonFocus()
    }

    @Test
    fun stopAbandonsFocusMidSequence() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        beeper.beepMultiple(5u)
        Thread.sleep(1000) // Let it start

        beeper.stop()

        // Should abandon focus when stopped
        verify(audioFocusManager, atLeastOnce()).abandonFocus()
    }

    @Test
    fun beepWithNoneSoundDoesNotRequestFocus() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.NONE)

        beeper.beep()
        Thread.sleep(500)

        // Should NOT request focus since no sound plays
        verify(audioFocusManager, never()).requestFocus()
    }

    @Test
    fun beepMultipleWithZeroDoesNotCrash() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        // Should not crash
        beeper.beepMultiple(0u)

        // Should not request focus for zero beeps
        verify(audioFocusManager, never()).requestFocus()
    }

    @Test
    fun releaseStopsPlaybackAndAbandonsFocus() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        beeper.beepMultiple(5u)
        Thread.sleep(500)

        beeper.release()

        // Should abandon focus
        verify(audioFocusManager, atLeastOnce()).abandonFocus()
    }

    @Test
    fun consecutiveSingleBeepsWorkCorrectly() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        beeper.beep()
        Thread.sleep(1500)

        beeper.beep()
        Thread.sleep(1500)

        beeper.beep()
        Thread.sleep(1500)

        // Each beep should request and abandon focus
        verify(audioFocusManager, times(3)).requestFocus()
        verify(audioFocusManager, times(3)).abandonFocus()
    }

    @Test
    fun interruptingMultipleBeepsWithSingleBeep() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        beeper.beepMultiple(5u)
        Thread.sleep(1000)

        // Interrupt with single beep
        beeper.beep()
        Thread.sleep(1500)

        // Should have abandoned focus when stopped, then requested again for new beep
        verify(audioFocusManager, times(2)).requestFocus()
        verify(audioFocusManager, atLeast(2)).abandonFocus()
    }

    @Test
    fun differentSoundTypesPlayCorrectly() {
        // Test BEEP
        val beeperBeep = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)
        beeperBeep.beep()
        Thread.sleep(1500)
        beeperBeep.release()

        // Test BELL_1
        val beeperBell = Beeper(context, 0.5f, audioFocusManager, SoundType.BELL_1)
        beeperBell.beep()
        Thread.sleep(1500)
        beeperBell.release()

        // Neither should crash
    }

    @Test
    fun lowVolumeInitialization() {
        beeper = Beeper(context, 0.1f, audioFocusManager, SoundType.BEEP)

        beeper.beep()
        Thread.sleep(1500)

        // Should work with low volume
    }

    @Test
    fun highVolumeInitialization() {
        beeper = Beeper(context, 1.0f, audioFocusManager, SoundType.BEEP)

        beeper.beep()
        Thread.sleep(1500)

        // Should work with max volume
    }

    @Test
    fun stopWithoutBeepIsDefensive() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        // Should not crash when stopping without any beep
        beeper.stop()
    }

    @Test
    fun releaseWithoutBeepIsDefensive() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        // Should not crash
        beeper.release()
    }

    @Test
    fun audioFocusDeniedStillPlaysSound() {
        // Mock focus to be denied
        whenever(audioFocusManager.requestFocus()).thenReturn(false)

        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        // Should still attempt to play (doesn't throw exception)
        beeper.beep()
        Thread.sleep(1500)
    }
}
