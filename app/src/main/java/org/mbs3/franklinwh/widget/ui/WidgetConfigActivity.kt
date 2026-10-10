package org.mbs3.franklinwh.widget.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
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
import org.mbs3.franklinwh.widget.work.RefreshScheduler
import java.io.IOException

/** Sets the gateway host and port. Used as the widget's configure activity and from the launcher. */
class WidgetConfigActivity : Activity() {

    private val scope = MainScope()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var solarInput: EditText
    private lateinit var gridInput: EditText
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
        solarInput = findViewById(R.id.solar_capacity)
        gridInput = findViewById(R.id.grid_limit)
        status = findViewById(R.id.status)

        val config = GatewayConfig.load(this)
        hostInput.setText(config.host)
        portInput.setText(config.port.toString())
        solarInput.hint = getString(R.string.config_optional)
        gridInput.hint = getString(R.string.config_optional)
        config.solarCapacityKw?.let { solarInput.setText(it.toString()) }
        config.gridLimitKw?.let { gridInput.setText(it.toString()) }

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

    /** Parses an optional capacity field, showing an inline error and returning false if invalid. */
    private fun capacity(input: EditText, out: (Double?) -> Unit): Boolean = try {
        out(GatewayConfig.parseCapacity(input.text.toString()))
        input.error = null
        true
    } catch (_: IllegalArgumentException) {
        input.error = getString(R.string.config_capacity_invalid)
        false
    }

    private fun save() {
        val connection = currentConfig() ?: return
        var solarKw: Double? = null
        var gridKw: Double? = null
        val solarOk = capacity(solarInput) { solarKw = it }
        val gridOk = capacity(gridInput) { gridKw = it }
        if (!solarOk || !gridOk) return
        val config = connection.copy(solarCapacityKw = solarKw, gridLimitKw = gridKw)
        config.save(this)
        findViewById<Button>(R.id.save).isEnabled = false
        scope.launch {
            RefreshScheduler.ensurePeriodic(applicationContext)
            RefreshAction.requestRefresh(applicationContext)
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
