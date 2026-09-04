package com.sachit.moneypal.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Test double pinning the clock to a fixed date, so period/rollover logic can be
 * exercised deterministically instead of against the real wall clock.
 */
class FakeTimeProvider(
    private val fixedDate: LocalDate,
) : TimeProvider {
    private val fixedInstant: Instant =
        fixedDate.atStartOfDay(ZoneId.systemDefault()).toInstant()

    override fun nowEpochMillis(): Long = fixedInstant.toEpochMilli()

    override fun nowInstant(): Instant = fixedInstant

    override fun today(): LocalDate = fixedDate
}
