package gx.zelda.bossrush.model

/**
 * A Lightning Round time in hundredths of a second, as the game keeps it: each boss's best is three
 * story flags, minutes, seconds and hundredths (`STORYFLAG_<BOSS>_TIME_MIN/SEC/MS` in the zeldaret/ss
 * decompilation), and the fight's timer shows six digits, `mm ss cc`. Shown `m:ss.cc` here.
 */
object RecordTime {
    /** The longest time the game keeps: 59:59.99 (`TIME_CONVERT(59, 59, 99)` in the decompilation). */
    const val MAX: Int = 59 * 6000 + 59 * 100 + 99

    /** 10_543 → "1:45.43": minutes unpadded, seconds and hundredths two digits each. */
    fun format(hundredths: Int): String {
        val h = hundredths.coerceIn(0, MAX)
        return "${h / 6000}:${(h / 100 % 60).toString().padStart(2, '0')}.${(h % 100).toString().padStart(2, '0')}"
    }

    /** A gap between two times: "2.15" under a minute, otherwise as [format]. */
    fun formatDifference(hundredths: Int): String {
        val h = hundredths.coerceIn(0, MAX)
        return if (h < 6000) "${h / 100}.${(h % 100).toString().padStart(2, '0')}" else format(h)
    }

    /**
     * Reads a typed time, or null if it isn't one. Accepted:
     * - `m:ss.ff`, `m:ss` (no hundredths) and `s.ff` in seconds alone (`95.5` is 1:35.50), with the
     *   `'` and `"` marks guides use as separators too, a comma for the decimal point, and
     *   `m.ss.cc` for a decimal keypad without a colon;
     * - bare digits, read from the right as the number pad fills a stopwatch: `14532` is 1:45.32,
     *   `532` is 5.32 seconds.
     * Seconds after minutes must be under 60; one hundredth digit means tenths (`1:02.5` is 1:02.50).
     * Zero isn't a time.
     */
    fun parse(text: String): Int? {
        var t = text.trim().replace('\'', ':').replace('"', '.').replace(',', '.').replace(" ", "")
        if (t.isEmpty()) return null
        // A decimal keypad has no colon: "1.45.32" is 1:45.32.
        if (':' !in t && t.count { it == '.' } == 2) t = t.replaceFirst('.', ':')
        val total = if (t.all(Char::isDigit)) fromDigits(t) else fromParts(t)
        return total?.takeIf { it in 1..MAX }
    }

    /** Stopwatch entry: the last two digits are hundredths, the two before seconds, the rest minutes. */
    fun fromDigits(digits: String): Int? {
        if (digits.isEmpty() || digits.length > 6 || !digits.all(Char::isDigit)) return null
        val n = digits.toInt()
        val hundredths = n % 100
        val seconds = n / 100 % 100
        val minutes = n / 10_000
        if (minutes > 0 && seconds >= 60) return null
        return minutes * 6000 + seconds * 100 + hundredths
    }

    private fun fromParts(t: String): Int? {
        val colon = t.split(':')
        if (colon.size > 2) return null
        val minutes = if (colon.size == 2) colon[0].toIntOrNull()?.takeIf { colon[0].length <= 2 } ?: return null else 0
        val rest = colon.last()
        val dot = rest.split('.')
        if (dot.size > 2 || dot[0].isEmpty() || !dot[0].all(Char::isDigit)) return null
        val seconds = dot[0].toInt()
        if (colon.size == 2 && (dot[0].length != 2 || seconds >= 60)) return null
        val fraction = dot.getOrNull(1)
        val hundredths = when {
            fraction == null -> 0
            fraction.isEmpty() || fraction.length > 2 || !fraction.all(Char::isDigit) -> return null
            fraction.length == 1 -> fraction.toInt() * 10
            else -> fraction.toInt()
        }
        return minutes * 6000 + seconds * 100 + hundredths
    }
}
