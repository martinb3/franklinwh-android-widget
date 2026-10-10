package org.mbs3.franklinwh.widget.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A required field of the gateway's `dataArea` (or a stored reading) is missing or not a number. */
class ReadingParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * One snapshot of the gateway's deviceDetailInfo reply.
 *
 * Power values are watts. [gridW] is positive when importing from the grid; [batteryW] is
 * positive when the batteries discharge into the home. The `kwh*` fields are the gateway's
 * daily counters, which reset at the gateway's midnight.
 */
data class GatewayReading(
    /** Phone wall clock when the reading was received. */
    val timestampMs: Long,
    val soc: Double,
    val batterySocEach: List<Double>,
    val solarW: Double,
    val loadW: Double,
    val gridW: Double,
    val batteryW: Double,
    val generatorW: Double,
    val kwhSolar: Double,
    val kwhLoad: Double,
    val kwhGridIn: Double,
    val kwhGridOut: Double,
    val kwhBatteryCharge: Double,
    val kwhBatteryDischarge: Double,
    val kwhGenerator: Double,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put(K_TS, timestampMs)
        put(K_SOC, soc)
        put(K_SOC_EACH, JSONArray().apply { batterySocEach.forEach { put(it) } })
        put(K_SOLAR, solarW)
        put(K_LOAD, loadW)
        put(K_GRID, gridW)
        put(K_BATTERY, batteryW)
        put(K_GEN, generatorW)
        put(K_KWH_SOLAR, kwhSolar)
        put(K_KWH_LOAD, kwhLoad)
        put(K_KWH_GRID_IN, kwhGridIn)
        put(K_KWH_GRID_OUT, kwhGridOut)
        put(K_KWH_BAT_CHG, kwhBatteryCharge)
        put(K_KWH_BAT_DIS, kwhBatteryDischarge)
        put(K_KWH_GEN, kwhGenerator)
    }

    companion object {
        private const val K_TS = "timestampMs"
        private const val K_SOC = "soc"
        private const val K_SOC_EACH = "batterySocEach"
        private const val K_SOLAR = "solarW"
        private const val K_LOAD = "loadW"
        private const val K_GRID = "gridW"
        private const val K_BATTERY = "batteryW"
        private const val K_GEN = "generatorW"
        private const val K_KWH_SOLAR = "kwhSolar"
        private const val K_KWH_LOAD = "kwhLoad"
        private const val K_KWH_GRID_IN = "kwhGridIn"
        private const val K_KWH_GRID_OUT = "kwhGridOut"
        private const val K_KWH_BAT_CHG = "kwhBatteryCharge"
        private const val K_KWH_BAT_DIS = "kwhBatteryDischarge"
        private const val K_KWH_GEN = "kwhGenerator"

        /**
         * Builds a reading from the gateway's `dataArea`. `soc`, `p_sun`, `p_load` and `p_uti` are
         * required; other fields default to 0 (or an empty list) when absent or null.
         *
         * @throws ReadingParseException if a required field is missing, or any field is present
         *   but not a finite number.
         */
        fun fromDataArea(json: JSONObject, nowMs: Long): GatewayReading = GatewayReading(
            timestampMs = nowMs,
            soc = json.requiredDouble("soc"),
            batterySocEach = json.doubleList("fhpSoc"),
            solarW = json.requiredDouble("p_sun"),
            loadW = json.requiredDouble("p_load"),
            gridW = json.requiredDouble("p_uti"),
            batteryW = json.optionalDouble("p_fhp"),
            generatorW = json.optionalDouble("p_gen"),
            kwhSolar = json.optionalDouble("kwh_sun"),
            kwhLoad = json.optionalDouble("kwh_load"),
            kwhGridIn = json.optionalDouble("kwh_uti_in"),
            kwhGridOut = json.optionalDouble("kwh_uti_out"),
            kwhBatteryCharge = json.optionalDouble("kwh_fhp_chg"),
            kwhBatteryDischarge = json.optionalDouble("kwh_fhp_di"),
            kwhGenerator = json.optionalDouble("kwh_gen"),
        )

        /** Inverse of [toJson]. @throws ReadingParseException if any field is missing or invalid. */
        fun fromJson(json: JSONObject): GatewayReading {
            val ts = json.requiredDouble(K_TS)
            return GatewayReading(
                timestampMs = ts.toLong(),
                soc = json.requiredDouble(K_SOC),
                batterySocEach = json.doubleList(K_SOC_EACH),
                solarW = json.requiredDouble(K_SOLAR),
                loadW = json.requiredDouble(K_LOAD),
                gridW = json.requiredDouble(K_GRID),
                batteryW = json.requiredDouble(K_BATTERY),
                generatorW = json.requiredDouble(K_GEN),
                kwhSolar = json.requiredDouble(K_KWH_SOLAR),
                kwhLoad = json.requiredDouble(K_KWH_LOAD),
                kwhGridIn = json.requiredDouble(K_KWH_GRID_IN),
                kwhGridOut = json.requiredDouble(K_KWH_GRID_OUT),
                kwhBatteryCharge = json.requiredDouble(K_KWH_BAT_CHG),
                kwhBatteryDischarge = json.requiredDouble(K_KWH_BAT_DIS),
                kwhGenerator = json.requiredDouble(K_KWH_GEN),
            )
        }

        private fun JSONObject.requiredDouble(key: String): Double {
            if (!has(key) || isNull(key)) throw ReadingParseException("missing field $key")
            return toDouble(key, opt(key))
        }

        private fun JSONObject.optionalDouble(key: String): Double =
            if (!has(key) || isNull(key)) 0.0 else toDouble(key, opt(key))

        private fun JSONObject.doubleList(key: String): List<Double> {
            if (!has(key) || isNull(key)) return emptyList()
            val arr = opt(key) as? JSONArray ?: throw ReadingParseException("field $key is not a list")
            return (0 until arr.length()).map { toDouble(key, arr.opt(it)) }
        }

        private fun toDouble(key: String, value: Any?): Double {
            val d = when (value) {
                is Number -> value.toDouble()
                is String -> value.trim().toDoubleOrNull()
                else -> null
            }
            if (d == null || !d.isFinite()) throw ReadingParseException("field $key is not a number")
            return d
        }

        /** Parses a stored reading, wrapping org.json errors. */
        fun fromJsonString(text: String): GatewayReading = try {
            fromJson(JSONObject(text))
        } catch (e: JSONException) {
            throw ReadingParseException("stored reading is not valid JSON", e)
        }
    }
}
