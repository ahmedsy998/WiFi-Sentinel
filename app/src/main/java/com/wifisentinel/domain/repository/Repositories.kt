package com.wifisentinel.domain.repository

import android.net.Uri
import com.wifisentinel.data.local.OwnerVerificationResult
import com.wifisentinel.domain.model.ActivityEvent
import com.wifisentinel.domain.model.ActivityEventType
import com.wifisentinel.domain.model.AppLanguage
import com.wifisentinel.domain.model.AppSettings
import com.wifisentinel.domain.model.ScanOutcome
import com.wifisentinel.domain.model.SecurityAssessment
import com.wifisentinel.domain.model.WifiConnectionStatus
import com.wifisentinel.domain.model.WifiNetwork
import com.wifisentinel.domain.model.WordlistComparisonSummary
import com.wifisentinel.domain.model.WordlistEntry
import com.wifisentinel.domain.model.WordlistFile
import com.wifisentinel.domain.model.WordlistImportResult
import com.wifisentinel.domain.model.WordlistSortOption
import kotlinx.coroutines.flow.Flow

interface WifiRepository {
    fun observeDiscoveredNetworks(): Flow<List<WifiNetwork>>
    fun observeDiscoveredCount(): Flow<Int>
    fun getConnectionStatus(): WifiConnectionStatus
    fun requiredScanPermissions(): Array<String>
    fun hasScanPermissions(): Boolean
    suspend fun scanNearbyNetworks(): ScanOutcome
    suspend fun verifyOwnerAuthorizedConnection(
        ssid: String,
        passphrase: String,
        useWpa3: Boolean
    ): OwnerVerificationResult
}

interface WordlistRepository {
    fun observeWordlists(): Flow<List<WordlistFile>>
    fun observeWordlistCount(): Flow<Int>
    fun observeTotalAnalyzedEntriesCount(): Flow<Int>
    fun observeEntriesForWordlist(wordlistId: Long, sortOption: WordlistSortOption): Flow<List<WordlistEntry>>
    suspend fun getWordlistById(wordlistId: Long): WordlistFile?
    suspend fun importWordlistFromUri(
        uri: Uri,
        onProgress: ((linesRead: Int) -> Unit)? = null
    ): WordlistImportResult
    suspend fun reimportWordlist(
        wordlistId: Long,
        onProgress: ((linesRead: Int) -> Unit)? = null
    ): WordlistImportResult
    suspend fun comparePasswordLocally(password: String): WordlistComparisonSummary
    fun decryptEntryPassword(encryptedValue: String): String
    suspend fun deleteWordlist(wordlistId: Long)
    suspend fun clearAllWordlists()
}

interface AssessmentRepository {
    fun observeAssessments(): Flow<List<SecurityAssessment>>
    fun observeAssessmentCount(): Flow<Int>
    fun observeAssessmentById(id: String): Flow<SecurityAssessment?>
    suspend fun getAssessmentById(id: String): SecurityAssessment?
    suspend fun saveAssessment(assessment: SecurityAssessment)
    suspend fun deleteAssessment(id: String)
    suspend fun clearAllAssessments()
    fun decryptAssessmentPassword(encryptedValue: String): String
}

interface ActivityLogRepository {
    fun observeRecentActivity(): Flow<List<ActivityEvent>>
    suspend fun recordEvent(
        type: ActivityEventType,
        titleEn: String,
        titleAr: String,
        detailEn: String,
        detailAr: String
    )
    suspend fun clearAllActivity()
}

interface SettingsRepository {
    val settingsFlow: Flow<AppSettings>
    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setMaskPasswordsByDefault(enabled: Boolean)
    suspend fun setBiometricProtectionEnabled(enabled: Boolean)
    suspend fun setAppLockEnabled(enabled: Boolean)
    suspend fun setAppLockPinHash(pinHash: String?)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setAutoClearClipboard(enabled: Boolean)
    suspend fun setBlockScreenshots(enabled: Boolean)
}
