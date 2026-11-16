package org.mbs3.franklinwh.widget.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbs3.franklinwh.widget.protocol.CloudClient
import org.mbs3.franklinwh.widget.protocol.FakeCloudServer
import org.mbs3.franklinwh.widget.protocol.FakeCloudServer.Companion.COMPOSITE
import org.mbs3.franklinwh.widget.protocol.FakeCloudServer.Companion.LOGIN
import org.mbs3.franklinwh.widget.protocol.FakeCloudServer.Companion.codeOnly
import org.mbs3.franklinwh.widget.protocol.FakeCloudServer.Companion.resource
import org.mbs3.franklinwh.widget.protocol.FrameException
import java.net.UnknownHostException

class GatewayRefresherTest {
    private val config = GatewayConfig("192.0.2.10", 9000)
    private val t0 = 1_760_000_000_000L

    @Test
    fun successIsStored() {
        val store = ReadingStore(FakeSharedPreferences())
        store.setRefreshing(t0)
        val result = GatewayRefresher.refresh(config, store, { t0 }) {
            JSONObject("""{"soc":55,"p_sun":1,"p_load":2,"p_uti":3}""")
        }
        assertTrue(result is GatewayRefresher.Result.Success)
        assertEquals(55.0, store.latest!!.soc, 0.0)
        assertEquals(0L, store.refreshingSinceMs)
    }

    @Test
    fun networkFailureKeepsLastGoodReading() {
        val store = ReadingStore(FakeSharedPreferences())
        val good = reading(t0)
        store.recordSuccess(good)
        store.setRefreshing(t0)
        val result = GatewayRefresher.refresh(config, store, { t0 + MIN }) { throw UnknownHostException("x.lan") }
        assertEquals(GatewayRefresher.Result.Failure("Unknown host"), result)
        assertEquals(good, store.latest)
        assertEquals("Unknown host", store.lastError)
        assertEquals(0L, store.refreshingSinceMs)
    }

    @Test
    fun parseFailuresAreRecordedWithoutDetailFromTheFrame() {
        val store = ReadingStore(FakeSharedPreferences())
        GatewayRefresher.refresh(config, store, { t0 }) { throw FrameException("bad SERIAL123") }
        assertEquals("Unreadable reply from gateway", store.lastError)
        GatewayRefresher.refresh(config, store, { t0 }) { JSONObject("{}") }
        assertEquals("Unexpected reply: missing field soc", store.lastError)
    }

    @Test
    fun unconfiguredDoesNotFetch() {
        val store = ReadingStore(FakeSharedPreferences())
        val result = GatewayRefresher.refresh(GatewayConfig("", 9000), store, { t0 }) { error("fetched") }
        assertEquals(GatewayRefresher.Result.NotConfigured, result)
    }

    private val account = "user@example.com"
    private val serial = "TESTSN0001"
    private val hash = CloudClient.md5Hex("password")
    private val cloudConfig = GatewayConfig(
        "", 0,
        source = GatewayConfig.Source.CLOUD,
        cloudAccount = account,
        cloudGatewayId = serial,
    )

    private fun credentials(token: String?) = CloudCredentials(FakeSharedPreferences(), FakeCipher()).apply {
        savePasswordMd5(hash)
        token?.let { saveToken(it) }
    }

    private fun refreshCloud(server: FakeCloudServer, creds: CloudCredentials, store: ReadingStore) =
        GatewayRefresher.refresh(cloudConfig, store, { t0 }) {
            GatewayRefresher.fetchCloud(server.client(), creds, cloudConfig)
        }

    @Test
    fun cloudUsesStoredTokenWithoutLoggingIn() {
        FakeCloudServer().use { server ->
            server.enqueue(COMPOSITE, resource("composite_ok.json"))
            val store = ReadingStore(FakeSharedPreferences())
            val result = refreshCloud(server, credentials("stored-token"), store)

            assertTrue(result is GatewayRefresher.Result.Success)
            assertEquals(5528.0, store.latest!!.loadW, 1e-6)
            assertEquals(t0, store.latest!!.timestampMs)
            assertEquals(listOf("/$COMPOSITE"), server.requests.map { it.path })
            assertEquals("stored-token", server.requests.single().loginToken)
        }
    }

    @Test
    fun cloudLogsInOnceWhenTheTokenIsRejected() {
        FakeCloudServer().use { server ->
            server.enqueue(COMPOSITE, codeOnly(401))
            server.enqueue(COMPOSITE, resource("composite_ok.json"))
            server.enqueue(LOGIN, resource("login_ok.json"))
            val creds = credentials("expired-token")
            val result = refreshCloud(server, creds, ReadingStore(FakeSharedPreferences()))

            assertTrue(result is GatewayRefresher.Result.Success)
            assertEquals(listOf("/$COMPOSITE", "/$LOGIN", "/$COMPOSITE"), server.requests.map { it.path })
            assertEquals(listOf("expired-token", null, "test-token-1"), server.requests.map { it.loginToken })
            assertTrue(server.requests[1].body.contains("password=$hash"))
            assertEquals("test-token-1", creds.token)
        }
    }

    @Test
    fun cloudLogsInFirstWhenThereIsNoToken() {
        FakeCloudServer().use { server ->
            server.enqueue(LOGIN, resource("login_ok.json"))
            server.enqueue(COMPOSITE, resource("composite_ok.json"))
            val result = refreshCloud(server, credentials(null), ReadingStore(FakeSharedPreferences()))

            assertTrue(result is GatewayRefresher.Result.Success)
            assertEquals(listOf("/$LOGIN", "/$COMPOSITE"), server.requests.map { it.path })
        }
    }

    @Test
    fun cloudStopsAfterOneReloginOnRepeated401() {
        FakeCloudServer().use { server ->
            server.enqueue(COMPOSITE, codeOnly(401))
            server.enqueue(LOGIN, resource("login_ok.json"))
            val store = ReadingStore(FakeSharedPreferences())
            val result = refreshCloud(server, credentials("expired-token"), store)

            assertEquals(GatewayRefresher.Result.Failure("Sign-in failed"), result)
            assertEquals(listOf("/$COMPOSITE", "/$LOGIN", "/$COMPOSITE"), server.requests.map { it.path })
            assertEquals("Sign-in failed", store.lastError)
        }
    }

    @Test
    fun cloudErrorTextLeaksNoSecrets() {
        val cases = listOf(
            codeOnly(401) to "Sign-in failed",
            codeOnly(400) to "Account locked",
            """{"code":200,"result":{"token":"","mfaRequired":true,"mfaToken":"m"}}""" to
                "Sign-in needs MFA, which is not supported",
            """{"code":500,"message":"$account $serial"}""" to "Unexpected reply from cloud",
            "not json $account" to "Unexpected reply from cloud",
        )
        for ((loginReply, expected) in cases) {
            FakeCloudServer().use { server ->
                server.enqueue(LOGIN, loginReply)
                val store = ReadingStore(FakeSharedPreferences())
                refreshCloud(server, credentials(null), store)
                assertEquals(expected, store.lastError)
            }
        }
        FakeCloudServer().use { server ->
            server.enqueue(COMPOSITE, "$account $serial", status = 502)
            val store = ReadingStore(FakeSharedPreferences())
            refreshCloud(server, credentials("secret-token"), store)
            val error = store.lastError!!
            assertEquals("HTTP 502 from cloud", error)
            for (secret in listOf(account, serial, hash, "secret-token")) assertFalse(error.contains(secret))
        }
    }

    @Test
    fun cloudUnconfiguredDoesNotFetch() {
        val store = ReadingStore(FakeSharedPreferences())
        val result = GatewayRefresher.refresh(cloudConfig.copy(cloudGatewayId = ""), store, { t0 }) { error("fetched") }
        assertEquals(GatewayRefresher.Result.NotConfigured, result)
    }
}
