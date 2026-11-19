package com.wifisentinel.domain.usecase

import com.wifisentinel.domain.model.AssessmentClassification
import com.wifisentinel.domain.model.PasswordAnalysisResult
import com.wifisentinel.domain.model.PasswordStrengthClass
import com.wifisentinel.domain.model.SecurityAssessment
import com.wifisentinel.domain.model.VerificationStatus
import com.wifisentinel.domain.model.WeaknessCode
import com.wifisentinel.domain.model.WifiSecurityType
import com.wifisentinel.domain.repository.AssessmentRepository
import com.wifisentinel.domain.repository.WordlistRepository
import com.wifisentinel.security.passwordanalysis.PasswordStrengthAnalyzer
import com.wifisentinel.security.securestorage.SecureDataCipher
import java.util.UUID

class BuildSecurityAssessmentUseCase(
    private val passwordAnalyzer: PasswordStrengthAnalyzer,
    private val wordlistRepository: WordlistRepository,
    private val assessmentRepository: AssessmentRepository,
    private val cipher: SecureDataCipher
) {
    suspend operator fun invoke(
        ssid: String,
        bssid: String?,
        securityProtocol: WifiSecurityType,
        frequencyMhz: Int?,
        ownerPassword: String,
        ownerAuthorizationConfirmed: Boolean,
        verificationStatus: VerificationStatus = VerificationStatus.NOT_VERIFIED,
        verificationMethod: String? = null,
        verifiedAt: Long? = null
    ): SecurityAssessment {
        val now = System.currentTimeMillis()
        val analysis: PasswordAnalysisResult = passwordAnalyzer.analyze(
            password = ownerPassword,
            targetSsid = ssid,
            nowMillis = now
        )
        val wordlistCheck = wordlistRepository.comparePasswordLocally(ownerPassword)

        val weaknesses = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        // A. Network Configuration Analysis
        when (securityProtocol) {
            WifiSecurityType.OPEN -> {
                weaknesses += "Network is Open (Unencrypted) — wireless traffic can be observed passively."
                recommendations += "Enable WPA3-SAE or WPA2/WPA3 Transitional mode on your router immediately."
            }
            WifiSecurityType.WEP -> {
                weaknesses += "WEP encryption is cryptographically broken and deprecated."
                recommendations += "Migrate router security from WEP to WPA3-SAE or WPA2-AES (CCMP)."
            }
            WifiSecurityType.WPA -> {
                weaknesses += "Legacy WPA-TKIP has known cryptographic weaknesses."
                recommendations += "Disable TKIP and upgrade to WPA2-AES (CCMP) or WPA3-SAE."
            }
            WifiSecurityType.WPA2 -> {
                recommendations += "Ensure AES/CCMP (not TKIP) is enforced; upgrade to WPA3-SAE when client devices support it."
            }
            WifiSecurityType.WPA2_WPA3, WifiSecurityType.WPA3, WifiSecurityType.ENTERPRISE -> {
                // Modern security protocol
            }
            WifiSecurityType.UNKNOWN -> {
                weaknesses += "Network security protocol could not be confirmed."
                recommendations += "Verify router encryption settings in your router admin panel."
            }
        }

        // B. Local Password Strength & Local Wordlist Audit
        if (securityProtocol != WifiSecurityType.OPEN) {
            if (ownerPassword.isBlank()) {
                weaknesses += "No owner passphrase was supplied for local strength evaluation."
                recommendations += "Provide your network passphrase to assess length, entropy, and local wordlist exposure."
            } else {
                analysis.weaknessCodes.forEach { code ->
                    weaknesses += describeWeaknessEn(code)
                }
                if (wordlistCheck.exactMatchFound) {
                    weaknesses += "Exact match found in imported local wordlist (${wordlistCheck.matchedWordlistNames.joinToString()})."
                    recommendations += "Replace this passphrase immediately — it appears in a local password audit wordlist."
                } else if (wordlistCheck.caseInsensitiveMatchFound) {
                    weaknesses += "Case-insensitive variant found in imported local wordlist (${wordlistCheck.matchedWordlistNames.joinToString()})."
                    recommendations += "Choose a passphrase that is not a casing variation of a known wordlist entry."
                }
                recommendations += analysis.recommendationsEn
            }
        }

        val overallClassification = computeOverallClassification(
            protocol = securityProtocol,
            passwordScore = analysis.score,
            wordlistMatch = wordlistCheck.exactMatchFound || wordlistCheck.caseInsensitiveMatchFound,
            hasPassword = ownerPassword.isNotBlank()
        )

        val shortId = "WS-" + UUID.randomUUID().toString().substring(0, 8).uppercase()
        val assessment = SecurityAssessment(
            id = shortId,
            networkSsid = ssid.trim(),
            networkBssid = bssid?.trim()?.takeIf { it.isNotEmpty() },
            securityProtocol = securityProtocol,
            frequencyMhz = frequencyMhz,
            ownerAuthorizationConfirmed = ownerAuthorizationConfirmed,
            encryptedPassword = ownerPassword.takeIf { it.isNotEmpty() }?.let { cipher.encrypt(it) },
            maskedPassword = ownerPassword.takeIf { it.isNotEmpty() }?.let { passwordAnalyzer.maskPassword(it) },
            passwordLength = analysis.length,
            passwordStrengthScore = if (ownerPassword.isBlank()) 0 else analysis.score,
            passwordStrengthClass = if (ownerPassword.isBlank()) PasswordStrengthClass.VERY_WEAK else analysis.strengthClass,
            estimatedEntropyBits = if (ownerPassword.isBlank()) 0.0 else analysis.estimatedEntropyBits,
            wordlistMatchFound = wordlistCheck.exactMatchFound || wordlistCheck.caseInsensitiveMatchFound,
            wordlistEntriesChecked = wordlistCheck.totalEntriesCompared,
            matchedWordlistNames = wordlistCheck.matchedWordlistNames,
            overallClassification = overallClassification,
            identifiedWeaknesses = weaknesses.distinct(),
            recommendedImprovements = recommendations.distinct(),
            verificationStatus = verificationStatus,
            verificationMethod = verificationMethod,
            verifiedAt = verifiedAt,
            createdAt = now
        )

        assessmentRepository.saveAssessment(assessment)
        return assessment
    }

    private fun computeOverallClassification(
        protocol: WifiSecurityType,
        passwordScore: Int,
        wordlistMatch: Boolean,
        hasPassword: Boolean
    ): AssessmentClassification {
        if (protocol == WifiSecurityType.OPEN ||
            protocol == WifiSecurityType.WEP ||
            protocol == WifiSecurityType.WPA ||
            wordlistMatch
        ) {
            return AssessmentClassification.WEAK
        }
        if (!hasPassword) return AssessmentClassification.NEEDS_IMPROVEMENT
        return when {
            passwordScore >= 75 && protocol.riskScore <= 25 -> AssessmentClassification.STRONG
            passwordScore >= 50 -> AssessmentClassification.NEEDS_IMPROVEMENT
            else -> AssessmentClassification.WEAK
        }
    }

    private fun describeWeaknessEn(code: WeaknessCode): String = when (code) {
        WeaknessCode.TOO_SHORT_FOR_WPA -> "Passphrase is shorter than the 8-character WPA2/WPA3 minimum."
        WeaknessCode.SHORT_LENGTH -> "Passphrase is shorter than 12 characters."
        WeaknessCode.MISSING_UPPERCASE -> "No uppercase characters detected."
        WeaknessCode.MISSING_LOWERCASE -> "No lowercase characters detected."
        WeaknessCode.MISSING_DIGITS -> "No numeric digits detected."
        WeaknessCode.MISSING_SYMBOLS -> "No special symbols detected."
        WeaknessCode.REPEATED_SEQUENCE -> "Contains repeated characters or duplicated segments."
        WeaknessCode.KEYBOARD_PATTERN -> "Contains predictable keyboard walk pattern."
        WeaknessCode.COMMON_DICTIONARY_WORD -> "Contains a common dictionary word or leetspeak variant."
        WeaknessCode.COMMON_WIFI_DEFAULT -> "Matches a widely known default Wi-Fi/router password."
        WeaknessCode.DATE_OR_YEAR_PATTERN -> "Contains a predictable calendar year/date pattern."
        WeaknessCode.SEQUENTIAL_CHARACTERS -> "Contains sequential character or digit run."
        WeaknessCode.CONTAINS_SSID -> "Passphrase embeds the network name (SSID)."
    }
}
