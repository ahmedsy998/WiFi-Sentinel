package com.wifisentinel.presentation.securitylab

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wifisentinel.data.local.OwnerVerificationResult
import com.wifisentinel.data.local.ReportExporter
import com.wifisentinel.domain.model.ActivityEventType
import com.wifisentinel.domain.model.PasswordAnalysisResult
import com.wifisentinel.domain.model.SecurityAssessment
import com.wifisentinel.domain.model.VerificationStatus
import com.wifisentinel.domain.model.WifiNetwork
import com.wifisentinel.domain.model.WifiSecurityType
import com.wifisentinel.domain.model.WordlistComparisonSummary
import com.wifisentinel.domain.repository.ActivityLogRepository
import com.wifisentinel.domain.repository.AssessmentRepository
import com.wifisentinel.domain.repository.WifiRepository
import com.wifisentinel.domain.repository.WordlistRepository
import com.wifisentinel.domain.usecase.BuildSecurityAssessmentUseCase
import com.wifisentinel.security.passwordanalysis.PasswordStrengthAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SecurityLabUiState(
    val ownerAuthorized: Boolean = false,
    val networkSsid: String = "",
    val networkBssid: String = "",
    val securityProtocol: WifiSecurityType = WifiSecurityType.WPA2,
    val frequencyMhz: Int? = null,
    val ownerPassphrase: String = "",
    val livePasswordAnalysis: PasswordAnalysisResult? = null,
    val wordlistComparison: WordlistComparisonSummary? = null,
    val verificationStatus: VerificationStatus = VerificationStatus.NOT_VERIFIED,
    val verificationMethod: String? = null,
    val verifiedAt: Long? = null,
    val isVerifying: Boolean = false,
    val isSaving: Boolean = false,
    val feedbackMessage: String? = null,
    val isFeedbackError: Boolean = false,
    val discoveredNetworks: List<WifiNetwork> = emptyList(),
    val savedAssessments: List<SecurityAssessment> = emptyList()
)

class SecurityLabViewModel(
    private val wifiRepository: WifiRepository,
    private val wordlistRepository: WordlistRepository,
    private val assessmentRepository: AssessmentRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val passwordAnalyzer: PasswordStrengthAnalyzer,
    private val buildAssessmentUseCase: BuildSecurityAssessmentUseCase,
    private val reportExporter: ReportExporter
) : ViewModel() {

    private val _formState = MutableStateFlow(SecurityLabUiState())

    val uiState: StateFlow<SecurityLabUiState> = combine(
        _formState,
        wifiRepository.observeDiscoveredNetworks(),
        assessmentRepository.observeAssessments()
    ) { form, networks, assessments ->
        form.copy(
            discoveredNetworks = networks,
            savedAssessments = assessments
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SecurityLabUiState()
    )

    // Standalone Password Analyzer state
    private val _standalonePassword = MutableStateFlow("")
    private val _standaloneSsid = MutableStateFlow("")
    private val _standaloneResult = MutableStateFlow<PasswordAnalysisResult?>(null)
    val standalonePassword: StateFlow<String> = _standalonePassword.asStateFlow()
    val standaloneSsid: StateFlow<String> = _standaloneSsid.asStateFlow()
    val standaloneResult: StateFlow<PasswordAnalysisResult?> = _standaloneResult.asStateFlow()

    fun updateStandalonePassword(value: String) {
        _standalonePassword.value = value
        _standaloneResult.value = if (value.isNotEmpty()) {
            passwordAnalyzer.analyze(value, targetSsid = _standaloneSsid.value.takeIf { it.isNotBlank() })
        } else {
            null
        }
    }

    fun updateStandaloneSsid(value: String) {
        _standaloneSsid.value = value
        val pwd = _standalonePassword.value
        if (pwd.isNotEmpty()) {
            _standaloneResult.value = passwordAnalyzer.analyze(pwd, targetSsid = value.takeIf { it.isNotBlank() })
        }
    }

    fun logStandaloneAnalysis() {
        val result = _standaloneResult.value ?: return
        viewModelScope.launch {
            activityLogRepository.recordEvent(
                type = ActivityEventType.PASSWORD_ANALYZED,
                titleEn = "Local Password Analyzed",
                titleAr = "تم تحليل كلمة مرور محلياً",
                detailEn = "Score ${result.score}/100 (${result.strengthClass.labelEn}), ~${result.estimatedEntropyBits} bits entropy.",
                detailAr = "النتيجة ${result.score}/100 (${result.strengthClass.labelAr})، الإنتروبيا ~${result.estimatedEntropyBits} بت."
            )
        }
    }

    // Security Lab Form Actions
    fun setOwnerAuthorized(confirmed: Boolean) {
        _formState.value = _formState.value.copy(ownerAuthorized = confirmed)
    }

    fun prefillFromNetwork(network: WifiNetwork) {
        _formState.value = _formState.value.copy(
            networkSsid = if (network.isHiddenSsid) "" else network.ssid,
            networkBssid = network.bssid,
            securityProtocol = network.securityType,
            frequencyMhz = network.frequencyMhz,
            verificationStatus = VerificationStatus.NOT_VERIFIED,
            verificationMethod = null,
            verifiedAt = null
        )
        recomputeLocalAnalysis()
    }

    fun updateNetworkSsid(ssid: String) {
        _formState.value = _formState.value.copy(
            networkSsid = ssid,
            verificationStatus = VerificationStatus.NOT_VERIFIED,
            verificationMethod = null,
            verifiedAt = null
        )
        recomputeLocalAnalysis()
    }

    fun updateNetworkBssid(bssid: String) {
        _formState.value = _formState.value.copy(networkBssid = bssid)
    }

    fun updateSecurityProtocol(protocol: WifiSecurityType) {
        _formState.value = _formState.value.copy(securityProtocol = protocol)
    }

    fun updateOwnerPassphrase(passphrase: String) {
        _formState.value = _formState.value.copy(
            ownerPassphrase = passphrase,
            verificationStatus = VerificationStatus.NOT_VERIFIED,
            verificationMethod = null,
            verifiedAt = null
        )
        recomputeLocalAnalysis()
    }

    private fun recomputeLocalAnalysis() {
        val current = _formState.value
        val pwd = current.ownerPassphrase
        if (pwd.isEmpty()) {
            _formState.value = current.copy(livePasswordAnalysis = null, wordlistComparison = null)
            return
        }
        val analysis = passwordAnalyzer.analyze(pwd, targetSsid = current.networkSsid.takeIf { it.isNotBlank() })
        _formState.value = current.copy(livePasswordAnalysis = analysis)
        viewModelScope.launch {
            val comparison = wordlistRepository.comparePasswordLocally(pwd)
            _formState.value = _formState.value.copy(wordlistComparison = comparison)
        }
    }

    fun verifyOwnerConnection() {
        val current = _formState.value
        if (!current.ownerAuthorized) {
            _formState.value = current.copy(
                feedbackMessage = "Confirm owner authorization first.",
                isFeedbackError = true
            )
            return
        }
        if (current.networkSsid.isBlank() || current.ownerPassphrase.length < 8) {
            _formState.value = current.copy(
                feedbackMessage = "Enter your authorized network SSID and passphrase (8+ characters).",
                isFeedbackError = true
            )
            return
        }
        viewModelScope.launch {
            _formState.value = _formState.value.copy(isVerifying = true, feedbackMessage = null)
            val useWpa3 = current.securityProtocol == WifiSecurityType.WPA3
            when (val res = wifiRepository.verifyOwnerAuthorizedConnection(current.networkSsid, current.ownerPassphrase, useWpa3)) {
                is OwnerVerificationResult.Verified -> {
                    _formState.value = _formState.value.copy(
                        isVerifying = false,
                        verificationStatus = VerificationStatus.OWNER_VERIFIED,
                        verificationMethod = res.method,
                        verifiedAt = res.verifiedAt,
                        feedbackMessage = "Owner authentication verified via ${res.method}.",
                        isFeedbackError = false
                    )
                }
                is OwnerVerificationResult.Failed -> {
                    _formState.value = _formState.value.copy(
                        isVerifying = false,
                        verificationStatus = VerificationStatus.VERIFICATION_FAILED,
                        verificationMethod = "Android WifiNetworkSpecifier",
                        verifiedAt = null,
                        feedbackMessage = res.reason,
                        isFeedbackError = true
                    )
                }
                is OwnerVerificationResult.Unsupported -> {
                    _formState.value = _formState.value.copy(
                        isVerifying = false,
                        feedbackMessage = res.reason,
                        isFeedbackError = true
                    )
                }
            }
        }
    }

    fun generateAndSaveAssessment(onSaved: (assessmentId: String) -> Unit) {
        val current = _formState.value
        if (!current.ownerAuthorized) {
            _formState.value = current.copy(
                feedbackMessage = "You must confirm network ownership or explicit authorization before running an assessment.",
                isFeedbackError = true
            )
            return
        }
        if (current.networkSsid.isBlank()) {
            _formState.value = current.copy(
                feedbackMessage = "Enter a network profile name (SSID).",
                isFeedbackError = true
            )
            return
        }
        viewModelScope.launch {
            _formState.value = _formState.value.copy(isSaving = true, feedbackMessage = null)
            val saved = buildAssessmentUseCase(
                ssid = current.networkSsid,
                bssid = current.networkBssid,
                securityProtocol = current.securityProtocol,
                frequencyMhz = current.frequencyMhz,
                ownerPassword = current.ownerPassphrase,
                ownerAuthorizationConfirmed = true,
                verificationStatus = current.verificationStatus,
                verificationMethod = current.verificationMethod,
                verifiedAt = current.verifiedAt
            )
            _formState.value = _formState.value.copy(
                isSaving = false,
                feedbackMessage = "Assessment ${saved.id} saved locally.",
                isFeedbackError = false
            )
            onSaved(saved.id)
        }
    }

    fun observeAssessment(assessmentId: String): StateFlow<SecurityAssessment?> =
        assessmentRepository.observeAssessmentById(assessmentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun decryptAssessmentPassword(encryptedValue: String): String =
        assessmentRepository.decryptAssessmentPassword(encryptedValue)

    fun deleteAssessment(id: String) {
        viewModelScope.launch {
            assessmentRepository.deleteAssessment(id)
        }
    }

    fun exportAssessmentTxt(uri: Uri, assessment: SecurityAssessment, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val res = reportExporter.exportAssessmentTxt(uri, assessment)
            if (res.isSuccess) {
                activityLogRepository.recordEvent(
                    type = ActivityEventType.REPORT_EXPORTED,
                    titleEn = "TXT Report Exported",
                    titleAr = "تم تصدير تقرير TXT",
                    detailEn = "Exported assessment ${assessment.id} (${assessment.networkSsid}).",
                    detailAr = "تم تصدير التقييم ${assessment.id} (${assessment.networkSsid})."
                )
                onComplete("TXT report exported successfully.")
            } else {
                onComplete("Export failed: ${res.exceptionOrNull()?.message ?: "unknown error"}")
            }
        }
    }

    fun exportAssessmentPdf(uri: Uri, assessment: SecurityAssessment, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val res = reportExporter.exportAssessmentPdf(uri, assessment)
            if (res.isSuccess) {
                activityLogRepository.recordEvent(
                    type = ActivityEventType.REPORT_EXPORTED,
                    titleEn = "PDF Report Exported",
                    titleAr = "تم تصدير تقرير PDF",
                    detailEn = "Exported assessment ${assessment.id} (${assessment.networkSsid}).",
                    detailAr = "تم تصدير التقييم ${assessment.id} (${assessment.networkSsid})."
                )
                onComplete("PDF report exported successfully.")
            } else {
                onComplete("PDF export failed: ${res.exceptionOrNull()?.message ?: "unknown error"}")
            }
        }
    }

    fun clearFeedback() {
        _formState.value = _formState.value.copy(feedbackMessage = null)
    }
}
