package org.mbs3.franklinwh.widget.data

const val MIN = 60_000L
const val HOUR = 60 * MIN

fun reading(
    ts: Long,
    soc: Double = 50.0,
    kwhSolar: Double = 0.0,
    kwhLoad: Double = 0.0,
    kwhGridIn: Double = 0.0,
    kwhGridOut: Double = 0.0,
    kwhBatteryCharge: Double = 0.0,
    kwhBatteryDischarge: Double = 0.0,
    kwhGenerator: Double = 0.0,
) = GatewayReading(
    timestampMs = ts,
    soc = soc,
    batterySocEach = listOf(soc),
    solarW = 0.0,
    loadW = 0.0,
    gridW = 0.0,
    batteryW = 0.0,
    generatorW = 0.0,
    kwhSolar = kwhSolar,
    kwhLoad = kwhLoad,
    kwhGridIn = kwhGridIn,
    kwhGridOut = kwhGridOut,
    kwhBatteryCharge = kwhBatteryCharge,
    kwhBatteryDischarge = kwhBatteryDischarge,
    kwhGenerator = kwhGenerator,
)
