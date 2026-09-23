package com.sachit.moneypal.domain.calculator

/**
 * Plan 042: replaces already-formatted money strings with a masked variant so
 * home-screen widgets never expose balances while the "hide amounts" setting
 * is on (widgets render on the launcher, outside app-lock/FLAG_SECURE).
 *
 * String-based by design: widget formatters already produce display strings,
 * so masking happens on the final string. The currency symbol (when supplied)
 * stays visible so users can still tell which currency the widget tracks.
 */
object MaskedAmountFormatter {

    private const val MASK = "•••"

    /**
     * Returns [formattedAmount] unchanged when [hide] is false, otherwise a
     * masked string. When [currencySymbol] is present it is prefixed to the
     * mask (e.g. `$•••`); without it the bare mask is returned.
     */
    fun format(formattedAmount: String, hide: Boolean, currencySymbol: String? = null): String {
        if (!hide) return formattedAmount
        return if (currencySymbol.isNullOrBlank()) MASK else "$currencySymbol$MASK"
    }
}
