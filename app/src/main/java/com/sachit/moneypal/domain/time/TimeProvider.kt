package com.sachit.moneypal.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface TimeProvider {
    fun nowEpochMillis(): Long
    fun nowInstant(): Instant

    /** Today's date in the device's default zone — overridable in tests. */
    fun today(): LocalDate = nowInstant().atZone(ZoneId.systemDefault()).toLocalDate()
}

class SystemTimeProvider : TimeProvider {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()

    override fun nowInstant(): Instant = Instant.ofEpochMilli(nowEpochMillis())
}
