package com.wifisentinel.domain.model

data class WordlistFile(
    val id: Long = 0L,
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

data class WordlistEntry(
    val id: Long = 0L,
    val wordlistId: Long,
    val maskedValue: String,
    val encryptedValue: String,
    val sha256Hash: String,
    val length: Int,
    val strengthScore: Int,
    val strengthClass: PasswordStrengthClass,
    val estimatedEntropyBits: Double,
    val weaknessCodes: List<WeaknessCode>,
    val analyzedAt: Long
)

enum class WordlistSortOption {
    WEAKEST_FIRST,
    STRONGEST_FIRST,
    SHORTEST_FIRST,
    LONGEST_FIRST,
    RECENTLY_ANALYZED
}

sealed class WordlistImportResult {
    data class Success(val wordlist: WordlistFile) : WordlistImportResult()
    data class Error(val message: String) : WordlistImportResult()
}
