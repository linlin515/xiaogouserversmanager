package com.xiaogou.serverseyes.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val groupName: String,
    val host: String,
    val collectionMode: String,
    val sshPort: Int,
    val sshUsername: String,
    val sshAuthType: String,
    val encryptedSshPassword: String,
    val encryptedSshPrivateKey: String,
    val encryptedSshKeyPassphrase: String,
    val trustedHostFingerprint: String?,
    val agentUrl: String,
    val encryptedAgentToken: String,
    val sortOrder: Int,
    val isPinned: Boolean,
    val enabled: Boolean,
    val timeoutSeconds: Int = 10,
    val jumpHostEnabled: Boolean = false,
    val jumpHost: String = "",
    val jumpPort: Int = 22,
    val jumpUsername: String = "root",
    val jumpAuthType: String = "PASSWORD",
    val encryptedJumpPassword: String = "",
    val encryptedJumpPrivateKey: String = "",
    val encryptedJumpKeyPassphrase: String = "",
    val lastHostname: String = "",
    val lastOsName: String = "",
    val lastSeenAtMs: Long = 0L
)

@Entity(
    tableName = "metric_samples",
    indices = [Index(value = ["serverId", "timestampMs"])]
)
data class MetricSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String,
    val timestampMs: Long,
    val cpuPercent: Double,
    val memPercent: Double,
    val diskMaxPercent: Double,
    val netRxBytesPerSec: Long,
    val netTxBytesPerSec: Long,
    val load1: Double,
    val sourceUsed: String
)

@Entity(tableName = "alert_rules")
data class AlertRuleEntity(
    @PrimaryKey val id: String,
    val serverId: String?,
    val metricType: String,
    val warningThreshold: Double,
    val criticalThreshold: Double,
    val cooldownMinutes: Int,
    val enabled: Boolean
)

@Entity(
    tableName = "alert_events",
    indices = [
        Index(value = ["timestampMs"]),
        Index(value = ["serverId", "metricType"]),
        Index(value = ["serverId", "metricType", "isResolved"])
    ]
)
data class AlertEventEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val serverName: String,
    val metricType: String,
    val severity: String,
    val currentValue: Double,
    val thresholdValue: Double,
    val message: String,
    val timestampMs: Long,
    val acknowledged: Boolean,
    val isResolved: Boolean = false,
    val resolvedAtMs: Long? = null,
    val hidden: Boolean = false
)
