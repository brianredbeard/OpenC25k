package se.wmuth.openc25k.audio

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.wmuth.openc25k.both.Beeper
import se.wmuth.openc25k.data.model.SoundType

/**
 * Integration tests for audio focus management
 *
 * These tests verify real-world scenarios with actual Android components
 */
@RunWith(AndroidJUnit4::class)
class AudioFocusIntegrationTest {

    private lateinit var context: Context
    private lateinit var audioFocusManager: AudioFocusManager
    private var beeper: Beeper? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        audioFocusManager = AudioFocusManager(context)
    }

    @After
    fun teardown() {
        beeper?.release()
        beeper = null
        // Ensure focus is abandoned
        if (audioFocusManager.hasFocus()) {
            audioFocusManager.abandonFocus()
        }
    }

    @Test
    fun beeperSingleBeep_requestsAndReleasesAudioFocus() = runBlocking {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        // Initially no focus
        assertThat(audioFocusManager.hasFocus()).isFalse()

        // Start beep
        beeper?.beep()

        // Should have focus immediately after starting beep
        assertThat(audioFocusManager.hasFocus()).isTrue()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(1)

        // Wait for beep to complete (beep is typically short, ~500ms)
        delay(1000)

        // Focus should be released after beep completes
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun beeperMultipleBeeps_maintainsFocusThroughSequence() = runBlocking {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        // Start sequence of 3 beeps
        beeper?.beepMultiple(3u)

        // Should have focus during sequence
        assertThat(audioFocusManager.hasFocus()).isTrue()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(1)

        // Wait for all beeps to complete
        delay(2000)

        // Focus should be released after sequence completes
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun beeperInterrupted_releasesAudioFocus() = runBlocking {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        // Start sequence
        beeper?.beepMultiple(5u)
        assertThat(audioFocusManager.hasFocus()).isTrue()

        // Interrupt with stop
        delay(200) // Let first beep start
        beeper?.stop()

        // Focus should be released immediately
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun rapidBeepCalls_doNotLeakFocus() = runBlocking {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        // Simulate rapid skip button presses
        for (i in 1..5) {
            beeper?.beep()
            delay(50) // Very short delay between calls
        }

        // Let final beep complete
        delay(1000)

        // Focus should be completely released
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun beeperRelease_abandonsAnyHeldFocus() {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        // Start beep
        beeper?.beep()
        assertThat(audioFocusManager.hasFocus()).isTrue()

        // Release beeper immediately
        beeper?.release()

        // Focus should be abandoned
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun silentMode_doesNotRequestFocus() = runBlocking {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.NONE)

        // Beep in silent mode
        beeper?.beep()

        // Should never request focus
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)

        delay(500)

        // Still no focus
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun rapidSkipScenario_withIntervalChange() = runBlocking {
        beeper = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        // Simulate rapid interval changes (user pressing skip button repeatedly)
        // Each interval change: stop -> beep
        for (i in 1..10) {
            beeper?.stop()
            if (i % 2 == 0) {
                beeper?.beepMultiple(2u) // Jog
            } else {
                beeper?.beep() // Walk
            }
            delay(100) // Very rapid skipping
        }

        // Let final sequence complete
        delay(1500)

        // No focus leak
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun multipleBeepers_shareAudioFocusManager() = runBlocking {
        val beeper1 = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)
        val beeper2 = Beeper(context, 0.5f, audioFocusManager, SoundType.BEEP)

        try {
            // Both beepers use same focus manager
            beeper1.beep()
            assertThat(audioFocusManager.hasFocus()).isTrue()

            // Second beeper starts before first finishes
            delay(100)
            beeper2.beep()

            // Both share focus (reference counting)
            // Note: Each beeper's beep() calls stop() first, so this will actually
            // stop beeper1 and start beeper2
            assertThat(audioFocusManager.hasFocus()).isTrue()

            // Let beeper2 complete
            delay(1000)

            assertThat(audioFocusManager.hasFocus()).isFalse()
            assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
        } finally {
            beeper1.release()
            beeper2.release()
        }
    }
}
