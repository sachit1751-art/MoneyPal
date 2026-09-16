# Plan 013 — SMS parser: real-world format coverage

**Status:** TODO
**Written against commit:** `ff6f773` (2026-09-16)
**Category:** feature — user priority ("make auto parsing of the transaction better")
**Depends on:** nothing (012 is independent)
**Effort:** M · **Risk of fix:** low (pure function, fully unit-tested)

## Why

The generic parser (`BankSmsParser`, design decided 2026-09-08 — do NOT switch
to per-bank templates) misses very common real-world shapes. Every miss is a
 silently uncaptured expense — the user's #1 product ask. Verified gaps:

1. **Verbs without word-boundary-friendly stems** — "charged", "charge",
   "autopay", "auto-payment", "emi", " billed " are absent from
   `DEBIT_KEYWORD` (`BankSmsParser.kt:48-55`).
2. **UPI-style "Paid to X"** works today only because "paid" is a keyword —
   but "Sent Rs 500 to X" requires the amount *right* next to the keyword; a
   body like "Paid Rs.500 to Amazon Wallet" is fine, while
   "Payment of Rs 500 done on card" fails (no debit keyword adjacent:
   "Payment" is not a keyword, "done" is nothing).
3. **"of"/"for" amount connector pattern** — "Txn of Rs 1,234" has no
   keyword at all; needs a debit-context phrase list (txn, transaction,
   purchase, order) combined with a currency amount.
4. **"Rs 500.00/-" trailing `/-`** (very common in Indian bank SMS) — the
   current `NUMBER` regex stops at the digits so `500.00` parses but the
   `/-` leaves garbage that is harmless today; HOWEVER "INR 500/-debited"
   (no space) makes the keyword `find` still work but is worth a test.
5. **Balance noise beats the real amount** — "Bal: Rs 5,000. Avl lmt Rs 500
   debited for Rs 300 at Amazon" — distance-based selection picks the closest
   currency amount to the keyword; verify with tests and add a negative
   lookahead to skip amounts inside "bal", "balance", "avl", "avail",
   "limit", "lmt" contexts.
6. **Number-only senders** — real bank senders are e.g. `HDFC-BK`, `JD-HDFC`,
   `+919595000000`. The parser ignores `sender` except for the dedupe key, so
   no change needed — but add a `isLikelyBankSender()` helper (contains a dash
   and letters, or is numeric with country code) for plan 014 to use as a
   confidence signal.

## Current state (verified excerpts)

`app/src/main/java/com/sachit/moneypal/domain/sms/BankSmsParser.kt`:

```kotlin
private val DEBIT_KEYWORD = Regex(
    "(?:\\b(?:debited|debit|spent|paid|withdrawn|withdraw|deducted|deduct|purchased|purchase|sent|transferred|transfer)\\b)",
    RegexOption.IGNORE_CASE,
)

private val CREDIT_KEYWORD = Regex(
    "(?:\\b(?:credited|credit|received|recd|recv|refund|refunded|deposited|deposit|cashback|cash_back|added)\\b)",
    RegexOption.IGNORE_CASE,
)
```

Existing tests: `app/src/test/java/com/sachit/moneypal/domain/sms/BankSmsParserTest.kt`
(183 lines, Truth-style backtick names) — follow that exact style.

## Steps

### Step 1 — Extend keyword lists (additive only)

```kotlin
private val DEBIT_KEYWORD = Regex(
    "(?:\\b(?:debited|debit|debits|spent|spend|paid|pay|withdrawn|withdraw|deducted|deduct|" +
        "purchased|purchase|sent|transferred|transfer|charged|charge|autopay|auto_pay|" +
        "autopy|emi|billed|billing|payment)\\b)",
    RegexOption.IGNORE_CASE,
)
```

Notes: "payment"/"pay" are safe as debit words because the parser already
rejects messages containing BOTH a debit and a credit keyword, and "payment"
rarely co-occurs with "credited" in legit credit SMS. Add `|payment` to credit
side? No — "payment received" already matches via "received".

### Step 2 — Balance-context exclusion

Add:

```kotlin
/** Amounts immediately preceded by a balance/limit cue are noise, not the txn amount. */
private val BALANCE_CUE = Regex(
    "(?i)(?:bal|balance|avl\\s?bal|available|limit|lmt|lmit|total\\s?bal)[.:\\s]*$"
)
```

In `findAmountClosestToKeyword`, skip candidates whose preceding ~20 chars end
with a balance cue:

```kotlin
val prefix = body.substring(0, match.range.first).takeLast(24)
if (BALANCE_CUE.containsMatchIn(prefix)) return@mapNotNull null
```

### Step 3 — "Txn of Rs X" connector pattern

Add a second-chance match: if the keyword+amount scan found nothing, look for

```kotlin
private val CONNECTOR_AMOUNT = Regex(
    "(?i)(?:txn|transaction|order|payment)\\s+(?:of|for|:)?\\s*(?:Rs\\.?|INR|₹|\\$|€|£)\\s?($NUMBER)",
)
```

If found, treat the message as a debit (`isCredit = false`) — "transaction of
Rs X" is universally a spend in practice. Document this assumption in the KDoc.

### Step 4 — `isLikelyBankSender` helper

```kotlin
/** Heuristic used by plan 014: does this sender look like a bank/PSP sender? */
fun isLikelyBankSender(sender: String): Boolean {
    val s = sender.trim()
    if (s.isEmpty()) return false
    // Indian-style: XX-HDFC / JD-HDFC-BANK / VM-XXXPAY
    if (s.contains('-') && s.any { it.isLetter() }) return true
    // numeric sender with country code (+91xxxxxxxxxx) or short code
    return s.all { it.isDigit() } || (s.startsWith("+") && s.drop(1).all { it.isDigit() })
}
```

### Step 5 — Tests

Add to `BankSmsParserTest.kt`, keeping its `parse()` helper:

- `parses charged verb` — `"Your card XX1234 was charged for Rs 899 on 12-03"`
- `parses autopay mandate debit` — `"AutoPay of Rs 299 executed for Netflix"`
- `parses emi debit` — `"EMI of Rs 4,500 deducted from a/c XX"`
- `parses txn-of connector without keyword` — `"Txn of Rs 1,234.00 on ICICI card"` → debit, 1234.00
- `balance amounts are skipped` — `"Bal: Rs 5,000. Rs 300 debited at Amazon"` → 300 (not 5000)
- `available limit noise skipped` — `"Avl lmt Rs 1,00,000. Spent Rs 250 at Zomato"` → 250
- `trailing slash-dash parses` — `"Rs 500.00/- debited from a/c XX99"` → 500.00
- `upi paid-to parses` — `"Paid Rs 150 to UPI ID rahul@ybl"` → debit
- `payment-received credit still works` — `"Payment of Rs 999 received on card XX"` → credit
- `isLikelyBankSender` — true for `"JD-HDFC"`, `"+919595000000"`, `"404040"`; false for `"Google"`, `""`.
- One **negative round**: `"Your OTP is 123456"` still returns null.

## Out of scope

- Per-bank templates (rejected design), merchant/comment extraction (014),
  review inbox (015), watch module.
- Changing `dedupeKey` format (012 owns that area).

## Done criteria

1. `./gradlew :app:compileFossDebugKotlin` — exit 0.
2. `./gradlew :app:testFossDebugUnitTest --tests "com.sachit.moneypal.domain.sms.BankSmsParserTest"` — exit 0, all new tests present.
3. `grep -c "charged" app/src/main/java/com/sachit/moneypal/domain/sms/BankSmsParser.kt` — ≥ 1.

## Maintenance notes

- Keep the parser pure Kotlin (no Android imports) — it runs in JVM tests and
  (future) in the Wear module without bringing Android deps.
- The keyword lists are additive; when a bank SMS format is reported as a bug,
  add the format + a failing test first, then extend the regex.
