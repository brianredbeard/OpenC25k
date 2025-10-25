package se.wmuth.openc25k.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import se.wmuth.openc25k.data.Interval
import se.wmuth.openc25k.data.Run
import se.wmuth.openc25k.data.model.RunProgress
import se.wmuth.openc25k.main.DataHandler

/**
 * Unit tests for ProgressRepository
 */
class ProgressRepositoryTest {

    private lateinit var dataHandler: DataHandler
    private lateinit var repository: ProgressRepository
    private lateinit var testRuns: Array<Run>

    @Before
    fun setup() {
        dataHandler = mock()
        repository = ProgressRepository(dataHandler)

        // Create test runs
        testRuns = arrayOf(
            Run("Week 1 Day 1", "Test", false, arrayOf(Interval(60, "Jog"))),
            Run("Week 1 Day 2", "Test", false, arrayOf(Interval(60, "Jog"))),
            Run("Week 1 Day 3", "Test", false, arrayOf(Interval(60, "Jog"))),
            Run("Week 2 Day 1", "Test", false, arrayOf(Interval(60, "Jog"))),
        )
    }

    @Test
    fun `getAllProgress returns progress from DataHandler`() {
        val expectedProgress = Array(27) { RunProgress() }
        whenever(dataHandler.getRunProgress()).thenReturn(expectedProgress)

        val result = repository.getAllProgress()

        assertThat(result).isEqualTo(expectedProgress)
        verify(dataHandler).getRunProgress()
    }

    @Test
    fun `getProgress returns specific run progress`() {
        val allProgress = arrayOf(
            RunProgress(1, 1000L),
            RunProgress(2, 2000L),
            RunProgress(0, null)
        )
        whenever(dataHandler.getRunProgress()).thenReturn(allProgress)

        val result = repository.getProgress(1)

        assertThat(result.completionCount).isEqualTo(2)
        assertThat(result.lastCompletedDate).isEqualTo(2000L)
    }

    @Test
    fun `getProgress returns empty RunProgress for invalid index`() {
        val allProgress = arrayOf(RunProgress(1, 1000L))
        whenever(dataHandler.getRunProgress()).thenReturn(allProgress)

        val result = repository.getProgress(5)

        assertThat(result.completionCount).isEqualTo(0)
        assertThat(result.lastCompletedDate).isNull()
    }

    @Test
    fun `recordCompletion increments count and sets last run index`() {
        repository.recordCompletion(2)

        verify(dataHandler).incrementRunCompletion(2)
        verify(dataHandler).setLastRunIndex(2)
    }

    @Test
    fun `getNextRecommendedRun returns next after last run`() {
        whenever(dataHandler.getLastRunIndex()).thenReturn(1)
        whenever(dataHandler.getRunProgress()).thenReturn(Array(4) { RunProgress() })

        val result = repository.getNextRecommendedRun(testRuns)

        assertThat(result).isEqualTo(2) // Next after index 1
    }

    @Test
    fun `getNextRecommendedRun returns first uncompleted when no last run`() {
        whenever(dataHandler.getLastRunIndex()).thenReturn(-1)
        whenever(dataHandler.getRunProgress()).thenReturn(
            arrayOf(
                RunProgress(1, 1000L), // Completed
                RunProgress(0, null),   // Not completed
                RunProgress(0, null),
                RunProgress(0, null)
            )
        )

        val result = repository.getNextRecommendedRun(testRuns)

        assertThat(result).isEqualTo(1) // First uncompleted
    }

    @Test
    fun `getNextRecommendedRun returns 0 when all completed`() {
        whenever(dataHandler.getLastRunIndex()).thenReturn(3) // Last run
        whenever(dataHandler.getRunProgress()).thenReturn(
            arrayOf(
                RunProgress(1, 1000L),
                RunProgress(1, 2000L),
                RunProgress(1, 3000L),
                RunProgress(1, 4000L)
            )
        )

        val result = repository.getNextRecommendedRun(testRuns)

        assertThat(result).isEqualTo(0) // Start over
    }

    @Test
    fun `isRecommendedRun returns true for recommended index`() {
        whenever(dataHandler.getLastRunIndex()).thenReturn(1)
        whenever(dataHandler.getRunProgress()).thenReturn(Array(4) { RunProgress() })

        val result = repository.isRecommendedRun(2, testRuns)

        assertThat(result).isTrue()
    }

    @Test
    fun `isRecommendedRun returns false for non-recommended index`() {
        whenever(dataHandler.getLastRunIndex()).thenReturn(1)
        whenever(dataHandler.getRunProgress()).thenReturn(Array(4) { RunProgress() })

        val result = repository.isRecommendedRun(0, testRuns)

        assertThat(result).isFalse()
    }

    @Test
    fun `getCompletionCount returns correct count`() {
        val allProgress = arrayOf(
            RunProgress(0, null),
            RunProgress(3, 1000L),
            RunProgress(1, 2000L)
        )
        whenever(dataHandler.getRunProgress()).thenReturn(allProgress)

        assertThat(repository.getCompletionCount(0)).isEqualTo(0)
        assertThat(repository.getCompletionCount(1)).isEqualTo(3)
        assertThat(repository.getCompletionCount(2)).isEqualTo(1)
    }

    @Test
    fun `getProgressSummary calculates correct statistics`() {
        whenever(dataHandler.getLastRunIndex()).thenReturn(2)
        whenever(dataHandler.getRunProgress()).thenReturn(
            arrayOf(
                RunProgress(1, 1000L),
                RunProgress(1, 2000L),
                RunProgress(1, 3000L),
                RunProgress(0, null)
            )
        )

        val summary = repository.getProgressSummary(testRuns)

        assertThat(summary.currentWeek).isEqualTo(2)
        assertThat(summary.currentDay).isEqualTo(1)
        assertThat(summary.totalCompleted).isEqualTo(3)
        assertThat(summary.totalRuns).isEqualTo(4)
        assertThat(summary.lastRunDate).isEqualTo(3000L)
        assertThat(summary.nextRecommendedIndex).isEqualTo(3)
    }

    @Test
    fun `getProgressSummary handles no completions`() {
        whenever(dataHandler.getLastRunIndex()).thenReturn(-1)
        whenever(dataHandler.getRunProgress()).thenReturn(Array(4) { RunProgress() })

        val summary = repository.getProgressSummary(testRuns)

        assertThat(summary.currentWeek).isEqualTo(1)
        assertThat(summary.currentDay).isEqualTo(1)
        assertThat(summary.totalCompleted).isEqualTo(0)
        assertThat(summary.totalRuns).isEqualTo(4)
        assertThat(summary.lastRunDate).isNull()
        assertThat(summary.nextRecommendedIndex).isEqualTo(0)
    }
}
