package com.sachit.moneypal.presentation.ui.onboarding

data class OnboardingUiState(
    val isCompleted: Boolean = false,
)

sealed interface OnboardingUiIntent {
    data object OnWelcomeDismissed : OnboardingUiIntent

    /** Records the user's SMS auto-capture choice made on the last carousel page. */
    data class OnSmsCaptureDecision(val enabled: Boolean) : OnboardingUiIntent
}

sealed interface OnboardingUiEffect {
    data object OnboardingCompleted : OnboardingUiEffect

    data class OnboardingFailed(val message: String) : OnboardingUiEffect
}
