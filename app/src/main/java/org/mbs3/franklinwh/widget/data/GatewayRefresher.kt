package org.mbs3.franklinwh.widget.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mbs3.franklinwh.widget.protocol.CloudAccountLockedException
import org.mbs3.franklinwh.widget.protocol.CloudApiException
import org.mbs3.franklinwh.widget.protocol.CloudAuthException
import org.mbs3.franklinwh.widget.protocol.CloudClient
import org.mbs3.franklinwh.widget.protocol.CloudHttpException
import org.mbs3.franklinwh.widget.protocol.CloudMfaRequiredException
import org.mbs3.franklinwh.widget.protocol.FrameException
import org.mbs3.franklinwh.widget.protocol.GatewayClient
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Fetches one reading, from the gateway or the cloud per [GatewayConfig.source], and records the
 * outcome in the [ReadingStore].
 */
object GatewayRefresher {

    sealed class Result {
        data object NotConfigured : Result()
        data class Success(val reading: GatewayReading) : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun refreshOnce(context: Context): Result {
        val config = GatewayConfig.load(context)
        val store = ReadingStore(context)
        return withContext(Dispatchers.IO) {
            refresh(config, store, System::currentTimeMillis) {
                when (config.source) {
                    GatewayConfig.Source.LOCAL -> GatewayClient(config.host, config.port).queryDeviceDetail()
                    GatewayConfig.Source.CLOUD -> fetchCloud(CloudClient(), CloudCredentials(context), config)
                }
            }
        }
    }

    /**
     * Fetches the cloud's runtimeData with the stored token. If there is no token, or it is
     * rejected, signs in once with the stored password hash, saves the new token and retries;
     * a second rejection propagates.
     */
    fun fetchCloud(client: CloudClient, credentials: CloudCredentials, config: GatewayConfig): JSONObject {
        credentials.token?.let { token ->
            try {
                return client.compositeInfo(token, config.cloudGatewayId)
            } catch (_: CloudAuthException) {
                // Expired or revoked; sign in again below.
            }
        }
        val hash = credentials.passwordMd5 ?: throw CloudAuthException()
        val token = client.login(config.cloudAccount, hash)
        credentials.saveToken(token)
        return client.compositeInfo(token, config.cloudGatewayId)
    }

    /**
     * Blocking core of [refreshOnce]. Only network and parse failures are recorded; anything
     * else propagates. The refreshing flag is cleared either way.
     */
    fun refresh(
        config: GatewayConfig,
        store: ReadingStore,
        clock: () -> Long,
        fetch: () -> JSONObject,
    ): Result {
        if (!config.isConfigured) {
            store.setRefreshing(null)
            return Result.NotConfigured
        }
        try {
            val reading = GatewayReading.fromDataArea(fetch(), clock())
            store.recordSuccess(reading)
            return Result.Success(reading)
        } catch (e: IOException) {
            return fail(store, clock(), describe(e))
        } catch (e: ReadingParseException) {
            return fail(store, clock(), describe(e))
        } finally {
            store.setRefreshing(null)
        }
    }

    private fun fail(store: ReadingStore, nowMs: Long, message: String): Result {
        store.recordFailure(nowMs, message)
        return Result.Failure(message)
    }

    /**
     * A short user-facing description. Decoder details are dropped because they can quote the
     * reply, which contains the gateway's serial number. Cloud cases never quote the account,
     * serial, token or password hash.
     */
    fun describe(e: Exception): String = when (e) {
        is UnknownHostException -> "Unknown host"
        is SocketTimeoutException -> "Timed out"
        is ConnectException -> "Connection refused or unreachable"
        is NoRouteToHostException -> "No route to host"
        is FrameException -> "Unreadable reply from gateway"
        is CloudMfaRequiredException -> "Sign-in needs MFA, which is not supported"
        is CloudAuthException -> "Sign-in failed"
        is CloudAccountLockedException -> "Account locked"
        is CloudHttpException -> "HTTP ${e.status} from cloud"
        is CloudApiException -> "Unexpected reply from cloud"
        is ReadingParseException -> "Unexpected reply: ${e.message}"
        is IOException -> "Network error (${e.javaClass.simpleName})"
        else -> e.javaClass.simpleName
    }
}
