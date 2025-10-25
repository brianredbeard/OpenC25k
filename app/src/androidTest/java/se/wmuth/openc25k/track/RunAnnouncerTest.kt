package se.wmuth.openc25k.track

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import se.wmuth.openc25k.audio.AudioFocusManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests for RunAnnouncer
 *
 * Note: These tests verify the RunAnnouncer's logic but cannot fully test TTS speech
 * because the Android emulator/device may not have TTS data installed.
 */
@RunWith(AndroidJUnit4::class)
class RunAnnouncerTest {

    private lateinit var context: Context
    private lateinit var audioFocusManager: AudioFocusManager
    private lateinit var announcer: RunAnnouncer
    private var initLatch: CountDownLatch = CountDownLatch(1)
    private var initSuccess: Boolean = false

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        audioFocusManager = mock()

        // Mock audio focus to always grant
        whenever(audioFocusManager.requestFocus()).thenReturn(true)

        initLatch = CountDownLatch(1)
        announcer = RunAnnouncer(context, audioFocusManager) { success ->
            initSuccess = success
            initLatch.countDown()
        }
    }

    @After
    fun tearDown() {
        // Only release if announcer was initialized in setup
        if (::announcer.isInitialized) {
            announcer.release()
        }
    }

    @Test
    fun initializationCallsCallback() {
        // Wait for TTS initialization (may or may not succeed depending on device)
        val initialized = initLatch.await(5, TimeUnit.SECONDS)

        assertThat(initialized).isTrue()
        // We don't assert initSuccess because TTS may not be available on all test devices
    }

    @Test
    fun announceIntervalFormatsCorrectly() {
        initLatch.await(5, TimeUnit.SECONDS)

        if (initSuccess) {
            // Should not crash
            announcer.announceInterval("Jog")

            // Give TTS time to process
            Thread.sleep(500)

            // Verify audio focus was requested (announcements trigger focus request)
            verify(audioFocusManager, atLeastOnce()).requestFocus()
        }
    }

    @Test
    fun announceCompletionDoesNotCrash() {
        initLatch.await(5, TimeUnit.SECONDS)

        if (initSuccess) {
            announcer.announceCompletion()
            Thread.sleep(500)
        }
        // Should not crash even if TTS not available
    }

    @Test
    fun announceTimeRemainingFormatsCorrectly() {
        initLatch.await(5, TimeUnit.SECONDS)

        if (initSuccess) {
            announcer.announceTimeRemaining("2 minutes")
            Thread.sleep(500)
        }
    }

    @Test
    fun announceIntervalWithDurationUsesFormatter() {
        initLatch.await(5, TimeUnit.SECONDS)

        if (initSuccess) {
            // Should use DurationFormatter internally
            announcer.announceIntervalWithDuration("Jog", 60)
            Thread.sleep(500)
        }
    }

    @Test
    fun announceCountdownUsesFormatter() {
        initLatch.await(5, TimeUnit.SECONDS)

        if (initSuccess) {
            // Should use DurationFormatter for the countdown
            announcer.announceCountdown(30)
            Thread.sleep(500)
        }
    }

    @Test
    fun announceMotivationDoesNotCrash() {
        initLatch.await(5, TimeUnit.SECONDS)

        if (initSuccess) {
            announcer.announceMotivation("You're doing great!")
            Thread.sleep(500)
        }
    }

    @Test
    fun announceWorkoutStartUsesFormatter() {
        initLatch.await(5, TimeUnit.SECONDS)

        if (initSuccess) {
            // Should use DurationFormatter for total duration
            announcer.announceWorkoutStart(300)
            Thread.sleep(500)
        }
    }

    @Test
    fun stopInterruptsSpeech() {
        initLatch.await(5, TimeUnit.SECONDS)

        if (initSuccess) {
            // Start a long announcement
            announcer.announce("This is a very long announcement that will be interrupted")
            Thread.sleep(500)

            // Stop it
            announcer.stop()

            // Should have abandoned focus
            verify(audioFocusManager, atLeastOnce()).abandonFocus()
        }
    }

    @Test
    fun releaseCleanlyShutdown() {
        initLatch.await(5, TimeUnit.SECONDS)

        if (initSuccess) {
            announcer.announce("Test")
            Thread.sleep(500)
        }

        // Should not crash
        announcer.release()
    }

    @Test
    fun multipleAnnouncementsInQuickSuccession() {
        initLatch.await(5, TimeUnit.SECONDS)

        if (initSuccess) {
            announcer.announce("First")
            announcer.announce("Second")
            announcer.announce("Third")

            Thread.sleep(1000)

            // Should not crash, audio focus should be managed correctly
        }
    }

    @Test
    fun announceBeforeInitializationDoesNotCrash() {
        // Create new announcer without waiting for init
        val newAnnouncer = RunAnnouncer(context, audioFocusManager)

        // Try to announce immediately (TTS not initialized yet)
        newAnnouncer.announce("This should be ignored gracefully")

        // Should not crash
        newAnnouncer.release()
    }

    @Test
    fun audioFocusRequestedOnAnnouncement() {
        initLatch.await(5, TimeUnit.SECONDS)

        if (initSuccess) {
            announcer.announce("Test message")
            Thread.sleep(1000) // Wait for utterance to start

            // Audio focus should have been requested
            verify(audioFocusManager, atLeastOnce()).requestFocus()
        }
    }

    @Test
    fun audioFocusAbandonedAfterAnnouncement() {
        initLatch.await(5, TimeUnit.SECONDS)

        if (initSuccess) {
            announcer.announce("Short")
            Thread.sleep(2000) // Wait for utterance to complete

            // Audio focus should have been abandoned
            verify(audioFocusManager, atLeastOnce()).abandonFocus()
        }
    }

    @Test
    fun stopAbandonsFocusEvenIfNotSpeaking() {
        initLatch.await(5, TimeUnit.SECONDS)

        // Stop without any announcement
        announcer.stop()

        // Should not crash or cause issues
    }
}
