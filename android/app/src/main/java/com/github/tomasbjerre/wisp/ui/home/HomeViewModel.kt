package com.github.tomasbjerre.wisp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tomasbjerre.wisp.data.Session
import com.github.tomasbjerre.wisp.data.SessionRepository
import com.github.tomasbjerre.wisp.data.TrackPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: SessionRepository,
) : ViewModel() {
    val sessions: StateFlow<List<Session>> =
        repository
            .observeSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    // See specs/ui-flows.md#1-home and specs/permissions-and-privacy.md#data-handling.
    fun delete(session: Session) {
        viewModelScope.launch { repository.deleteSession(session) }
    }

    // See specs/export.md#history-as-csv.
    suspend fun loadPointsBySession(): List<Pair<Session, List<TrackPoint>>> {
        val currentSessions = sessions.value
        return currentSessions.map { it to repository.getPoints(it.id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
