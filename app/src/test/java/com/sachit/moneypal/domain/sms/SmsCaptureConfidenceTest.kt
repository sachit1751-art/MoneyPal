package com.sachit.moneypal.domain.sms

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class SmsCaptureConfidenceTest {

    @Test
    fun `all signals score 100`() {
        assertThat(
            SmsCaptureConfidence.score(
                senderLooksLikeBank = true,
                merchantFound = true,
                categorySuggested = true,
                amount = BigDecimal("500"),
            )
        ).isEqualTo(100)
    }

    @Test
    fun `no signals score 0`() {
        assertThat(
            SmsCaptureConfidence.score(
                senderLooksLikeBank = false,
                merchantFound = false,
                categorySuggested = false,
                amount = BigDecimal.ZERO,
            )
        ).isEqualTo(0)
    }

    @Test
    fun `sender plus merchant sits below threshold`() {
        assertThat(
            SmsCaptureConfidence.score(
                senderLooksLikeBank = true,
                merchantFound = true,
                categorySuggested = false,
                amount = BigDecimal.ZERO,
            )
        ).isEqualTo(55)
        assertThat(55).isLessThan(SmsCaptureConfidence.REVIEW_THRESHOLD)
    }

    @Test
    fun `sender merchant and amount clears threshold`() {
        assertThat(
            SmsCaptureConfidence.score(
                senderLooksLikeBank = true,
                merchantFound = true,
                categorySuggested = false,
                amount = BigDecimal("120"),
            )
        ).isEqualTo(75)
        assertThat(75).isAtLeast(SmsCaptureConfidence.REVIEW_THRESHOLD)
    }

    @Test
    fun `huge amount fails sanity signal`() {
        assertThat(
            SmsCaptureConfidence.score(
                senderLooksLikeBank = true,
                merchantFound = true,
                categorySuggested = true,
                amount = BigDecimal("1000001"),
            )
        ).isEqualTo(80)
    }

    @Test
    fun `review threshold is 60`() {
        assertThat(SmsCaptureConfidence.REVIEW_THRESHOLD).isEqualTo(60)
    }
}
