package com.sachit.moneypal.presentation.ui.settings.datahealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sachit.moneypal.data.repository.DataHealthRepository
import com.sachit.moneypal.domain.datahealth.DataHealthReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

/** Load state of the read-only data health dashboard (plan 049). */
data class DataHealthUiState(
    val isLoading: Boolean = true,
    val report: DataHealthReport? = null,
    val loadFailed: Boolean = false,
)

/**
 * Backing view model for the data health dashboard (plan 049). Scans once
 * per screen visit; the whole feature is read-only by design.
 */
@HiltViewModel
class DataHealthViewModel @Inject constructor(
    private val dataHealthRepository: DataHealthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataHealthUiState())
    val uiState: StateFlow<DataHealthUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadFailed = false) }
            _uiState.value = try {
                val report = dataHealthRepository.scan()
                DataHealthUiState(isLoading = false, report = report)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                logcat { "Data health scan failed\n${e.asLogSafe()}" }
                DataHealthUiState(isLoading = false, loadFailed = true)
            }
        }
    }

    private fun Exception.asLogSafe(): String = message ?: "unknown error"
}
