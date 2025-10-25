package se.wmuth.openc25k.data.model

/**
 * Tracks progress and statistics for a single run
 *
 * @param completionCount How many times this run has been completed
 * @param lastCompletedDate Timestamp (millis since epoch) of last completion, null if never completed
 */
data class RunProgress(
    val completionCount: Int = 0,
    val lastCompletedDate: Long? = null
) {
    /**
     * Check if this run has ever been completed
     */
    fun isCompleted(): Boolean = completionCount > 0

    /**
     * Get human-readable last completion text
     */
    fun getLastCompletedText(): String? {
        if (lastCompletedDate == null) return null

        val now = System.currentTimeMillis()
        val diff = now - lastCompletedDate
        val days = diff / (1000 * 60 * 60 * 24)

        return when {
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7 -> "$days days ago"
            days < 14 -> "1 week ago"
            days < 30 -> "${days / 7} weeks ago"
            else -> "${days / 30} months ago"
        }
    }

    /**
     * Create new progress with incremented completion
     */
    fun withCompletion(): RunProgress {
        return copy(
            completionCount = completionCount + 1,
            lastCompletedDate = System.currentTimeMillis()
        )
    }
}

/**
 * Overall program progress summary
 */
data class ProgressSummary(
    val currentWeek: Int,
    val currentDay: Int,
    val totalCompleted: Int,
    val totalRuns: Int,
    val lastRunDate: Long?,
    val nextRecommendedIndex: Int
) {
    /**
     * Format as header text: "Week 3 Day 2 • 8/27 runs • Last: 2 days ago"
     */
    fun toHeaderText(): String {
        val weekDay = "Week $currentWeek Day $currentDay"
        val completion = "$totalCompleted/$totalRuns runs"

        val lastRun = if (lastRunDate != null) {
            val progress = RunProgress(lastCompletedDate = lastRunDate)
            " • Last: ${progress.getLastCompletedText()}"
        } else {
            ""
        }

        return "$weekDay • $completion$lastRun"
    }
}
