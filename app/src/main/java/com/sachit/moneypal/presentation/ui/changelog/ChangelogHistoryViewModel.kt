package com.sachit.moneypal.presentation.ui.changelog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sachit.moneypal.data.repository.ChangelogRepository
import com.sachit.moneypal.domain.model.changelog.VersionRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ChangelogHistoryViewModel @Inject constructor(
    private val changelogRepository: ChangelogRepository,
) : ViewModel() {

    val releases: StateFlow<List<VersionRelease>> = flow {
        emit(changelogRepository.getAllReleases())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )
}
