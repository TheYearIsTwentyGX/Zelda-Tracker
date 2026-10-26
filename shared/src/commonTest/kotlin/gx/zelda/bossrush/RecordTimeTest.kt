package gx.zelda.bossrush

import gx.zelda.bossrush.model.RecordTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Lightning Round times: the game's m:ss.cc in hundredths, typed every way the board accepts. */
class RecordTimeTest {
    @Test
    fun formatsMinutesSecondsAndHundredths() {
        assertEquals("1:45.32", RecordTime.format(10_532))
        assertEquals("0:05.07", RecordTime.format(507))
        assertEquals("0:00.01", RecordTime.format(1))
        assertEquals("10:00.00", RecordTime.format(60_000))
        assertEquals("59:59.99", RecordTime.format(RecordTime.MAX))
        assertEquals("59:59.99", RecordTime.format(RecordTime.MAX + 1), "clamped to the game's limit")
    }

    @Test
    fun formatsDifferences() {
        assertEquals("2.15", RecordTime.formatDifference(215))
        assertEquals("0.01", RecordTime.formatDifference(1))
        assertEquals("59.99", RecordTime.formatDifference(5_999))
        assertEquals("1:00.00", RecordTime.formatDifference(6_000))
    }

    @Test
    fun readsSeparatedTimes() {
        assertEquals(10_532, RecordTime.parse("1:45.32"))
        assertEquals(10_532, RecordTime.parse(" 1:45.32 "))
        assertEquals(10_532, RecordTime.parse("1'45\"32"), "minute and second marks, as guides write times")
        assertEquals(10_532, RecordTime.parse("1:45,32"), "a decimal comma")
        assertEquals(10_500, RecordTime.parse("1:45"), "no hundredths")
        assertEquals(10_532, RecordTime.parse("1.45.32"), "a decimal keypad's two points")
        assertEquals(10_550, RecordTime.parse("1:45.5"), "one digit is tenths")
        assertEquals(507, RecordTime.parse("5.07"))
        assertEquals(9_550, RecordTime.parse("95.5"), "seconds alone may pass a minute")
        assertEquals(RecordTime.MAX, RecordTime.parse("59:59.99"))
        assertNull(RecordTime.parse("60:00.00"), "past the game's limit")
    }

    @Test
    fun readsBareDigitsLikeAStopwatch() {
        assertEquals(10_532, RecordTime.parse("14532"))
        assertEquals(532, RecordTime.parse("532"))
        assertEquals(7, RecordTime.parse("7"))
        assertEquals(60_000, RecordTime.parse("100000"))
        assertEquals(RecordTime.MAX, RecordTime.parse("595999"))
        assertNull(RecordTime.parse("600000"), "past the game's limit")
        assertEquals(6_000, RecordTime.parse("6000"), "60 seconds with no minutes is a minute")
        assertNull(RecordTime.parse("16000"), "seconds after minutes stop at 59")
        assertNull(RecordTime.parse("1000000"), "more than the timer's digits")
    }

    @Test
    fun rejectsWhatIsntATime() {
        listOf("", "  ", "0", "0:00.00", "abc", "1:60.00", "1:5.00", "1:45.321", "1:45.", "1:2:3", "1.2.3", "1.45.32.1", "123:00.00", "99:59.99", ":45", "1:45.3a", "-1:00", "100:00.00")
            .forEach { assertNull(RecordTime.parse(it), "\"$it\"") }
    }
}
