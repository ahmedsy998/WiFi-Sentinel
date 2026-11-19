package com.wifisentinel.di

import android.content.Context
import com.wifisentinel.data.database.WifiSentinelDatabase
import com.wifisentinel.data.local.ReportExporter
import com.wifisentinel.data.local.WifiScannerDataSource
import com.wifisentinel.data.preferences.SettingsDataStore
import com.wifisentinel.data.preferences.sentinelSettingsDataStore
import com.wifisentinel.data.repository.ActivityLogRepositoryImpl
import com.wifisentinel.data.repository.AssessmentRepositoryImpl
import com.wifisentinel.data.repository.SettingsRepositoryImpl
import com.wifisentinel.data.repository.WifiRepositoryImpl
import com.wifisentinel.data.repository.WordlistRepositoryImpl
import com.wifisentinel.domain.repository.ActivityLogRepository
import com.wifisentinel.domain.repository.AssessmentRepository
import com.wifisentinel.domain.repository.SettingsRepository
import com.wifisentinel.domain.repository.WifiRepository
import com.wifisentinel.domain.repository.WordlistRepository
import com.wifisentinel.domain.usecase.BuildSecurityAssessmentUseCase
import com.wifisentinel.security.passwordanalysis.PasswordStrengthAnalyzer
import com.wifisentinel.security.securestorage.SecureDataCipher
import com.wifisentinel.security.validation.WordlistParser

/**
 * Application-scoped dependency graph providing clean constructor injection across
 * Data, Security, Domain, and Presentation layers.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: WifiSentinelDatabase by lazy { WifiSentinelDatabase.create(appContext) }
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(appContext.sentinelSettingsDataStore) }

    val passwordAnalyzer: PasswordStrengthAnalyzer by lazy { PasswordStrengthAnalyzer() }
    val secureDataCipher: SecureDataCipher by lazy { SecureDataCipher() }
    val wordlistParser: WordlistParser by lazy { WordlistParser() }
    val wifiScannerDataSource: WifiScannerDataSource by lazy { WifiScannerDataSource(appContext) }
    val reportExporter: ReportExporter by lazy { ReportExporter(appContext) }

    val activityLogRepository: ActivityLogRepository by lazy {
        ActivityLogRepositoryImpl(database.activityLogDao())
    }

    val wifiRepository: WifiRepository by lazy {
        WifiRepositoryImpl(
            scannerDataSource = wifiScannerDataSource,
            networkDao = database.networkDao(),
            activityLogRepository = activityLogRepository
        )
    }

    val wordlistRepository: WordlistRepository by lazy {
        WordlistRepositoryImpl(
            context = appContext,
            wordlistDao = database.wordlistDao(),
            parser = wordlistParser,
            analyzer = passwordAnalyzer,
            cipher = secureDataCipher,
            activityLogRepository = activityLogRepository
        )
    }

    val assessmentRepository: AssessmentRepository by lazy {
        AssessmentRepositoryImpl(
            assessmentDao = database.assessmentDao(),
            cipher = secureDataCipher,
            activityLogRepository = activityLogRepository
        )
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(settingsDataStore)
    }

    val buildSecurityAssessmentUseCase: BuildSecurityAssessmentUseCase by lazy {
        BuildSecurityAssessmentUseCase(
            passwordAnalyzer = passwordAnalyzer,
            wordlistRepository = wordlistRepository,
            assessmentRepository = assessmentRepository,
            cipher = secureDataCipher
        )
    }
}
