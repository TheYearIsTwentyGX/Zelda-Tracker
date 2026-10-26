package gx.zelda.core

/** Today on the device's calendar, as days since 1970-01-01 (`java.time.LocalDate.toEpochDay`). */
expect fun todayEpochDay(): Long

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** [epochDay] as "26 Oct 2026": the proleptic Gregorian calendar, worked out here so common code needs no date library. */
fun formatEpochDay(epochDay: Long): String {
    // Howard Hinnant's civil_from_days.
    val z = epochDay + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0
    return "$day ${MONTHS[(month - 1).toInt()]} $year"
}
