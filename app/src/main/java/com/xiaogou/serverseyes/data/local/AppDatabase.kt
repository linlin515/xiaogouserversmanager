package com.xiaogou.serverseyes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.xiaogou.serverseyes.core.model.AlertMetricType

@Database(
    entities = [
        ServerEntity::class,
        MetricSampleEntity::class,
        AlertRuleEntity::class,
        AlertEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun metricSampleDao(): MetricSampleDao
    abstract fun alertRuleDao(): AlertRuleDao
    abstract fun alertEventDao(): AlertEventDao

    companion object {
        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "server_eyes.db"
            ).build()
        }

        val DEFAULT_RULES = listOf(
            AlertRuleEntity(
                id = "global_cpu",
                serverId = null,
                metricType = AlertMetricType.CPU.name,
                warningThreshold = 80.0,
                criticalThreshold = 95.0,
                cooldownMinutes = 10,
                enabled = true
            ),
            AlertRuleEntity(
                id = "global_memory",
                serverId = null,
                metricType = AlertMetricType.MEMORY.name,
                warningThreshold = 85.0,
                criticalThreshold = 95.0,
                cooldownMinutes = 10,
                enabled = true
            ),
            AlertRuleEntity(
                id = "global_disk",
                serverId = null,
                metricType = AlertMetricType.DISK.name,
                warningThreshold = 85.0,
                criticalThreshold = 95.0,
                cooldownMinutes = 30,
                enabled = true
            ),
            AlertRuleEntity(
                id = "global_offline",
                serverId = null,
                metricType = AlertMetricType.OFFLINE.name,
                warningThreshold = 1.0,
                criticalThreshold = 2.0,
                cooldownMinutes = 5,
                enabled = true
            )
        )
    }
}
