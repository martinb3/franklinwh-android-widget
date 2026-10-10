package org.mbs3.franklinwh.widget.protocol

/** Request `cmdType` codes. The gateway replies with `cmdType = request + 1`. */
object Commands {
    /** deviceDetailInfo: live power, state of charge and daily energy counters. Reply is 1302. */
    const val DEVICE_DETAIL_INFO = 1301
}
