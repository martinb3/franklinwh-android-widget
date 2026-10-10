package org.mbs3.franklinwh.widget.data

/**
 * Average power in watts between two readings, derived from the daily kWh counters.
 * [gridW] is net import (positive = importing); [batteryW] is net discharge (positive = discharging).
 */
data class PowerAverages(
    val solarW: Double,
    val loadW: Double,
    val gridW: Double,
    val batteryW: Double,
) {
    companion object {
        /** The gateway updates its counters about every 5 minutes, so shorter windows are noise. */
        const val MIN_WINDOW_MS = 15 * 60 * 1000L

        /**
         * Returns the averages from [older] to [newer], or null when the window is shorter than
         * [MIN_WINDOW_MS] or any counter decreased (the gateway's midnight reset).
         */
        fun between(older: GatewayReading, newer: GatewayReading): PowerAverages? {
            val dtMs = newer.timestampMs - older.timestampMs
            if (dtMs < MIN_WINDOW_MS) return null
            val counters = listOf(
                GatewayReading::kwhSolar,
                GatewayReading::kwhLoad,
                GatewayReading::kwhGridIn,
                GatewayReading::kwhGridOut,
                GatewayReading::kwhBatteryCharge,
                GatewayReading::kwhBatteryDischarge,
                GatewayReading::kwhGenerator,
            )
            if (counters.any { it(newer) < it(older) }) return null

            val hours = dtMs / 3_600_000.0
            fun w(deltaKwh: Double) = deltaKwh / hours * 1000.0
            return PowerAverages(
                solarW = w(newer.kwhSolar - older.kwhSolar),
                loadW = w(newer.kwhLoad - older.kwhLoad),
                gridW = w((newer.kwhGridIn - older.kwhGridIn) - (newer.kwhGridOut - older.kwhGridOut)),
                batteryW = w(
                    (newer.kwhBatteryDischarge - older.kwhBatteryDischarge) -
                        (newer.kwhBatteryCharge - older.kwhBatteryCharge),
                ),
            )
        }
    }
}
