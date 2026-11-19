package com.wifisentinel.domain.model

enum class AssessmentClassification {
    STRONG,
    NEEDS_IMPROVEMENT,
    WEAK
}

enum class VerificationStatus {
    NOT_VERIFIED,
    OWNER_VERIFIED,
    VERIFICATION_FAILED
}

data class WordlistComparisonSummary(
    val totalEntriesCompared: Int,
    val exactMatchFound: Boolean,
    val caseInsensitiveMatchFound: Boolean,
    val matchedWordlistNames: List<String>
)

data class SecurityAssessment(
    val id: String,
    val networkSsid: String,
    val networkBssid: String?,
    val securityProtocol: WifiSecurityType,
    val frequencyMhz: Int?,
    val ownerAuthorizationConfirmed: Boolean,
    val encryptedPassword: String?,
    val maskedPassword: String?,
    val passwordLength: Int,
    val passwordStrengthScore: Int,
    val passwordStrengthClass: PasswordStrengthClass,
    val estimatedEntropyBits: Double,
    val wordlistMatchFound: Boolean,
    val wordlistEntriesChecked: Int,
    val matchedWordlistNames: List<String>,
    val overallClassification: AssessmentClassification,
    val identifiedWeaknesses: List<String>,
    val recommendedImprovements: List<String>,
    val verificationStatus: VerificationStatus,
    val verificationMethod: String?,
    val verifiedAt: Long?,
    val createdAt: Long
)

enum class ActivityEventType {
    NETWORK_SCAN,
    WORDLIST_IMPORT,
    WORDLIST_DELETED,
    PASSWORD_ANALYZED,
    ASSESSMENT_SAVED,
    AUTH_VERIFIED,
    REPORT_EXPORTED,
    DATA_CLEARED
}

data class ActivityEvent(
    val id: Long = 0L,
    val type: ActivityEventType,
    val titleEn: String,
    val titleAr: String,
    val detailEn: String,
    val detailAr: String,
    val timestamp: Long
)

enum class AppLanguage(val code: String, val nativeName: String) {
    ENGLISH("en", "English"),
    ARABIC("ar", "العربية");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

data class AppSettings(
    val darkTheme: Boolean = true,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val maskPasswordsByDefault: Boolean = true,
    val biometricProtectionEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val appLockPinHash: String? = null,
    val notificationsEnabled: Boolean = true,
    val autoClearClipboard: Boolean = true,
    val blockScreenshots: Boolean = true
)
