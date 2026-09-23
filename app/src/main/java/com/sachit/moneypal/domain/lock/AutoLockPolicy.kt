package com.sachit.moneypal.domain.lock

/**
 * How long the app may sit in the background before the lock re-engages
 * (plan 041). Persisted by [name] — never rename the constants without a
 * migration.
 */
enum class AutoLockTimeout(val minutes: Int) {
    IMMEDIATELY(0),
    ONE_MINUTE(1),
    FIVE_MINUTES(5),
}

object AutoLockPolicy {

    /**
     * True when the lock should be re-engaged now.
     *
     * - Lock disabled → never locks.
     * - [AutoLockTimeout.IMMEDIATELY] → always locks on return.
     * - No recorded pause timestamp (process death, first launch) → locks —
     *   fail closed.
     * - Otherwise locks once the elapsed background time reaches the timeout.
     */
    fun shouldLock(
        enabled: Boolean,
        timeout: AutoLockTimeout,
        lastPausedAtEpochMs: Long?,
        nowEpochMs: Long,
    ): Boolean {
        if (!enabled) return false
        if (timeout == AutoLockTimeout.IMMEDIATELY) return true
        val pausedAt = lastPausedAtEpochMs ?: return true
        return nowEpochMs - pausedAt >= timeout.minutes * 60_000L
    }
}
