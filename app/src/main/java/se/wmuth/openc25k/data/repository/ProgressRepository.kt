package se.wmuth.openc25k.data.repository

import se.wmuth.openc25k.data.Run
import se.wmuth.openc25k.data.model.ProgressSummary
import se.wmuth.openc25k.data.model.RunProgress
import se.wmuth.openc25k.main.DataHandler

/**
 * Repository for managing overall program progress and run statistics
 *
 * Centralizes logic for:
 * - Tracking which runs have been completed and how many times
 * - Determining the next recommended run
 * - Calculating overall progress statistics
 */
class ProgressRepository(private val dataHandler: DataHandler) {

    /**
     * Get progress for all runs
     * Returns array matching runs array length, with progress for each run
     */
    fun getAllProgress(): Array<RunProgress> {
        return dataHandler.getRunProgress()
    }

    /**
     * Get progress for a specific run
     */
    fun getProgress(runIndex: Int): RunProgress {
        val allProgress = getAllProgress()
        return if (runIndex in allProgress.indices) {
            allProgress[runIndex]
        } else {
            RunProgress()
        }
    }

    /**
     * Record a run completion
     */
    fun recordCompletion(runIndex: Int) {
        dataHandler.incrementRunCompletion(runIndex)
        dataHandler.setLastRunIndex(runIndex)
    }

    /**
     * Get the next recommended run index
     *
     * Logic:
     * 1. If last run exists and is not the final run, suggest next run
     * 2. If no runs completed, suggest first run (index 0)
     * 3. If all runs completed, suggest first uncompleted or first run
     */
    fun getNextRecommendedRun(runs: Array<Run>): Int {
        val lastRunIndex = dataHandler.getLastRunIndex()

        // If we have a last run and it's not the final run, suggest next
        if (lastRunIndex != -1 && lastRunIndex < runs.size - 1) {
            return lastRunIndex + 1
        }

        // Find first uncompleted run
        val allProgress = getAllProgress()
        val firstUncompleted = allProgress.indexOfFirst { !it.isCompleted() }
        if (firstUncompleted != -1) {
            return firstUncompleted
        }

        // All completed, suggest first run (for repeat)
        return 0
    }

    /**
     * Get overall progress summary for header display
     */
    fun getProgressSummary(runs: Array<Run>): ProgressSummary {
        val allProgress = getAllProgress()
        val lastRunIndex = dataHandler.getLastRunIndex()

        // Calculate totals
        val totalCompleted = allProgress.count { it.isCompleted() }
        val totalRuns = runs.size

        // Get last completion date across all runs
        val lastRunDate = allProgress
            .mapNotNull { it.lastCompletedDate }
            .maxOrNull()

        // Determine current week/day from recommended run
        val nextIndex = getNextRecommendedRun(runs)
        val (week, day) = parseRunName(runs.getOrNull(nextIndex)?.name ?: "Week 1 Day 1")

        return ProgressSummary(
            currentWeek = week,
            currentDay = day,
            totalCompleted = totalCompleted,
            totalRuns = totalRuns,
            lastRunDate = lastRunDate,
            nextRecommendedIndex = nextIndex
        )
    }

    /**
     * Parse run name to extract week and day numbers
     * Expected format: "Week X Day Y"
     */
    private fun parseRunName(name: String): Pair<Int, Int> {
        val weekRegex = """Week (\d+)""".toRegex()
        val dayRegex = """Day (\d+)""".toRegex()

        val week = weekRegex.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val day = dayRegex.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

        return Pair(week, day)
    }

    /**
     * Check if a run should be highlighted as "next"
     */
    fun isRecommendedRun(runIndex: Int, runs: Array<Run>): Boolean {
        return runIndex == getNextRecommendedRun(runs)
    }

    /**
     * Get completion count for a run
     */
    fun getCompletionCount(runIndex: Int): Int {
        return getProgress(runIndex).completionCount
    }
}
