package com.xiaogou.serverseyes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xiaogou.serverseyes.core.model.AlertMetricType

@Database(
    entities = [
        ServerEntity::class,
        MetricSampleEntity::class,
        AlertRuleEntity::class,
        AlertEventEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun metricSampleDao(): MetricSampleDao
    abstract fun alertRuleDao(): AlertRuleDao
    abstract fun alertEventDao(): AlertEventDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alert_events ADD COLUMN isResolved INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alert_events ADD COLUMN resolvedAtMs INTEGER")
                db.execSQL("ALTER TABLE alert_events ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_alert_events_serverId_metricType_isResolved " +
                        "ON alert_events(serverId, metricType, isResolved)"
                )
                db.execSQL(
                    "UPDATE alert_rules SET warningThreshold = 2.0, criticalThreshold = 3.0 " +
                        "WHERE id = 'global_offline' AND warningThreshold = 1.0 AND criticalThreshold = 2.0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE servers ADD COLUMN timeoutSeconds INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE servers ADD COLUMN jumpHostEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE servers ADD COLUMN jumpHost TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE servers ADD COLUMN jumpPort INTEGER NOT NULL DEFAULT 22")
                db.execSQL("ALTER TABLE servers ADD COLUMN jumpUsername TEXT NOT NULL DEFAULT 'root'")
                db.execSQL("ALTER TABLE servers ADD COLUMN jumpAuthType TEXT NOT NULL DEFAULT 'PASSWORD'")
                db.execSQL("ALTER TABLE servers ADD COLUMN encryptedJumpPassword TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE servers ADD COLUMN encryptedJumpPrivateKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE servers ADD COLUMN encryptedJumpKeyPassphrase TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "server_eyes.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
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
                warningThreshold = 2.0,
                criticalThreshold = 3.0,
                cooldownMinutes = 5,
                enabled = true
            )
        )
    }
}
