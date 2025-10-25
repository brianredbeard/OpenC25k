package se.wmuth.openc25k.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for RunProgress data model
 */
class RunProgressTest {

    @Test
    fun `default RunProgress has zero completions`() {
        val progress = RunProgress()

        assertThat(progress.completionCount).isEqualTo(0)
        assertThat(progress.lastCompletedDate).isNull()
        assertThat(progress.isCompleted()).isFalse()
    }

    @Test
    fun `isCompleted returns true when count greater than zero`() {
        val progress = RunProgress(completionCount = 1)

        assertThat(progress.isCompleted()).isTrue()
    }

    @Test
    fun `isCompleted returns false when count is zero`() {
        val progress = RunProgress(completionCount = 0)

        assertThat(progress.isCompleted()).isFalse()
    }

    @Test
    fun `getLastCompletedText returns null when no completion date`() {
        val progress = RunProgress()

        assertThat(progress.getLastCompletedText()).isNull()
    }

    @Test
    fun `getLastCompletedText returns Today for today`() {
        val now = System.currentTimeMillis()
        val progress = RunProgress(lastCompletedDate = now)

        assertThat(progress.getLastCompletedText()).isEqualTo("Today")
    }

    @Test
    fun `getLastCompletedText returns Yesterday for 1 day ago`() {
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val progress = RunProgress(lastCompletedDate = oneDayAgo)

        assertThat(progress.getLastCompletedText()).isEqualTo("Yesterday")
    }

    @Test
    fun `getLastCompletedText returns days for recent dates`() {
        val threeDaysAgo = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000)
        val progress = RunProgress(lastCompletedDate = threeDaysAgo)

        assertThat(progress.getLastCompletedText()).isEqualTo("3 days ago")
    }

    @Test
    fun `getLastCompletedText returns weeks for older dates`() {
        val twoWeeksAgo = System.currentTimeMillis() - (14L * 24 * 60 * 60 * 1000)
        val progress = RunProgress(lastCompletedDate = twoWeeksAgo)

        assertThat(progress.getLastCompletedText()).isEqualTo("2 weeks ago")
    }

    @Test
    fun `getLastCompletedText returns months for very old dates`() {
        val twoMonthsAgo = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000)
        val progress = RunProgress(lastCompletedDate = twoMonthsAgo)

        assertThat(progress.getLastCompletedText()).isEqualTo("2 months ago")
    }

    @Test
    fun `withCompletion increments count and sets date`() {
        val original = RunProgress(completionCount = 2, lastCompletedDate = 1000L)

        val updated = original.withCompletion()

        assertThat(updated.completionCount).isEqualTo(3)
        assertThat(updated.lastCompletedDate).isGreaterThan(original.lastCompletedDate)
    }

    @Test
    fun `withCompletion creates new instance`() {
        val original = RunProgress(completionCount = 1)

        val updated = original.withCompletion()

        assertThat(updated).isNotSameInstanceAs(original)
        assertThat(original.completionCount).isEqualTo(1) // unchanged
        assertThat(updated.completionCount).isEqualTo(2)
    }
}
