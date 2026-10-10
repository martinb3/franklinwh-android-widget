package org.mbs3.franklinwh.widget.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException

/**
 * Persists the latest reading, a bounded history of successful readings, and the outcome of the
 * last refresh attempt. Synchronous; call from background threads.
 */
class ReadingStore(private val prefs: SharedPreferences) {

    constructor(context: Context) :
        this(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    val latest: GatewayReading?
        get() = prefs.getString(KEY_LATEST, null)?.let {
            try {
                GatewayReading.fromJsonString(it)
            } catch (_: ReadingParseException) {
                null
            }
        }

    val history: List<GatewayReading>
        get() = parseHistory(prefs.getString(KEY_HISTORY, null))

    val lastAttemptMs: Long
        get() = prefs.getLong(KEY_LAST_ATTEMPT, 0L)

    /** Error from the most recent attempt, or null if it succeeded. */
    val lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)

    /** When a refresh was requested and has not finished yet, or 0. */
    val refreshingSinceMs: Long
        get() = prefs.getLong(KEY_REFRESHING_SINCE, 0L)

    /** Records a successful refresh: replaces the latest reading and appends it to the history. */
    fun recordSuccess(reading: GatewayReading) = synchronized(LOCK) {
        val updated = pruneHistory(history + reading, reading.timestampMs)
        prefs.edit()
            .putString(KEY_LATEST, reading.toJson().toString())
            .putString(KEY_HISTORY, JSONArray().apply { updated.forEach { put(it.toJson()) } }.toString())
            .putLong(KEY_LAST_ATTEMPT, reading.timestampMs)
            .remove(KEY_LAST_ERROR)
            .commit()
        Unit
    }

    /** Records a failed refresh. The latest reading and history are kept. */
    fun recordFailure(nowMs: Long, error: String) {
        prefs.edit()
            .putLong(KEY_LAST_ATTEMPT, nowMs)
            .putString(KEY_LAST_ERROR, error)
            .commit()
    }

    fun setRefreshing(sinceMs: Long?) {
        prefs.edit().apply {
            if (sinceMs == null) remove(KEY_REFRESHING_SINCE) else putLong(KEY_REFRESHING_SINCE, sinceMs)
        }.commit()
    }

    /** The newest history entry at least [PowerAverages.MIN_WINDOW_MS] older than [latest], or null. */
    fun baselineFor(latest: GatewayReading): GatewayReading? = baselineFor(history, latest)

    companion object {
        const val PREFS_NAME = "readings"
        const val MAX_AGE_MS = 36 * 60 * 60 * 1000L
        const val MAX_ENTRIES = 200

        /** Store instances are created freely; serialize history read-modify-write across them. */
        private val LOCK = Any()

        private const val KEY_LATEST = "latest"
        private const val KEY_HISTORY = "history"
        private const val KEY_LAST_ATTEMPT = "lastAttemptMs"
        private const val KEY_LAST_ERROR = "lastError"
        private const val KEY_REFRESHING_SINCE = "refreshingSinceMs"

        /** Keeps entries at most [MAX_AGE_MS] older than [newestMs], newest [MAX_ENTRIES], oldest first. */
        fun pruneHistory(entries: List<GatewayReading>, newestMs: Long): List<GatewayReading> =
            entries.filter { newestMs - it.timestampMs <= MAX_AGE_MS }
                .sortedBy { it.timestampMs }
                .takeLast(MAX_ENTRIES)

        fun baselineFor(history: List<GatewayReading>, latest: GatewayReading): GatewayReading? =
            history.filter { latest.timestampMs - it.timestampMs >= PowerAverages.MIN_WINDOW_MS }
                .maxByOrNull { it.timestampMs }

        private fun parseHistory(text: String?): List<GatewayReading> {
            if (text == null) return emptyList()
            return try {
                val arr = JSONArray(text)
                (0 until arr.length()).mapNotNull {
                    try {
                        GatewayReading.fromJson(arr.getJSONObject(it))
                    } catch (_: ReadingParseException) {
                        null
                    }
                }
            } catch (_: JSONException) {
                emptyList()
            }
        }
    }
}
