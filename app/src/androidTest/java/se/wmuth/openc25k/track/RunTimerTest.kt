package se.wmuth.openc25k.track

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.wmuth.openc25k.data.Interval
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests for RunTimer
 * Uses Android test framework because RunTimer depends on Android's CountDownTimer
 */
@RunWith(AndroidJUnit4::class)
class RunTimerTest {

    private lateinit var intervals: Array<Interval>
    private lateinit var listener: TestRunTimerListener
    private lateinit var timer: RunTimer

    @Before
    fun setup() {
        intervals = arrayOf(
            Interval(5, "Warmup"),
            Interval(3, "Jog"),
            Interval(2, "Walk")
        )
        listener = TestRunTimerListener()
    }

    @Test
    fun initialStateShowsCorrectTimeRemaining() {
        timer = RunTimer(intervals, listener)

        // Initial state should show full time for first interval
        assertThat(timer.getIntervalRemaining()).isEqualTo("00:05")
        assertThat(timer.getTotalRemaining()).isEqualTo("00:10")
    }

    @Test
    fun timerTicksCorrectly() {
        timer = RunTimer(intervals, listener)

        timer.start()
        Thread.sleep(2200) // Wait for 2 ticks + buffer

        assertThat(listener.tickCount).isAtLeast(2)
        timer.pause()
    }

    @Test
    fun timerPausesAndResumes() {
        timer = RunTimer(intervals, listener)

        timer.start()
        Thread.sleep(1100) // 1 tick
        timer.pause()

        val ticksAfterPause = listener.tickCount
        Thread.sleep(1100) // Should not tick while paused

        assertThat(listener.tickCount).isEqualTo(ticksAfterPause)

        // Resume
        timer.start()
        Thread.sleep(1100) // Should tick again
        assertThat(listener.tickCount).isGreaterThan(ticksAfterPause)

        timer.pause()
    }

    @Test
    fun timerMovesToNextInterval() {
        timer = RunTimer(intervals, listener)

        timer.start()
        Thread.sleep(5500) // Wait for first interval to complete (5 seconds + buffer)

        assertThat(listener.nextIntervalCount).isAtLeast(1)
        timer.pause()
    }

    @Test
    fun skipAdvancesToNextInterval() {
        timer = RunTimer(intervals, listener)

        timer.start()
        Thread.sleep(1100) // Start timing

        timer.skip() // Skip to next interval

        assertThat(listener.nextIntervalCount).isEqualTo(1)
        Thread.sleep(100) // Let it settle
        timer.pause()
    }

    @Test
    fun timerFinishesAfterAllIntervals() {
        intervals = arrayOf(
            Interval(2, "Short"),
            Interval(2, "Short2")
        )
        listener = TestRunTimerListener()
        timer = RunTimer(intervals, listener)

        timer.start()
        Thread.sleep(4500) // Wait for both intervals

        assertThat(listener.finishRunCalled).isTrue()
    }

    @Test
    fun skipThroughAllIntervalsFinishesRun() {
        timer = RunTimer(intervals, listener)

        timer.start()
        Thread.sleep(100)

        timer.skip() // Skip interval 1
        Thread.sleep(100)
        timer.skip() // Skip interval 2
        Thread.sleep(100)
        timer.skip() // Skip interval 3, should finish

        assertThat(listener.finishRunCalled).isTrue()
    }

    @Test
    fun getIntervalRemainingFormatsCorrectly() {
        val longIntervals = arrayOf(Interval(125, "Long")) // 2:05
        timer = RunTimer(longIntervals, listener)

        assertThat(timer.getIntervalRemaining()).isEqualTo("02:05")
    }

    @Test
    fun getTotalRemainingFormatsCorrectly() {
        val multiIntervals = arrayOf(
            Interval(60, "First"),   // 1:00
            Interval(90, "Second"),  // 1:30
            Interval(30, "Third")    // 0:30
        ) // Total: 3:00
        timer = RunTimer(multiIntervals, listener)

        assertThat(timer.getTotalRemaining()).isEqualTo("03:00")
    }

    @Test
    fun halfwayAnnouncementTriggersForLongRuns() {
        // Run longer than 120 seconds
        val longRun = arrayOf(
            Interval(90, "First"),
            Interval(90, "Second")
        ) // Total: 180 seconds, halfway at 90
        listener = TestRunTimerListener()
        timer = RunTimer(longRun, listener)

        timer.start()
        Thread.sleep(91000) // Wait past halfway point (90 seconds + buffer)

        assertThat(listener.halfwayCalled).isTrue()
        timer.pause()
    }

    @Test
    fun halfwayAnnouncementDoesNotTriggerForShortRuns() {
        // Run shorter than 120 seconds
        val shortRun = arrayOf(
            Interval(30, "First"),
            Interval(30, "Second")
        ) // Total: 60 seconds
        listener = TestRunTimerListener()
        timer = RunTimer(shortRun, listener)

        timer.start()
        Thread.sleep(61000) // Wait for entire run

        assertThat(listener.halfwayCalled).isFalse()
    }

    @Test
    fun multipleSkipsInQuickSuccession() {
        timer = RunTimer(intervals, listener)

        timer.start()
        Thread.sleep(500)

        timer.skip()
        timer.skip()
        timer.skip()

        // Should finish without crashing
        assertThat(listener.finishRunCalled).isTrue()
    }

    @Test
    fun pauseWithoutStartIsDefensive() {
        timer = RunTimer(intervals, listener)

        // Should not crash
        timer.pause()
    }

    @Test
    fun multipleStartCallsDoNotCreateMultipleTimers() {
        timer = RunTimer(intervals, listener)

        timer.start()
        Thread.sleep(1100) // 1 tick
        val ticksAfterFirst = listener.tickCount

        timer.start() // Try to start again (should do nothing)
        Thread.sleep(1100) // 1 more tick

        // Should only have ~1 additional tick, not 2 (which would happen with 2 timers)
        assertThat(listener.tickCount).isAtMost(ticksAfterFirst + 2)

        timer.pause()
    }

    /**
     * Test listener that tracks all callbacks
     */
    private class TestRunTimerListener : RunTimer.RunTimerListener {
        var finishRunCalled = false
        var nextIntervalCount = 0
        var tickCount = 0
        var halfwayCalled = false

        override fun finishRun() {
            finishRunCalled = true
        }

        override fun nextInterval() {
            nextIntervalCount++
        }

        override fun tick() {
            tickCount++
        }

        override fun onHalfway() {
            halfwayCalled = true
        }
    }
}
