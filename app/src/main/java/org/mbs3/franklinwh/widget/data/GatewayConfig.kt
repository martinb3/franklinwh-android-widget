package org.mbs3.franklinwh.widget.data

import android.content.Context
import android.content.SharedPreferences

/** Where to reach the gateway on the LAN. */
data class GatewayConfig(val host: String, val port: Int) {

    val isConfigured: Boolean
        get() = host.isNotBlank() && port in 1..65535

    fun save(prefs: SharedPreferences) {
        prefs.edit().putString(KEY_HOST, host.trim()).putInt(KEY_PORT, port).commit()
    }

    fun save(context: Context) = save(prefs(context))

    companion object {
        const val PREFS_NAME = "gateway"
        const val DEFAULT_PORT = 9000
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"

        fun load(prefs: SharedPreferences): GatewayConfig =
            GatewayConfig(prefs.getString(KEY_HOST, "") ?: "", prefs.getInt(KEY_PORT, DEFAULT_PORT))

        fun load(context: Context): GatewayConfig = load(prefs(context))

        private fun prefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
