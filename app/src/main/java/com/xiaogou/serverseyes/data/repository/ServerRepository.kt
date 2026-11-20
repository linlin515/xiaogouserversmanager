package com.xiaogou.serverseyes.data.repository

import com.xiaogou.serverseyes.core.backup.ConfigBackupManager
import com.xiaogou.serverseyes.core.model.AlertEvent
import com.xiaogou.serverseyes.core.model.AlertMetricType
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.AlertSeverity
import com.xiaogou.serverseyes.core.model.CollectionMode
import com.xiaogou.serverseyes.core.model.CpuJiffies
import com.xiaogou.serverseyes.core.model.LogTargetType
import com.xiaogou.serverseyes.core.model.MetricPoint
import com.xiaogou.serverseyes.core.model.MetricSource
import com.xiaogou.serverseyes.core.model.NetBytes
import com.xiaogou.serverseyes.core.model.ServerConfig
import com.xiaogou.serverseyes.core.model.ServerSnapshot
import com.xiaogou.serverseyes.core.model.ServerStatus
import com.xiaogou.serverseyes.core.model.SshAuthType
import com.xiaogou.serverseyes.core.model.UserFacingError
import com.xiaogou.serverseyes.core.model.WorkloadAction
import com.xiaogou.serverseyes.core.network.AgentApiClient
import com.xiaogou.serverseyes.core.parser.ErrorMapper
import com.xiaogou.serverseyes.core.security.CryptoManager
import com.xiaogou.serverseyes.core.ssh.SshClientManager
import com.xiaogou.serverseyes.data.alert.AlertEvaluator
import com.xiaogou.serverseyes.data.local.AlertEventDao
import com.xiaogou.serverseyes.data.local.AlertEventEntity
import com.xiaogou.serverseyes.data.local.AlertRuleDao
import com.xiaogou.serverseyes.data.local.AlertRuleEntity
import com.xiaogou.serverseyes.data.local.AppDatabase
import com.xiaogou.serverseyes.data.local.MetricSampleDao
import com.xiaogou.serverseyes.data.local.MetricSampleEntity
import com.xiaogou.serverseyes.data.local.ServerDao
import com.xiaogou.serverseyes.data.local.ServerEntity
import com.xiaogou.serverseyes.data.local.SettingsStore
import com.xiaogou.serverseyes.service.AlertNotifier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ServerRuntimeData(
    val snapshot: ServerSnapshot? = null,
    val recentSparklineCpu: List<Double> = emptyList(),
    val isRefreshing: Boolean = false,
    val consecutiveFailures: Int = 0,
    val lastError: UserFacingError? = null,
    val lastUpdatedMs: Long = 0L
)

data class ServerUiModel(
    val config: ServerConfig,
    val status: ServerStatus,
    val snapshot: ServerSnapshot?,
    val sparklineCpu: List<Double>,
    val isRefreshing: Boolean,
    val lastError: UserFacingError?,
    val lastSeenAtMs: Long
)

data class ConnectionTestSummary(
    val agentOk: Boolean? = null,
    val agentDetail: String = "",
    val sshOk: Boolean? = null,
    val sshDetail: String = "",
    val observedFingerprint: String? = null,
    val observedKeyType: String? = null,
    val error: UserFacingError? = null
) {
    val isOverallSuccess: Boolean
        get() = (agentOk == true || sshOk == true) && error == null
}

class ServerRepository(
    private val serverDao: ServerDao,
    private val sampleDao: MetricSampleDao,
    private val alertRuleDao: AlertRuleDao,
    private val alertEventDao: AlertEventDao,
    private val settingsStore: SettingsStore,
    private val cryptoManager: CryptoManager,
    private val sshClient: SshClientManager,
    private val agentClient: AgentApiClient,
    private val alertNotifier: AlertNotifier
) {
    private val runtimeMap = MutableStateFlow<Map<String, ServerRuntimeData>>(emptyMap())
    private val prevCpuMap = ConcurrentHashMap<String, CpuJiffies>()
    private val prevNetMap = ConcurrentHashMap<String, NetBytes>()
    private val pollSemaphore = Semaphore(6)

    suspend fun ensureDefaultAlertRules() {
        alertRuleDao.insertIfAbsent(AppDatabase.DEFAULT_RULES)
    }

    fun observeServers(): Flow<List<ServerUiModel>> {
        return combine(
            serverDao.observeAll(),
            runtimeMap,
            alertRuleDao.observeAll()
        ) { entities, rtMap, ruleEntities ->
            val rules = ruleEntities.map { it.toDomain() }
            entities.map { entity ->
                val config = entity.toDomain()
                val rt = rtMap[entity.id] ?: ServerRuntimeData()
                val serverRules = AlertEvaluator.effectiveRulesForServer(entity.id, rules)
                val status = when {
                    rt.isRefreshing && rt.snapshot == null -> ServerStatus.CONNECTING
                    else -> AlertEvaluator.computeServerStatus(
                        snapshot = rt.snapshot,
                        consecutiveFailures = rt.consecutiveFailures,
                        rules = serverRules
                    )
                }
                ServerUiModel(
                    config = config,
                    status = status,
                    snapshot = rt.snapshot,
                    sparklineCpu = rt.recentSparklineCpu,
                    isRefreshing = rt.isRefreshing,
                    lastError = rt.lastError,
                    lastSeenAtMs = rt.lastUpdatedMs.takeIf { it > 0 } ?: entity.lastSeenAtMs
                )
            }
        }
    }

    fun observeAlertRules(): Flow<List<AlertRule>> =
        alertRuleDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeAlertEvents(): Flow<List<AlertEvent>> =
        alertEventDao.observeRecent().map { list -> list.map { it.toDomain() } }

    fun observeLocalHistory(serverId: String, minutes: Int = 60): Flow<List<MetricPoint>> {
        val since = System.currentTimeMillis() - minutes * 60_000L
        return sampleDao.observeRecent(serverId, since).map { list ->
            list.map {
                MetricPoint(
                    timestampMs = it.timestampMs,
                    cpuPercent = it.cpuPercent,
                    memPercent = it.memPercent,
                    netRxBytesPerSec = it.netRxBytesPerSec,
                    netTxBytesPerSec = it.netTxBytesPerSec,
                    load1 = it.load1
                )
            }
        }
    }

    suspend fun getServerById(id: String): ServerConfig? =
        serverDao.getById(id)?.toDomain()

    suspend fun saveServer(config: ServerConfig) {
        val finalId = config.id.ifBlank { UUID.randomUUID().toString() }
        val normalized = config.copy(id = finalId)
        serverDao.upsert(normalized.toEntity())
        sshClient.invalidateSession(finalId)
    }

    suspend fun deleteServer(serverId: String) {
        sshClient.invalidateSession(serverId)
        serverDao.deleteById(serverId)
        sampleDao.deleteForServer(serverId)
        runtimeMap.update { it - serverId }
    }

    suspend fun trustFingerprint(serverId: String, fingerprint: String) {
        serverDao.updateFingerprint(serverId, fingerprint)
        sshClient.invalidateSession(serverId)
    }

    suspend fun setPinned(serverId: String, pinned: Boolean) {
        serverDao.updatePinned(serverId, pinned)
    }

    suspend fun setEnabled(serverId: String, enabled: Boolean) {
        serverDao.updateEnabled(serverId, enabled)
        if (!enabled) {
            sshClient.invalidateSession(serverId)
        }
    }

    suspend fun saveAlertRule(rule: AlertRule) {
        alertRuleDao.upsert(rule.toEntity())
    }

    suspend fun deleteAlertRule(ruleId: String) {
        alertRuleDao.deleteById(ruleId)
    }

    suspend fun exportBackupJson(includeSecrets: Boolean, passphrase: String?): String {
        val allServers = serverDao.getAll().map { it.toDomain() }
        val allRules = alertRuleDao.getAll().map { it.toDomain() }
        return ConfigBackupManager.exportJson(
            servers = allServers,
            alertRules = allRules,
            includeSecrets = includeSecrets,
            passphrase = passphrase
        )
    }

    suspend fun importBackupJson(rawJson: String, passphrase: String?): Result<Pair<Int, Int>> {
        return ConfigBackupManager.importJson(rawJson, passphrase).map { data ->
            data.servers.forEach { srv ->
                serverDao.upsert(srv.toEntity())
                sshClient.invalidateSession(srv.id)
            }
            data.alertRules.forEach { rule ->
                alertRuleDao.upsert(rule.toEntity())
            }
            data.servers.size to data.alertRules.size
        }
    }

    suspend fun acknowledgeAlert(eventId: String) {
        alertEventDao.acknowledge(eventId)
    }

    suspend fun acknowledgeAllAlerts() {
        alertEventDao.acknowledgeAll()
    }

    suspend fun clearAlerts() {
        alertEventDao.clearAll()
    }

    suspend fun refreshAllServers() = coroutineScope {
        ensureDefaultAlertRules()
        val servers = serverDao.getAll().filter { it.enabled }.map { it.toDomain() }
        if (servers.isEmpty()) return@coroutineScope
        servers.map { config ->
            async {
                pollSemaphore.withPermit {
                    refreshSingleServer(config)
                }
            }
        }.awaitAll()
        // Keep last 48h local samples to support 24h / 48h views
        val cutoff = System.currentTimeMillis() - 48 * 60 * 60_000L
        sampleDao.pruneOlderThan(cutoff)
    }

    suspend fun refreshServer(serverId: String) {
        val entity = serverDao.getById(serverId) ?: return
        refreshSingleServer(entity.toDomain())
    }

    private suspend fun refreshSingleServer(config: ServerConfig) {
        runtimeMap.update { current ->
            val old = current[config.id] ?: ServerRuntimeData()
            current + (config.id to old.copy(isRefreshing = true))
        }

        val outcome = collectByMode(config)
        val now = System.currentTimeMillis()
        val allRules = alertRuleDao.getAll().map { it.toDomain() }
        val serverRules = AlertEvaluator.effectiveRulesForServer(config.id, allRules)
        val settings = settingsStore.settingsFlow.first()

        outcome.fold(
            onSuccess = { snapshot ->
                snapshot.rawCpuJiffies?.let { prevCpuMap[config.id] = it }
                snapshot.rawNetBytes?.let { prevNetMap[config.id] = it }

                sampleDao.insert(
                    MetricSampleEntity(
                        serverId = config.id,
                        timestampMs = snapshot.timestampMs,
                        cpuPercent = snapshot.cpuPercent,
                        memPercent = snapshot.memPercent,
                        diskMaxPercent = snapshot.maxDiskPercent,
                        netRxBytesPerSec = snapshot.netRxBytesPerSec,
                        netTxBytesPerSec = snapshot.netTxBytesPerSec,
                        load1 = snapshot.load1,
                        sourceUsed = snapshot.sourceUsed.name
                    )
                )
                serverDao.updateRuntimeMeta(
                    id = config.id,
                    hostname = snapshot.hostname.ifBlank { config.host },
                    osName = snapshot.osName,
                    lastSeenAtMs = snapshot.timestampMs
                )

                runtimeMap.update { current ->
                    val old = current[config.id] ?: ServerRuntimeData()
                    val spark = (old.recentSparklineCpu + snapshot.cpuPercent).takeLast(20)
                    current + (
                        config.id to old.copy(
                            snapshot = snapshot,
                            recentSparklineCpu = spark,
                            isRefreshing = false,
                            consecutiveFailures = 0,
                            lastError = null,
                            lastUpdatedMs = snapshot.timestampMs
                        )
                    )
                }

                val lastAlertTimes = mutableMapOf<AlertMetricType, Long>()
                listOf(AlertMetricType.CPU, AlertMetricType.MEMORY, AlertMetricType.DISK).forEach { type ->
                    alertEventDao.getLatestFor(config.id, type.name)?.let {
                        lastAlertTimes[type] = it.timestampMs
                    }
                }
                val newEvents = AlertEvaluator.evaluateSnapshotAlerts(
                    serverId = config.id,
                    serverName = config.name,
                    snapshot = snapshot,
                    rules = serverRules,
                    lastAlertTimestampByMetric = lastAlertTimes,
                    nowMs = now
                )
                newEvents.forEach { ev ->
                    alertEventDao.insert(ev.toEntity())
                    if (settings.notificationsEnabled) {
                        alertNotifier.notifyAlert(ev)
                    }
                }
            },
            onFailure = { throwable ->
                val userErr = ErrorMapper.toUserFacingError(throwable)
                var failures = 1
                runtimeMap.update { current ->
                    val old = current[config.id] ?: ServerRuntimeData()
                    failures = old.consecutiveFailures + 1
                    current + (
                        config.id to old.copy(
                            isRefreshing = false,
                            consecutiveFailures = failures,
                            lastError = userErr
                        )
                    )
                }
                val lastOffline = alertEventDao.getLatestFor(config.id, AlertMetricType.OFFLINE.name)?.timestampMs ?: 0L
                val offlineEv = AlertEvaluator.evaluateOfflineAlert(
                    serverId = config.id,
                    serverName = config.name,
                    consecutiveFailures = failures,
                    rules = serverRules,
                    lastOfflineAlertMs = lastOffline,
                    nowMs = now
                )
                if (offlineEv != null) {
                    alertEventDao.insert(offlineEv.toEntity())
                    if (settings.notificationsEnabled) {
                        alertNotifier.notifyAlert(offlineEv)
                    }
                }
            }
        )
    }

    private suspend fun collectByMode(config: ServerConfig): Result<ServerSnapshot> {
        return when (config.collectionMode) {
            CollectionMode.AGENT_ONLY -> {
                agentClient.fetchSnapshot(config.id, config.agentUrl, config.agentToken)
            }
            CollectionMode.SSH_ONLY -> {
                sshClient.collectSnapshot(config, prevCpuMap[config.id], prevNetMap[config.id])
            }
            CollectionMode.HYBRID -> {
                if (config.agentUrl.isNotBlank()) {
                    val agentResult = agentClient.fetchSnapshot(config.id, config.agentUrl, config.agentToken)
                    if (agentResult.isSuccess) return agentResult
                    // If SSH is also configured, silently fallback to SSH
                    if (config.host.isNotBlank() && config.sshUsername.isNotBlank()) {
                        return sshClient.collectSnapshot(config, prevCpuMap[config.id], prevNetMap[config.id])
                    }
                    agentResult
                } else {
                    sshClient.collectSnapshot(config, prevCpuMap[config.id], prevNetMap[config.id])
                }
            }
        }
    }

    suspend fun testConnection(
        config: ServerConfig,
        trustUnknownHostKey: Boolean
    ): ConnectionTestSummary {
        var agentOk: Boolean? = null
        var agentDetail = ""
        var sshOk: Boolean? = null
        var sshDetail = ""
        var observedFingerprint: String? = null
        var observedKeyType: String? = null
        var firstError: UserFacingError? = null

        if (config.collectionMode != CollectionMode.SSH_ONLY && config.agentUrl.isNotBlank()) {
            val res = agentClient.testHealth(config.agentUrl, config.agentToken)
            res.fold(
                onSuccess = { (health, latency) ->
                    agentOk = true
                    agentDetail = "Agent v${health.version} (${health.hostname.ifBlank { "OK" }}) · ${latency}ms"
                },
                onFailure = { e ->
                    agentOk = false
                    val mapped = ErrorMapper.toUserFacingError(e)
                    agentDetail = mapped.message
                    firstError = mapped
                }
            )
        }

        if (config.collectionMode != CollectionMode.AGENT_ONLY && config.host.isNotBlank()) {
            val res = sshClient.testConnection(config, trustUnknownHostKey)
            res.fold(
                onSuccess = { test ->
                    sshOk = true
                    sshDetail = "${test.hostname} (${test.kernel}) · ${test.latencyMs}ms"
                    observedFingerprint = test.fingerprint
                    observedKeyType = test.keyType
                    if (config.collectionMode == CollectionMode.HYBRID && agentOk == false) {
                        firstError = null // Hybrid fallback works via SSH
                    }
                },
                onFailure = { e ->
                    sshOk = false
                    val mapped = ErrorMapper.toUserFacingError(e)
                    sshDetail = mapped.message
                    if (mapped is UserFacingError.HostKeyUntrusted) {
                        observedFingerprint = mapped.fingerprint
                        observedKeyType = mapped.keyType
                    }
                    if (firstError == null || config.collectionMode == CollectionMode.SSH_ONLY) {
                        firstError = mapped
                    }
                }
            )
        }

        return ConnectionTestSummary(
            agentOk = agentOk,
            agentDetail = agentDetail,
            sshOk = sshOk,
            sshDetail = sshDetail,
            observedFingerprint = observedFingerprint,
            observedKeyType = observedKeyType,
            error = firstError
        )
    }

    suspend fun fetchHistoryWithAgentFallback(
        config: ServerConfig,
        minutes: Int = 60
    ): List<MetricPoint> {
        if (config.collectionMode != CollectionMode.SSH_ONLY && config.agentUrl.isNotBlank()) {
            val remote = agentClient.fetchHistory(config.agentUrl, config.agentToken, minutes)
            if (remote.isSuccess) {
                val points = remote.getOrNull().orEmpty()
                if (points.isNotEmpty()) return points
            }
        }
        val since = System.currentTimeMillis() - minutes * 60_000L
        return sampleDao.observeRecent(config.id, since).first().map {
            MetricPoint(
                timestampMs = it.timestampMs,
                cpuPercent = it.cpuPercent,
                memPercent = it.memPercent,
                netRxBytesPerSec = it.netRxBytesPerSec,
                netTxBytesPerSec = it.netTxBytesPerSec,
                load1 = it.load1
            )
        }
    }

    suspend fun fetchLogs(
        config: ServerConfig,
        targetType: LogTargetType,
        targetName: String,
        lines: Int = 120
    ): Result<List<String>> {
        if (config.collectionMode != CollectionMode.SSH_ONLY && config.agentUrl.isNotBlank()) {
            val agentRes = agentClient.fetchLogs(config.agentUrl, config.agentToken, targetType, targetName, lines)
            if (agentRes.isSuccess) return agentRes
            if (config.collectionMode == CollectionMode.AGENT_ONLY) return agentRes
        }
        return sshClient.fetchLogs(config, targetType, targetName, lines)
    }

    suspend fun restartTarget(
        config: ServerConfig,
        targetType: LogTargetType,
        targetName: String
    ): Result<String> = performWorkloadAction(config, targetType, targetName, WorkloadAction.RESTART)

    suspend fun performWorkloadAction(
        config: ServerConfig,
        targetType: LogTargetType,
        targetName: String,
        action: WorkloadAction
    ): Result<String> {
        val result = if (config.collectionMode != CollectionMode.SSH_ONLY && config.agentUrl.isNotBlank()) {
            val agentRes = agentClient.performWorkloadAction(
                config.agentUrl,
                config.agentToken,
                targetType,
                targetName,
                action
            )
            if (agentRes.isSuccess || config.collectionMode == CollectionMode.AGENT_ONLY) {
                agentRes
            } else {
                sshClient.performWorkloadAction(config, targetType, targetName, action)
            }
        } else {
            sshClient.performWorkloadAction(config, targetType, targetName, action)
        }
        if (result.isSuccess) {
            refreshSingleServer(config)
        }
        return result
    }

    suspend fun runDiagnosticCommand(config: ServerConfig, command: String): Result<String> {
        return sshClient.runDiagnosticCommand(config, command)
    }

    suspend fun clearLocalMetricsCache() {
        sampleDao.pruneOlderThan(Long.MAX_VALUE)
    }

    private fun ServerEntity.toDomain(): ServerConfig = ServerConfig(
        id = id,
        name = name,
        groupName = groupName,
        host = host,
        collectionMode = runCatching { CollectionMode.valueOf(collectionMode) }.getOrDefault(CollectionMode.HYBRID),
        sshPort = sshPort,
        sshUsername = sshUsername,
        sshAuthType = runCatching { SshAuthType.valueOf(sshAuthType) }.getOrDefault(SshAuthType.PASSWORD),
        sshPassword = runCatching { cryptoManager.decrypt(encryptedSshPassword) }.getOrDefault(""),
        sshPrivateKey = runCatching { cryptoManager.decrypt(encryptedSshPrivateKey) }.getOrDefault(""),
        sshKeyPassphrase = runCatching { cryptoManager.decrypt(encryptedSshKeyPassphrase) }.getOrDefault(""),
        trustedHostFingerprint = trustedHostFingerprint,
        agentUrl = agentUrl,
        agentToken = runCatching { cryptoManager.decrypt(encryptedAgentToken) }.getOrDefault(""),
        sortOrder = sortOrder,
        isPinned = isPinned,
        enabled = enabled
    )

    private fun ServerConfig.toEntity(): ServerEntity = ServerEntity(
        id = id,
        name = name,
        groupName = groupName,
        host = host,
        collectionMode = collectionMode.name,
        sshPort = sshPort,
        sshUsername = sshUsername,
        sshAuthType = sshAuthType.name,
        encryptedSshPassword = cryptoManager.encrypt(sshPassword),
        encryptedSshPrivateKey = cryptoManager.encrypt(sshPrivateKey),
        encryptedSshKeyPassphrase = cryptoManager.encrypt(sshKeyPassphrase),
        trustedHostFingerprint = trustedHostFingerprint,
        agentUrl = agentUrl,
        encryptedAgentToken = cryptoManager.encrypt(agentToken),
        sortOrder = sortOrder,
        isPinned = isPinned,
        enabled = enabled
    )

    private fun AlertRuleEntity.toDomain(): AlertRule = AlertRule(
        id = id,
        serverId = serverId,
        metricType = AlertMetricType.valueOf(metricType),
        warningThreshold = warningThreshold,
        criticalThreshold = criticalThreshold,
        cooldownMinutes = cooldownMinutes,
        enabled = enabled
    )

    private fun AlertRule.toEntity(): AlertRuleEntity = AlertRuleEntity(
        id = id,
        serverId = serverId,
        metricType = metricType.name,
        warningThreshold = warningThreshold,
        criticalThreshold = criticalThreshold,
        cooldownMinutes = cooldownMinutes,
        enabled = enabled
    )

    private fun AlertEventEntity.toDomain(): AlertEvent = AlertEvent(
        id = id,
        serverId = serverId,
        serverName = serverName,
        metricType = AlertMetricType.valueOf(metricType),
        severity = AlertSeverity.valueOf(severity),
        currentValue = currentValue,
        thresholdValue = thresholdValue,
        message = message,
        timestampMs = timestampMs,
        acknowledged = acknowledged
    )

    private fun AlertEvent.toEntity(): AlertEventEntity = AlertEventEntity(
        id = id,
        serverId = serverId,
        serverName = serverName,
        metricType = metricType.name,
        severity = severity.name,
        currentValue = currentValue,
        thresholdValue = thresholdValue,
        message = message,
        timestampMs = timestampMs,
        acknowledged = acknowledged
    )
}
