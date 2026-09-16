package com.sachit.moneypal.domain.sms

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmsMerchantExtractorTest {

    @Test
    fun `extracts merchant after at following the amount`() {
        assertThat(SmsMerchantExtractor.extract("Debited Rs 500 at AMAZON on 12-03"))
            .isEqualTo("Amazon")
    }

    @Test
    fun `extracts multiword merchant after to`() {
        assertThat(SmsMerchantExtractor.extract("Rs 250 spent at Amazon Pay on card XX1234"))
            .isEqualTo("Amazon Pay")
    }

    @Test
    fun `extracts merchant after on`() {
        assertThat(SmsMerchantExtractor.extract("INR 99.00 spent on SWIGGY order 12345"))
            .isEqualTo("Swiggy")
    }

    @Test
    fun `known merchant found without preposition`() {
        assertThat(SmsMerchantExtractor.extract("AutoPay of Rs 299 executed for NETFLIX subscription"))
            .isEqualTo("Netflix")
    }

    @Test
    fun `preposition before amount is ignored`() {
        // "at Zomato" appears before the amount → not anchored to the txn amount;
        // dictionary fallback finds ZOMATO anyway.
        assertThat(SmsMerchantExtractor.extract("Offer at ZOMATO! Pay Rs 100 to save 20"))
            .isEqualTo("Zomato")
    }

    @Test
    fun `card fragment is not a merchant`() {
        assertThat(SmsMerchantExtractor.extract("Rs 500 debited at XX1234 on 01-01")).isNull()
    }

    @Test
    fun `bank token is not a merchant`() {
        assertThat(SmsMerchantExtractor.extract("Rs 500 debited at HDFC BANK LTD")).isNull()
    }

    @Test
    fun `blank body returns null`() {
        assertThat(SmsMerchantExtractor.extract("")).isNull()
    }

    @Test
    fun `no merchant signals returns null`() {
        assertThat(SmsMerchantExtractor.extract("Rs 500 debited from a/c XX99 ref 8812"))
            .isNull()
    }
}
