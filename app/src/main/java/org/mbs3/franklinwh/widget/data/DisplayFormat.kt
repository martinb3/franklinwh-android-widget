package org.mbs3.franklinwh.widget.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Text formatting for the widget. Pure; matches the pixel-clock bridge's display rules. */
object DisplayFormat {
    /** Magnitudes below this many watts display as an unsigned 0.0 kW. */
    const val DEADBAND_W = 100.0
    const val MINUS = '\u2212'

    /** Unsigned kW with one decimal, for solar and load. */
    fun powerUnsigned(watts: Double): String = "${formatTenths(abs(roundTenths(deadband(watts) / 100.0)))} kW"

    /**
     * Signed kW with one decimal, for grid and battery. `+` means supplying the house (grid
     * importing, battery discharging), [MINUS] means absorbing. The sign is decided after
     * rounding, so 0.0 is never signed.
     */
    fun powerSigned(watts: Double): String {
        val t = roundTenths(deadband(watts) / 100.0)
        val sign = when {
            t > 0 -> "+"
            t < 0 -> MINUS.toString()
            else -> ""
        }
        return "$sign${formatTenths(abs(t))} kW"
    }

    /** Battery percentage, rounded: 42.9 -> "43%". */
    fun soc(percent: Double): String = "${percent.roundToInt()}%"

    /** Energy with one decimal: "10.3 kWh". */
    fun energy(kwh: Double): String {
        val t = roundTenths(kwh * 10.0)
        return "${if (t < 0) MINUS.toString() else ""}${formatTenths(abs(t))} kWh"
    }

    /**
     * How old a reading is: "just now" under a minute, "Nm ago" under an hour, otherwise
     * "as of HH:MM" in [zone] using the 24 h or 12 h clock.
     */
    fun ageText(
        nowMs: Long,
        thenMs: Long,
        is24Hour: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val ageMs = (nowMs - thenMs).coerceAtLeast(0)
        return when {
            ageMs < 60_000 -> "just now"
            ageMs < 3_600_000 -> "${ageMs / 60_000}m ago"
            else -> {
                val pattern = if (is24Hour) "HH:mm" else "h:mm a"
                "as of " + DateTimeFormatter.ofPattern(pattern, locale)
                    .format(Instant.ofEpochMilli(thenMs).atZone(zone))
            }
        }
    }

    private fun deadband(watts: Double): Double = if (abs(watts) < DEADBAND_W) 0.0 else watts

    /** Rounds half away from zero to a whole number of tenths. */
    private fun roundTenths(tenths: Double): Long {
        val r = floor(abs(tenths) + 0.5).toLong()
        return if (tenths < 0) -r else r
    }

    private fun formatTenths(t: Long): String = "${t / 10}.${t % 10}"
}
