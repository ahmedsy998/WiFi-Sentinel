package com.wifisentinel.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordlistDao {
    @Query("SELECT * FROM wordlist_files ORDER BY importedAt DESC")
    fun observeAllWordlists(): Flow<List<WordlistFileEntity>>

    @Query("SELECT COUNT(*) FROM wordlist_files")
    fun observeWordlistCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM wordlist_entries")
    fun observeTotalEntryCount(): Flow<Int>

    @Query("SELECT * FROM wordlist_files WHERE id = :id")
    suspend fun getWordlistById(id: Long): WordlistFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordlist(file: WordlistFileEntity): Long

    @Update
    suspend fun updateWordlist(file: WordlistFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<WordlistEntryEntity>)

    @Query("SELECT * FROM wordlist_entries WHERE wordlistId = :wordlistId ORDER BY strengthScore ASC")
    fun observeEntriesByScoreAsc(wordlistId: Long): Flow<List<WordlistEntryEntity>>

    @Query("SELECT * FROM wordlist_entries WHERE wordlistId = :wordlistId ORDER BY strengthScore DESC")
    fun observeEntriesByScoreDesc(wordlistId: Long): Flow<List<WordlistEntryEntity>>

    @Query("SELECT * FROM wordlist_entries WHERE wordlistId = :wordlistId ORDER BY length ASC")
    fun observeEntriesByLengthAsc(wordlistId: Long): Flow<List<WordlistEntryEntity>>

    @Query("SELECT * FROM wordlist_entries WHERE wordlistId = :wordlistId ORDER BY length DESC")
    fun observeEntriesByLengthDesc(wordlistId: Long): Flow<List<WordlistEntryEntity>>

    @Query("SELECT * FROM wordlist_entries WHERE wordlistId = :wordlistId ORDER BY analyzedAt DESC")
    fun observeEntriesByAnalyzedDesc(wordlistId: Long): Flow<List<WordlistEntryEntity>>

    @Query("SELECT COUNT(*) FROM wordlist_entries")
    suspend fun getTotalEntriesCount(): Int

    @Query(
        """
        SELECT DISTINCT wf.displayName
        FROM wordlist_entries we
        INNER JOIN wordlist_files wf ON we.wordlistId = wf.id
        WHERE we.sha256Hash = :exactHash
        """
    )
    suspend fun findWordlistsContainingExactHash(exactHash: String): List<String>

    @Query(
        """
        SELECT DISTINCT wf.displayName
        FROM wordlist_entries we
        INNER JOIN wordlist_files wf ON we.wordlistId = wf.id
        WHERE we.lowerSha256Hash = :lowerHash
        """
    )
    suspend fun findWordlistsContainingLowerHash(lowerHash: String): List<String>

    @Query("DELETE FROM wordlist_files WHERE id = :wordlistId")
    suspend fun deleteWordlistById(wordlistId: Long)

    @Query("DELETE FROM wordlist_files")
    suspend fun deleteAllWordlists()
}

@Dao
interface AssessmentDao {
    @Query("SELECT * FROM security_assessments ORDER BY createdAt DESC")
    fun observeAllAssessments(): Flow<List<SecurityAssessmentEntity>>

    @Query("SELECT COUNT(*) FROM security_assessments")
    fun observeAssessmentCount(): Flow<Int>

    @Query("SELECT * FROM security_assessments WHERE id = :id")
    fun observeAssessmentById(id: String): Flow<SecurityAssessmentEntity?>

    @Query("SELECT * FROM security_assessments WHERE id = :id")
    suspend fun getAssessmentById(id: String): SecurityAssessmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessment(assessment: SecurityAssessmentEntity)

    @Query("DELETE FROM security_assessments WHERE id = :id")
    suspend fun deleteAssessmentById(id: String)

    @Query("DELETE FROM security_assessments")
    suspend fun deleteAllAssessments()
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT 50")
    fun observeRecentActivity(): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ActivityLogEntity)

    @Query("DELETE FROM activity_logs")
    suspend fun deleteAllEvents()
}

@Dao
interface NetworkDao {
    @Query("SELECT * FROM discovered_networks ORDER BY rssiDbm DESC")
    fun observeDiscoveredNetworks(): Flow<List<DiscoveredNetworkEntity>>

    @Query("SELECT COUNT(*) FROM discovered_networks")
    fun observeDiscoveredCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(networks: List<DiscoveredNetworkEntity>)

    @Query("DELETE FROM discovered_networks")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(networks: List<DiscoveredNetworkEntity>) {
        deleteAll()
        if (networks.isNotEmpty()) {
            insertAll(networks)
        }
    }
}
