package se.wmuth.openc25k.main

import android.content.Context
import androidx.appcompat.app.AlertDialog
import se.wmuth.openc25k.R
import se.wmuth.openc25k.data.model.SoundType

/**
 * Creates the sound selection dialog and updates the listener
 * when the user selects a new sound type
 *
 * @param p the parent or listener to send the sound selection event to
 * @param pCon the context of the parent
 */
class SoundSelectionDialog(
    private val parent: SoundSelectionDialogListener,
    private val parentContext: Context
) {

    interface SoundSelectionDialogListener {
        /**
         * Set the sound type in the listener to the selected type
         * @param soundType the newly selected sound type
         */
        fun setSoundType(soundType: SoundType)
    }

    /**
     * Creates the sound selection alert dialog
     *
     * @param currentSoundType the currently selected sound type
     */
    fun createAlertDialog(currentSoundType: SoundType) {
        val soundTypes = SoundType.values()

        // Get localized display names from string resources
        val displayNames = soundTypes.map { soundType ->
            when (soundType) {
                SoundType.BEEP -> parentContext.getString(R.string.sound_beep)
                SoundType.BELL_1 -> parentContext.getString(R.string.sound_bell_1)
                SoundType.BELL_2 -> parentContext.getString(R.string.sound_bell_2)
                SoundType.CHIME_1 -> parentContext.getString(R.string.sound_chime_1)
                SoundType.CHIME_2 -> parentContext.getString(R.string.sound_chime_2)
                SoundType.WHISTLE_1 -> parentContext.getString(R.string.sound_whistle_1)
                SoundType.WHISTLE_2 -> parentContext.getString(R.string.sound_whistle_2)
                SoundType.NONE -> parentContext.getString(R.string.sound_none)
            }
        }.toTypedArray()

        // Find the index of the current sound type
        val currentIndex = soundTypes.indexOf(currentSoundType)

        AlertDialog.Builder(parentContext)
            .setTitle(R.string.soundSelection)
            .setSingleChoiceItems(displayNames, currentIndex) { dialog, which ->
                val selectedSoundType = soundTypes[which]
                parent.setSoundType(selectedSoundType)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
