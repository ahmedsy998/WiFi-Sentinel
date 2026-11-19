package com.wifisentinel.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "wordlist_files")
data class WordlistFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val displayName: String,
    val sourceUri: String,
    val fileSizeBytes: Long,
    val totalLinesRead: Int,
    val validEntryCount: Int,
    val emptyEntryCount: Int,
    val duplicateEntryCount: Int,
    val malformedEntryCount: Int,
    val averageScore: Int,
    val weakEntryCount: Int,
    val importedAt: Long
)

@Entity(
    tableName = "wordlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = WordlistFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["wordlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["wordlistId"]),
        Index(value = ["sha256Hash"]),
        Index(value = ["strengthScore"])
    ]
)
data class WordlistEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val wordlistId: Long,
    val maskedValue: String,
    val encryptedValue: String,
    val sha256Hash: String,
    val lowerSha256Hash: String,
    val length: Int,
    val strengthScore: Int,
    val strengthClassName: String,
    val estimatedEntropyBits: Double,
    val weaknessCodesCsv: String,
    val analyzedAt: Long
)

@Entity(tableName = "security_assessments")
data class SecurityAssessmentEntity(
    @PrimaryKey val id: String,
    val networkSsid: String,
    val networkBssid: String?,
    val securityProtocolName: String,
    val frequencyMhz: Int?,
    val ownerAuthorizationConfirmed: Boolean,
    val encryptedPassword: String?,
    val maskedPassword: String?,
    val passwordLength: Int,
    val passwordStrengthScore: Int,
    val passwordStrengthClassName: String,
    val estimatedEntropyBits: Double,
    val wordlistMatchFound: Boolean,
    val wordlistEntriesChecked: Int,
    val matchedWordlistNamesSerialized: String,
    val overallClassificationName: String,
    val identifiedWeaknessesSerialized: String,
    val recommendedImprovementsSerialized: String,
    val verificationStatusName: String,
    val verificationMethod: String?,
    val verifiedAt: Long?,
    val createdAt: Long
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val typeName: String,
    val titleEn: String,
    val titleAr: String,
    val detailEn: String,
    val detailAr: String,
    val timestamp: Long
)

@Entity(tableName = "discovered_networks")
data class DiscoveredNetworkEntity(
    @PrimaryKey val bssid: String,
    val ssid: String,
    val isHiddenSsid: Boolean,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val capabilities: String,
    val securityTypeName: String,
    val lastObservedAt: Long
)
