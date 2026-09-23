package com.sachit.moneypal.presentation.widget

import android.content.Context
import com.sachit.moneypal.data.repository.SettingsRepository
import com.sachit.moneypal.domain.calculator.MaskedAmountFormatter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Plan 042: shared privacy redaction for home-screen widgets.
 *
 * Widgets render on the launcher and are not protected by app lock or
 * FLAG_SECURE, so when the user enables "hide amounts" every widget masks its
 * money strings through [maskAmount].
 *
 * The flag is read live (not snapshotted into widget state) because settings
 * toggles and widget re-renders happen at different times; widgets re-render
 * when the flag changes via [WidgetPrivacyRefresh].
 */
object WidgetPrivacy {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetPrivacyEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    /** Live read of the "hide amounts" preference; safe to call from any process context. */
    suspend fun isHidden(context: Context): Boolean {
        val repository = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetPrivacyEntryPoint::class.java,
        ).settingsRepository()
        return repository.observeSettings().first().widgetsHideAmounts
    }

    /**
     * Masks an already-formatted money string when the privacy flag is on.
     * [currencySymbol] keeps the currency visible (e.g. `$•••`); pass null to
     * show the bare mask.
     */
    suspend fun maskAmount(context: Context, formattedAmount: String, currencySymbol: String? = null): String {
        if (formattedAmount.isBlank()) return formattedAmount
        return MaskedAmountFormatter.format(
            formattedAmount = formattedAmount,
            hide = isHidden(context),
            currencySymbol = currencySymbol,
        )
    }
}
