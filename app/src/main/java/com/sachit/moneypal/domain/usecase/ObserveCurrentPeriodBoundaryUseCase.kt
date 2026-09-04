package com.sachit.moneypal.domain.usecase

import com.sachit.moneypal.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCurrentPeriodBoundaryUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<Pair<Long, Long>> {
        return settingsRepository.observeCurrentPeriodBoundary()
    }
}
