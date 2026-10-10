package org.mbs3.franklinwh.widget.protocol

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Exercises the socket path against a loopback server that replays a golden reply. */
class GatewayClientTest {

    private fun goldenReply(): ByteArray {
        val arr = JSONArray(javaClass.classLoader!!.getResource("golden.json")!!.readText())
        val hex = (0 until arr.length()).map { arr.getJSONObject(it) }
            .single { it.getString("name") == "response_device_detail_sn_10060010A00X00000000" }
            .getString("frame_hex")
        return ByteArray(hex.length / 2) { hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
    }

    @Test
    fun queryDeviceDetailSendsDummySerialAndDecodesReply() {
        val reply = goldenReply()
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            var received = ByteArray(0)
            val t = thread {
                server.accept().use { s ->
                    val buf = ByteArray(4096)
                    val n = s.getInputStream().read(buf)
                    received = buf.copyOf(n)
                    // Split the reply to check that reading continues past the first chunk.
                    s.getOutputStream().write(reply, 0, 100)
                    s.getOutputStream().flush()
                    Thread.sleep(50)
                    s.getOutputStream().write(reply, 100, reply.size - 100)
                    s.getOutputStream().flush()
                }
            }
            val data = GatewayClient("127.0.0.1", server.localPort).queryDeviceDetail()
            t.join()

            val header = "{\"cmdType\":1301,\"equipNo\":\"00000000\","
            assertTrue(String(received, Charsets.ISO_8859_1).startsWith(header))
            val sent = Frame.parseResponse(received)
            assertEquals(1, sent.snno)
            assertEquals("{\"opt\":0}", sent.dataArea.toString())
            assertEquals(1816, data.getInt("p_sun"))
            assertEquals(28.157896041870117, data.getDouble("soc"), 0.0)
        }
    }
}
