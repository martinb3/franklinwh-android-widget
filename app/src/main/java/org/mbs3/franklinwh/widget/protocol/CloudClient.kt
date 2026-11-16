package org.mbs3.franklinwh.widget.protocol

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** A cloud API failure. Messages never quote the account, serial, token or password hash. */
open class CloudException(message: String) : IOException(message)

/** The cloud rejected the credentials or the token (JSON `code` 401). */
class CloudAuthException : CloudException("authentication rejected")

/** The cloud reports the account as locked (JSON `code` 400 on login). */
class CloudAccountLockedException : CloudException("account locked")

/** The login reply asks for a second factor, which this app does not support. */
class CloudMfaRequiredException : CloudException("MFA required")

/** The server answered with a non-2xx HTTP status. */
class CloudHttpException(val status: Int) : CloudException("HTTP $status")

/** The reply was not JSON, had an unexpected `code`, or lacked an expected field. */
class CloudApiException(message: String) : CloudException(message)

/**
 * Blocking client for FranklinWH's unofficial app cloud API, the one the `franklinwh` Python
 * library uses. Each call opens a fresh connection. Must not be called on the main thread.
 */
class CloudClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMs: Int = 10000,
    private val readTimeoutMs: Int = 15000,
) {

    /**
     * Logs in with the account email and the MD5 hex of the password, returning the login token.
     * @throws CloudAuthException on bad credentials, [CloudAccountLockedException] if locked,
     *   [CloudMfaRequiredException] if the account needs a second factor.
     */
    @Throws(IOException::class)
    fun login(account: String, md5Hex: String): String {
        val form = formEncode(
            "account" to account,
            "password" to md5Hex,
            "lang" to "en_US",
            "type" to "1",
        )
        val connection = open(LOGIN_PATH)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
        val reply = read(connection)
        when (reply.optInt("code", -1)) {
            200 -> {}
            401 -> throw CloudAuthException()
            400 -> throw CloudAccountLockedException()
            else -> throw CloudApiException("unexpected login code")
        }
        val result = reply.optJSONObject("result") ?: throw CloudApiException("login reply has no result")
        val token = result.stringOrEmpty("token")
        if (token.isNotBlank()) return token
        if (isTruthy(result.opt("mfaRequired")) || result.stringOrEmpty("mfaToken").isNotBlank()) {
            throw CloudMfaRequiredException()
        }
        throw CloudApiException("login reply has no token")
    }

    /**
     * Fetches the gateway's `runtimeData`, with power converted to watts so it reads like the
     * local `dataArea` (see [toLocalUnits]).
     * @throws CloudAuthException if the token was rejected (expired or invalid).
     */
    @Throws(IOException::class)
    fun compositeInfo(token: String, gatewayId: String): JSONObject {
        val query = formEncode("gatewayId" to gatewayId, "refreshFlag" to "1", "lang" to "en_US")
        val connection = open("$COMPOSITE_PATH?$query")
        connection.requestMethod = "GET"
        connection.setRequestProperty("loginToken", token)
        val reply = read(connection)
        when (reply.optInt("code", -1)) {
            200 -> {}
            401 -> throw CloudAuthException()
            else -> throw CloudApiException("unexpected data code")
        }
        val runtimeData = reply.optJSONObject("result")?.optJSONObject("runtimeData")
            ?: throw CloudApiException("data reply has no runtimeData")
        return toLocalUnits(runtimeData)
    }

    private fun open(path: String): HttpURLConnection =
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            useCaches = false
        }

    private fun read(connection: HttpURLConnection): JSONObject {
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw CloudHttpException(status)
            val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            return try {
                JSONObject(body)
            } catch (_: JSONException) {
                throw CloudApiException("reply is not JSON")
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://energy.franklinwh.com/"
        private const val LOGIN_PATH = "hes-gateway/terminal/initialize/appUserOrInstallerLogin"
        private const val COMPOSITE_PATH = "hes-gateway/terminal/getDeviceCompositeInfo"

        /** Power fields the cloud reports in kW; the local protocol and `GatewayReading` use W. */
        private val POWER_KEYS = listOf("p_sun", "p_load", "p_uti", "p_fhp", "p_gen")

        /** Lower-case hex MD5 of the UTF-8 text, the form the login expects for the password. */
        fun md5Hex(text: String): String =
            MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        /**
         * Returns a copy of the cloud's `runtimeData` with the power fields converted from kW to W,
         * so it can go through `GatewayReading.fromDataArea` like the local `dataArea`. Absent or
         * null fields, and values that are not numbers, are left as they are.
         */
        fun toLocalUnits(runtimeData: JSONObject): JSONObject {
            val out = JSONObject(runtimeData.toString())
            for (key in POWER_KEYS) {
                if (!out.has(key) || out.isNull(key)) continue
                val v = out.opt(key)
                val kw = if (v is Number) v.toDouble() else (v as? String)?.trim()?.toDoubleOrNull()
                if (kw != null) out.put(key, kw * 1000)
            }
            return out
        }

        private fun isTruthy(value: Any?): Boolean = when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> value.equals("true", ignoreCase = true)
            else -> false
        }

        // Android's optString turns JSON null into "null", so check for it first.
        private fun JSONObject.stringOrEmpty(key: String): String =
            if (isNull(key)) "" else optString(key, "")

        private fun formEncode(vararg fields: Pair<String, String>): String =
            fields.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
    }
}
