package com.sachit.moneypal.domain.lock

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutoLockPolicyTest {

    @Test
    fun `disabled never locks`() {
        assertThat(
            AutoLockPolicy.shouldLock(
                enabled = false,
                timeout = AutoLockTimeout.IMMEDIATELY,
                lastPausedAtEpochMs = null,
                nowEpochMs = 1_000L,
            )
        ).isFalse()
        assertThat(
            AutoLockPolicy.shouldLock(
                enabled = false,
                timeout = AutoLockTimeout.FIVE_MINUTES,
                lastPausedAtEpochMs = 0L,
                nowEpochMs = Long.MAX_VALUE,
            )
        ).isFalse()
    }

    @Test
    fun `immediately always locks while enabled`() {
        assertThat(
            AutoLockPolicy.shouldLock(
                enabled = true,
                timeout = AutoLockTimeout.IMMEDIATELY,
                lastPausedAtEpochMs = 1_000L,
                nowEpochMs = 1_001L,
            )
        ).isTrue()
        assertThat(
            AutoLockPolicy.shouldLock(
                enabled = true,
                timeout = AutoLockTimeout.IMMEDIATELY,
                lastPausedAtEpochMs = null,
                nowEpochMs = 0L,
            )
        ).isTrue()
    }

    @Test
    fun `one minute boundary locks at exactly 60_000ms`() {
        assertThat(
            AutoLockPolicy.shouldLock(
                enabled = true,
                timeout = AutoLockTimeout.ONE_MINUTE,
                lastPausedAtEpochMs = 0L,
                nowEpochMs = 60_000L,
            )
        ).isTrue()
    }

    @Test
    fun `one minute does not lock just before the boundary`() {
        assertThat(
            AutoLockPolicy.shouldLock(
                enabled = true,
                timeout = AutoLockTimeout.ONE_MINUTE,
                lastPausedAtEpochMs = 0L,
                nowEpochMs = 59_999L,
            )
        ).isFalse()
    }

    @Test
    fun `five minute boundary respected`() {
        assertThat(
            AutoLockPolicy.shouldLock(
                enabled = true,
                timeout = AutoLockTimeout.FIVE_MINUTES,
                lastPausedAtEpochMs = 0L,
                nowEpochMs = 5L * 60_000L - 1,
            )
        ).isFalse()
        assertThat(
            AutoLockPolicy.shouldLock(
                enabled = true,
                timeout = AutoLockTimeout.FIVE_MINUTES,
                lastPausedAtEpochMs = 0L,
                nowEpochMs = 5L * 60_000L,
            )
        ).isTrue()
    }

    @Test
    fun `null pausedAt locks when a delay is configured (fail closed)`() {
        assertThat(
            AutoLockPolicy.shouldLock(
                enabled = true,
                timeout = AutoLockTimeout.ONE_MINUTE,
                lastPausedAtEpochMs = null,
                nowEpochMs = 0L,
            )
        ).isTrue()
    }
}
