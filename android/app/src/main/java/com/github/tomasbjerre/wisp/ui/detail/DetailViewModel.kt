package com.github.tomasbjerre.wisp.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tomasbjerre.wisp.data.Session
import com.github.tomasbjerre.wisp.data.SessionRepository
import com.github.tomasbjerre.wisp.data.TrackPoint
import com.github.tomasbjerre.wisp.location.LatLon
import com.github.tomasbjerre.wisp.util.GeoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val repository: SessionRepository,
    private val sessionId: Long,
) : ViewModel() {
    val session: StateFlow<Session?> =
        repository
            .observeSession(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _route = MutableStateFlow<List<LatLon>>(emptyList())
    val route: StateFlow<List<LatLon>> = _route.asStateFlow()

    // See specs/tracking.md#km-splits — derived from the same points loaded for the
    // route above, not a separate query.
    private val _kmSplitsSeconds = MutableStateFlow<List<Long>>(emptyList())
    val kmSplitsSeconds: StateFlow<List<Long>> = _kmSplitsSeconds.asStateFlow()

    init {
        viewModelScope.launch {
            val points = repository.getPoints(sessionId)
            _route.value = points.map { it.toLatLon() }
            _kmSplitsSeconds.value = GeoUtils.kmSplitsSeconds(points)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            session.value?.let { repository.deleteSession(it) }
            onDeleted()
        }
    }

    private fun TrackPoint.toLatLon() = LatLon(latitude, longitude)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
