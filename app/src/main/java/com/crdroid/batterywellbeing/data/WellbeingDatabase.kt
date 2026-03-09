package com.crdroid.batterywellbeing.data

import androidx.room.*

@Entity(tableName = "daily_usage")
data class DailyUsage(
    @PrimaryKey val dateLong: Long, // Midnight timestamp
    val totalScreenTimeMs: Long,
    val totalUnlocks: Int,
    val totalNotifications: Int
)

@Entity(tableName = "app_usage", primaryKeys = ["dateLong", "packageName"])
data class AppUsage(
    val dateLong: Long,
    val packageName: String,
    val appCategory: Int, // ApplicationInfo.category (e.g., CATEGORY_GAME)
    val screenTimeMs: Long,
    val opens: Int,
    val notifications: Int
)

@Dao
interface UsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyUsage(usage: DailyUsage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppUsages(usages: List<AppUsage>)

    @Query("SELECT * FROM daily_usage WHERE dateLong BETWEEN :startDate AND :endDate ORDER BY dateLong ASC")
    suspend fun getUsageHistory(startDate: Long, endDate: Long): List<DailyUsage>

    @Query("SELECT * FROM app_usage WHERE packageName = :pkg AND dateLong = :date")
    suspend fun getAppUsageForDate(pkg: String, date: Long): AppUsage?
}

@Database(entities = [DailyUsage::class, AppUsage::class], version = 1)
abstract class WellbeingDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
}
