package com.sachit.moneypal.presentation.ui.onboarding

data class OnboardingUiState(
    val isCompleted: Boolean = false,
)

sealed interface OnboardingUiIntent {
    data object OnWelcomeDismissed : OnboardingUiIntent
}

sealed interface OnboardingUiEffect {
    data object OnboardingCompleted : OnboardingUiEffect

    data class OnboardingFailed(val message: String) : OnboardingUiEffect
}
