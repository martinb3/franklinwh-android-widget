package org.mbs3.franklinwh.widget.data

import kotlin.math.abs

/** Fill fractions for the solar and grid bars, scaled against user-supplied capacities. */
object CapacityBars {
    /** Solar output as a fraction of capacity, clamped to 0..1; null when no capacity is set. */
    fun solar(solarW: Double, capacityKw: Double?): Float? = fraction(solarW, capacityKw)

    /** Grid flow in either direction as a fraction of the limit, clamped to 0..1; null when unset. */
    fun grid(gridW: Double, limitKw: Double?): Float? = fraction(abs(gridW), limitKw)

    private fun fraction(watts: Double, capacityKw: Double?): Float? {
        if (capacityKw == null || capacityKw <= 0) return null
        return (watts / (capacityKw * 1000.0)).coerceIn(0.0, 1.0).toFloat()
    }
}
