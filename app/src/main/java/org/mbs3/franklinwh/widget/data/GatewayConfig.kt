package org.mbs3.franklinwh.widget.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Where to read the gateway: on the LAN ([host], [port]) or through the cloud ([cloudAccount],
 * [cloudGatewayId]; the secrets live in [CloudCredentials]), per [source]. Also optional
 * capacities used to scale the solar and grid bars on larger widget sizes (null when not set).
 */
data class GatewayConfig(
    val host: String,
    val port: Int,
    val solarCapacityKw: Double? = null,
    val gridLimitKw: Double? = null,
    val source: Source = Source.LOCAL,
    val cloudAccount: String = "",
    val cloudGatewayId: String = "",
) {

    enum class Source { LOCAL, CLOUD }

    /** Whether the fields the selected [source] needs are filled in. */
    val isConfigured: Boolean
        get() = when (source) {
            Source.LOCAL -> host.isNotBlank() && port in 1..65535
            Source.CLOUD -> cloudAccount.isNotBlank() && cloudGatewayId.isNotBlank()
        }

    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putString(KEY_HOST, host.trim())
            .putInt(KEY_PORT, port)
            .putOptionalDouble(KEY_SOLAR_KW, solarCapacityKw)
            .putOptionalDouble(KEY_GRID_KW, gridLimitKw)
            .putString(KEY_SOURCE, source.name)
            .putString(KEY_CLOUD_ACCOUNT, cloudAccount.trim())
            .putString(KEY_CLOUD_GATEWAY_ID, cloudGatewayId.trim())
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
        private const val KEY_SOURCE = "source"
        private const val KEY_CLOUD_ACCOUNT = "cloudAccount"
        private const val KEY_CLOUD_GATEWAY_ID = "cloudGatewayId"

        fun load(prefs: SharedPreferences): GatewayConfig = GatewayConfig(
            host = prefs.getString(KEY_HOST, "") ?: "",
            port = prefs.getInt(KEY_PORT, DEFAULT_PORT),
            solarCapacityKw = prefs.getOptionalDouble(KEY_SOLAR_KW),
            gridLimitKw = prefs.getOptionalDouble(KEY_GRID_KW),
            // Installs from before the cloud source have no value here and stay local.
            source = Source.entries.find { it.name == prefs.getString(KEY_SOURCE, null) } ?: Source.LOCAL,
            cloudAccount = prefs.getString(KEY_CLOUD_ACCOUNT, "") ?: "",
            cloudGatewayId = prefs.getString(KEY_CLOUD_GATEWAY_ID, "") ?: "",
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
