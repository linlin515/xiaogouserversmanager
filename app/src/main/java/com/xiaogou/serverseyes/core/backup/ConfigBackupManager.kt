package com.xiaogou.serverseyes.core.backup

import com.xiaogou.serverseyes.core.model.AlertEvent
import com.xiaogou.serverseyes.core.model.AlertMetricType
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.AlertSeverity
import com.xiaogou.serverseyes.core.model.CollectionMode
import com.xiaogou.serverseyes.core.model.ServerConfig
import com.xiaogou.serverseyes.core.model.SshAuthType
import com.xiaogou.serverseyes.data.local.MetricSampleEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class BackupImportStrategy {
    MERGE,
    REPLACE
}

@Serializable
data class BackupServerDto(
    val id: String,
    val name: String,
    val groupName: String = "默认分组",
    val host: String,
    val collectionMode: String = CollectionMode.HYBRID.name,
    val sshPort: Int = 22,
    val sshUsername: String = "root",
    val sshAuthType: String = SshAuthType.PASSWORD.name,
    val sshPassword: String = "",
    val sshPrivateKey: String = "",
    val sshKeyPassphrase: String = "",
    val trustedHostFingerprint: String? = null,
    val timeoutSeconds: Int = 10,
    val jumpHostEnabled: Boolean = false,
    val jumpHost: String = "",
    val jumpPort: Int = 22,
    val jumpUsername: String = "root",
    val jumpAuthType: String = SshAuthType.PASSWORD.name,
    val jumpPassword: String = "",
    val jumpPrivateKey: String = "",
    val jumpKeyPassphrase: String = "",
    val agentUrl: String = "",
    val agentToken: String = "",
    val sortOrder: Int = 0,
    val isPinned: Boolean = false,
    val enabled: Boolean = true
)

@Serializable
data class BackupAlertRuleDto(
    val id: String,
    val serverId: String? = null,
    val metricType: String,
    val warningThreshold: Double,
    val criticalThreshold: Double,
    val cooldownMinutes: Int = 10,
    val enabled: Boolean = true
)

@Serializable
data class BackupAlertEventDto(
    val id: String,
    val serverId: String,
    val serverName: String,
    val metricType: String,
    val severity: String,
    val currentValue: Double,
    val thresholdValue: Double,
    val message: String,
    val timestampMs: Long,
    val acknowledged: Boolean = false,
    val isResolved: Boolean = false,
    val resolvedAtMs: Long? = null
)

@Serializable
data class BackupMetricSampleDto(
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

@Serializable
data class BackupPayloadDto(
    val servers: List<BackupServerDto> = emptyList(),
    val alertRules: List<BackupAlertRuleDto> = emptyList(),
    val alertEvents: List<BackupAlertEventDto> = emptyList(),
    val metricSamples: List<BackupMetricSampleDto> = emptyList()
)

@Serializable
data class BackupEnvelopeDto(
    val format: String = "xiaogou_servers_backup",
    val version: Int = 2,
    val exportedAtMs: Long,
    val encrypted: Boolean,
    val containsSecrets: Boolean,
    val saltBase64: String? = null,
    val ivBase64: String? = null,
    val ciphertextBase64: String? = null,
    val payload: BackupPayloadDto? = null
)

data class BackupData(
    val servers: List<ServerConfig>,
    val alertRules: List<AlertRule>,
    val alertEvents: List<AlertEvent> = emptyList(),
    val metricSamples: List<MetricSampleEntity> = emptyList(),
    val containsSecrets: Boolean,
    val exportedAtMs: Long = 0L
)

data class BackupPreview(
    val serverCount: Int,
    val serverNames: List<String>,
    val ruleCount: Int,
    val eventCount: Int,
    val sampleCount: Int,
    val containsSecrets: Boolean,
    val encrypted: Boolean,
    val exportedAtMs: Long
)

object ConfigBackupManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val AES_KEY_BITS = 256
    private const val MIN_PASSPHRASE_LENGTH = 6

    fun exportJson(
        servers: List<ServerConfig>,
        alertRules: List<AlertRule>,
        includeSecrets: Boolean,
        passphrase: String? = null,
        nowMs: Long = System.currentTimeMillis(),
        alertEvents: List<AlertEvent> = emptyList(),
        metricSamples: List<MetricSampleEntity> = emptyList()
    ): String {
        val pass = passphrase?.takeIf { it.isNotEmpty() }
        if (includeSecrets) {
            require(pass != null && pass.length >= MIN_PASSPHRASE_LENGTH) {
                "导出包含敏感凭据时，必须设置至少 $MIN_PASSPHRASE_LENGTH 位的加密口令"
            }
        }
        if (pass != null) {
            require(pass.length >= MIN_PASSPHRASE_LENGTH) {
                "加密口令长度至少为 $MIN_PASSPHRASE_LENGTH 位"
            }
        }

        val cleanPayload = BackupPayloadDto(
            servers = servers.map { s ->
                BackupServerDto(
                    id = s.id,
                    name = s.name,
                    groupName = s.groupName,
                    host = s.host,
                    collectionMode = s.collectionMode.name,
                    sshPort = s.sshPort,
                    sshUsername = s.sshUsername,
                    sshAuthType = s.sshAuthType.name,
                    sshPassword = if (includeSecrets) s.sshPassword else "",
                    sshPrivateKey = if (includeSecrets) s.sshPrivateKey else "",
                    sshKeyPassphrase = if (includeSecrets) s.sshKeyPassphrase else "",
                    trustedHostFingerprint = s.trustedHostFingerprint,
                    timeoutSeconds = s.timeoutSeconds,
                    jumpHostEnabled = s.jumpHostEnabled,
                    jumpHost = s.jumpHost,
                    jumpPort = s.jumpPort,
                    jumpUsername = s.jumpUsername,
                    jumpAuthType = s.jumpAuthType.name,
                    jumpPassword = if (includeSecrets) s.jumpPassword else "",
                    jumpPrivateKey = if (includeSecrets) s.jumpPrivateKey else "",
                    jumpKeyPassphrase = if (includeSecrets) s.jumpKeyPassphrase else "",
                    agentUrl = s.agentUrl,
                    agentToken = if (includeSecrets) s.agentToken else "",
                    sortOrder = s.sortOrder,
                    isPinned = s.isPinned,
                    enabled = s.enabled
                )
            },
            alertRules = alertRules.map { r ->
                BackupAlertRuleDto(
                    id = r.id,
                    serverId = r.serverId,
                    metricType = r.metricType.name,
                    warningThreshold = r.warningThreshold,
                    criticalThreshold = r.criticalThreshold,
                    cooldownMinutes = r.cooldownMinutes,
                    enabled = r.enabled
                )
            },
            alertEvents = alertEvents.map { ev ->
                BackupAlertEventDto(
                    id = ev.id,
                    serverId = ev.serverId,
                    serverName = ev.serverName,
                    metricType = ev.metricType.name,
                    severity = ev.severity.name,
                    currentValue = ev.currentValue,
                    thresholdValue = ev.thresholdValue,
                    message = ev.message,
                    timestampMs = ev.timestampMs,
                    acknowledged = ev.acknowledged,
                    isResolved = ev.isResolved,
                    resolvedAtMs = ev.resolvedAtMs
                )
            },
            metricSamples = metricSamples.map { m ->
                BackupMetricSampleDto(
                    serverId = m.serverId,
                    timestampMs = m.timestampMs,
                    cpuPercent = m.cpuPercent,
                    memPercent = m.memPercent,
                    diskMaxPercent = m.diskMaxPercent,
                    netRxBytesPerSec = m.netRxBytesPerSec,
                    netTxBytesPerSec = m.netTxBytesPerSec,
                    load1 = m.load1,
                    sourceUsed = m.sourceUsed
                )
            }
        )

        val version = if (alertEvents.isEmpty() && metricSamples.isEmpty()) 1 else 2

        return if (pass == null) {
            val env = BackupEnvelopeDto(
                version = version,
                exportedAtMs = nowMs,
                encrypted = false,
                containsSecrets = false,
                payload = cleanPayload
            )
            json.encodeToString(env)
        } else {
            val rng = SecureRandom()
            val salt = ByteArray(16).also { rng.nextBytes(it) }
            val iv = ByteArray(12).also { rng.nextBytes(it) }
            val key = deriveAesKey(pass, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plainBytes = json.encodeToString(cleanPayload).toByteArray(Charsets.UTF_8)
            val encryptedBytes = cipher.doFinal(plainBytes)

            val encoder = Base64.getEncoder()
            val env = BackupEnvelopeDto(
                version = version,
                exportedAtMs = nowMs,
                encrypted = true,
                containsSecrets = includeSecrets,
                saltBase64 = encoder.encodeToString(salt),
                ivBase64 = encoder.encodeToString(iv),
                ciphertextBase64 = encoder.encodeToString(encryptedBytes),
                payload = null
            )
            json.encodeToString(env)
        }
    }

    fun previewJson(rawJson: String, passphrase: String? = null): Result<BackupPreview> {
        return decodeEnvelopeAndPayload(rawJson, passphrase).map { (env, data) ->
            BackupPreview(
                serverCount = data.servers.size,
                serverNames = data.servers.map { it.name },
                ruleCount = data.alertRules.size,
                eventCount = data.alertEvents.size,
                sampleCount = data.metricSamples.size,
                containsSecrets = data.containsSecrets,
                encrypted = env.encrypted,
                exportedAtMs = env.exportedAtMs
            )
        }
    }

    fun importJson(
        rawJson: String,
        passphrase: String? = null
    ): Result<BackupData> = decodeEnvelopeAndPayload(rawJson, passphrase).map { it.second }

    private fun decodeEnvelopeAndPayload(
        rawJson: String,
        passphrase: String?
    ): Result<Pair<BackupEnvelopeDto, BackupData>> = runCatching {
        val env = json.decodeFromString<BackupEnvelopeDto>(rawJson.trim())
        require(env.format == "xiaogou_servers_backup") { "不支持的备份文件格式" }
        require(env.version in 1..2) { "不支持的备份版本：${env.version}" }

        val payload = if (!env.encrypted) {
            env.payload ?: error("备份内容为空")
        } else {
            val pass = passphrase?.takeIf { it.isNotEmpty() }
                ?: error("该备份文件已加密，请输入解密口令")
            val decoder = Base64.getDecoder()
            val salt = decoder.decode(env.saltBase64 ?: error("备份缺少盐值"))
            val iv = decoder.decode(env.ivBase64 ?: error("备份缺少 IV"))
            val cipherBytes = decoder.decode(env.ciphertextBase64 ?: error("备份缺少密文"))
            val key = deriveAesKey(pass, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plain = try {
                cipher.doFinal(cipherBytes)
            } catch (_: Exception) {
                error("解密口令错误或备份文件已损坏")
            }
            json.decodeFromString<BackupPayloadDto>(String(plain, Charsets.UTF_8))
        }

        val servers = payload.servers.map { dto ->
            ServerConfig(
                id = dto.id,
                name = dto.name.ifBlank { dto.host },
                groupName = dto.groupName.ifBlank { "默认分组" },
                host = dto.host,
                collectionMode = runCatching { CollectionMode.valueOf(dto.collectionMode) }
                    .getOrDefault(CollectionMode.HYBRID),
                sshPort = dto.sshPort.coerceIn(1, 65535),
                sshUsername = dto.sshUsername.ifBlank { "root" },
                sshAuthType = runCatching { SshAuthType.valueOf(dto.sshAuthType) }
                    .getOrDefault(SshAuthType.PASSWORD),
                sshPassword = dto.sshPassword,
                sshPrivateKey = dto.sshPrivateKey,
                sshKeyPassphrase = dto.sshKeyPassphrase,
                trustedHostFingerprint = dto.trustedHostFingerprint,
                timeoutSeconds = dto.timeoutSeconds.coerceIn(3, 60),
                jumpHostEnabled = dto.jumpHostEnabled,
                jumpHost = dto.jumpHost,
                jumpPort = dto.jumpPort.coerceIn(1, 65535),
                jumpUsername = dto.jumpUsername.ifBlank { "root" },
                jumpAuthType = runCatching { SshAuthType.valueOf(dto.jumpAuthType) }
                    .getOrDefault(SshAuthType.PASSWORD),
                jumpPassword = dto.jumpPassword,
                jumpPrivateKey = dto.jumpPrivateKey,
                jumpKeyPassphrase = dto.jumpKeyPassphrase,
                agentUrl = dto.agentUrl,
                agentToken = dto.agentToken,
                sortOrder = dto.sortOrder,
                isPinned = dto.isPinned,
                enabled = dto.enabled
            )
        }

        val rules = payload.alertRules.mapNotNull { dto ->
            val metricType = runCatching { AlertMetricType.valueOf(dto.metricType) }.getOrNull()
                ?: return@mapNotNull null
            AlertRule(
                id = dto.id,
                serverId = dto.serverId,
                metricType = metricType,
                warningThreshold = dto.warningThreshold,
                criticalThreshold = dto.criticalThreshold,
                cooldownMinutes = dto.cooldownMinutes.coerceAtLeast(1),
                enabled = dto.enabled
            )
        }

        val events = payload.alertEvents.mapNotNull { dto ->
            val metricType = runCatching { AlertMetricType.valueOf(dto.metricType) }.getOrNull()
                ?: return@mapNotNull null
            val severity = runCatching { AlertSeverity.valueOf(dto.severity) }.getOrNull()
                ?: AlertSeverity.WARNING
            AlertEvent(
                id = dto.id,
                serverId = dto.serverId,
                serverName = dto.serverName,
                metricType = metricType,
                severity = severity,
                currentValue = dto.currentValue,
                thresholdValue = dto.thresholdValue,
                message = dto.message,
                timestampMs = dto.timestampMs,
                acknowledged = dto.acknowledged,
                isResolved = dto.isResolved,
                resolvedAtMs = dto.resolvedAtMs
            )
        }

        val samples = payload.metricSamples.map { m ->
            MetricSampleEntity(
                serverId = m.serverId,
                timestampMs = m.timestampMs,
                cpuPercent = m.cpuPercent,
                memPercent = m.memPercent,
                diskMaxPercent = m.diskMaxPercent,
                netRxBytesPerSec = m.netRxBytesPerSec,
                netTxBytesPerSec = m.netTxBytesPerSec,
                load1 = m.load1,
                sourceUsed = m.sourceUsed
            )
        }

        env to BackupData(
            servers = servers,
            alertRules = rules,
            alertEvents = events,
            metricSamples = samples,
            containsSecrets = env.containsSecrets,
            exportedAtMs = env.exportedAtMs
        )
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_BITS)
        val secret = factory.generateSecret(spec).encoded
        return SecretKeySpec(secret, "AES")
    }
}
