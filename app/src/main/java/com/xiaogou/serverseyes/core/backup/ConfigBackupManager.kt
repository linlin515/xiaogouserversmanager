package com.xiaogou.serverseyes.core.backup

import com.xiaogou.serverseyes.core.model.AlertMetricType
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.CollectionMode
import com.xiaogou.serverseyes.core.model.ServerConfig
import com.xiaogou.serverseyes.core.model.SshAuthType
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
data class BackupPayloadDto(
    val servers: List<BackupServerDto> = emptyList(),
    val alertRules: List<BackupAlertRuleDto> = emptyList()
)

@Serializable
data class BackupEnvelopeDto(
    val format: String = "xiaogou_servers_backup",
    val version: Int = 1,
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
    val containsSecrets: Boolean
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

    fun exportJson(
        servers: List<ServerConfig>,
        alertRules: List<AlertRule>,
        includeSecrets: Boolean,
        passphrase: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): String {
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
            }
        )

        val pass = passphrase?.takeIf { it.isNotEmpty() }
        return if (pass == null) {
            val env = BackupEnvelopeDto(
                exportedAtMs = nowMs,
                encrypted = false,
                containsSecrets = includeSecrets,
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

    fun importJson(
        rawJson: String,
        passphrase: String? = null
    ): Result<BackupData> = runCatching {
        val env = json.decodeFromString<BackupEnvelopeDto>(rawJson.trim())
        require(env.format == "xiaogou_servers_backup") { "不支持的备份文件格式" }
        require(env.version == 1) { "不支持的备份版本：${env.version}" }

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

        BackupData(
            servers = servers,
            alertRules = rules,
            containsSecrets = env.containsSecrets
        )
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_BITS)
        val secret = factory.generateSecret(spec).encoded
        return SecretKeySpec(secret, "AES")
    }
}
