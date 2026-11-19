package com.wifisentinel.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifisentinel.domain.model.ActivityEvent
import com.wifisentinel.domain.model.WifiConnectionStatus
import com.wifisentinel.domain.repository.ActivityLogRepository
import com.wifisentinel.domain.repository.AssessmentRepository
import com.wifisentinel.domain.repository.WifiRepository
import com.wifisentinel.domain.repository.WordlistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val connectionStatus: WifiConnectionStatus,
    val networksCount: Int = 0,
    val wordlistsCount: Int = 0,
    val analyzedPasswordsCount: Int = 0,
    val assessmentsCount: Int = 0,
    val recentActivity: List<ActivityEvent> = emptyList()
)

class DashboardViewModel(
    private val wifiRepository: WifiRepository,
    wordlistRepository: WordlistRepository,
    assessmentRepository: AssessmentRepository,
    activityLogRepository: ActivityLogRepository
) : ViewModel() {

    private val connectionStatusFlow = MutableStateFlow(wifiRepository.getConnectionStatus())

    val uiState: StateFlow<DashboardUiState> = combine(
        connectionStatusFlow,
        wifiRepository.observeDiscoveredCount(),
        wordlistRepository.observeWordlistCount(),
        wordlistRepository.observeTotalAnalyzedEntriesCount(),
        assessmentRepository.observeAssessmentCount(),
        activityLogRepository.observeRecentActivity()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        DashboardUiState(
            connectionStatus = values[0] as WifiConnectionStatus,
            networksCount = values[1] as Int,
            wordlistsCount = values[2] as Int,
            analyzedPasswordsCount = values[3] as Int,
            assessmentsCount = values[4] as Int,
            recentActivity = values[5] as List<ActivityEvent>
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(connectionStatus = wifiRepository.getConnectionStatus())
    )

    fun refreshConnectionStatus() {
        connectionStatusFlow.value = wifiRepository.getConnectionStatus()
    }
}
