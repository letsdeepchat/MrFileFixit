package com.mrfilefixit.core.ads

/**
 * Frequency cap for interstitials: at most one every [minIntervalMillis] and at most
 * [maxPerSession] per app session (process lifetime). Pure logic, so it is unit-tested on the JVM.
 *
 * [now] must be a monotonic clock (elapsedRealtime), so changing the wall clock cannot bypass the cap.
 */
class InterstitialPacing(
    private val now: () -> Long,
    private val minIntervalMillis: Long = MIN_INTERVAL_MILLIS,
    private val maxPerSession: Int = MAX_PER_SESSION,
) {
    private var lastShownAt: Long? = null
    private var shownThisSession = 0

    @Synchronized
    fun canShow(): Boolean {
        if (shownThisSession >= maxPerSession) return false
        val last = lastShownAt ?: return true
        return now() - last >= minIntervalMillis
    }

    /** Call when the ad actually appeared on screen (not when it was requested). */
    @Synchronized
    fun recordShown() {
        lastShownAt = now()
        shownThisSession++
    }

    companion object {
        const val MIN_INTERVAL_MILLIS = 150_000L
        const val MAX_PER_SESSION = 6
    }
}
