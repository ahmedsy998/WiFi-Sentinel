package com.wifisentinel

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wifisentinel.data.database.ActivityLogEntity
import com.wifisentinel.data.database.SecurityAssessmentEntity
import com.wifisentinel.data.database.WifiSentinelDatabase
import com.wifisentinel.data.database.WordlistEntryEntity
import com.wifisentinel.data.database.WordlistFileEntity
import com.wifisentinel.data.preferences.SettingsDataStore
import com.wifisentinel.domain.model.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WifiSentinelDatabaseAndDataStoreTest {

    private lateinit var context: Context
    private lateinit var db: WifiSentinelDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WifiSentinelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `wordlist CRUD, hash lookup, and cascade deletion work reliably`() = runTest {
        val wordlistDao = db.wordlistDao()
        val id = wordlistDao.insertWordlist(
            WordlistFileEntity(
                displayName = "audit_sample.txt",
                sourceUri = "content://test/audit_sample.txt",
                fileSizeBytes = 1024L,
                totalLinesRead = 2,
                validEntryCount = 2,
                emptyEntryCount = 0,
                duplicateEntryCount = 0,
                malformedEntryCount = 0,
                averageScore = 45,
                weakEntryCount = 1,
                importedAt = 1000L
            )
        )

        wordlistDao.insertEntries(
            listOf(
                WordlistEntryEntity(
                    wordlistId = id,
                    maskedValue = "p•••••••3",
                    encryptedValue = "enc1",
                    sha256Hash = "hash_exact_1",
                    lowerSha256Hash = "hash_lower_1",
                    length = 11,
                    strengthScore = 18,
                    strengthClassName = "VERY_WEAK",
                    estimatedEntropyBits = 12.0,
                    weaknessCodesCsv = "COMMON_WIFI_DEFAULT",
                    analyzedAt = 1000L
                ),
                WordlistEntryEntity(
                    wordlistId = id,
                    maskedValue = "K•••••••••!",
                    encryptedValue = "enc2",
                    sha256Hash = "hash_exact_2",
                    lowerSha256Hash = "hash_lower_2",
                    length = 16,
                    strengthScore = 88,
                    strengthClassName = "STRONG",
                    estimatedEntropyBits = 76.4,
                    weaknessCodesCsv = "",
                    analyzedAt = 1000L
                )
            )
        )

        assertEquals(2, wordlistDao.getTotalEntriesCount())
        val matched = wordlistDao.findWordlistsContainingExactHash("hash_exact_1")
        assertEquals(listOf("audit_sample.txt"), matched)

        // Cascade delete must remove entries too
        wordlistDao.deleteWordlistById(id)
        assertEquals(0, wordlistDao.getTotalEntriesCount())
    }

    @Test
    fun `assessments and activity logs persist in Room`() = runTest {
        db.assessmentDao().insertAssessment(
            SecurityAssessmentEntity(
                id = "WS-1234ABCD",
                networkSsid = "OwnerLab5G",
                networkBssid = "AA:BB:CC:DD:EE:FF",
                securityProtocolName = "WPA3",
                frequencyMhz = 5180,
                ownerAuthorizationConfirmed = true,
                encryptedPassword = null,
                maskedPassword = "K•••••••!",
                passwordLength = 16,
                passwordStrengthScore = 92,
                passwordStrengthClassName = "VERY_STRONG",
                estimatedEntropyBits = 84.5,
                wordlistMatchFound = false,
                wordlistEntriesChecked = 100,
                matchedWordlistNamesSerialized = "",
                overallClassificationName = "STRONG",
                identifiedWeaknessesSerialized = "",
                recommendedImprovementsSerialized = "Rotate periodically",
                verificationStatusName = "NOT_VERIFIED",
                verificationMethod = null,
                verifiedAt = null,
                createdAt = 2000L
            )
        )
        db.activityLogDao().insertEvent(
            ActivityLogEntity(
                typeName = "ASSESSMENT_SAVED",
                titleEn = "Saved",
                titleAr = "تم الحفظ",
                detailEn = "WS-1234ABCD",
                detailAr = "WS-1234ABCD",
                timestamp = 2000L
            )
        )

        assertEquals(1, db.assessmentDao().observeAssessmentCount().first())
        assertEquals("OwnerLab5G", db.assessmentDao().getAssessmentById("WS-1234ABCD")?.networkSsid)
        assertEquals(1, db.activityLogDao().observeRecentActivity().first().size)
    }

    @Test
    fun `DataStore preferences persist dark theme, Arabic language, and privacy toggles`() = runTest {
        val tempFile = File.createTempFile("sentinel_prefs_test", ".preferences_pb", context.cacheDir)
        val ds = PreferenceDataStoreFactory.create(scope = backgroundScope) { tempFile }
        val settingsStore = SettingsDataStore(ds)

        val defaults = settingsStore.settingsFlow.first()
        assertTrue(defaults.darkTheme)
        assertEquals(AppLanguage.ENGLISH, defaults.language)
        assertTrue(defaults.maskPasswordsByDefault)

        settingsStore.setDarkTheme(false)
        settingsStore.setLanguage(AppLanguage.ARABIC)
        settingsStore.setAppLockEnabled(true)
        settingsStore.setAutoClearClipboard(false)

        val updated = settingsStore.settingsFlow.first()
        assertFalse(updated.darkTheme)
        assertEquals(AppLanguage.ARABIC, updated.language)
        assertTrue(updated.appLockEnabled)
        assertFalse(updated.autoClearClipboard)

        tempFile.delete()
    }
}
