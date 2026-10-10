package org.mbs3.franklinwh.widget.data

/** Everything the widget needs to render. */
sealed class WidgetState {
    data object Unconfigured : WidgetState()

    data class NoData(val lastError: String?) : WidgetState()

    data class Ready(
        val reading: GatewayReading,
        /** Averages since the history baseline, or null if there is none. */
        val averages: PowerAverages?,
        val ageMs: Long,
        /** True when the reading is recent enough to show instantaneous power. */
        val isLive: Boolean,
        val refreshing: Boolean,
        /** Error from the most recent attempt, if it failed after [reading] was taken. */
        val lastError: String?,
    ) : WidgetState()
}

object WidgetStateFactory {
    /** Readings younger than this show instantaneous power; older ones show daily energy. */
    const val LIVE_MAX_AGE_MS = 2 * 60 * 1000L

    /** A refresh request older than this is assumed abandoned and no longer shown. */
    const val REFRESHING_TIMEOUT_MS = 2 * 60 * 1000L

    fun build(store: ReadingStore, config: GatewayConfig, nowMs: Long): WidgetState {
        if (!config.isConfigured) return WidgetState.Unconfigured
        val latest = store.latest ?: return WidgetState.NoData(store.lastError)
        val ageMs = (nowMs - latest.timestampMs).coerceAtLeast(0)
        val since = store.refreshingSinceMs
        return WidgetState.Ready(
            reading = latest,
            averages = store.baselineFor(latest)?.let { PowerAverages.between(it, latest) },
            ageMs = ageMs,
            isLive = ageMs < LIVE_MAX_AGE_MS,
            refreshing = since > 0 && nowMs - since < REFRESHING_TIMEOUT_MS,
            lastError = store.lastError,
        )
    }
}
