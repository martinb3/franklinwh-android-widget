package org.mbs3.franklinwh.widget.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
     * How old a reading is. Relative wording is used only inside the live window
     * ([WidgetStateFactory.LIVE_MAX_AGE_MS]), where a redraw is guaranteed when it ends; after
     * that the widget may not redraw for a long time, so the text names a fixed time instead:
     * - younger than the live window: "just now"
     * - same calendar day in [zone]: "as of 14:05" (or "as of 2:05 PM" when not [is24Hour])
     * - 1 to 6 calendar days earlier: "as of Mon 14:05"
     * - older: "as of 3 Oct 14:05"
     */
    fun ageText(
        nowMs: Long,
        thenMs: Long,
        is24Hour: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        if (nowMs - thenMs < WidgetStateFactory.LIVE_MAX_AGE_MS) return "just now"
        val then = Instant.ofEpochMilli(thenMs).atZone(zone)
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate())
        val time = if (is24Hour) "HH:mm" else "h:mm a"
        val pattern = when {
            days <= 0 -> time
            days <= 6 -> "EEE $time"
            else -> "d MMM $time"
        }
        return "as of " + DateTimeFormatter.ofPattern(pattern, locale).format(then)
    }

    private fun deadband(watts: Double): Double = if (abs(watts) < DEADBAND_W) 0.0 else watts

    /** Rounds half away from zero to a whole number of tenths. */
    private fun roundTenths(tenths: Double): Long {
        val r = floor(abs(tenths) + 0.5).toLong()
        return if (tenths < 0) -r else r
    }

    private fun formatTenths(t: Long): String = "${t / 10}.${t % 10}"
}
