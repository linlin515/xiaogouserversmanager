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
    val logTitle: String = "",
    val logLines: List<String> = emptyList(),
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
                performRefreshCycle()
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

    fun openLogs(serverId: String, targetType: LogTargetType, targetName: String) {
        viewModelScope.launch {
            val title = if (targetType == LogTargetType.SYSTEM_JOURNAL) "系统日志" else targetName
            _detailOps.value = _detailOps.value.copy(
                logTitle = title,
                logLines = emptyList(),
                isLogsOpen = true,
                isLoadingLogs = true
            )
            val cfg = repo.getServerById(serverId)
            if (cfg == null) {
                _detailOps.value = _detailOps.value.copy(isLoadingLogs = false, logLines = listOf("未找到服务器配置"))
                return@launch
            }
            repo.fetchLogs(cfg, targetType, targetName, lines = 150).fold(
                onSuccess = { lines ->
                    _detailOps.value = _detailOps.value.copy(
                        isLoadingLogs = false,
                        logLines = if (lines.isEmpty()) listOf("暂无日志输出") else lines
                    )
                },
                onFailure = { e ->
                    val err = ErrorMapper.toUserFacingError(e)
                    _detailOps.value = _detailOps.value.copy(
                        isLoadingLogs = false,
                        logLines = listOf("${err.title}：${err.message}")
                    )
                }
            )
        }
    }

    fun closeLogs() {
        _detailOps.value = _detailOps.value.copy(isLogsOpen = false)
    }

    fun restartTarget(serverId: String, targetType: LogTargetType, targetName: String) {
        viewModelScope.launch {
            _detailOps.value = _detailOps.value.copy(isActionPending = true)
            val cfg = repo.getServerById(serverId)
            if (cfg != null) {
                repo.restartTarget(cfg, targetType, targetName).fold(
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
