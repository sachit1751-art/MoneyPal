package com.sachit.moneypal.domain.usecase

import com.sachit.moneypal.data.repository.SettingsRepository
import javax.inject.Inject

class MarkOnboardingCompletedUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke() {
        settingsRepository.setOnboardingCompleted(true)
    }
}
