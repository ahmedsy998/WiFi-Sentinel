package com.wifisentinel.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.wifisentinel.presentation.dashboard.DashboardViewModel
import com.wifisentinel.presentation.networks.NetworksViewModel
import com.wifisentinel.presentation.securitylab.SecurityLabViewModel
import com.wifisentinel.presentation.settings.SettingsViewModel
import com.wifisentinel.presentation.wordlists.WordlistsViewModel

class SentinelViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(
            wifiRepository = container.wifiRepository,
            wordlistRepository = container.wordlistRepository,
            assessmentRepository = container.assessmentRepository,
            activityLogRepository = container.activityLogRepository
        ) as T

        modelClass.isAssignableFrom(NetworksViewModel::class.java) -> NetworksViewModel(
            wifiRepository = container.wifiRepository
        ) as T

        modelClass.isAssignableFrom(WordlistsViewModel::class.java) -> WordlistsViewModel(
            wordlistRepository = container.wordlistRepository
        ) as T

        modelClass.isAssignableFrom(SecurityLabViewModel::class.java) -> SecurityLabViewModel(
            wifiRepository = container.wifiRepository,
            wordlistRepository = container.wordlistRepository,
            assessmentRepository = container.assessmentRepository,
            activityLogRepository = container.activityLogRepository,
            passwordAnalyzer = container.passwordAnalyzer,
            buildAssessmentUseCase = container.buildSecurityAssessmentUseCase,
            reportExporter = container.reportExporter
        ) as T

        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
            settingsRepository = container.settingsRepository,
            wordlistRepository = container.wordlistRepository,
            assessmentRepository = container.assessmentRepository,
            activityLogRepository = container.activityLogRepository,
            reportExporter = container.reportExporter,
            passwordAnalyzer = container.passwordAnalyzer
        ) as T

        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
