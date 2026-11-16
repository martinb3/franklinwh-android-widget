package org.mbs3.franklinwh.widget.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mbs3.franklinwh.widget.R
import org.mbs3.franklinwh.widget.data.CloudCredentials
import org.mbs3.franklinwh.widget.data.DisplayFormat
import org.mbs3.franklinwh.widget.data.GatewayConfig
import org.mbs3.franklinwh.widget.data.GatewayReading
import org.mbs3.franklinwh.widget.data.GatewayRefresher
import org.mbs3.franklinwh.widget.data.ReadingParseException
import org.mbs3.franklinwh.widget.protocol.CloudClient
import org.mbs3.franklinwh.widget.protocol.GatewayClient
import org.mbs3.franklinwh.widget.work.RefreshScheduler
import java.io.IOException

/**
 * Picks the data source and sets the gateway host and port (Local) or the account and gateway
 * serial (Cloud). Used as the widget's configure activity and from the launcher.
 */
class WidgetConfigActivity : Activity() {

    private val scope = MainScope()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var sourceGroup: RadioGroup
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var solarInput: EditText
    private lateinit var gridInput: EditText
    private lateinit var accountInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var serialInput: EditText
    private lateinit var status: TextView
    private val credentials by lazy { CloudCredentials(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // If the user backs out, the host must not add the widget.
        setResult(RESULT_CANCELED, resultIntent())

        setContentView(R.layout.activity_config)
        sourceGroup = findViewById(R.id.source)
        hostInput = findViewById(R.id.host)
        portInput = findViewById(R.id.port)
        solarInput = findViewById(R.id.solar_capacity)
        gridInput = findViewById(R.id.grid_limit)
        accountInput = findViewById(R.id.cloud_account)
        passwordInput = findViewById(R.id.cloud_password)
        serialInput = findViewById(R.id.cloud_serial)
        status = findViewById(R.id.status)

        val config = GatewayConfig.load(this)
        hostInput.setText(config.host)
        portInput.setText(config.port.toString())
        solarInput.hint = getString(R.string.config_optional)
        gridInput.hint = getString(R.string.config_optional)
        config.solarCapacityKw?.let { solarInput.setText(it.toString()) }
        config.gridLimitKw?.let { gridInput.setText(it.toString()) }
        accountInput.setText(config.cloudAccount)
        serialInput.setText(config.cloudGatewayId)
        // The password is never shown again; only its hash is stored.
        if (credentials.passwordMd5 != null) passwordInput.hint = getString(R.string.config_cloud_password_saved)
        sourceGroup.check(
            if (config.source == GatewayConfig.Source.CLOUD) R.id.source_cloud else R.id.source_local,
        )
        showFieldsFor(selectedSource())
        sourceGroup.setOnCheckedChangeListener { _, _ ->
            status.text = ""
            showFieldsFor(selectedSource())
        }

        findViewById<Button>(R.id.test).setOnClickListener { testConnection() }
        findViewById<Button>(R.id.save).setOnClickListener { save() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun selectedSource(): GatewayConfig.Source =
        if (sourceGroup.checkedRadioButtonId == R.id.source_cloud) GatewayConfig.Source.CLOUD
        else GatewayConfig.Source.LOCAL

    private fun showFieldsFor(source: GatewayConfig.Source) {
        val cloud = source == GatewayConfig.Source.CLOUD
        findViewById<View>(R.id.local_fields).visibility = if (cloud) View.GONE else View.VISIBLE
        findViewById<View>(R.id.cloud_fields).visibility = if (cloud) View.VISIBLE else View.GONE
    }

    private fun currentConfig(): GatewayConfig? {
        val config = GatewayConfig(
            host = hostInput.text.toString().trim(),
            port = portInput.text.toString().toIntOrNull() ?: 0,
            source = selectedSource(),
            cloudAccount = accountInput.text.toString().trim(),
            cloudGatewayId = serialInput.text.toString().trim(),
        )
        if (!config.isConfigured) {
            status.text = getString(
                if (config.source == GatewayConfig.Source.CLOUD) R.string.config_cloud_invalid
                else R.string.config_invalid,
            )
            return null
        }
        return config
    }

    /** Hashes the typed password straight away; null when the field is empty. */
    private fun typedPasswordMd5(): String? =
        passwordInput.text.toString().takeIf { it.isNotEmpty() }?.let(CloudClient::md5Hex)

    /** The typed password's hash, else the stored one; null (with a prompt shown) if neither. */
    private fun cloudPasswordMd5(): String? {
        val md5 = typedPasswordMd5() ?: credentials.passwordMd5
        if (md5 == null) status.text = getString(R.string.config_cloud_password_needed)
        return md5
    }

    private fun testConnection() {
        val config = currentConfig() ?: return
        val cloudMd5 = if (config.source == GatewayConfig.Source.CLOUD) cloudPasswordMd5() ?: return else null
        status.text = getString(R.string.config_testing)
        scope.launch {
            status.text = withContext(Dispatchers.IO) {
                try {
                    // Reads once from the selected source without storing anything.
                    val data = if (cloudMd5 == null) {
                        GatewayClient(config.host, config.port).queryDeviceDetail()
                    } else {
                        val client = CloudClient()
                        client.compositeInfo(client.login(config.cloudAccount, cloudMd5), config.cloudGatewayId)
                    }
                    val reading = GatewayReading.fromDataArea(data, System.currentTimeMillis())
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
        if (connection.source == GatewayConfig.Source.CLOUD && cloudPasswordMd5() == null) return
        val typedMd5 = typedPasswordMd5()
        var solarKw: Double? = null
        var gridKw: Double? = null
        val solarOk = capacity(solarInput) { solarKw = it }
        val gridOk = capacity(gridInput) { gridKw = it }
        if (!solarOk || !gridOk) return
        val config = connection.copy(solarCapacityKw = solarKw, gridLimitKw = gridKw)
        config.save(this)
        typedMd5?.let { credentials.savePasswordMd5(it) }
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
