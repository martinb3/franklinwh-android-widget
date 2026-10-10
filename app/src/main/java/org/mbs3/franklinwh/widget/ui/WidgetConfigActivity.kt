package org.mbs3.franklinwh.widget.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mbs3.franklinwh.widget.R
import org.mbs3.franklinwh.widget.data.DisplayFormat
import org.mbs3.franklinwh.widget.data.GatewayConfig
import org.mbs3.franklinwh.widget.data.GatewayReading
import org.mbs3.franklinwh.widget.data.GatewayRefresher
import org.mbs3.franklinwh.widget.data.ReadingParseException
import org.mbs3.franklinwh.widget.protocol.GatewayClient
import java.io.IOException

/** Sets the gateway host and port. Used as the widget's configure activity and from the launcher. */
class WidgetConfigActivity : Activity() {

    private val scope = MainScope()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // If the user backs out, the host must not add the widget.
        setResult(RESULT_CANCELED, resultIntent())

        setContentView(R.layout.activity_config)
        hostInput = findViewById(R.id.host)
        portInput = findViewById(R.id.port)
        status = findViewById(R.id.status)

        val config = GatewayConfig.load(this)
        hostInput.setText(config.host)
        portInput.setText(config.port.toString())

        findViewById<Button>(R.id.test).setOnClickListener { testConnection() }
        findViewById<Button>(R.id.save).setOnClickListener { save() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun currentConfig(): GatewayConfig? {
        val config = GatewayConfig(hostInput.text.toString().trim(), portInput.text.toString().toIntOrNull() ?: 0)
        if (!config.isConfigured) {
            status.text = getString(R.string.config_invalid)
            return null
        }
        return config
    }

    private fun testConnection() {
        val config = currentConfig() ?: return
        status.text = getString(R.string.config_testing)
        scope.launch {
            status.text = withContext(Dispatchers.IO) {
                try {
                    val reading = GatewayReading.fromDataArea(
                        GatewayClient(config.host, config.port).queryDeviceDetail(),
                        System.currentTimeMillis(),
                    )
                    getString(
                        R.string.config_test_ok,
                        DisplayFormat.soc(reading.soc),
                        reading.solarW.toLong(),
                    )
                } catch (e: IOException) {
                    getString(R.string.config_test_failed, GatewayRefresher.describe(e))
                } catch (e: ReadingParseException) {
                    getString(R.string.config_test_failed, GatewayRefresher.describe(e))
                }
            }
        }
    }

    private fun save() {
        val config = currentConfig() ?: return
        config.save(this)
        val appContext = applicationContext
        // Outlives this activity; the refresh takes about a second on the LAN.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            RefreshAction.requestRefresh(appContext)
        }
        setResult(RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
