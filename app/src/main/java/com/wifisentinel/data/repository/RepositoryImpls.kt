package com.wifisentinel.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.wifisentinel.data.database.ActivityLogDao
import com.wifisentinel.data.database.ActivityLogEntity
import com.wifisentinel.data.database.AssessmentDao
import com.wifisentinel.data.database.DiscoveredNetworkEntity
import com.wifisentinel.data.database.NetworkDao
import com.wifisentinel.data.database.SecurityAssessmentEntity
import com.wifisentinel.data.database.WordlistDao
import com.wifisentinel.data.database.WordlistEntryEntity
import com.wifisentinel.data.database.WordlistFileEntity
import com.wifisentinel.data.local.OwnerVerificationResult
import com.wifisentinel.data.local.WifiScannerDataSource
import com.wifisentinel.data.preferences.SettingsDataStore
import com.wifisentinel.domain.model.ActivityEvent
import com.wifisentinel.domain.model.ActivityEventType
import com.wifisentinel.domain.model.AppLanguage
import com.wifisentinel.domain.model.AppSettings
import com.wifisentinel.domain.model.AssessmentClassification
import com.wifisentinel.domain.model.PasswordStrengthClass
import com.wifisentinel.domain.model.ScanOutcome
import com.wifisentinel.domain.model.SecurityAssessment
import com.wifisentinel.domain.model.VerificationStatus
import com.wifisentinel.domain.model.WeaknessCode
import com.wifisentinel.domain.model.WifiConnectionStatus
import com.wifisentinel.domain.model.WifiNetwork
import com.wifisentinel.domain.model.WifiSecurityType
import com.wifisentinel.domain.model.WordlistComparisonSummary
import com.wifisentinel.domain.model.WordlistEntry
import com.wifisentinel.domain.model.WordlistFile
import com.wifisentinel.domain.model.WordlistImportResult
import com.wifisentinel.domain.model.WordlistSortOption
import com.wifisentinel.domain.repository.ActivityLogRepository
import com.wifisentinel.domain.repository.AssessmentRepository
import com.wifisentinel.domain.repository.SettingsRepository
import com.wifisentinel.domain.repository.WifiRepository
import com.wifisentinel.domain.repository.WordlistRepository
import com.wifisentinel.security.passwordanalysis.PasswordStrengthAnalyzer
import com.wifisentinel.security.securestorage.SecureDataCipher
import com.wifisentinel.security.validation.WordlistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WifiRepositoryImpl(
    private val scannerDataSource: WifiScannerDataSource,
    private val networkDao: NetworkDao,
    private val activityLogRepository: ActivityLogRepository
) : WifiRepository {

    override fun observeDiscoveredNetworks(): Flow<List<WifiNetwork>> =
        networkDao.observeDiscoveredNetworks().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeDiscoveredCount(): Flow<Int> = networkDao.observeDiscoveredCount()

    override fun getConnectionStatus(): WifiConnectionStatus = scannerDataSource.getConnectionStatus()

    override fun requiredScanPermissions(): Array<String> = scannerDataSource.requiredScanPermissions()

    override fun hasScanPermissions(): Boolean = scannerDataSource.hasScanPermissions()

    override suspend fun scanNearbyNetworks(): ScanOutcome {
        val outcome = scannerDataSource.triggerPassiveScan()
        when (outcome) {
            is ScanOutcome.Success -> {
                networkDao.replaceAll(outcome.networks.map { it.toEntity() })
                activityLogRepository.recordEvent(
                    type = ActivityEventType.NETWORK_SCAN,
                    titleEn = "Passive Wi-Fi Scan Completed",
                    titleAr = "اكتمل فحص الواي فاي السلبي",
                    detailEn = "Observed ${outcome.networks.size} nearby wireless networks.",
                    detailAr = "تم رصد ${outcome.networks.size} شبكة لاسلكية قريبة."
                )
            }
            is ScanOutcome.Throttled -> {
                if (outcome.cachedNetworks.isNotEmpty()) {
                    networkDao.replaceAll(outcome.cachedNetworks.map { it.toEntity() })
                }
                activityLogRepository.recordEvent(
                    type = ActivityEventType.NETWORK_SCAN,
                    titleEn = "Scan Throttled by Android OS",
                    titleAr = "قيّد النظام طلب الفحص",
                    detailEn = "Loaded ${outcome.cachedNetworks.size} cached networks from OS.",
                    detailAr = "تم عرض ${outcome.cachedNetworks.size} شبكة من ذاكرة النظام."
                )
            }
            else -> Unit
        }
        return outcome
    }

    override suspend fun verifyOwnerAuthorizedConnection(
        ssid: String,
        passphrase: String,
        useWpa3: Boolean
    ): OwnerVerificationResult {
        val result = scannerDataSource.verifyOwnerAuthorizedConnection(ssid, passphrase, useWpa3)
        if (result is OwnerVerificationResult.Verified) {
            activityLogRepository.recordEvent(
                type = ActivityEventType.AUTH_VERIFIED,
                titleEn = "Owner Authentication Verified",
                titleAr = "تم التحقق من مصادقة المالك",
                detailEn = "Verified authorized profile \"$ssid\" via ${result.method}.",
                detailAr = "تم التحقق من الشبكة المصرح بها \"$ssid\" بنجاح."
            )
        }
        return result
    }

    private fun DiscoveredNetworkEntity.toDomain() = WifiNetwork(
        bssid = bssid,
        ssid = ssid,
        isHiddenSsid = isHiddenSsid,
        rssiDbm = rssiDbm,
        frequencyMhz = frequencyMhz,
        channel = channel,
        capabilities = capabilities,
        securityType = runCatching { WifiSecurityType.valueOf(securityTypeName) }.getOrDefault(WifiSecurityType.UNKNOWN),
        lastObservedAt = lastObservedAt
    )

    private fun WifiNetwork.toEntity() = DiscoveredNetworkEntity(
        bssid = bssid,
        ssid = ssid,
        isHiddenSsid = isHiddenSsid,
        rssiDbm = rssiDbm,
        frequencyMhz = frequencyMhz,
        channel = channel,
        capabilities = capabilities,
        securityTypeName = securityType.name,
        lastObservedAt = lastObservedAt
    )
}

class WordlistRepositoryImpl(
    private val context: Context,
    private val wordlistDao: WordlistDao,
    private val parser: WordlistParser,
    private val analyzer: PasswordStrengthAnalyzer,
    private val cipher: SecureDataCipher,
    private val activityLogRepository: ActivityLogRepository
) : WordlistRepository {

    override fun observeWordlists(): Flow<List<WordlistFile>> =
        wordlistDao.observeAllWordlists().map { list -> list.map { it.toDomain() } }

    override fun observeWordlistCount(): Flow<Int> = wordlistDao.observeWordlistCount()

    override fun observeTotalAnalyzedEntriesCount(): Flow<Int> = wordlistDao.observeTotalEntryCount()

    override fun observeEntriesForWordlist(
        wordlistId: Long,
        sortOption: WordlistSortOption
    ): Flow<List<WordlistEntry>> {
        val flow = when (sortOption) {
            WordlistSortOption.WEAKEST_FIRST -> wordlistDao.observeEntriesByScoreAsc(wordlistId)
            WordlistSortOption.STRONGEST_FIRST -> wordlistDao.observeEntriesByScoreDesc(wordlistId)
            WordlistSortOption.SHORTEST_FIRST -> wordlistDao.observeEntriesByLengthAsc(wordlistId)
            WordlistSortOption.LONGEST_FIRST -> wordlistDao.observeEntriesByLengthDesc(wordlistId)
            WordlistSortOption.RECENTLY_ANALYZED -> wordlistDao.observeEntriesByAnalyzedDesc(wordlistId)
        }
        return flow.map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getWordlistById(wordlistId: Long): WordlistFile? =
        wordlistDao.getWordlistById(wordlistId)?.toDomain()

    override suspend fun importWordlistFromUri(
        uri: Uri,
        onProgress: ((linesRead: Int) -> Unit)?
    ): WordlistImportResult = withContext(Dispatchers.IO) {
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some document providers do not grant persistable permissions; still readable now.
            }

            val (displayName, fileSize) = queryFileMetadata(uri)
            if (!displayName.endsWith(".txt", ignoreCase = true)) {
                val mime = context.contentResolver.getType(uri)
                if (mime != null && !mime.startsWith("text/")) {
                    return@withContext WordlistImportResult.Error(
                        "Unsupported file format. Please select a plain UTF-8 .txt wordlist file."
                    )
                }
            }

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext WordlistImportResult.Error(
                    "Unable to open selected file. Verify file permissions and try again."
                )

            val now = System.currentTimeMillis()
            val placeholderId = wordlistDao.insertWordlist(
                WordlistFileEntity(
                    displayName = displayName,
                    sourceUri = uri.toString(),
                    fileSizeBytes = fileSize,
                    totalLinesRead = 0,
                    validEntryCount = 0,
                    emptyEntryCount = 0,
                    duplicateEntryCount = 0,
                    malformedEntryCount = 0,
                    averageScore = 0,
                    weakEntryCount = 0,
                    importedAt = now
                )
            )

            var scoreSum = 0L
            var weakCount = 0
            var processedValid = 0

            val stats = try {
                inputStream.use { stream ->
                    parser.parseStream(
                        inputStream = stream,
                        onProgress = onProgress
                    ) { chunk ->
                        val entities = chunk.map { rawPassword ->
                            val analysis = analyzer.analyze(rawPassword, nowMillis = now)
                            scoreSum += analysis.score
                            if (analysis.strengthClass == PasswordStrengthClass.VERY_WEAK ||
                                analysis.strengthClass == PasswordStrengthClass.WEAK
                            ) {
                                weakCount++
                            }
                            processedValid++
                            WordlistEntryEntity(
                                wordlistId = placeholderId,
                                maskedValue = analyzer.maskPassword(rawPassword),
                                encryptedValue = cipher.encrypt(rawPassword),
                                sha256Hash = analyzer.sha256(rawPassword),
                                lowerSha256Hash = analyzer.sha256(rawPassword.lowercase()),
                                length = analysis.length,
                                strengthScore = analysis.score,
                                strengthClassName = analysis.strengthClass.name,
                                estimatedEntropyBits = analysis.estimatedEntropyBits,
                                weaknessCodesCsv = analysis.weaknessCodes.joinToString(",") { it.name },
                                analyzedAt = analysis.analyzedAt
                            )
                        }
                        wordlistDao.insertEntries(entities)
                    }
                }
            } catch (e: Exception) {
                wordlistDao.deleteWordlistById(placeholderId)
                return@withContext WordlistImportResult.Error(
                    "Failed to parse wordlist: ${e.message ?: "unexpected read error"}"
                )
            }

            if (stats.validEntryCount == 0) {
                wordlistDao.deleteWordlistById(placeholderId)
                return@withContext WordlistImportResult.Error(
                    "No valid password entries found in \"$displayName\" (empty: ${stats.emptyEntryCount}, malformed: ${stats.malformedEntryCount})."
                )
            }

            val avgScore = if (processedValid > 0) (scoreSum / processedValid).toInt() else 0
            val finalEntity = WordlistFileEntity(
                id = placeholderId,
                displayName = displayName,
                sourceUri = uri.toString(),
                fileSizeBytes = fileSize,
                totalLinesRead = stats.totalLinesRead,
                validEntryCount = stats.validEntryCount,
                emptyEntryCount = stats.emptyEntryCount,
                duplicateEntryCount = stats.duplicateEntryCount,
                malformedEntryCount = stats.malformedEntryCount,
                averageScore = avgScore,
                weakEntryCount = weakCount,
                importedAt = now
            )
            wordlistDao.updateWordlist(finalEntity)

            activityLogRepository.recordEvent(
                type = ActivityEventType.WORDLIST_IMPORT,
                titleEn = "Wordlist Imported & Analyzed",
                titleAr = "تم استيراد وتحليل قائمة الكلمات",
                detailEn = "\"$displayName\" — ${stats.validEntryCount} unique entries (${stats.duplicateEntryCount} duplicates removed).",
                detailAr = "\"$displayName\" — ${stats.validEntryCount} إدخال فريد (تمت إزالة ${stats.duplicateEntryCount} مكرر)."
            )

            WordlistImportResult.Success(finalEntity.toDomain())
        } catch (e: Exception) {
            WordlistImportResult.Error(e.message ?: "Unable to import wordlist file.")
        }
    }

    override suspend fun reimportWordlist(
        wordlistId: Long,
        onProgress: ((linesRead: Int) -> Unit)?
    ): WordlistImportResult = withContext(Dispatchers.IO) {
        val existing = wordlistDao.getWordlistById(wordlistId)
            ?: return@withContext WordlistImportResult.Error("Wordlist record no longer exists.")
        val uri = Uri.parse(existing.sourceUri)
        val result = importWordlistFromUri(uri, onProgress)
        if (result is WordlistImportResult.Success) {
            wordlistDao.deleteWordlistById(wordlistId)
        }
        result
    }

    override suspend fun comparePasswordLocally(password: String): WordlistComparisonSummary =
        withContext(Dispatchers.IO) {
            val total = wordlistDao.getTotalEntriesCount()
            if (total == 0 || password.isEmpty()) {
                return@withContext WordlistComparisonSummary(
                    totalEntriesCompared = total,
                    exactMatchFound = false,
                    caseInsensitiveMatchFound = false,
                    matchedWordlistNames = emptyList()
                )
            }
            val exactHash = analyzer.sha256(password)
            val lowerHash = analyzer.sha256(password.lowercase())
            val exactMatches = wordlistDao.findWordlistsContainingExactHash(exactHash)
            val lowerMatches = wordlistDao.findWordlistsContainingLowerHash(lowerHash)
            val combined = (exactMatches + lowerMatches).distinct()

            WordlistComparisonSummary(
                totalEntriesCompared = total,
                exactMatchFound = exactMatches.isNotEmpty(),
                caseInsensitiveMatchFound = lowerMatches.isNotEmpty(),
                matchedWordlistNames = combined
            )
        }

    override fun decryptEntryPassword(encryptedValue: String): String =
        runCatching { cipher.decrypt(encryptedValue) }.getOrDefault("")

    override suspend fun deleteWordlist(wordlistId: Long) = withContext(Dispatchers.IO) {
        val existing = wordlistDao.getWordlistById(wordlistId)
        wordlistDao.deleteWordlistById(wordlistId)
        if (existing != null) {
            activityLogRepository.recordEvent(
                type = ActivityEventType.WORDLIST_DELETED,
                titleEn = "Wordlist Removed",
                titleAr = "تم حذف قائمة الكلمات",
                detailEn = "Deleted \"${existing.displayName}\" from local storage.",
                detailAr = "تم حذف \"${existing.displayName}\" من التخزين المحلي."
            )
        }
    }

    override suspend fun clearAllWordlists() = withContext(Dispatchers.IO) {
        wordlistDao.deleteAllWordlists()
        activityLogRepository.recordEvent(
            type = ActivityEventType.DATA_CLEARED,
            titleEn = "All Wordlists Cleared",
            titleAr = "تم مسح جميع قوائم الكلمات",
            detailEn = "Removed all imported wordlists and entries from local Room storage.",
            detailAr = "تمت إزالة جميع القوائم والمدخلات من قاعدة البيانات المحلية."
        )
    }

    private fun queryFileMetadata(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "wordlist.txt"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { name = it }
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return name to size
    }

    private fun WordlistFileEntity.toDomain() = WordlistFile(
        id = id,
        displayName = displayName,
        sourceUri = sourceUri,
        fileSizeBytes = fileSizeBytes,
        totalLinesRead = totalLinesRead,
        validEntryCount = validEntryCount,
        emptyEntryCount = emptyEntryCount,
        duplicateEntryCount = duplicateEntryCount,
        malformedEntryCount = malformedEntryCount,
        averageScore = averageScore,
        weakEntryCount = weakEntryCount,
        importedAt = importedAt
    )

    private fun WordlistEntryEntity.toDomain() = WordlistEntry(
        id = id,
        wordlistId = wordlistId,
        maskedValue = maskedValue,
        encryptedValue = encryptedValue,
        sha256Hash = sha256Hash,
        length = length,
        strengthScore = strengthScore,
        strengthClass = runCatching { PasswordStrengthClass.valueOf(strengthClassName) }
            .getOrDefault(PasswordStrengthClass.VERY_WEAK),
        estimatedEntropyBits = estimatedEntropyBits,
        weaknessCodes = weaknessCodesCsv
            .split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { WeaknessCode.valueOf(it) }.getOrNull() },
        analyzedAt = analyzedAt
    )
}

class AssessmentRepositoryImpl(
    private val assessmentDao: AssessmentDao,
    private val cipher: SecureDataCipher,
    private val activityLogRepository: ActivityLogRepository
) : AssessmentRepository {

    override fun observeAssessments(): Flow<List<SecurityAssessment>> =
        assessmentDao.observeAllAssessments().map { list -> list.map { it.toDomain() } }

    override fun observeAssessmentCount(): Flow<Int> = assessmentDao.observeAssessmentCount()

    override fun observeAssessmentById(id: String): Flow<SecurityAssessment?> =
        assessmentDao.observeAssessmentById(id).map { it?.toDomain() }

    override suspend fun getAssessmentById(id: String): SecurityAssessment? =
        assessmentDao.getAssessmentById(id)?.toDomain()

    override suspend fun saveAssessment(assessment: SecurityAssessment) = withContext(Dispatchers.IO) {
        assessmentDao.insertAssessment(assessment.toEntity())
        activityLogRepository.recordEvent(
            type = ActivityEventType.ASSESSMENT_SAVED,
            titleEn = "Security Assessment Saved",
            titleAr = "تم حفظ تقييم الأمان",
            detailEn = "Profile \"${assessment.networkSsid}\" (${assessment.id}) — ${assessment.overallClassification.name}.",
            detailAr = "الشبكة \"${assessment.networkSsid}\" (${assessment.id}) — ${assessment.overallClassification.name}."
        )
    }

    override suspend fun deleteAssessment(id: String) = withContext(Dispatchers.IO) {
        assessmentDao.deleteAssessmentById(id)
    }

    override suspend fun clearAllAssessments() = withContext(Dispatchers.IO) {
        assessmentDao.deleteAllAssessments()
        activityLogRepository.recordEvent(
            type = ActivityEventType.DATA_CLEARED,
            titleEn = "Assessment History Cleared",
            titleAr = "تم مسح سجل التقييمات",
            detailEn = "Deleted all locally saved security assessments.",
            detailAr = "تم حذف جميع تقارير التقييم المحفوظة محلياً."
        )
    }

    override fun decryptAssessmentPassword(encryptedValue: String): String =
        runCatching { cipher.decrypt(encryptedValue) }.getOrDefault("")

    private fun SecurityAssessmentEntity.toDomain() = SecurityAssessment(
        id = id,
        networkSsid = networkSsid,
        networkBssid = networkBssid,
        securityProtocol = runCatching { WifiSecurityType.valueOf(securityProtocolName) }
            .getOrDefault(WifiSecurityType.UNKNOWN),
        frequencyMhz = frequencyMhz,
        ownerAuthorizationConfirmed = ownerAuthorizationConfirmed,
        encryptedPassword = encryptedPassword,
        maskedPassword = maskedPassword,
        passwordLength = passwordLength,
        passwordStrengthScore = passwordStrengthScore,
        passwordStrengthClass = runCatching { PasswordStrengthClass.valueOf(passwordStrengthClassName) }
            .getOrDefault(PasswordStrengthClass.VERY_WEAK),
        estimatedEntropyBits = estimatedEntropyBits,
        wordlistMatchFound = wordlistMatchFound,
        wordlistEntriesChecked = wordlistEntriesChecked,
        matchedWordlistNames = deserializeList(matchedWordlistNamesSerialized),
        overallClassification = runCatching { AssessmentClassification.valueOf(overallClassificationName) }
            .getOrDefault(AssessmentClassification.WEAK),
        identifiedWeaknesses = deserializeList(identifiedWeaknessesSerialized),
        recommendedImprovements = deserializeList(recommendedImprovementsSerialized),
        verificationStatus = runCatching { VerificationStatus.valueOf(verificationStatusName) }
            .getOrDefault(VerificationStatus.NOT_VERIFIED),
        verificationMethod = verificationMethod,
        verifiedAt = verifiedAt,
        createdAt = createdAt
    )

    private fun SecurityAssessment.toEntity() = SecurityAssessmentEntity(
        id = id,
        networkSsid = networkSsid,
        networkBssid = networkBssid,
        securityProtocolName = securityProtocol.name,
        frequencyMhz = frequencyMhz,
        ownerAuthorizationConfirmed = ownerAuthorizationConfirmed,
        encryptedPassword = encryptedPassword,
        maskedPassword = maskedPassword,
        passwordLength = passwordLength,
        passwordStrengthScore = passwordStrengthScore,
        passwordStrengthClassName = passwordStrengthClass.name,
        estimatedEntropyBits = estimatedEntropyBits,
        wordlistMatchFound = wordlistMatchFound,
        wordlistEntriesChecked = wordlistEntriesChecked,
        matchedWordlistNamesSerialized = serializeList(matchedWordlistNames),
        overallClassificationName = overallClassification.name,
        identifiedWeaknessesSerialized = serializeList(identifiedWeaknesses),
        recommendedImprovementsSerialized = serializeList(recommendedImprovements),
        verificationStatusName = verificationStatus.name,
        verificationMethod = verificationMethod,
        verifiedAt = verifiedAt,
        createdAt = createdAt
    )

    private fun serializeList(items: List<String>): String = items.joinToString(DELIMITER)

    private fun deserializeList(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else raw.split(DELIMITER).filter { it.isNotBlank() }

    companion object {
        private const val DELIMITER = "\u001F"
    }
}

class ActivityLogRepositoryImpl(
    private val activityLogDao: ActivityLogDao
) : ActivityLogRepository {

    override fun observeRecentActivity(): Flow<List<ActivityEvent>> =
        activityLogDao.observeRecentActivity().map { list ->
            list.map { entity ->
                ActivityEvent(
                    id = entity.id,
                    type = runCatching { ActivityEventType.valueOf(entity.typeName) }
                        .getOrDefault(ActivityEventType.NETWORK_SCAN),
                    titleEn = entity.titleEn,
                    titleAr = entity.titleAr,
                    detailEn = entity.detailEn,
                    detailAr = entity.detailAr,
                    timestamp = entity.timestamp
                )
            }
        }

    override suspend fun recordEvent(
        type: ActivityEventType,
        titleEn: String,
        titleAr: String,
        detailEn: String,
        detailAr: String
    ) {
        activityLogDao.insertEvent(
            ActivityLogEntity(
                typeName = type.name,
                titleEn = titleEn,
                titleAr = titleAr,
                detailEn = detailEn,
                detailAr = detailAr,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun clearAllActivity() {
        activityLogDao.deleteAllEvents()
    }
}

class SettingsRepositoryImpl(
    private val settingsDataStore: SettingsDataStore
) : SettingsRepository {
    override val settingsFlow: Flow<AppSettings> = settingsDataStore.settingsFlow
    override suspend fun setDarkTheme(enabled: Boolean) = settingsDataStore.setDarkTheme(enabled)
    override suspend fun setLanguage(language: AppLanguage) = settingsDataStore.setLanguage(language)
    override suspend fun setMaskPasswordsByDefault(enabled: Boolean) = settingsDataStore.setMaskPasswordsByDefault(enabled)
    override suspend fun setBiometricProtectionEnabled(enabled: Boolean) = settingsDataStore.setBiometricProtectionEnabled(enabled)
    override suspend fun setAppLockEnabled(enabled: Boolean) = settingsDataStore.setAppLockEnabled(enabled)
    override suspend fun setAppLockPinHash(pinHash: String?) = settingsDataStore.setAppLockPinHash(pinHash)
    override suspend fun setNotificationsEnabled(enabled: Boolean) = settingsDataStore.setNotificationsEnabled(enabled)
    override suspend fun setAutoClearClipboard(enabled: Boolean) = settingsDataStore.setAutoClearClipboard(enabled)
    override suspend fun setBlockScreenshots(enabled: Boolean) = settingsDataStore.setBlockScreenshots(enabled)
}
