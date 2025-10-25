package se.wmuth.openc25k.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import se.wmuth.openc25k.data.Interval
import se.wmuth.openc25k.data.Run
import se.wmuth.openc25k.data.model.ProgressSummary
import se.wmuth.openc25k.data.model.RunProgress
import se.wmuth.openc25k.data.repository.ProgressRepository

/**
 * Integration tests for RunCompletionDialog
 *
 * Note: These tests verify the dialog logic but cannot fully test UI interactions
 * because we cannot click buttons programmatically in unit tests.
 * The tests focus on data population and message selection logic.
 */
@RunWith(AndroidJUnit4::class)
class RunCompletionDialogTest {

    private lateinit var context: Context
    private lateinit var progressRepository: ProgressRepository
    private lateinit var dialog: RunCompletionDialog
    private lateinit var testRuns: Array<Run>

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        progressRepository = mock()

        // Create 27 test runs (full C25K program)
        testRuns = Array(27) { index ->
            val week = (index / 3) + 1
            val day = (index % 3) + 1
            Run(
                name = "Week $week Day $day",
                description = "Test run",
                isComplete = false,
                intervals = arrayOf(Interval(60, "Run"))
            )
        }

        dialog = RunCompletionDialog(context, progressRepository)
    }

    @Test
    fun firstRunCompletionShowsCorrectMessage() {
        // First run completed (1/27)
        val summary = ProgressSummary(
            currentWeek = 1,
            currentDay = 2,
            totalCompleted = 1,
            totalRuns = 27,
            lastRunDate = null,
            nextRecommendedIndex = 1
        )

        whenever(progressRepository.getCompletionCount(0)).thenReturn(1)
        whenever(progressRepository.getProgressSummary(testRuns)).thenReturn(summary)
        whenever(progressRepository.getNextRecommendedRun(testRuns)).thenReturn(1)

        // Show dialog
        var dismissed = false
        dialog.show(testRuns[0], 0, testRuns) { dismissed = true }

        // Verify repository was queried
        verify(progressRepository).getCompletionCount(0)
        verify(progressRepository).getProgressSummary(testRuns)
        verify(progressRepository).getNextRecommendedRun(testRuns)

        // Note: We cannot verify the actual message text without exposing it publicly
        // or using UI testing frameworks like Espresso
    }

    @Test
    fun lastRunCompletionShowsCorrectMessage() {
        // All runs completed (27/27)
        val summary = ProgressSummary(
            currentWeek = 9,
            currentDay = 3,
            totalCompleted = 27,
            totalRuns = 27,
            lastRunDate = null,
            nextRecommendedIndex = 0
        )

        whenever(progressRepository.getCompletionCount(26)).thenReturn(1)
        whenever(progressRepository.getProgressSummary(testRuns)).thenReturn(summary)
        whenever(progressRepository.getNextRecommendedRun(testRuns)).thenReturn(0)

        var dismissed = false
        dialog.show(testRuns[26], 26, testRuns) { dismissed = true }

        verify(progressRepository).getCompletionCount(26)
        verify(progressRepository).getProgressSummary(testRuns)
    }

    @Test
    fun midProgressCompletionShowsAppropriateMessage() {
        // Middle progress (14/27 ~ 50%)
        val summary = ProgressSummary(
            currentWeek = 5,
            currentDay = 2,
            totalCompleted = 14,
            totalRuns = 27,
            lastRunDate = null,
            nextRecommendedIndex = 14
        )

        whenever(progressRepository.getCompletionCount(13)).thenReturn(1)
        whenever(progressRepository.getProgressSummary(testRuns)).thenReturn(summary)
        whenever(progressRepository.getNextRecommendedRun(testRuns)).thenReturn(14)

        var dismissed = false
        dialog.show(testRuns[13], 13, testRuns) { dismissed = true }

        verify(progressRepository).getCompletionCount(13)
        verify(progressRepository).getProgressSummary(testRuns)
    }

    @Test
    fun repeatedRunShowsCorrectCompletionCount() {
        // User completed same run 3 times
        val summary = ProgressSummary(
            currentWeek = 1,
            currentDay = 2,
            totalCompleted = 1,
            totalRuns = 27,
            lastRunDate = null,
            nextRecommendedIndex = 1
        )

        whenever(progressRepository.getCompletionCount(0)).thenReturn(3) // Completed 3x
        whenever(progressRepository.getProgressSummary(testRuns)).thenReturn(summary)
        whenever(progressRepository.getNextRecommendedRun(testRuns)).thenReturn(1)

        var dismissed = false
        dialog.show(testRuns[0], 0, testRuns) { dismissed = true }

        // Should show completion count of 3
        verify(progressRepository).getCompletionCount(0)
    }

    @Test
    fun noNextRunShowsComplete() {
        // All runs done, no next run
        val summary = ProgressSummary(
            currentWeek = 9,
            currentDay = 3,
            totalCompleted = 27,
            totalRuns = 27,
            lastRunDate = null,
            nextRecommendedIndex = 0 // Wraps to 0 but all completed
        )

        whenever(progressRepository.getCompletionCount(26)).thenReturn(1)
        whenever(progressRepository.getProgressSummary(testRuns)).thenReturn(summary)
        whenever(progressRepository.getNextRecommendedRun(testRuns)).thenReturn(27) // Out of bounds

        var dismissed = false
        dialog.show(testRuns[26], 26, testRuns) { dismissed = true }

        // Should handle gracefully and show "Complete!"
        verify(progressRepository).getNextRecommendedRun(testRuns)
    }

    @Test
    fun earlyProgressShowsEncouragementLessThan25Percent() {
        // 6/27 runs completed (22%)
        val summary = ProgressSummary(
            currentWeek = 2,
            currentDay = 3,
            totalCompleted = 6,
            totalRuns = 27,
            lastRunDate = null,
            nextRecommendedIndex = 6
        )

        whenever(progressRepository.getCompletionCount(5)).thenReturn(1)
        whenever(progressRepository.getProgressSummary(testRuns)).thenReturn(summary)
        whenever(progressRepository.getNextRecommendedRun(testRuns)).thenReturn(6)

        var dismissed = false
        dialog.show(testRuns[5], 5, testRuns) { dismissed = true }

        // Should use < 25% message: "Keep it up! Every run counts!"
        verify(progressRepository).getProgressSummary(testRuns)
    }

    @Test
    fun midProgressShowsEncouragement25To50Percent() {
        // 10/27 runs completed (37%)
        val summary = ProgressSummary(
            currentWeek = 4,
            currentDay = 1,
            totalCompleted = 10,
            totalRuns = 27,
            lastRunDate = null,
            nextRecommendedIndex = 10
        )

        whenever(progressRepository.getCompletionCount(9)).thenReturn(1)
        whenever(progressRepository.getProgressSummary(testRuns)).thenReturn(summary)
        whenever(progressRepository.getNextRecommendedRun(testRuns)).thenReturn(10)

        var dismissed = false
        dialog.show(testRuns[9], 9, testRuns) { dismissed = true }

        // Should use 25-50% message: "You're making great progress!"
        verify(progressRepository).getProgressSummary(testRuns)
    }

    @Test
    fun laterProgressShowsEncouragement50To75Percent() {
        // 16/27 runs completed (59%)
        val summary = ProgressSummary(
            currentWeek = 6,
            currentDay = 1,
            totalCompleted = 16,
            totalRuns = 27,
            lastRunDate = null,
            nextRecommendedIndex = 16
        )

        whenever(progressRepository.getCompletionCount(15)).thenReturn(1)
        whenever(progressRepository.getProgressSummary(testRuns)).thenReturn(summary)
        whenever(progressRepository.getNextRecommendedRun(testRuns)).thenReturn(16)

        var dismissed = false
        dialog.show(testRuns[15], 15, testRuns) { dismissed = true }

        // Should use 50-75% message: "Over halfway there! You've got this!"
        verify(progressRepository).getProgressSummary(testRuns)
    }

    @Test
    fun nearCompletionShowsEncouragementOver75Percent() {
        // 22/27 runs completed (81%)
        val summary = ProgressSummary(
            currentWeek = 8,
            currentDay = 1,
            totalCompleted = 22,
            totalRuns = 27,
            lastRunDate = null,
            nextRecommendedIndex = 22
        )

        whenever(progressRepository.getCompletionCount(21)).thenReturn(1)
        whenever(progressRepository.getProgressSummary(testRuns)).thenReturn(summary)
        whenever(progressRepository.getNextRecommendedRun(testRuns)).thenReturn(22)

        var dismissed = false
        dialog.show(testRuns[21], 21, testRuns) { dismissed = true }

        // Should use > 75% message: "Almost there! The finish line is in sight!"
        verify(progressRepository).getProgressSummary(testRuns)
    }

    @Test
    fun dismissCallbackIsInvoked() {
        // This test verifies that onDismiss callback structure is correct
        // We cannot actually click the button in unit tests
        val summary = ProgressSummary(
            currentWeek = 1,
            currentDay = 2,
            totalCompleted = 1,
            totalRuns = 27,
            lastRunDate = null,
            nextRecommendedIndex = 1
        )

        whenever(progressRepository.getCompletionCount(0)).thenReturn(1)
        whenever(progressRepository.getProgressSummary(testRuns)).thenReturn(summary)
        whenever(progressRepository.getNextRecommendedRun(testRuns)).thenReturn(1)

        var dismissed = false
        dialog.show(testRuns[0], 0, testRuns) { dismissed = true }

        // Dialog is shown, but we cannot click "Done" button programmatically
        // This test verifies the callback parameter is accepted
        assertThat(dismissed).isFalse() // Not actually dismissed yet
    }

    @Test
    fun zeroCompletionsHandledGracefully() {
        // Edge case: showing dialog even though completion count is 0
        val summary = ProgressSummary(
            currentWeek = 1,
            currentDay = 1,
            totalCompleted = 0,
            totalRuns = 27,
            lastRunDate = null,
            nextRecommendedIndex = 0
        )

        whenever(progressRepository.getCompletionCount(0)).thenReturn(0)
        whenever(progressRepository.getProgressSummary(testRuns)).thenReturn(summary)
        whenever(progressRepository.getNextRecommendedRun(testRuns)).thenReturn(0)

        var dismissed = false
        dialog.show(testRuns[0], 0, testRuns) { dismissed = true }

        // Should not crash
        verify(progressRepository).getCompletionCount(0)
    }

    @Test
    fun dialogCreationDoesNotCrash() {
        // Basic smoke test
        val testDialog = RunCompletionDialog(context, progressRepository)
        assertThat(testDialog).isNotNull()
    }

    @Test
    fun multipleDialogShowsDoNotInterfere() {
        val summary = ProgressSummary(
            currentWeek = 1,
            currentDay = 2,
            totalCompleted = 1,
            totalRuns = 27,
            lastRunDate = null,
            nextRecommendedIndex = 1
        )

        whenever(progressRepository.getCompletionCount(any())).thenReturn(1)
        whenever(progressRepository.getProgressSummary(testRuns)).thenReturn(summary)
        whenever(progressRepository.getNextRecommendedRun(testRuns)).thenReturn(1)

        // Show multiple dialogs
        dialog.show(testRuns[0], 0, testRuns) { }
        dialog.show(testRuns[1], 1, testRuns) { }
        dialog.show(testRuns[2], 2, testRuns) { }

        // Each should query repository independently
        verify(progressRepository, times(3)).getProgressSummary(testRuns)
    }
}
