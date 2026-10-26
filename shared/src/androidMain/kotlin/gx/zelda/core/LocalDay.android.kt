package gx.zelda.core

import java.time.LocalDate

actual fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
