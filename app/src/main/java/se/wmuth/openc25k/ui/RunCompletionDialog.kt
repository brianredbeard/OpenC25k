package se.wmuth.openc25k.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import se.wmuth.openc25k.R
import se.wmuth.openc25k.data.Run
import se.wmuth.openc25k.data.repository.ProgressRepository

/**
 * Dialog shown when a run is completed
 *
 * Displays:
 * - Run name
 * - Completion count for this run
 * - Overall progress (X/27 runs)
 * - Next recommended run
 * - Encouragement message
 */
class RunCompletionDialog(
    private val context: Context,
    private val progressRepository: ProgressRepository
) {

    /**
     * Show the completion dialog
     *
     * @param run The run that was just completed
     * @param runIndex The index of the completed run
     * @param allRuns Array of all runs for progress calculation
     * @param onDismiss Callback when dialog is dismissed
     */
    fun show(run: Run, runIndex: Int, allRuns: Array<Run>, onDismiss: () -> Unit) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_run_completion, null)

        // Get progress data
        val completionCount = progressRepository.getCompletionCount(runIndex)
        val summary = progressRepository.getProgressSummary(allRuns)
        val nextRunIndex = progressRepository.getNextRecommendedRun(allRuns)
        val nextRun = allRuns.getOrNull(nextRunIndex)

        // Populate views
        view.findViewById<TextView>(R.id.tvCompletionRunName).text = run.name
        view.findViewById<TextView>(R.id.tvCompletionCount).text = completionCount.toString()
        view.findViewById<TextView>(R.id.tvTotalProgress).text =
            "${summary.totalCompleted}/${summary.totalRuns} runs"
        view.findViewById<TextView>(R.id.tvNextRun).text = nextRun?.name ?: "Complete!"

        // Show encouragement based on progress
        val encouragement = getEncouragementMessage(summary.totalCompleted, summary.totalRuns)
        view.findViewById<TextView>(R.id.tvEncouragement).text = encouragement

        // Build and show dialog
        AlertDialog.Builder(context)
            .setView(view)
            .setPositiveButton("Done") { dialog, _ ->
                dialog.dismiss()
                onDismiss()
            }
            .setNeutralButton("Share") { _, _ ->
                // Future: Share progress to social media
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Get an encouragement message based on progress
     */
    private fun getEncouragementMessage(completed: Int, total: Int): String {
        val percentage = (completed.toFloat() / total.toFloat() * 100).toInt()

        return when {
            completed == 1 -> "Great start! You're on your way to 5K!"
            completed == total -> "Incredible! You completed the entire program!"
            percentage < 25 -> "Keep it up! Every run counts!"
            percentage < 50 -> "You're making great progress!"
            percentage < 75 -> "Over halfway there! You've got this!"
            else -> "Almost there! The finish line is in sight!"
        }
    }
}
