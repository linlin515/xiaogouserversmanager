package com.xiaogou.serverseyes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY isPinned DESC, sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers ORDER BY isPinned DESC, sortOrder ASC, name ASC")
    suspend fun getAll(): List<ServerEntity>

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM servers")
    suspend fun deleteAll()

    @Query("UPDATE servers SET lastHostname = :hostname, lastOsName = :osName, lastSeenAtMs = :lastSeenAtMs WHERE id = :id")
    suspend fun updateRuntimeMeta(id: String, hostname: String, osName: String, lastSeenAtMs: Long)

    @Query("UPDATE servers SET trustedHostFingerprint = :fingerprint WHERE id = :id")
    suspend fun updateFingerprint(id: String, fingerprint: String)

    @Query("UPDATE servers SET isPinned = :pinned WHERE id = :id")
    suspend fun updatePinned(id: String, pinned: Boolean)

    @Query("UPDATE servers SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean)
}

@Dao
interface MetricSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: MetricSampleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(samples: List<MetricSampleEntity>)

    @Query("SELECT * FROM metric_samples WHERE serverId = :serverId AND timestampMs >= :sinceMs ORDER BY timestampMs ASC")
    fun observeRecent(serverId: String, sinceMs: Long): Flow<List<MetricSampleEntity>>

    @Query("SELECT * FROM metric_samples WHERE timestampMs >= :sinceMs ORDER BY timestampMs ASC")
    suspend fun getAllSince(sinceMs: Long): List<MetricSampleEntity>

    @Query("SELECT * FROM metric_samples WHERE serverId = :serverId ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getLatestN(serverId: String, limit: Int): List<MetricSampleEntity>

    @Query("DELETE FROM metric_samples WHERE timestampMs < :beforeMs")
    suspend fun pruneOlderThan(beforeMs: Long)

    @Query("DELETE FROM metric_samples WHERE serverId = :serverId")
    suspend fun deleteForServer(serverId: String)

    @Query("DELETE FROM metric_samples")
    suspend fun deleteAll()
}

@Dao
interface AlertRuleDao {
    @Query("SELECT * FROM alert_rules ORDER BY serverId IS NOT NULL, metricType ASC")
    fun observeAll(): Flow<List<AlertRuleEntity>>

    @Query("SELECT * FROM alert_rules")
    suspend fun getAll(): List<AlertRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AlertRuleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(rules: List<AlertRuleEntity>)

    @Query("DELETE FROM alert_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM alert_rules WHERE serverId = :serverId")
    suspend fun deleteForServer(serverId: String)

    @Query("DELETE FROM alert_rules")
    suspend fun deleteAll()
}

@Dao
interface AlertEventDao {
    @Query("SELECT * FROM alert_events WHERE hidden = 0 ORDER BY timestampMs DESC LIMIT 200")
    fun observeRecent(): Flow<List<AlertEventEntity>>

    @Query("SELECT * FROM alert_events WHERE hidden = 0 ORDER BY timestampMs DESC LIMIT 500")
    suspend fun getAllVisible(): List<AlertEventEntity>

    @Query("SELECT * FROM alert_events WHERE serverId = :serverId AND metricType = :metricType ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getLatestFor(serverId: String, metricType: String): AlertEventEntity?

    @Query("SELECT * FROM alert_events WHERE serverId = :serverId AND metricType = :metricType AND isResolved = 0 ORDER BY timestampMs DESC")
    suspend fun getActiveUnresolvedFor(serverId: String, metricType: String): List<AlertEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AlertEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(events: List<AlertEventEntity>)

    @Query("UPDATE alert_events SET isResolved = 1, resolvedAtMs = :resolvedAtMs WHERE serverId = :serverId AND metricType = :metricType AND isResolved = 0")
    suspend fun markResolvedFor(serverId: String, metricType: String, resolvedAtMs: Long)

    @Query("UPDATE alert_events SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: String)

    @Query("UPDATE alert_events SET acknowledged = 1 WHERE hidden = 0")
    suspend fun acknowledgeAll()

    @Query("UPDATE alert_events SET hidden = 1, acknowledged = 1")
    suspend fun softClearAll()

    @Query("DELETE FROM alert_events WHERE timestampMs < :beforeMs")
    suspend fun pruneOlderThan(beforeMs: Long)

    @Query("DELETE FROM alert_events WHERE serverId = :serverId")
    suspend fun deleteForServer(serverId: String)

    @Query("DELETE FROM alert_events")
    suspend fun clearAllHard()
}
