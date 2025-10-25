package se.wmuth.openc25k.utils

/**
 * Formats durations for natural speech in TTS announcements
 */
object DurationFormatter {

    /**
     * Converts seconds to natural speech format
     * Examples:
     * - 30 -> "thirty seconds"
     * - 60 -> "one minute"
     * - 90 -> "ninety seconds"
     * - 120 -> "two minutes"
     * - 150 -> "two minutes thirty seconds"
     */
    fun toSpeech(seconds: Int): String {
        return when {
            seconds < 60 -> {
                val secondWord = numberToWord(seconds)
                if (seconds == 1) "$secondWord second" else "$secondWord seconds"
            }
            seconds % 60 == 0 -> {
                val minutes = seconds / 60
                val minuteWord = numberToWord(minutes)
                if (minutes == 1) "$minuteWord minute" else "$minuteWord minutes"
            }
            else -> {
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                val minuteWord = numberToWord(minutes)
                val secondWord = numberToWord(remainingSeconds)
                val minutePart = if (minutes == 1) "$minuteWord minute" else "$minuteWord minutes"
                val secondPart = if (remainingSeconds == 1) "$secondWord second" else "$secondWord seconds"
                "$minutePart $secondPart"
            }
        }
    }

    /**
     * Converts numbers 1-120 to words for natural speech
     */
    private fun numberToWord(num: Int): String {
        return when (num) {
            1 -> "one"
            2 -> "two"
            3 -> "three"
            4 -> "four"
            5 -> "five"
            6 -> "six"
            7 -> "seven"
            8 -> "eight"
            9 -> "nine"
            10 -> "ten"
            15 -> "fifteen"
            20 -> "twenty"
            30 -> "thirty"
            40 -> "forty"
            45 -> "forty five"
            50 -> "fifty"
            60 -> "sixty"
            90 -> "ninety"
            120 -> "one hundred twenty"
            else -> num.toString() // Fallback to digit for unsupported numbers
        }
    }
}
