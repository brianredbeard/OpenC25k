package se.wmuth.openc25k.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for DurationFormatter
 */
class DurationFormatterTest {

    @Test
    fun `toSpeech formats single second correctly`() {
        val result = DurationFormatter.toSpeech(1)
        assertThat(result).isEqualTo("one second")
    }

    @Test
    fun `toSpeech formats multiple seconds correctly`() {
        assertThat(DurationFormatter.toSpeech(30)).isEqualTo("thirty seconds")
        assertThat(DurationFormatter.toSpeech(45)).isEqualTo("forty five seconds")
        // 60 seconds is exactly 1 minute, so it should say "one minute"
    }

    @Test
    fun `toSpeech formats single minute correctly`() {
        val result = DurationFormatter.toSpeech(60)
        assertThat(result).isEqualTo("one minute")
    }

    @Test
    fun `toSpeech formats multiple minutes correctly`() {
        assertThat(DurationFormatter.toSpeech(120)).isEqualTo("two minutes")
        assertThat(DurationFormatter.toSpeech(180)).isEqualTo("three minutes")
    }

    @Test
    fun `toSpeech formats minutes and seconds correctly`() {
        assertThat(DurationFormatter.toSpeech(90)).isEqualTo("one minute thirty seconds")
        assertThat(DurationFormatter.toSpeech(150)).isEqualTo("two minutes thirty seconds")
    }

    @Test
    fun `toSpeech handles zero seconds`() {
        // Zero is not in the word map, should fallback to digits
        val result = DurationFormatter.toSpeech(0)
        assertThat(result).isEqualTo("0 seconds")
    }

    @Test
    fun `toSpeech handles unsupported numbers with fallback`() {
        // 11-14, 16-19, 21-29, 31-39, etc not in word map
        val result = DurationFormatter.toSpeech(25)
        assertThat(result).isEqualTo("25 seconds")
    }

    @Test
    fun `toSpeech handles common workout intervals`() {
        // Common C25K intervals
        assertThat(DurationFormatter.toSpeech(60)).isEqualTo("one minute")
        assertThat(DurationFormatter.toSpeech(90)).isEqualTo("one minute thirty seconds")
        assertThat(DurationFormatter.toSpeech(300)).isEqualTo("five minutes")
    }

    @Test
    fun `toSpeech formats exact minutes with supported words`() {
        // 1, 2, 3, 4, 5, 6 minutes are all supported
        assertThat(DurationFormatter.toSpeech(60)).isEqualTo("one minute")
        assertThat(DurationFormatter.toSpeech(240)).isEqualTo("four minutes")
        assertThat(DurationFormatter.toSpeech(300)).isEqualTo("five minutes")
        assertThat(DurationFormatter.toSpeech(360)).isEqualTo("six minutes")
    }

    @Test
    fun `toSpeech handles large durations`() {
        // 20 minutes (1200 seconds) - 20 is in word map
        val result = DurationFormatter.toSpeech(1200)
        assertThat(result).isEqualTo("twenty minutes")
    }

    @Test
    fun `toSpeech singular vs plural for seconds`() {
        assertThat(DurationFormatter.toSpeech(1)).contains("second") // singular
        assertThat(DurationFormatter.toSpeech(1)).doesNotContain("seconds")

        assertThat(DurationFormatter.toSpeech(2)).contains("seconds") // plural
    }

    @Test
    fun `toSpeech singular vs plural for minutes`() {
        assertThat(DurationFormatter.toSpeech(60)).contains("minute") // singular
        assertThat(DurationFormatter.toSpeech(60)).doesNotContain("minutes")

        assertThat(DurationFormatter.toSpeech(120)).contains("minutes") // plural
    }

    @Test
    fun `toSpeech handles boundary values`() {
        assertThat(DurationFormatter.toSpeech(59)).isEqualTo("59 seconds") // Just before 1 minute
        assertThat(DurationFormatter.toSpeech(61)).isEqualTo("one minute one second") // 1 is in word map
    }
}
