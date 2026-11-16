package org.mbs3.franklinwh.widget.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbs3.franklinwh.widget.data.GatewayReading
import org.mbs3.franklinwh.widget.protocol.FakeCloudServer.Companion.COMPOSITE
import org.mbs3.franklinwh.widget.protocol.FakeCloudServer.Companion.LOGIN
import org.mbs3.franklinwh.widget.protocol.FakeCloudServer.Companion.codeOnly
import org.mbs3.franklinwh.widget.protocol.FakeCloudServer.Companion.resource
import java.net.URLDecoder

/** Exercises [CloudClient] against a loopback server that replays canned replies. */
class CloudClientTest {

    private fun formFields(body: String): Map<String, String> =
        body.split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }

    @Test
    fun md5HexMatchesKnownDigest() {
        assertEquals("5f4dcc3b5aa765d61d8327deb882cf99", CloudClient.md5Hex("password"))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", CloudClient.md5Hex(""))
    }

    @Test
    fun loginPostsFormAndReturnsToken() {
        FakeCloudServer().use { server ->
            server.enqueue(LOGIN, resource("login_ok.json"))
            val hash = CloudClient.md5Hex("password")
            val token = server.client().login("user+test@example.com", hash)

            assertEquals("test-token-1", token)
            val request = server.requests.single()
            assertEquals("POST", request.method)
            assertEquals("/$LOGIN", request.path)
            assertEquals("application/x-www-form-urlencoded", request.contentType)
            assertEquals(
                mapOf(
                    "account" to "user+test@example.com",
                    "password" to "5f4dcc3b5aa765d61d8327deb882cf99",
                    "lang" to "en_US",
                    "type" to "1",
                ),
                formFields(request.body),
            )
        }
    }

    @Test
    fun loginMapsErrorCodes() {
        for ((code, type) in listOf(
            401 to CloudAuthException::class.java,
            400 to CloudAccountLockedException::class.java,
            500 to CloudApiException::class.java,
        )) {
            FakeCloudServer().use { server ->
                server.enqueue(LOGIN, codeOnly(code))
                assertThrows(type) { server.client().login("a@example.com", "h") }
            }
        }
    }

    @Test
    fun loginNeedingMfaIsDistinct() {
        val replies = listOf(
            """{"code":200,"result":{"token":"","mfaRequired":true,"mfaToken":"m"}}""",
            """{"code":200,"result":{"token":"t","mfaRequired":true}}""",
            """{"code":200,"result":{"token":null,"mfaRequired":false,"mfaToken":"m"}}""",
        )
        for (reply in replies) {
            FakeCloudServer().use { server ->
                server.enqueue(LOGIN, reply)
                assertThrows(CloudMfaRequiredException::class.java) { server.client().login("a@example.com", "h") }
            }
        }
    }

    @Test
    fun loginWithoutTokenIsAnApiError() {
        FakeCloudServer().use { server ->
            server.enqueue(LOGIN, """{"code":200,"result":{"mfaRequired":false}}""")
            assertThrows(CloudApiException::class.java) { server.client().login("a@example.com", "h") }
        }
    }

    @Test
    fun compositeInfoSendsTokenAndReturnsRuntimeDataInWatts() {
        FakeCloudServer().use { server ->
            server.enqueue(COMPOSITE, resource("composite_ok.json"))
            val data = server.client().compositeInfo("test-token-1", "TESTSN0001")

            val request = server.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/$COMPOSITE", request.path)
            assertEquals("gatewayId=TESTSN0001&refreshFlag=1&lang=en_US", request.query)
            assertEquals("test-token-1", request.loginToken)

            assertEquals(5528.0, data.getDouble("p_load"), 1e-6)
            assertEquals(5506.0, data.getDouble("p_fhp"), 1e-6)
            assertEquals(22.0, data.getDouble("p_uti"), 1e-6)
            assertEquals(24.391, data.getDouble("kwh_sun"), 0.0)
            assertEquals(19.105, data.getDouble("soc"), 0.0)
        }
    }

    @Test
    fun compositeInfoMapsErrors() {
        FakeCloudServer().use { server ->
            server.enqueue(COMPOSITE, codeOnly(401))
            assertThrows(CloudAuthException::class.java) { server.client().compositeInfo("t", "SN") }
        }
        FakeCloudServer().use { server ->
            server.enqueue(COMPOSITE, """{"code":200,"result":{}}""")
            assertThrows(CloudApiException::class.java) { server.client().compositeInfo("t", "SN") }
        }
        FakeCloudServer().use { server ->
            server.enqueue(COMPOSITE, "<html>busy</html>")
            assertThrows(CloudApiException::class.java) { server.client().compositeInfo("t", "SN") }
        }
        FakeCloudServer().use { server ->
            server.enqueue(COMPOSITE, "", status = 503)
            val e = assertThrows(CloudHttpException::class.java) { server.client().compositeInfo("t", "SN") }
            assertEquals(503, e.status)
        }
    }

    @Test
    fun toLocalUnitsConvertsPowerAndLeavesTheRest() {
        val cloud = JSONObject(
            """{"soc":19.105,"fhpSoc":[23.2,23.1],"p_sun":"1.25","p_load":5.528,"p_uti":-0.302,
               |"p_fhp":5.506,"p_gen":null,"kwh_load":33.666}""".trimMargin(),
        )
        val local = CloudClient.toLocalUnits(cloud)
        assertEquals(1250.0, local.getDouble("p_sun"), 1e-6)
        assertEquals(5528.0, local.getDouble("p_load"), 1e-6)
        assertEquals(-302.0, local.getDouble("p_uti"), 1e-6)
        assertEquals(5506.0, local.getDouble("p_fhp"), 1e-6)
        assertTrue(local.isNull("p_gen"))
        assertFalse(local.has("p_ev"))
        assertEquals(33.666, local.getDouble("kwh_load"), 0.0)
        assertEquals(5.528, cloud.getDouble("p_load"), 0.0)

        val reading = GatewayReading.fromDataArea(local, 1L)
        assertEquals(5528.0, reading.loadW, 1e-6)
        assertEquals(0.0, reading.generatorW, 0.0)
        assertEquals(listOf(23.2, 23.1), reading.batterySocEach)
    }
}
