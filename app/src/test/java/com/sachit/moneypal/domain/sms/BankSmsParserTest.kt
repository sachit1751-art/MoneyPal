package com.sachit.moneypal.domain.sms

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class BankSmsParserTest {

    private fun parse(body: String, sender: String = "HDFC-BANK", timestamp: Long = 1_700_000_000_000) =
        BankSmsParser.parse(sender, body, timestamp)

    // ---------- debit formats ----------

    @Test
    fun `parses Rs prefix debit`() {
        val match = parse("Your a/c XX1234 is debited for Rs 500 on 01-01-26")
        assertThat(match).isNotNull()
        assertThat(match!!.amount).isEqualTo(BigDecimal("500"))
        assertThat(match.isCredit).isFalse()
        assertThat(match.sender).isEqualTo("HDFC-BANK")
    }

    @Test
    fun `parses Rs dot comma amount`() {
        val match = parse("Rs.1,234.56 debited from a/c XX99 towards purchase at Amazon")
        assertThat(match).isNotNull()
        assertThat(match!!.amount).isEqualTo(BigDecimal("1234.56"))
        assertThat(match.isCredit).isFalse()
    }

    @Test
    fun `parses INR prefix debit`() {
        val match = parse("INR 250.75 spent on card XX4412 at SWIGGY")
        assertThat(match).isNotNull()
        assertThat(match!!.amount).isEqualTo(BigDecimal("250.75"))
        assertThat(match.isCredit).isFalse()
    }

    @Test
    fun `parses rupee symbol debit`() {
        val match = parse("₹250 debited for UPI payment to XYZ")
        assertThat(match).isNotNull()
        assertThat(match!!.amount).isEqualTo(BigDecimal("250"))
    }

    @Test
    fun `parses currency suffix debit`() {
        val match = parse("Thank you for using our ATM. 500.00 Rs withdrawn from A/C XX1")
        assertThat(match).isNotNull()
        assertThat(match!!.amount).isEqualTo(BigDecimal("500.00"))
    }

    @Test
    fun `uses first amount after debit keyword`() {
        // Account number XX1234 must not be picked as the amount
        val match = parse("Debited Rs 100 from account 1234567890 ref 555123")
        assertThat(match).isNotNull()
        assertThat(match!!.amount).isEqualTo(BigDecimal("100"))
    }

    @Test
    fun `parses amount before debit keyword`() {
        val match = parse("Rs 500 debited from a/c XX1234 on 01-01-26")
        assertThat(match).isNotNull()
        assertThat(match!!.amount).isEqualTo(BigDecimal("500"))
        assertThat(match.isCredit).isFalse()
    }

    @Test
    fun `parses amount before credit keyword`() {
        val match = parse("Rs 1,234.56 credited to your a/c XX99")
        assertThat(match).isNotNull()
        assertThat(match!!.amount).isEqualTo(BigDecimal("1234.56"))
        assertThat(match.isCredit).isTrue()
    }

    @Test
    fun `prefers amount closest to keyword`() {
        // A balance amount appears before the debit keyword; the transaction
        // amount right after it must win.
        val match = parse("Balance Rs 9000. Debited Rs 250 at AMAZON")
        assertThat(match).isNotNull()
        assertThat(match!!.amount).isEqualTo(BigDecimal("250"))
    }

    @Test
    fun `account numbers are not mistaken for amounts`() {
        val match = parse("Rs 300 spent using card 4111111111111111")
        assertThat(match).isNotNull()
        assertThat(match!!.amount).isEqualTo(BigDecimal("300"))
    }

    // ---------- credit formats ----------

    @Test
    fun `parses credit`() {
        val match = parse("Rs 1000 credited to your account XX1234 by NEFT from ABC CORP")
        assertThat(match).isNotNull()
        assertThat(match!!.amount).isEqualTo(BigDecimal("1000"))
        assertThat(match.isCredit).isTrue()
    }

    @Test
    fun `parses refund as credit`() {
        val match = parse("Refund of Rs 299 received for order 123 on your card")
        assertThat(match).isNotNull()
        assertThat(match!!.isCredit).isTrue()
        assertThat(match.amount).isEqualTo(BigDecimal("299"))
    }

    @Test
    fun `parses cashback as credit`() {
        val match = parse("You have received cashback of Rs 50")
        assertThat(match).isNotNull()
        assertThat(match!!.isCredit).isTrue()
    }

    // ---------- non-money and ambiguous messages ----------

    @Test
    fun `rejects OTP message even with amount`() {
        val match = parse("OTP 123456 for debit of Rs 500. Do not share.")
        assertThat(match).isNull()
    }

    @Test
    fun `rejects balance-only message`() {
        val match = parse("Your balance is Rs 5,000.00")
        assertThat(match).isNull()
    }

    @Test
    fun `rejects message with neither keyword nor amount`() {
        assertThat(parse("Hello from your bank")).isNull()
    }

    @Test
    fun `rejects message with both debit and credit keywords`() {
        val match = parse("Rs 100 debited, earlier Rs 200 credited was reversed")
        assertThat(match).isNull()
    }

    @Test
    fun `rejects zero amount`() {
        val match = parse("Debited Rs 0.00 from account")
        assertThat(match).isNull()
    }

    @Test
    fun `rejects blank body`() {
        assertThat(parse("")).isNull()
    }

    // ---------- amount + dedupe helpers ----------

    @Test
    fun `parseAmount strips commas`() {
        assertThat(BankSmsParser.parseAmount("1,23,456.78")).isEqualTo(BigDecimal("123456.78"))
    }

    @Test
    fun `parseAmount rejects garbage`() {
        assertThat(BankSmsParser.parseAmount("abc")).isNull()
    }

    @Test
    fun `dedupeKey is stable and direction-aware`() {
        val debit = BankSmsMatch(
            amount = BigDecimal("100"),
            isCredit = false,
            sender = "HDFC-BANK",
            timestampMillis = 1_700_000_060_000, // same minute bucket as below
            rawAmountText = "100",
        )
        val debitAgain = debit.copy(timestampMillis = 1_700_000_090_000)
        val credit = debit.copy(isCredit = true)

        assertThat(BankSmsParser.dedupeKey(debit))
            .isEqualTo(BankSmsParser.dedupeKey(debitAgain))
        assertThat(BankSmsParser.dedupeKey(debit))
            .isNotEqualTo(BankSmsParser.dedupeKey(credit))
    }
}
