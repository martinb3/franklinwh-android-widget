package org.mbs3.franklinwh.widget.protocol

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Loopback HTTP server that replays canned replies for [CloudClient] tests. Replies are queued per
 * path; the last one queued for a path keeps being served.
 */
class FakeCloudServer : AutoCloseable {

    data class Request(
        val method: String,
        val path: String,
        val query: String?,
        val loginToken: String?,
        val contentType: String?,
        val body: String,
    )

    private class Reply(val status: Int, val body: String)

    val requests = CopyOnWriteArrayList<Request>()
    private val replies = HashMap<String, ArrayDeque<Reply>>()

    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/") { exchange ->
            val uri = exchange.requestURI
            requests += Request(
                method = exchange.requestMethod,
                path = uri.path,
                query = uri.rawQuery,
                loginToken = exchange.requestHeaders.getFirst("loginToken"),
                contentType = exchange.requestHeaders.getFirst("Content-Type"),
                body = exchange.requestBody.readBytes().toString(Charsets.UTF_8),
            )
            val reply = synchronized(replies) {
                val queue = replies[uri.path]
                when {
                    queue == null || queue.isEmpty() -> Reply(404, "")
                    queue.size == 1 -> queue.first()
                    else -> queue.removeFirst()
                }
            }
            val bytes = reply.body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(reply.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    val baseUrl: String = "http://127.0.0.1:${server.address.port}/"

    fun enqueue(path: String, body: String, status: Int = 200) {
        synchronized(replies) { replies.getOrPut("/$path") { ArrayDeque() }.addLast(Reply(status, body)) }
    }

    fun client(): CloudClient = CloudClient(baseUrl, connectTimeoutMs = 2000, readTimeoutMs = 2000)

    override fun close() = server.stop(0)

    companion object {
        const val LOGIN = "hes-gateway/terminal/initialize/appUserOrInstallerLogin"
        const val COMPOSITE = "hes-gateway/terminal/getDeviceCompositeInfo"

        fun resource(name: String): String =
            FakeCloudServer::class.java.classLoader!!.getResource("cloud/$name")!!.readText()

        fun codeOnly(code: Int): String = """{"code":$code,"message":"x","result":null,"success":false}"""
    }
}
