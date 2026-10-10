package org.mbs3.franklinwh.widget.protocol

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Blocking client for the gateway's local TCP protocol. Each request uses a fresh socket.
 *
 * Requests are sent with the dummy serial [Frame.DEFAULT_EQUIP_NO], which the gateway accepts;
 * replies carry the real serial and are decoded by [Frame.parseResponse].
 * Ported from franklinwh-local's `client.py`. Must not be called on the main thread.
 */
class GatewayClient(
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 10000,
    private val idleTimeoutMs: Int = 500,
) {
    private val snno = AtomicInteger(0)

    /** Sends deviceDetailInfo (1301) and returns the reply's `dataArea`. */
    @Throws(IOException::class)
    fun queryDeviceDetail(): JSONObject =
        request(Commands.DEVICE_DETAIL_INFO, "{\"opt\":0}").dataArea

    @Throws(IOException::class)
    fun request(cmdType: Int, dataAreaJson: String): Frame.Envelope {
        val frame = Frame.buildRequest(
            cmdType = cmdType,
            equipNo = Frame.DEFAULT_EQUIP_NO,
            dataAreaJson = dataAreaJson,
            snno = snno.incrementAndGet() and 0xFFFF,
            timestamp = System.currentTimeMillis() / 1000,
        )
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = readTimeoutMs
            socket.getOutputStream().apply {
                write(frame)
                flush()
            }
            return Frame.parseResponse(readFrame(socket))
        }
    }

    /**
     * Reads one reply: waits up to the read timeout for the first bytes, then keeps reading until
     * [idleTimeoutMs] passes with no data, the peer closes, [MAX_FRAME_BYTES] is reached, or the
     * read timeout has elapsed overall.
     */
    private fun readFrame(socket: Socket): ByteArray {
        val input = socket.getInputStream()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)

        val first = input.read(buf)
        if (first < 0) throw IOException("peer closed connection")
        out.write(buf, 0, first)

        socket.soTimeout = idleTimeoutMs
        val deadline = System.nanoTime() + readTimeoutMs * 1_000_000L
        while (out.size() < MAX_FRAME_BYTES) {
            val n = try {
                input.read(buf)
            } catch (_: SocketTimeoutException) {
                break
            }
            if (n < 0) break
            out.write(buf, 0, n)
            if (System.nanoTime() > deadline) break
        }
        return out.toByteArray()
    }

    companion object {
        const val MAX_FRAME_BYTES = 1 shl 20
    }
}
