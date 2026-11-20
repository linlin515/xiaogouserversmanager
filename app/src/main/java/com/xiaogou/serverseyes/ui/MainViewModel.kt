package com.xiaogou.serverseyes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaogou.serverseyes.ServersEyesApplication
import com.xiaogou.serverseyes.core.model.AlertEvent
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.LogTargetType
import com.xiaogou.serverseyes.core.model.MetricPoint
import com.xiaogou.serverseyes.core.model.ServerConfig
import com.xiaogou.serverseyes.core.model.ServerStatus
import com.xiaogou.serverseyes.core.model.ThemePreference
import com.xiaogou.serverseyes.core.model.WorkloadAction
import com.xiaogou.serverseyes.core.parser.ErrorMapper
import com.xiaogou.serverseyes.data.local.AppSettings
import com.xiaogou.serverseyes.data.repository.ConnectionTestSummary
import com.xiaogou.serverseyes.data.repository.ServerUiModel
import com.xiaogou.serverseyes.service.ForegroundMonitorService
import com.xiaogou.serverseyes.service.MonitoringWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

data class DetailOpsState(
    val historyPoints: List<MetricPoint> = emptyList(),
    val historyMinutes: Int = 60,
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
    val isRunningDiag: Boolean = false,
    val isActionPending: Boolean = false
)

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

    private val _snackbarEvents = MutableSharedFlow<String>()
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            repo.ensureDefaultAlertRules()
            val list = repo.observeServers().first()
            if (list.isNotEmpty() && _selectedServerId.value == null) {
                _selectedServerId.value = list.first().config.id
            }
            _isInitialLoading.value = false
            startPolling()
        }
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                if (!ForegroundMonitorService.isServiceRunning.value) {
                    performRefreshCycle()
                } else {
                    _selectedServerId.value?.let { id ->
                        loadHistoryInternal(id, _detailOps.value.historyMinutes)
                    }
                }
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
            performRefreshCycle()
        }
    }

    private suspend fun performRefreshCycle() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        try {
            repo.refreshAllServers()
            _lastRefreshMs.value = System.currentTimeMillis()
            _selectedServerId.value?.let { id ->
                loadHistoryInternal(id, _detailOps.value.historyMinutes)
            }
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
        _detailOps.value = DetailOpsState()
        viewModelScope.launch {
            loadHistoryInternal(serverId, _detailOps.value.historyMinutes)
        }
    }

    fun changeHistoryRange(serverId: String, minutes: Int) {
        _detailOps.value = _detailOps.value.copy(historyMinutes = minutes)
        viewModelScope.launch {
            loadHistoryInternal(serverId, minutes)
        }
    }

    private suspend fun loadHistoryInternal(serverId: String, minutes: Int) {
        val cfg = repo.getServerById(serverId) ?: return
        val pts = repo.fetchHistoryWithAgentFallback(cfg, minutes)
        _detailOps.value = _detailOps.value.copy(historyPoints = pts)
    }

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
                    onSuccess = { msg -> _snackbarEvents.emit(msg) },
                    onFailure = { e ->
                        val err = ErrorMapper.toUserFacingError(e)
                        _snackbarEvents.emit("${err.title}：${err.message}")
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
                    _detailOps.value = _detailOps.value.copy(
                        isRunningDiag = false,
                        diagOutput = "$ $command\n${out.ifBlank { "(无输出)" }}"
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
                _selectedServerId.value = servers.value.firstOrNull { it.config.id != serverId }?.config?.id
            }
            onDeleted()
            _snackbarEvents.emit("已删除服务器")
        }
    }

    fun saveAlertRule(rule: AlertRule) {
        viewModelScope.launch {
            repo.saveAlertRule(rule)
            _snackbarEvents.emit("告警阈值已更新")
        }
    }

    fun deleteAlertRule(ruleId: String) {
        viewModelScope.launch {
            repo.deleteAlertRule(ruleId)
            _snackbarEvents.emit("已恢复全局默认阈值")
        }
    }

    fun setPinned(serverId: String, pinned: Boolean) {
        viewModelScope.launch {
            repo.setPinned(serverId, pinned)
            _snackbarEvents.emit(if (pinned) "已置顶该服务器" else "已取消置顶")
        }
    }

    fun setEnabled(serverId: String, enabled: Boolean) {
        viewModelScope.launch {
            repo.setEnabled(serverId, enabled)
            _snackbarEvents.emit(if (enabled) "已恢复轮询采集" else "已暂停该节点采集")
        }
    }

    fun trustFingerprint(serverId: String, fingerprint: String) {
        viewModelScope.launch {
            repo.trustFingerprint(serverId, fingerprint)
            _snackbarEvents.emit("已更新信任的主机指纹")
            performRefreshCycle()
        }
    }

    suspend fun exportBackupJson(includeSecrets: Boolean, passphrase: String?): String =
        repo.exportBackupJson(includeSecrets, passphrase)

    suspend fun importBackupJson(rawJson: String, passphrase: String?): Result<Pair<Int, Int>> {
        val result = repo.importBackupJson(rawJson, passphrase)
        if (result.isSuccess) {
            performRefreshCycle()
        }
        return result
    }

    fun acknowledgeAlert(id: String) {
        viewModelScope.launch { repo.acknowledgeAlert(id) }
    }

    fun acknowledgeAllAlerts() {
        viewModelScope.launch {
            repo.acknowledgeAllAlerts()
            _snackbarEvents.emit("已将全部告警标记为已读")
        }
    }

    fun clearAlerts() {
        viewModelScope.launch {
            repo.clearAlerts()
            _snackbarEvents.emit("已清空告警历史")
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
            MonitoringWorker.schedule(app, enabled)
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

    fun clearLocalCache() {
        viewModelScope.launch {
            repo.clearLocalMetricsCache()
            _snackbarEvents.emit("已清理本地历史缓存")
        }
    }

    fun isAbnormalStatus(status: ServerStatus): Boolean =
        status == ServerStatus.WARNING || status == ServerStatus.CRITICAL || status == ServerStatus.OFFLINE
}
