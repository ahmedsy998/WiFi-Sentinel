package com.wifisentinel.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WordlistFileEntity::class,
        WordlistEntryEntity::class,
        SecurityAssessmentEntity::class,
        ActivityLogEntity::class,
        DiscoveredNetworkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WifiSentinelDatabase : RoomDatabase() {
    abstract fun wordlistDao(): WordlistDao
    abstract fun assessmentDao(): AssessmentDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun networkDao(): NetworkDao

    companion object {
        const val DATABASE_NAME = "wifi_sentinel_local.db"

        fun create(context: Context): WifiSentinelDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                WifiSentinelDatabase::class.java,
                DATABASE_NAME
            ).fallbackToDestructiveMigration(dropAllTables = true).build()
        }
    }
}
