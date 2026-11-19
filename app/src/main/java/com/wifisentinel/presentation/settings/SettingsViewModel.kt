package com.wifisentinel.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifisentinel.data.local.ReportExporter
import com.wifisentinel.domain.model.ActivityEventType
import com.wifisentinel.domain.model.AppLanguage
import com.wifisentinel.domain.model.AppSettings
import com.wifisentinel.domain.repository.ActivityLogRepository
import com.wifisentinel.domain.repository.AssessmentRepository
import com.wifisentinel.domain.repository.SettingsRepository
import com.wifisentinel.domain.repository.WordlistRepository
import com.wifisentinel.security.passwordanalysis.PasswordStrengthAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val wordlistRepository: WordlistRepository,
    private val assessmentRepository: AssessmentRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val reportExporter: ReportExporter,
    private val passwordAnalyzer: PasswordStrengthAnalyzer
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings()
        )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkTheme(enabled) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    fun setMaskPasswordsByDefault(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMaskPasswordsByDefault(enabled) }
    }

    fun setBiometricProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBiometricProtectionEnabled(enabled)
            if (enabled) {
                settingsRepository.setAppLockEnabled(true)
            }
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAppLockEnabled(enabled)
            if (!enabled) {
                settingsRepository.setBiometricProtectionEnabled(false)
            }
        }
    }

    fun saveAppLockPin(pin: String) {
        if (pin.length < 4) return
        val hashed = passwordAnalyzer.sha256("wifi_sentinel_pin_v1:$pin")
        viewModelScope.launch {
            settingsRepository.setAppLockPinHash(hashed)
            settingsRepository.setAppLockEnabled(true)
            _message.value = "App lock PIN saved locally."
        }
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = settings.value.appLockPinHash ?: return true
        return passwordAnalyzer.sha256("wifi_sentinel_pin_v1:$pin") == storedHash
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotificationsEnabled(enabled) }
    }

    fun setAutoClearClipboard(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoClearClipboard(enabled) }
    }

    fun setBlockScreenshots(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBlockScreenshots(enabled) }
    }

    fun clearAllWordlists() {
        viewModelScope.launch {
            wordlistRepository.clearAllWordlists()
            _message.value = "All imported wordlists deleted from local storage."
        }
    }

    fun clearAnalysisHistory() {
        viewModelScope.launch {
            assessmentRepository.clearAllAssessments()
            activityLogRepository.clearAllActivity()
            _message.value = "Assessment reports and activity history cleared."
        }
    }

    fun exportLocalData(uri: Uri) {
        viewModelScope.launch {
            val currentSettings = settingsRepository.settingsFlow.first()
            val wordlists = wordlistRepository.observeWordlists().first()
            val assessments = assessmentRepository.observeAssessments().first()
            val result = reportExporter.exportLocalMetadataJson(uri, currentSettings, wordlists, assessments)
            if (result.isSuccess) {
                activityLogRepository.recordEvent(
                    type = ActivityEventType.REPORT_EXPORTED,
                    titleEn = "Local Metadata Exported",
                    titleAr = "تم تصدير البيانات الوصفية المحلية",
                    detailEn = "Exported ${assessments.size} assessment summaries and preferences (passwords omitted).",
                    detailAr = "تم تصدير ${assessments.size} ملخص تقييم وإعدادات (بدون كلمات المرور)."
                )
                _message.value = "Local metadata exported successfully."
            } else {
                _message.value = "Export failed: ${result.exceptionOrNull()?.message ?: "error"}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
