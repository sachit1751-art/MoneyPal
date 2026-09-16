package com.sachit.moneypal.sync.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString

class BudgetStatePayloadTest {

    @Test
    fun budgetStatePayload_roundTripsThroughWearJson() {
        val payload = BudgetStatePayload(
            remainingToday = "42.50",
            dailyBudget = "10.00",
            currencyCode = "USD",
            progressPercent = 37,
            daysRemaining = 5,
            isOverBudget = false,
            updatedAtEpochMs = 1_726_400_000_000,
        )

        val raw = WearJson.json.encodeToString(payload)
        val decoded = WearJson.json.decodeFromString(BudgetStatePayload.serializer(), raw)

        assertEquals(payload, decoded)
    }

    @Test
    fun budgetStatePayload_ignoresUnknownKeysFromNewerPhone() {
        val raw = """
            {"remainingToday":"1.00","dailyBudget":"2.00","currencyCode":"EUR",
             "progressPercent":100,"daysRemaining":1,"isOverBudget":true,
             "updatedAtEpochMs":123,"futureField":"x"}
        """.trimIndent()

        val decoded = WearJson.json.decodeFromString(BudgetStatePayload.serializer(), raw)

        assertEquals("1.00", decoded.remainingToday)
        assertTrue(decoded.isOverBudget)
    }
}
