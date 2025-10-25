package se.wmuth.openc25k.data.model

import se.wmuth.openc25k.R

/**
 * Types of sounds available for interval notifications
 *
 * @property resourceId The R.raw resource ID for this sound (null for NONE)
 * @property displayName Human-readable name for UI display
 */
enum class SoundType(val resourceId: Int?, val displayName: String) {
    BEEP(R.raw.beep, "Beep"),
    BELL_1(R.raw.bell1, "Bell 1"),
    BELL_2(R.raw.bell2, "Bell 2"),
    CHIME_1(R.raw.chime1, "Chime 1"),
    CHIME_2(R.raw.chime2, "Chime 2"),
    WHISTLE_1(R.raw.whistle1, "Whistle 1"),
    WHISTLE_2(R.raw.whistle2, "Whistle 2"),
    NONE(null, "Silent");

    companion object {
        /**
         * Converts a string name to SoundType, defaulting to BEEP if not found
         *
         * @param name String representation of the sound type
         * @return Corresponding SoundType enum value, or BEEP as default
         */
        fun fromName(name: String): SoundType {
            return values().find { it.name == name } ?: BEEP
        }
    }
}
