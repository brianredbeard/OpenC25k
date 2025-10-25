package se.wmuth.openc25k.data.model

/**
 * Types of sounds available for interval notifications
 *
 * @property BEEP Standard beep sound (default)
 * @property CHIME Pleasant chime sound
 * @property WHISTLE Coach whistle sound
 * @property NONE Silent mode - no sound played
 */
enum class SoundType {
    BEEP,
    CHIME,
    WHISTLE,
    NONE;

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
