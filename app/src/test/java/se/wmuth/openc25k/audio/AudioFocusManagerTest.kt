package se.wmuth.openc25k.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for AudioFocusManager using Robolectric
 *
 * Tests reference counting, focus lifecycle, and defensive behavior
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioFocusManagerTest {

    private lateinit var audioFocusManager: AudioFocusManager
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        audioFocusManager = AudioFocusManager(context)
    }

    @Test
    fun `single request and abandon cycle`() {
        // Initially should not have focus
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)

        // Request focus
        val granted = audioFocusManager.requestFocus()
        assertThat(granted).isTrue()
        assertThat(audioFocusManager.hasFocus()).isTrue()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(1)

        // Abandon focus
        audioFocusManager.abandonFocus()
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun `multiple overlapping requests use reference counting`() {
        // First request
        audioFocusManager.requestFocus()
        assertThat(audioFocusManager.hasFocus()).isTrue()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(1)

        // Second request (overlapping)
        audioFocusManager.requestFocus()
        assertThat(audioFocusManager.hasFocus()).isTrue()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(2)

        // Third request
        audioFocusManager.requestFocus()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(3)

        // First abandon - should still have focus
        audioFocusManager.abandonFocus()
        assertThat(audioFocusManager.hasFocus()).isTrue()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(2)

        // Second abandon - should still have focus
        audioFocusManager.abandonFocus()
        assertThat(audioFocusManager.hasFocus()).isTrue()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(1)

        // Third abandon - should release focus
        audioFocusManager.abandonFocus()
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun `abandon without request is defensive`() {
        // Should not crash or throw
        audioFocusManager.abandonFocus()
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun `multiple abandons without request is defensive`() {
        // Multiple abandons without any requests should not crash
        audioFocusManager.abandonFocus()
        audioFocusManager.abandonFocus()
        audioFocusManager.abandonFocus()
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun `too many abandons is handled defensively`() {
        // Request once
        audioFocusManager.requestFocus()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(1)

        // Abandon twice (one too many)
        audioFocusManager.abandonFocus()
        assertThat(audioFocusManager.hasFocus()).isFalse()
        audioFocusManager.abandonFocus() // Should not crash

        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun `request after abandon works correctly`() {
        // First cycle
        audioFocusManager.requestFocus()
        audioFocusManager.abandonFocus()
        assertThat(audioFocusManager.hasFocus()).isFalse()

        // Second cycle
        audioFocusManager.requestFocus()
        assertThat(audioFocusManager.hasFocus()).isTrue()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(1)

        audioFocusManager.abandonFocus()
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun `rapid request and abandon cycles`() {
        // Simulate rapid skipping scenario
        for (i in 1..10) {
            audioFocusManager.requestFocus()
            assertThat(audioFocusManager.hasFocus()).isTrue()
            audioFocusManager.abandonFocus()
            assertThat(audioFocusManager.hasFocus()).isFalse()
            assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
        }
    }

    @Test
    fun `concurrent requests from different sources`() {
        // Simulate beeper and TTS both requesting focus
        val beeper = audioFocusManager.requestFocus()
        assertThat(beeper).isTrue()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(1)

        val tts = audioFocusManager.requestFocus()
        assertThat(tts).isTrue()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(2)

        // TTS finishes first
        audioFocusManager.abandonFocus()
        assertThat(audioFocusManager.hasFocus()).isTrue()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(1)

        // Beeper finishes
        audioFocusManager.abandonFocus()
        assertThat(audioFocusManager.hasFocus()).isFalse()
        assertThat(audioFocusManager.getRefCount()).isEqualTo(0)
    }

    @Test
    fun `focus lost callback can be set`() {
        var callbackInvoked = false
        audioFocusManager.setOnFocusLostListener {
            callbackInvoked = true
        }

        // Request focus
        audioFocusManager.requestFocus()
        assertThat(audioFocusManager.hasFocus()).isTrue()

        // Note: Testing AUDIOFOCUS_LOSS callback requires simulating focus change events
        // This test verifies the listener can be set without error
        assertThat(callbackInvoked).isFalse()
    }
}
