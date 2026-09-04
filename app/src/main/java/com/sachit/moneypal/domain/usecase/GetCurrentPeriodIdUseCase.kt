package com.sachit.moneypal.domain.usecase

import com.sachit.moneypal.data.repository.SettingsRepository
import javax.inject.Inject

class GetCurrentPeriodIdUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): Long {
        return settingsRepository.getCurrentPeriodId()
    }
}
