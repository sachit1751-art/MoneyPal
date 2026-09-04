package com.sachit.moneypal.presentation.ui.theme

import android.content.Context
import com.sachit.moneypal.domain.model.UserSettings
import com.sachit.moneypal.presentation.appColorScheme
import com.sachit.moneypal.presentation.appTheme
import com.sachit.moneypal.presentation.appTypography
import com.sachit.moneypal.presentation.isRoundedFontEnabled
import com.sachit.moneypal.presentation.isAmoledEnabled
import com.sachit.moneypal.presentation.dynamicColorEnabled
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeManager @Inject constructor() {

    fun applyUserSettings(context: Context, settings: UserSettings) {
        context.appTheme = settings.themeMode
        context.appTypography = settings.typographyMode
        context.isRoundedFontEnabled = settings.isRoundedFontEnabled
        context.isAmoledEnabled = settings.isAmoledEnabled
        context.appColorScheme = settings.colorScheme
        context.dynamicColorEnabled = settings.dynamicColorEnabled
    }
}
