package com.github.tomasbjerre.wisp.ui.splits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.tomasbjerre.wisp.data.SessionRepository
import com.github.tomasbjerre.wisp.data.UnitSystem
import com.github.tomasbjerre.wisp.util.GeoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** See specs/ui-flows.md#4-km-splits — splits derived from the session's points, like Detail's. */
class KmSplitsViewModel(
    repository: SessionRepository,
    sessionId: Long,
    unit: UnitSystem,
) : ViewModel() {
    private val _splits = MutableStateFlow(GeoUtils.KmSplits(emptyList(), null))
    val splits: StateFlow<GeoUtils.KmSplits> = _splits.asStateFlow()

    init {
        viewModelScope.launch { _splits.value = GeoUtils.kmSplits(repository.getPoints(sessionId), unit) }
    }
}
