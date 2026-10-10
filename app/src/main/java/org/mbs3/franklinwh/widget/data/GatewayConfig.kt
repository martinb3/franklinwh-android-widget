package org.mbs3.franklinwh.widget.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Where to reach the gateway on the LAN, plus optional capacities used to scale the solar and
 * grid bars on larger widget sizes (null when not set).
 */
data class GatewayConfig(
    val host: String,
    val port: Int,
    val solarCapacityKw: Double? = null,
    val gridLimitKw: Double? = null,
) {

    val isConfigured: Boolean
        get() = host.isNotBlank() && port in 1..65535

    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putString(KEY_HOST, host.trim())
            .putInt(KEY_PORT, port)
            .putOptionalDouble(KEY_SOLAR_KW, solarCapacityKw)
            .putOptionalDouble(KEY_GRID_KW, gridLimitKw)
            .commit()
    }

    fun save(context: Context) = save(prefs(context))

    companion object {
        const val PREFS_NAME = "gateway"
        const val DEFAULT_PORT = 9000
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_SOLAR_KW = "solarCapacityKw"
        private const val KEY_GRID_KW = "gridLimitKw"

        fun load(prefs: SharedPreferences): GatewayConfig = GatewayConfig(
            host = prefs.getString(KEY_HOST, "") ?: "",
            port = prefs.getInt(KEY_PORT, DEFAULT_PORT),
            solarCapacityKw = prefs.getOptionalDouble(KEY_SOLAR_KW),
            gridLimitKw = prefs.getOptionalDouble(KEY_GRID_KW),
        )

        fun load(context: Context): GatewayConfig = load(prefs(context))

        /**
         * Parses an optional capacity field: blank is null, a positive number is accepted.
         * @throws IllegalArgumentException if the text is not a positive, finite number.
         */
        fun parseCapacity(text: String): Double? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val value = trimmed.toDoubleOrNull()
            require(value != null && value.isFinite() && value > 0) { "must be a positive number" }
            return value
        }

        private fun prefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Stored as strings: SharedPreferences has no double type and Float loses precision.
        private fun SharedPreferences.Editor.putOptionalDouble(key: String, value: Double?) =
            if (value == null) remove(key) else putString(key, value.toString())

        private fun SharedPreferences.getOptionalDouble(key: String): Double? =
            getString(key, null)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    }
}
