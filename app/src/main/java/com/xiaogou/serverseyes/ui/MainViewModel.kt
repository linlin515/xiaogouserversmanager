package com.xiaogou.serverseyes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaogou.serverseyes.ServersEyesApplication
import com.xiaogou.serverseyes.core.backup.BackupImportStrategy
import com.xiaogou.serverseyes.core.backup.BackupPreview
import com.xiaogou.serverseyes.core.model.AlertEvent
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.HistoryWindow
import com.xiaogou.serverseyes.core.model.LogTargetType
import com.xiaogou.serverseyes.core.model.MetricPoint
import com.xiaogou.serverseyes.core.model.ServerConfig
import com.xiaogou.serverseyes.core.model.ServerSnapshot
import com.xiaogou.serverseyes.core.model.ServerSortOption
import com.xiaogou.serverseyes.core.model.ServerStatus
import com.xiaogou.serverseyes.core.model.ThemePreference
import com.xiaogou.serverseyes.core.model.TrendMetric
import com.xiaogou.serverseyes.core.model.WorkloadAction
import com.xiaogou.serverseyes.core.parser.ErrorMapper
import com.xiaogou.serverseyes.data.local.AppSettings
import com.xiaogou.serverseyes.data.repository.BackupImportSummary
import com.xiaogou.serverseyes.data.repository.ConnectionTestSummary
import com.xiaogou.serverseyes.data.repository.ServerUiModel
import com.xiaogou.serverseyes.service.ForegroundMonitorService
import com.xiaogou.serverseyes.service.MonitoringWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class DetailTab(val label: String) {
    OVERVIEW("资源概览"),
    WORKLOADS("容器与服务"),
    OPS("日志与诊断")
}

enum class ContainerStateFilter(val label: String) {
    ALL("全部"),
    RUNNING("运行中"),
    STOPPED("已停止")
}

enum class ServiceStateFilter(val label: String) {
    ALL("全部"),
    HEALTHY("运行中"),
    UNHEALTHY("异常")
}

enum class ProcessSort(val label: String) {
    CPU("CPU ↓"),
    MEMORY("内存 ↓")
}

data class DetailOpsState(
    val historyPoints: List<MetricPoint> = emptyList(),
    val historyWindow: HistoryWindow = HistoryWindow.LAST_1H,
    val historyMetric: TrendMetric = TrendMetric.CPU_MEM,
    val isLiveMode: Boolean = true,
    val isLoadingHistory: Boolean = false,
    val historyError: String? = null,
    val detailTab: DetailTab = DetailTab.OVERVIEW,
    val processSort: ProcessSort = ProcessSort.CPU,
    val serviceFilter: ServiceStateFilter = ServiceStateFilter.ALL,
    val workloadSearch: String = "",
    val containerFilter: ContainerStateFilter = ContainerStateFilter.ALL,
    val logServerId: String = "",
    val logTargetType: LogTargetType = LogTargetType.SYSTEM_JOURNAL,
    val logTargetName: String = "",
    val logLinesCount: Int = 150,
    val logTitle: String = "",
    val logLines: List<String> = emptyList(),
    val logError: String? = null,
    val isLogsOpen: Boolean = false,
    val isLoadingLogs: Boolean = false,
    val diagOutput: String = "点击上方预设诊断项或输入只读命令开始排查",
    val diagHistory: List<String> = emptyList(),
    val isRunningDiag: Boolean = false,
    val isActionPending: Boolean = false
)

private const val MAX_DETAIL_POINTS = 400

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ServersEyesApplication
    private val repo = app.container.repository
    private val settingsStore = app.container.settingsStore

    val servers: StateFlow<List<ServerUiModel>> = repo.observeServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val alertEvents: StateFlow<List<AlertEvent>> = repo.observeAlertEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val alertRules: StateFlow<List<AlertRule>> = repo.observeAlertRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastRefreshMs = MutableStateFlow(0L)
    val lastRefreshMs: StateFlow<Long> = _lastRefreshMs.asStateFlow()

    private val _selectedServerId = MutableStateFlow<String?>(null)
    val selectedServerId: StateFlow<String?> = _selectedServerId.asStateFlow()

    private val _groupFilter = MutableStateFlow("全部")
    val groupFilter: StateFlow<String> = _groupFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _detailOps = MutableStateFlow(DetailOpsState())
    val detailOps: StateFlow<DetailOpsState> = _detailOps.asStateFlow()

    // extraBufferCapacity 避免连续 emit 时发射方挂起；去重防止同一文案短时间内连环弹出。
    private val _snackbarEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private var lastSnackbarMessage: String? = null
    private var lastSnackbarAtMs = 0L

    fun emitSnackbar(message: String) {
        val now = System.currentTimeMillis()
        if (message == lastSnackbarMessage && now - lastSnackbarAtMs < 1500L) return
        lastSnackbarMessage = message
        lastSnackbarAtMs = now
        _snackbarEvents.tryEmit(message)
    }

    private var pollingJob: Job? = null
    private var liveJob: Job? = null
    private var lastLiveTimestamp = 0L
    private var historyRequestToken = 0

    init {
        viewModelScope.launch {
            repo.ensureDefaultAlertRules()
            val list = repo.observeServers().first()
            if (list.isNotEmpty() && _selectedServerId.value == null) {
                _selectedServerId.value = list.first().config.id
            }
            _isInitialLoading.value = false
            _selectedServerId.value?.let { id ->
                startLiveCollector(id)
                loadHistoryInternal(id, _detailOps.value.historyWindow)
            }
            startPolling()
        }
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                if (!ForegroundMonitorService.isServiceRunning.value) {
                    performRefreshCycle()
                }
                // 实时曲线由 observeServers 增量驱动；关闭实时后窗口固定，不随轮询滑移。
                val intervalSec = settingsStore.settingsFlow.first().pollIntervalSeconds
                delay(intervalSec * 1000L)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refreshNow() {
        viewModelScope.launch {
            performRefreshCycle(force = true)
            _selectedServerId.value?.let { id ->
                loadHistoryInternal(id, _detailOps.value.historyWindow)
            }
        }
    }

    private suspend fun performRefreshCycle(force: Boolean = false) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        try {
            repo.refreshAllServers(force = force)
            _lastRefreshMs.value = System.currentTimeMillis()
        } finally {
            _isRefreshing.value = false
        }
    }

    fun selectGroupFilter(group: String) {
        _groupFilter.value = group
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectServer(serverId: String) {
        _selectedServerId.value = serverId
        // 保留窗口、指标、实时开关与 Tab 选择，只清理与上一台节点绑定的数据。
        _detailOps.value = _detailOps.value.copy(
            historyPoints = emptyList(),
            isLoadingHistory = true,
            historyError = null,
            logServerId = "",
            logTitle = "",
            logLines = emptyList(),
            logError = null,
            isLogsOpen = false,
            diagOutput = "点击上方预设诊断项或输入只读命令开始排查",
            diagHistory = emptyList(),
            isRunningDiag = false
        )
        lastLiveTimestamp = 0L
        startLiveCollector(serverId)
        viewModelScope.launch {
            loadHistoryInternal(serverId, _detailOps.value.historyWindow)
        }
    }

    fun changeHistoryRange(serverId: String, minutes: Int) {
        val window = HistoryWindow.fromMinutes(minutes)
        if (_detailOps.value.historyWindow == window) return
        _detailOps.value = _detailOps.value.copy(historyWindow = window)
        viewModelScope.launch {
            loadHistoryInternal(serverId, window)
        }
    }

    fun setHistoryMetric(metric: TrendMetric) {
        if (_detailOps.value.historyMetric == metric) return
        _detailOps.value = _detailOps.value.copy(historyMetric = metric)
    }

    fun setLiveMode(enabled: Boolean) {
        if (_detailOps.value.isLiveMode == enabled) return
        _detailOps.value = _detailOps.value.copy(isLiveMode = enabled)
        if (enabled) {
            _selectedServerId.value?.let { id ->
                viewModelScope.launch { loadHistoryInternal(id, _detailOps.value.historyWindow) }
            }
        }
    }

    fun setDetailTab(tab: DetailTab) {
        _detailOps.value = _detailOps.value.copy(detailTab = tab)
    }

    fun setProcessSort(sort: ProcessSort) {
        _detailOps.value = _detailOps.value.copy(processSort = sort)
    }

    fun setServiceFilter(filter: ServiceStateFilter) {
        _detailOps.value = _detailOps.value.copy(serviceFilter = filter)
    }

    fun setWorkloadSearch(query: String) {
        _detailOps.value = _detailOps.value.copy(workloadSearch = query)
    }

    fun setContainerFilter(filter: ContainerStateFilter) {
        _detailOps.value = _detailOps.value.copy(containerFilter = filter)
    }

    private suspend fun loadHistoryInternal(serverId: String, window: HistoryWindow) {
        val token = ++historyRequestToken
        _detailOps.value = _detailOps.value.copy(isLoadingHistory = true, historyError = null)
        try {
            val cfg = repo.getServerById(serverId) ?: return
            val pts = repo.fetchHistoryWithAgentFallback(cfg, window.minutes)
            if (token != historyRequestToken || _selectedServerId.value != serverId) return
            lastLiveTimestamp = maxOf(lastLiveTimestamp, pts.lastOrNull()?.timestampMs ?: 0L)
            _detailOps.value = _detailOps.value.copy(
                historyPoints = pts,
                isLoadingHistory = false,
                historyError = null
            )
        } catch (e: Exception) {
            if (token != historyRequestToken) return
            _detailOps.value = _detailOps.value.copy(
                isLoadingHistory = false,
                historyError = "历史数据加载失败，请稍后重试"
            )
        }
    }

    /**
     * 实时模式：订阅节点快照，只追加比缓冲区更新的采样点。
     * 不在这里做全量重查，避免每 5 秒重建整条曲线。
     */
    private fun startLiveCollector(serverId: String) {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            repo.observeServers().collect { list ->
                if (_selectedServerId.value != serverId) return@collect
                val ops = _detailOps.value
                if (!ops.isLiveMode) return@collect
                val snapshot = list.firstOrNull { it.config.id == serverId }?.snapshot ?: return@collect
                if (snapshot.timestampMs <= lastLiveTimestamp) return@collect
                lastLiveTimestamp = snapshot.timestampMs

                val point = snapshot.toMetricPoint()
                val cutoff = System.currentTimeMillis() - ops.historyWindow.minutes * 60_000L
                val bucket = ops.historyWindow.bucketMs
                val merged = (ops.historyPoints
                    .filterNot { it.timestampMs / bucket == point.timestampMs / bucket } + point)
                    .filter { it.timestampMs >= cutoff }
                    .takeLast(MAX_DETAIL_POINTS)
                _detailOps.value = _detailOps.value.copy(historyPoints = merged)
            }
        }
    }

    private fun ServerSnapshot.toMetricPoint(): MetricPoint = MetricPoint(
        timestampMs = timestampMs,
        cpuPercent = cpuPercent,
        memPercent = memPercent,
        netRxBytesPerSec = netRxBytesPerSec,
        netTxBytesPerSec = netTxBytesPerSec,
        load1 = load1,
        diskMaxPercent = maxDiskPercent,
        cpuTempCelsius = cpuTempCelsius,
        diskReadBytesPerSec = diskReadBytesPerSec,
        diskWriteBytesPerSec = diskWriteBytesPerSec,
        swapUsedBytes = swapUsedBytes,
        swapTotalBytes = swapTotalBytes,
        load5 = load5,
        load15 = load15
    )

    fun openLogs(
        serverId: String,
        targetType: LogTargetType,
        targetName: String,
        lines: Int = _detailOps.value.logLinesCount
    ) {
        viewModelScope.launch {
            val title = if (targetType == LogTargetType.SYSTEM_JOURNAL) "系统日志" else targetName
            _detailOps.value = _detailOps.value.copy(
                logServerId = serverId,
                logTargetType = targetType,
                logTargetName = targetName,
                logLinesCount = lines,
                logTitle = title,
                logLines = emptyList(),
                logError = null,
                isLogsOpen = true,
                isLoadingLogs = true
            )
            val cfg = repo.getServerById(serverId)
            if (cfg == null) {
                _detailOps.value = _detailOps.value.copy(
                    isLoadingLogs = false,
                    logError = "未找到服务器配置"
                )
                return@launch
            }
            repo.fetchLogs(cfg, targetType, targetName, lines = lines).fold(
                onSuccess = { outLines ->
                    _detailOps.value = _detailOps.value.copy(
                        isLoadingLogs = false,
                        logError = null,
                        logLines = if (outLines.isEmpty()) listOf("暂无日志输出") else outLines
                    )
                },
                onFailure = { e ->
                    val err = ErrorMapper.toUserFacingError(e)
                    _detailOps.value = _detailOps.value.copy(
                        isLoadingLogs = false,
                        logError = "${err.title}：${err.message}"
                    )
                }
            )
        }
    }

    fun refreshOpenLogs(lines: Int = _detailOps.value.logLinesCount) {
        val current = _detailOps.value
        if (current.logServerId.isNotBlank()) {
            openLogs(current.logServerId, current.logTargetType, current.logTargetName, lines)
        }
    }

    fun closeLogs() {
        _detailOps.value = _detailOps.value.copy(isLogsOpen = false)
    }

    fun restartTarget(serverId: String, targetType: LogTargetType, targetName: String) {
        performWorkloadAction(serverId, targetType, targetName, WorkloadAction.RESTART)
    }

    fun performWorkloadAction(
        serverId: String,
        targetType: LogTargetType,
        targetName: String,
        action: WorkloadAction
    ) {
        viewModelScope.launch {
            _detailOps.value = _detailOps.value.copy(isActionPending = true)
            val cfg = repo.getServerById(serverId)
            if (cfg != null) {
                repo.performWorkloadAction(cfg, targetType, targetName, action).fold(
                    onSuccess = { msg -> emitSnackbar(msg) },
                    onFailure = { e ->
                        val err = ErrorMapper.toUserFacingError(e)
                        emitSnackbar("${err.title}：${err.message}")
                    }
                )
            }
            _detailOps.value = _detailOps.value.copy(isActionPending = false)
        }
    }

    fun runDiagnostic(serverId: String, command: String) {
        viewModelScope.launch {
            _detailOps.value = _detailOps.value.copy(isRunningDiag = true)
            val cfg = repo.getServerById(serverId)
            if (cfg == null) {
                _detailOps.value = _detailOps.value.copy(isRunningDiag = false)
                return@launch
            }
            repo.runDiagnosticCommand(cfg, command).fold(
                onSuccess = { out ->
                    val updatedHistory = (listOf(command) + _detailOps.value.diagHistory.filterNot { it == command }).take(12)
                    _detailOps.value = _detailOps.value.copy(
                        isRunningDiag = false,
                        diagOutput = "$ $command\n${out.ifBlank { "(无输出)" }}",
                        diagHistory = updatedHistory
                    )
                },
                onFailure = { e ->
                    val err = ErrorMapper.toUserFacingError(e)
                    _detailOps.value = _detailOps.value.copy(
                        isRunningDiag = false,
                        diagOutput = "${err.title}：${err.message}\n${err.suggestion}"
                    )
                }
            )
        }
    }

    suspend fun getServerForEdit(serverId: String?): ServerConfig? =
        serverId?.let { repo.getServerById(it) }

    suspend fun testConnection(config: ServerConfig, trustUnknown: Boolean): ConnectionTestSummary =
        repo.testConnection(config, trustUnknown)

    fun saveServer(config: ServerConfig, onSaved: () -> Unit) {
        viewModelScope.launch {
            repo.saveServer(config)
            if (_selectedServerId.value == null) {
                _selectedServerId.value = config.id
            }
            onSaved()
            performRefreshCycle()
        }
    }

    fun deleteServer(serverId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.deleteServer(serverId)
            if (_selectedServerId.value == serverId) {
                val nextId = servers.value.firstOrNull { it.config.id != serverId }?.config?.id
                _selectedServerId.value = nextId
                _detailOps.value = _detailOps.value.copy(historyPoints = emptyList())
                lastLiveTimestamp = 0L
                if (nextId != null) {
                    startLiveCollector(nextId)
                    loadHistoryInternal(nextId, _detailOps.value.historyWindow)
                } else {
                    liveJob?.cancel()
                }
            }
            onDeleted()
            emitSnackbar("已删除服务器")
        }
    }

    fun saveAlertRule(rule: AlertRule) {
        viewModelScope.launch {
            repo.saveAlertRule(rule)
            emitSnackbar("告警阈值已更新")
        }
    }

    fun deleteAlertRule(ruleId: String) {
        viewModelScope.launch {
            repo.deleteAlertRule(ruleId)
            emitSnackbar("已恢复全局默认阈值")
        }
    }

    fun setPinned(serverId: String, pinned: Boolean) {
        viewModelScope.launch {
            repo.setPinned(serverId, pinned)
            emitSnackbar(if (pinned) "已置顶该服务器" else "已取消置顶")
        }
    }

    fun setEnabled(serverId: String, enabled: Boolean) {
        viewModelScope.launch {
            repo.setEnabled(serverId, enabled)
            emitSnackbar(if (enabled) "已恢复轮询采集" else "已暂停该节点采集")
        }
    }

    fun batchSetPinned(serverIds: Set<String>, pinned: Boolean) {
        if (serverIds.isEmpty()) return
        viewModelScope.launch {
            repo.batchSetPinned(serverIds, pinned)
            emitSnackbar(if (pinned) "已批量置顶 ${serverIds.size} 台服务器" else "已取消置顶 ${serverIds.size} 台服务器")
        }
    }

    fun batchSetEnabled(serverIds: Set<String>, enabled: Boolean) {
        if (serverIds.isEmpty()) return
        viewModelScope.launch {
            repo.batchSetEnabled(serverIds, enabled)
            emitSnackbar(if (enabled) "已恢复 ${serverIds.size} 台节点采集" else "已暂停 ${serverIds.size} 台节点采集")
        }
    }

    fun batchDeleteServers(serverIds: Set<String>) {
        if (serverIds.isEmpty()) return
        viewModelScope.launch {
            repo.batchDeleteServers(serverIds)
            if (_selectedServerId.value in serverIds) {
                val nextId = servers.value.firstOrNull { it.config.id !in serverIds }?.config?.id
                _selectedServerId.value = nextId
                _detailOps.value = _detailOps.value.copy(historyPoints = emptyList())
                lastLiveTimestamp = 0L
                if (nextId != null) {
                    startLiveCollector(nextId)
                    loadHistoryInternal(nextId, _detailOps.value.historyWindow)
                } else {
                    liveJob?.cancel()
                }
            }
            emitSnackbar("已删除选中的 ${serverIds.size} 台服务器")
        }
    }

    fun setSortOption(option: ServerSortOption) {
        viewModelScope.launch { settingsStore.setSortOption(option) }
    }

    fun trustFingerprint(serverId: String, fingerprint: String) {
        viewModelScope.launch {
            repo.trustFingerprint(serverId, fingerprint)
            emitSnackbar("已更新信任的主机指纹")
            performRefreshCycle()
        }
    }

    suspend fun exportBackupJson(
        includeSecrets: Boolean,
        passphrase: String?,
        includeHistory: Boolean = false
    ): String = repo.exportBackupJson(includeSecrets, passphrase, includeHistory)

    fun previewBackupJson(rawJson: String, passphrase: String?): Result<BackupPreview> =
        repo.previewBackupJson(rawJson, passphrase)

    suspend fun importBackupJson(rawJson: String, passphrase: String?): Result<Pair<Int, Int>> {
        val result = repo.importBackupJson(rawJson, passphrase)
        if (result.isSuccess) {
            performRefreshCycle(force = true)
        }
        return result
    }

    suspend fun importBackup(
        rawJson: String,
        passphrase: String?,
        strategy: BackupImportStrategy,
        includeHistory: Boolean
    ): Result<BackupImportSummary> {
        val result = repo.importBackup(rawJson, passphrase, strategy, includeHistory)
        if (result.isSuccess) {
            performRefreshCycle(force = true)
        }
        return result
    }

    fun acknowledgeAlert(id: String) {
        viewModelScope.launch { repo.acknowledgeAlert(id) }
    }

    fun acknowledgeAllAlerts() {
        viewModelScope.launch {
            repo.acknowledgeAllAlerts()
            emitSnackbar("已将全部告警标记为已读")
        }
    }

    fun clearAlerts() {
        viewModelScope.launch {
            repo.clearAlerts()
            emitSnackbar("已清空告警历史")
        }
    }

    fun setThemePreference(pref: ThemePreference) {
        viewModelScope.launch { settingsStore.setThemePreference(pref) }
    }

    fun setPollIntervalSeconds(seconds: Int) {
        viewModelScope.launch { settingsStore.setPollIntervalSeconds(seconds) }
    }

    fun setBackgroundWorkEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setBackgroundWorkEnabled(enabled)
            val interval = settingsStore.settingsFlow.first().backgroundIntervalMinutes
            MonitoringWorker.schedule(app, enabled, interval, forceUpdate = true)
        }
    }

    fun setBackgroundIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsStore.setBackgroundIntervalMinutes(minutes)
            val enabled = settingsStore.settingsFlow.first().backgroundWorkEnabled
            if (enabled) {
                MonitoringWorker.schedule(app, true, minutes, forceUpdate = true)
            }
        }
    }

    fun setForegroundServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setForegroundServiceEnabled(enabled)
            ForegroundMonitorService.setRunning(app, enabled)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setNotificationsEnabled(enabled) }
    }

    fun setNotifyOnRecovery(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setNotifyOnRecovery(enabled) }
    }

    fun setDndConfig(
        enabled: Boolean,
        startMinute: Int,
        endMinute: Int,
        allowCritical: Boolean
    ) {
        viewModelScope.launch {
            settingsStore.setDndConfig(enabled, startMinute, endMinute, allowCritical)
        }
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            repo.clearLocalMetricsCache()
            emitSnackbar("已清理本地历史缓存")
        }
    }

    fun isAbnormalStatus(status: ServerStatus): Boolean =
        status == ServerStatus.WARNING || status == ServerStatus.CRITICAL || status == ServerStatus.OFFLINE
}
