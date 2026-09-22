package com.github.tomasbjerre.wisp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tomasbjerre.wisp.data.Session
import com.github.tomasbjerre.wisp.data.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    repository: SessionRepository,
) : ViewModel() {
    val sessions: StateFlow<List<Session>> =
        repository
            .observeSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
