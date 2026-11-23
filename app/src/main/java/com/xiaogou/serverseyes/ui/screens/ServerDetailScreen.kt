package com.xiaogou.serverseyes.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xiaogou.serverseyes.core.designsystem.components.AppCard
import com.xiaogou.serverseyes.core.designsystem.components.AppTextField
import com.xiaogou.serverseyes.core.designsystem.components.ConfirmActionDialog
import com.xiaogou.serverseyes.core.designsystem.components.DangerButton
import com.xiaogou.serverseyes.core.designsystem.components.PrimaryButton
import com.xiaogou.serverseyes.core.designsystem.components.SecondaryButton
import com.xiaogou.serverseyes.core.designsystem.components.SectionHeader
import com.xiaogou.serverseyes.core.designsystem.patterns.EmptyState
import com.xiaogou.serverseyes.core.designsystem.patterns.ErrorBanner
import com.xiaogou.serverseyes.core.designsystem.patterns.FilterChipRow
import com.xiaogou.serverseyes.core.designsystem.patterns.LoadingSkeletonList
import com.xiaogou.serverseyes.core.designsystem.primitives.AppDivider
import com.xiaogou.serverseyes.core.designsystem.primitives.MetricProgressBar
import com.xiaogou.serverseyes.core.designsystem.primitives.MetricTrendChart
import com.xiaogou.serverseyes.core.designsystem.primitives.StatusBadge
import com.xiaogou.serverseyes.core.designsystem.primitives.StatusChipRaw
import com.xiaogou.serverseyes.core.designsystem.primitives.TrendPoint
import com.xiaogou.serverseyes.core.designsystem.primitives.TrendSeries
import com.xiaogou.serverseyes.core.designsystem.primitives.TrendThreshold
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.tokens.AppRadius
import com.xiaogou.serverseyes.core.designsystem.tokens.AppSpacing
import com.xiaogou.serverseyes.core.designsystem.tokens.AppColorTokens
import com.xiaogou.serverseyes.core.designsystem.tokens.tabular
import com.xiaogou.serverseyes.core.model.AlertEvent
import com.xiaogou.serverseyes.core.model.AlertMetricType
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.AlertSeverity
import com.xiaogou.serverseyes.core.model.CollectionMode
import com.xiaogou.serverseyes.core.model.DiagnosticPreset
import com.xiaogou.serverseyes.core.model.HistoryWindow
import com.xiaogou.serverseyes.core.model.LogTargetType
import com.xiaogou.serverseyes.core.model.MetricPoint
import com.xiaogou.serverseyes.core.model.MetricSource
import com.xiaogou.serverseyes.core.model.ProcessSummary
import com.xiaogou.serverseyes.core.model.TrendMetric
import com.xiaogou.serverseyes.core.model.WorkloadAction
import com.xiaogou.serverseyes.core.util.Formatters
import com.xiaogou.serverseyes.data.alert.AlertEvaluator
import com.xiaogou.serverseyes.data.repository.ServerUiModel
import com.xiaogou.serverseyes.ui.ContainerStateFilter
import com.xiaogou.serverseyes.ui.DetailOpsState
import com.xiaogou.serverseyes.ui.DetailTab
import com.xiaogou.serverseyes.ui.ProcessSort
import com.xiaogou.serverseyes.ui.ServiceStateFilter
import java.util.Locale
import kotlin.math.roundToInt

private data class PendingWorkload(
    val targetType: LogTargetType,
    val targetName: String,
    val action: WorkloadAction
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerDetailPane(
    server: ServerUiModel?,
    opsState: DetailOpsState,
    onRefreshServer: (String) -> Unit,
    onChangeHistoryRange: (String, Int) -> Unit,
    onOpenLogs: (String, LogTargetType, String, Int) -> Unit,
    onRefreshLogs: (Int) -> Unit,
    onCloseLogs: () -> Unit,
    onPerformWorkloadAction: (String, LogTargetType, String, WorkloadAction) -> Unit,
    onRunDiagnostic: (String, String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit = { _, _ -> },
    onToggleEnable: (String, Boolean) -> Unit = { _, _ -> },
    onEditServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    alertEvents: List<AlertEvent> = emptyList(),
    alertRules: List<AlertRule> = emptyList(),
    onSelectTrendMetric: (TrendMetric) -> Unit = {},
    onToggleLiveMode: (Boolean) -> Unit = {},
    onSelectDetailTab: (DetailTab) -> Unit = {},
    onSelectProcessSort: (ProcessSort) -> Unit = {},
    onSelectServiceFilter: (ServiceStateFilter) -> Unit = {},
    onWorkloadSearchChange: (String) -> Unit = {},
    onSelectContainerFilter: (ContainerStateFilter) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    if (server == null) {
        Column(modifier = modifier.fillMaxSize().padding(AppSpacing.space4)) {
            EmptyState(
                title = "请选择一台服务器",
                description = "点击左侧服务器卡片查看 CPU/内存历史曲线、Docker 容器、Systemd 服务与实时日志。"
            )
        }
        return
    }

    var pendingAction by remember { mutableStateOf<PendingWorkload?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var customCmd by remember { mutableStateOf("") }
    var diagCopyHint by remember { mutableStateOf<String?>(null) }

    val snap = server.snapshot
    val isEnabled = server.config.enabled

    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding().testTag("detail_list"),
        contentPadding = PaddingValues(AppSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.space3)
    ) {
        // Header card
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (server.config.isPinned) {
                                    Icon(
                                        imageVector = Icons.Rounded.PushPin,
                                        contentDescription = "已置顶",
                                        tint = colors.actionPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = server.config.name,
                                    style = AppTheme.typography.titleLarge,
                                    color = colors.textPrimary
                                )
                            }
                            Text(
                                text = buildString {
                                    append(server.config.host)
                                    if (snap?.osName?.isNotBlank() == true) append(" · ${snap.osName}")
                                    if (snap != null && snap.uptimeSeconds > 0) {
                                        append(" · 运行 ${Formatters.formatUptime(snap.uptimeSeconds)}")
                                    }
                                },
                                style = AppTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }

                        Row {
                            IconButton(onClick = { onTogglePin(server.config.id, !server.config.isPinned) }) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = if (server.config.isPinned) "取消置顶" else "置顶",
                                    tint = if (server.config.isPinned) colors.actionPrimary else colors.textSecondary
                                )
                            }
                            IconButton(onClick = { onRefreshServer(server.config.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "立即刷新节点指标",
                                    tint = colors.textSecondary
                                )
                            }
                            IconButton(onClick = { onEditServer(server.config.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "编辑服务器",
                                    tint = colors.textSecondary
                                )
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "删除服务器",
                                    tint = colors.error
                                )
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!isEnabled) {
                            StatusChipRaw(
                                text = "已暂停采集",
                                icon = null,
                                backgroundColor = colors.surfaceSubtle,
                                contentColor = colors.textSecondary
                            )
                        } else {
                            StatusBadge(status = server.status)
                        }
                        val sourceText = when (snap?.sourceUsed) {
                            MetricSource.AGENT -> "Agent · ${snap.latencyMs}ms"
                            MetricSource.SSH -> "SSH · ${snap.latencyMs}ms"
                            null -> server.config.collectionMode.name
                        }
                        StatusChipRaw(
                            text = sourceText,
                            icon = null,
                            backgroundColor = colors.surfaceSubtle,
                            contentColor = colors.textSecondary
                        )
                        snap?.cpuTempCelsius?.let { temp ->
                            val hot = temp >= 80.0
                            StatusChipRaw(
                                text = String.format(Locale.US, "%.1f°C", temp),
                                icon = Icons.Rounded.DeviceThermostat,
                                backgroundColor = if (hot) colors.warningSoft else colors.successSoft,
                                contentColor = if (hot) colors.warning else colors.success
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isEnabled) {
                                "更新于 ${Formatters.formatRelativeTime(server.lastSeenAtMs)}"
                            } else {
                                "采集已暂停"
                            },
                            style = AppTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        if (opsState.isLoadingHistory) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = colors.actionPrimary
                            )
                        }
                        Text(
                            text = "暂停采集",
                            style = AppTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Switch(
                            checked = !isEnabled,
                            onCheckedChange = { paused -> onToggleEnable(server.config.id, !paused) }
                        )
                    }
                }
            }
        }

        if (server.lastError != null) {
            item {
                ErrorBanner(
                    title = server.lastError.title,
                    message = "${server.lastError.message} ${server.lastError.suggestion}",
                    onRetry = { onRefreshServer(server.config.id) }
                )
            }
        }

        if (snap != null) {
            item {
                val rootDisk = snap.disks.firstOrNull { it.mountPoint == "/" } ?: snap.disks.firstOrNull()
                val loadSub = String.format(
                    Locale.US,
                    "5m %.1f · 15m %.1f",
                    snap.load5, snap.load15
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiCell(
                            label = "CPU",
                            value = "${snap.cpuPercent.roundToInt()}%",
                            sub = "${snap.cpuCores} 核",
                            modifier = Modifier.weight(1f)
                        )
                        KpiCell(
                            label = "内存",
                            value = "${snap.memPercent.roundToInt()}%",
                            sub = "${Formatters.formatBytes(snap.memUsedBytes)} / ${Formatters.formatBytes(snap.memTotalBytes)}",
                            modifier = Modifier.weight(1f)
                        )
                        KpiCell(
                            label = "磁盘",
                            value = "${snap.maxDiskPercent.roundToInt()}%",
                            sub = rootDisk?.mountPoint ?: "无挂载点",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiCell(
                            label = "温度",
                            value = snap.cpuTempCelsius?.let { String.format(Locale.US, "%.1f°C", it) } ?: "--",
                            sub = if ((snap.cpuTempCelsius ?: 0.0) >= 80.0) "偏高" else "正常",
                            highlight = (snap.cpuTempCelsius ?: 0.0) >= 80.0,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCell(
                            label = "Load 1",
                            value = String.format(Locale.US, "%.2f", snap.load1),
                            sub = loadSub,
                            modifier = Modifier.weight(1f)
                        )
                        KpiCell(
                            label = "下行",
                            value = Formatters.formatBytesPerSec(snap.netRxBytesPerSec),
                            sub = "上行 ${Formatters.formatBytesPerSec(snap.netTxBytesPerSec)}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Sub-tabs
        item {
            Surface(
                shape = RoundedCornerShape(AppRadius.md),
                color = colors.surfaceSubtle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DetailTab.entries.forEach { tab ->
                        val active = tab == opsState.detailTab
                        Surface(
                            onClick = { onSelectDetailTab(tab) },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shape = RoundedCornerShape(AppRadius.sm),
                            color = if (active) colors.surface else Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                                Text(
                                    text = tab.label,
                                    style = AppTheme.typography.labelLarge,
                                    color = if (active) colors.textPrimary else colors.textSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        when (opsState.detailTab) {
            DetailTab.OVERVIEW -> {
                if (snap == null) {
                    item {
                        EmptyState(
                            title = "等待首次指标采集",
                            description = "正在尝试建立连接并获取服务器实时资源数据，请稍候或点击重试。"
                        )
                    }
                } else {
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "性能趋势",
                                    style = AppTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                    maxLines = 1
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "实时",
                                        style = AppTheme.typography.labelSmall,
                                        color = colors.textSecondary
                                    )
                                    Switch(
                                        checked = opsState.isLiveMode,
                                        onCheckedChange = onToggleLiveMode
                                    )
                                }
                            }
                            Spacer(Modifier.height(AppSpacing.space2))
                            FilterChipRow(
                                options = TrendMetric.entries.map { it.label },
                                selected = opsState.historyMetric.label,
                                onSelect = { label ->
                                    TrendMetric.entries.firstOrNull { it.label == label }
                                        ?.let(onSelectTrendMetric)
                                }
                            )
                            Spacer(Modifier.height(6.dp))
                            FilterChipRow(
                                options = HistoryWindow.entries.map { it.label },
                                selected = opsState.historyWindow.label,
                                onSelect = { label ->
                                    HistoryWindow.entries.firstOrNull { it.label == label }
                                        ?.let { onChangeHistoryRange(server.config.id, it.minutes) }
                                }
                            )
                            Spacer(Modifier.height(AppSpacing.space2))

                            val trend = remember(
                                opsState.historyPoints,
                                opsState.historyMetric,
                                opsState.historyWindow,
                                alertRules,
                                server.config.id,
                                colors
                            ) {
                                buildTrendSpec(
                                    metric = opsState.historyMetric,
                                    points = opsState.historyPoints,
                                    windowLabel = opsState.historyWindow.label,
                                    rules = AlertEvaluator
                                        .effectiveRulesForServer(server.config.id, alertRules)
                                        .values
                                        .toList(),
                                    colors = colors
                                )
                            }

                            when {
                                opsState.historyError != null -> ErrorBanner(
                                    title = "趋势数据加载失败",
                                    message = opsState.historyError,
                                    onRetry = { onRefreshServer(server.config.id) }
                                )
                                opsState.isLoadingHistory && opsState.historyPoints.isEmpty() ->
                                    LoadingSkeletonList()
                                trend.series.all { it.points.isEmpty() } -> EmptyState(
                                    title = "暂无 ${opsState.historyWindow.label} 历史采样",
                                    description = "节点刚接入或采集被暂停，历史曲线会在下一次采样后出现。",
                                    actionLabel = "立即刷新",
                                    onAction = { onRefreshServer(server.config.id) }
                                )
                                else -> {
                                    MetricTrendChart(
                                        series = trend.series,
                                        yMax = trend.yMax,
                                        unit = trend.unit,
                                        windowMinutes = opsState.historyWindow.minutes,
                                        thresholds = trend.thresholds,
                                        contentDescription = trend.description
                                    )
                                    Spacer(Modifier.height(AppSpacing.space2))
                                    TrendLegend(spec = trend)
                                }
                            }
                        }
                    }

                    // Memory & Swap card
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)
                        ) {
                            AppCard(modifier = Modifier.weight(1f)) {
                                Text("物理内存", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${snap.memPercent.roundToInt()}%",
                                    style = AppTheme.typography.titleMedium.tabular(),
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "${Formatters.formatBytes(snap.memUsedBytes)} / ${Formatters.formatBytes(snap.memTotalBytes)}",
                                    style = AppTheme.typography.bodySmall.tabular(),
                                    color = colors.textSecondary
                                )
                                Spacer(Modifier.height(AppSpacing.space2))
                                MetricProgressBar(percent = snap.memPercent)
                            }
                            AppCard(modifier = Modifier.weight(1f)) {
                                val swapPct = if (snap.swapTotalBytes > 0) {
                                    (snap.swapUsedBytes.toDouble() / snap.swapTotalBytes.toDouble() * 100.0).coerceIn(0.0, 100.0)
                                } else 0.0
                                Text("Swap 交换区", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (snap.swapTotalBytes > 0) "${swapPct.roundToInt()}%" else "未启用",
                                    style = AppTheme.typography.titleMedium.tabular(),
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = if (snap.swapTotalBytes > 0) {
                                        "${Formatters.formatBytes(snap.swapUsedBytes)} / ${Formatters.formatBytes(snap.swapTotalBytes)}"
                                    } else "0 B",
                                    style = AppTheme.typography.bodySmall.tabular(),
                                    color = colors.textSecondary
                                )
                                Spacer(Modifier.height(AppSpacing.space2))
                                MetricProgressBar(percent = swapPct)
                            }
                        }
                    }

                    // Realtime I/O throughput (Network + Disk)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)
                        ) {
                            AppCard(modifier = Modifier.weight(1f)) {
                                Text("实时网络吞吐", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "RX ${Formatters.formatBytesPerSec(snap.netRxBytesPerSec)}",
                                    style = AppTheme.typography.labelLarge.tabular(),
                                    color = colors.actionPrimary
                                )
                                Text(
                                    text = "TX ${Formatters.formatBytesPerSec(snap.netTxBytesPerSec)}",
                                    style = AppTheme.typography.bodySmall.tabular(),
                                    color = colors.warning
                                )
                            }
                            AppCard(modifier = Modifier.weight(1f)) {
                                Text("实时磁盘 I/O", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "读 ${Formatters.formatBytesPerSec(snap.diskReadBytesPerSec)}",
                                    style = AppTheme.typography.labelLarge.tabular(),
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "写 ${Formatters.formatBytesPerSec(snap.diskWriteBytesPerSec)}",
                                    style = AppTheme.typography.bodySmall.tabular(),
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }

                    if (snap.disks.isNotEmpty()) {
                        item {
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                SectionHeader(title = "磁盘挂载 (${snap.disks.size})")
                                Spacer(Modifier.height(AppSpacing.space2))
                                snap.disks.forEachIndexed { idx, disk ->
                                    if (idx > 0) Spacer(Modifier.height(AppSpacing.space2))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = disk.mountPoint,
                                            style = AppTheme.typography.labelLarge,
                                            color = colors.textPrimary
                                        )
                                        val inodeSuffix = disk.inodeUsedPercent?.let { " · Inode ${it.roundToInt()}%" }.orEmpty()
                                        Text(
                                            text = "${disk.usagePercent.roundToInt()}%$inodeSuffix · ${Formatters.formatBytes(disk.usedBytes)} / ${Formatters.formatBytes(disk.totalBytes)}",
                                            style = AppTheme.typography.bodySmall.tabular(),
                                            color = colors.textSecondary
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    MetricProgressBar(percent = disk.usagePercent)
                                }
                            }
                        }
                    }

                    val serverAlerts = alertEvents
                        .filter { it.serverId == server.config.id }
                        .take(5)
                    if (serverAlerts.isNotEmpty()) {
                        item {
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "近期告警",
                                        style = AppTheme.typography.titleSmall,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "最近 ${serverAlerts.size} 条",
                                        style = AppTheme.typography.labelSmall,
                                        color = colors.textDisabled
                                    )
                                }
                                Spacer(Modifier.height(AppSpacing.space2))
                                serverAlerts.forEachIndexed { idx, event ->
                                    if (idx > 0) AppDivider(Modifier.padding(vertical = 8.dp))
                                    AlertTimelineRow(event = event)
                                }
                            }
                        }
                    }

                    if (snap.topProcesses.isNotEmpty()) {
                        item {
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                SectionHeader(title = "Top 进程")
                                Spacer(Modifier.height(AppSpacing.space2))
                                FilterChipRow(
                                    options = ProcessSort.entries.map { it.label },
                                    selected = opsState.processSort.label,
                                    onSelect = { label ->
                                        ProcessSort.entries.firstOrNull { it.label == label }
                                            ?.let(onSelectProcessSort)
                                    }
                                )
                                Spacer(Modifier.height(AppSpacing.space2))
                                val sorted = if (opsState.processSort == ProcessSort.CPU) {
                                    snap.topProcesses.sortedByDescending { it.cpuPercent }
                                } else {
                                    snap.topProcesses.sortedByDescending { it.memPercent }
                                }
                                val maxValue = sorted.maxOfOrNull {
                                    if (opsState.processSort == ProcessSort.CPU) it.cpuPercent else it.memPercent
                                }?.coerceAtLeast(1.0) ?: 1.0
                                sorted.take(8).forEachIndexed { idx, proc ->
                                    if (idx > 0) AppDivider(Modifier.padding(vertical = 6.dp))
                                    ProcessRow(
                                        proc = proc,
                                        sort = opsState.processSort,
                                        maxValue = maxValue
                                    )
                                }
                            }
                        }
                    }
                }
            }

            DetailTab.WORKLOADS -> {
                if (snap == null) {
                    item {
                        EmptyState(title = "暂无容器与服务数据", description = "请先完成至少一次节点采集。")
                    }
                } else {
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.space2)) {
                                AppTextField(
                                    label = "搜索容器 / 服务",
                                    value = opsState.workloadSearch,
                                    onValueChange = onWorkloadSearchChange,
                                    placeholder = "按容器名、镜像或 systemd 单元过滤..."
                                )
                                FilterChipRow(
                                    options = ContainerStateFilter.entries.map { it.label },
                                    selected = opsState.containerFilter.label,
                                    onSelect = { label ->
                                        ContainerStateFilter.entries.firstOrNull { it.label == label }
                                            ?.let(onSelectContainerFilter)
                                    }
                                )
                            }
                        }
                    }

                    val filteredContainers = snap.containers.filter { c ->
                        val matchState = when (opsState.containerFilter) {
                            ContainerStateFilter.ALL -> true
                            ContainerStateFilter.RUNNING -> c.isRunning
                            ContainerStateFilter.STOPPED -> !c.isRunning
                        }
                        val matchQuery = opsState.workloadSearch.isBlank() ||
                            c.name.contains(opsState.workloadSearch, ignoreCase = true) ||
                            c.image.contains(opsState.workloadSearch, ignoreCase = true)
                        matchState && matchQuery
                    }

                    val filteredServices = snap.services.filter { sv ->
                        val matchState = when (opsState.serviceFilter) {
                            ServiceStateFilter.ALL -> true
                            ServiceStateFilter.HEALTHY -> sv.isHealthy
                            ServiceStateFilter.UNHEALTHY -> !sv.isHealthy
                        }
                        val matchQuery = opsState.workloadSearch.isBlank() ||
                            sv.unit.contains(opsState.workloadSearch, ignoreCase = true) ||
                            sv.description.contains(opsState.workloadSearch, ignoreCase = true)
                        matchState && matchQuery
                    }

                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            SectionHeader(title = "Docker 容器 (${filteredContainers.size}/${snap.containers.size})")
                            Spacer(Modifier.height(AppSpacing.space2))
                            if (filteredContainers.isEmpty()) {
                                Text(
                                    text = if (snap.containers.isEmpty()) "未检测到 Docker 容器或宿主机未安装 Docker。" else "当前筛选条件下无匹配容器。",
                                    style = AppTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            } else {
                                filteredContainers.forEachIndexed { idx, c ->
                                    if (idx > 0) AppDivider(Modifier.padding(vertical = 8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(c.name, style = AppTheme.typography.titleSmall, color = colors.textPrimary)
                                                Text("${c.image} · ${c.statusText}", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                                                if (c.ports.isNotBlank()) {
                                                    Text("端口: ${c.ports}", style = AppTheme.typography.bodySmall, color = colors.actionPrimary)
                                                }
                                            }
                                            StatusChipRaw(
                                                text = if (c.isRunning) "运行中" else c.state,
                                                icon = null,
                                                backgroundColor = if (c.isRunning) colors.successSoft else colors.errorSoft,
                                                contentColor = if (c.isRunning) colors.success else colors.error
                                            )
                                        }
                                        if (c.cpuPercent != null || c.memUsageText.isNotBlank()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = c.cpuPercent?.let { String.format(Locale.US, "CPU %.1f%%", it) } ?: "CPU --",
                                                        style = AppTheme.typography.bodySmall.tabular(),
                                                        color = colors.actionPrimary
                                                    )
                                                    val memPctSuffix = c.memPercent?.let { String.format(Locale.US, " (%.1f%%)", it) }.orEmpty()
                                                    Text(
                                                        text = if (c.memUsageText.isNotBlank()) "MEM ${c.memUsageText}$memPctSuffix" else "MEM --",
                                                        style = AppTheme.typography.bodySmall.tabular(),
                                                        color = colors.textSecondary
                                                    )
                                                }
                                                c.memPercent?.let { pct ->
                                                    MetricProgressBar(percent = pct, height = 4.dp)
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)
                                        ) {
                                            SecondaryButton(
                                                text = "查看日志",
                                                onClick = { onOpenLogs(server.config.id, LogTargetType.DOCKER_CONTAINER, c.name, 150) }
                                            )
                                            if (c.isRunning) {
                                                SecondaryButton(
                                                    text = "停止",
                                                    icon = Icons.Rounded.Stop,
                                                    enabled = !opsState.isActionPending,
                                                    onClick = {
                                                        pendingAction = PendingWorkload(LogTargetType.DOCKER_CONTAINER, c.name, WorkloadAction.STOP)
                                                    }
                                                )
                                                DangerButton(
                                                    text = "重启",
                                                    enabled = !opsState.isActionPending,
                                                    onClick = {
                                                        pendingAction = PendingWorkload(LogTargetType.DOCKER_CONTAINER, c.name, WorkloadAction.RESTART)
                                                    }
                                                )
                                            } else {
                                                PrimaryButton(
                                                    text = "启动",
                                                    icon = Icons.Rounded.PlayArrow,
                                                    enabled = !opsState.isActionPending,
                                                    onClick = {
                                                        pendingAction = PendingWorkload(LogTargetType.DOCKER_CONTAINER, c.name, WorkloadAction.START)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            SectionHeader(title = "Systemd 服务 (${filteredServices.size}/${snap.services.size})")
                            Spacer(Modifier.height(AppSpacing.space2))
                            FilterChipRow(
                                options = ServiceStateFilter.entries.map { it.label },
                                selected = opsState.serviceFilter.label,
                                onSelect = { label ->
                                    ServiceStateFilter.entries.firstOrNull { it.label == label }
                                        ?.let(onSelectServiceFilter)
                                }
                            )
                            Spacer(Modifier.height(AppSpacing.space2))
                            if (filteredServices.isEmpty()) {
                                Text(
                                    text = if (snap.services.isEmpty()) "未获取到 Systemd 服务列表。" else "无匹配的 Systemd 服务。",
                                    style = AppTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            } else {
                                filteredServices.take(25).forEachIndexed { idx, sv ->
                                    if (idx > 0) AppDivider(Modifier.padding(vertical = 8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(sv.unit, style = AppTheme.typography.labelLarge, color = colors.textPrimary)
                                                if (sv.description.isNotBlank()) {
                                                    Text(sv.description, style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                                                }
                                            }
                                            StatusChipRaw(
                                                text = sv.activeState,
                                                icon = null,
                                                backgroundColor = if (sv.isHealthy) colors.successSoft else colors.errorSoft,
                                                contentColor = if (sv.isHealthy) colors.success else colors.error
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)) {
                                            SecondaryButton(
                                                text = "日志",
                                                onClick = { onOpenLogs(server.config.id, LogTargetType.SYSTEMD_SERVICE, sv.unit, 150) }
                                            )
                                            DangerButton(
                                                text = "重启",
                                                enabled = !opsState.isActionPending,
                                                onClick = {
                                                    pendingAction = PendingWorkload(LogTargetType.SYSTEMD_SERVICE, sv.unit, WorkloadAction.RESTART)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (snap.ports.isNotEmpty()) {
                        item {
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                SectionHeader(title = "监听端口 (${snap.ports.size})")
                                Spacer(Modifier.height(AppSpacing.space2))
                                snap.ports.take(20).forEach { p ->
                                    Text(
                                        text = "${p.protocol.uppercase()}  ${p.localAddress}:${p.port}",
                                        style = AppTheme.typography.monoBody,
                                        color = colors.textPrimary,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            DetailTab.OPS -> {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(title = "快捷只读诊断（SSH）")
                        Spacer(Modifier.height(AppSpacing.space2))

                        if (server.config.collectionMode == CollectionMode.AGENT_ONLY) {
                            Text(
                                text = "提示：当前服务器配置为「仅 Agent」模式。执行只读命令诊断需要 SSH 登录凭据，建议在编辑页面将模式切换为「混合模式」或「仅 SSH」。",
                                style = AppTheme.typography.bodySmall,
                                color = colors.warning,
                                modifier = Modifier.padding(bottom = AppSpacing.space2)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.space2)) {
                            DiagnosticPreset.DEFAULT_PRESETS.forEach { preset ->
                                Surface(
                                    onClick = { onRunDiagnostic(server.config.id, preset.command) },
                                    enabled = !opsState.isRunningDiag && server.config.collectionMode != CollectionMode.AGENT_ONLY,
                                    shape = RoundedCornerShape(AppRadius.sm),
                                    color = colors.surfaceSubtle,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = AppSpacing.space3, vertical = AppSpacing.space2)) {
                                        Text(preset.title, style = AppTheme.typography.labelLarge, color = colors.textPrimary)
                                        Text(preset.description, style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(AppSpacing.space3))
                        AppTextField(
                            label = "自定义诊断命令",
                            value = customCmd,
                            onValueChange = { customCmd = it },
                            placeholder = "例如：uptime && df -h"
                        )
                        if (opsState.diagHistory.isNotEmpty()) {
                            Spacer(Modifier.height(AppSpacing.space2))
                            Text("最近执行记录（点击回填）", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                opsState.diagHistory.forEach { cmd ->
                                    Surface(
                                        onClick = { customCmd = cmd },
                                        shape = RoundedCornerShape(AppRadius.full),
                                        color = colors.surfaceSubtle
                                    ) {
                                        Text(
                                            text = cmd.take(28) + if (cmd.length > 28) "..." else "",
                                            style = AppTheme.typography.labelSmall,
                                            color = colors.actionPrimary,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(AppSpacing.space2))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)
                        ) {
                            PrimaryButton(
                                text = "执行命令",
                                loading = opsState.isRunningDiag,
                                enabled = customCmd.isNotBlank() && server.config.collectionMode != CollectionMode.AGENT_ONLY,
                                icon = Icons.Rounded.Terminal,
                                onClick = { onRunDiagnostic(server.config.id, customCmd.trim()) }
                            )
                            SecondaryButton(
                                text = "系统日志",
                                onClick = { onOpenLogs(server.config.id, LogTargetType.SYSTEM_JOURNAL, "system", 150) }
                            )
                            SecondaryButton(
                                text = "复制输出",
                                icon = Icons.Rounded.ContentCopy,
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("server-diag-output", opsState.diagOutput))
                                    diagCopyHint = "已复制诊断输出到剪贴板"
                                }
                            )
                        }
                        if (diagCopyHint != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(diagCopyHint!!, style = AppTheme.typography.bodySmall, color = colors.success)
                        }
                        Spacer(Modifier.height(AppSpacing.space3))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppRadius.sm))
                                .background(Color(0xFF0B0F14))
                                .padding(AppSpacing.space3)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            SelectionContainer {
                                Text(
                                    text = opsState.diagOutput,
                                    style = AppTheme.typography.monoBody,
                                    color = Color(0xFFCDD9E5)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingAction?.let { target ->
        ConfirmActionDialog(
            title = "确认${target.action.label}目标？",
            message = "将向 ${server.config.name} 发送${target.action.label} ${target.targetName} 的指令，期间相关服务可能短暂中断。",
            confirmText = "确认${target.action.label}",
            isDangerous = target.action == WorkloadAction.STOP || target.action == WorkloadAction.RESTART,
            onConfirm = {
                onPerformWorkloadAction(server.config.id, target.targetType, target.targetName, target.action)
                pendingAction = null
            },
            onDismiss = { pendingAction = null }
        )
    }

    if (showDeleteConfirm) {
        ConfirmActionDialog(
            title = "确认删除服务器？",
            message = "将从本机移除 ${server.config.name} 的配置与加密凭据，此操作不可撤销。",
            confirmText = "删除",
            isDangerous = true,
            onConfirm = {
                showDeleteConfirm = false
                onDeleteServer(server.config.id)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (opsState.isLogsOpen) {
        LogViewerDialog(
            title = opsState.logTitle,
            lines = opsState.logLines,
            currentLinesCount = opsState.logLinesCount,
            errorMessage = opsState.logError,
            isLoading = opsState.isLoadingLogs,
            onRefresh = { onRefreshLogs(opsState.logLinesCount) },
            onSelectLines = { n -> onRefreshLogs(n) },
            onDismiss = onCloseLogs
        )
    }
}

@Composable
private fun LogViewerDialog(
    title: String,
    lines: List<String>,
    currentLinesCount: Int,
    errorMessage: String?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSelectLines: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppTheme.colors
    var filterQuery by remember { mutableStateOf("") }
    val filteredLines = remember(lines, filterQuery) {
        if (filterQuery.isBlank()) lines
        else lines.filter { it.contains(filterQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 340.dp, max = 580.dp),
            shape = RoundedCornerShape(AppRadius.lg),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(modifier = Modifier.padding(AppSpacing.space4)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "日志 · $title",
                        style = AppTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onRefresh, enabled = !isLoading) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新日志",
                                tint = colors.textSecondary
                            )
                        }
                        TextButton(onClick = onDismiss) {
                            Text("关闭", color = colors.actionPrimary)
                        }
                    }
                }
                Spacer(Modifier.height(AppSpacing.space1))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(50, 150, 300).forEach { count ->
                            val active = currentLinesCount == count
                            Surface(
                                onClick = { onSelectLines(count) },
                                shape = RoundedCornerShape(AppRadius.full),
                                color = if (active) colors.infoSoft else colors.surfaceSubtle
                            ) {
                                Text(
                                    text = "${count}行",
                                    style = AppTheme.typography.labelSmall,
                                    color = if (active) colors.actionPrimary else colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    if (errorMessage != null) {
                        Text("获取失败", style = AppTheme.typography.bodySmall, color = colors.error)
                    }
                }
                Spacer(Modifier.height(AppSpacing.space2))

                AppTextField(
                    label = "关键词过滤",
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = "输入 error / warn 等过滤..."
                )
                Spacer(Modifier.height(AppSpacing.space2))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(AppRadius.sm))
                        .background(Color(0xFF0B0F14))
                        .padding(AppSpacing.space3)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(28.dp),
                            color = colors.actionPrimary
                        )
                    } else if (errorMessage != null) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(errorMessage, style = AppTheme.typography.bodySmall, color = colors.error)
                            SecondaryButton(text = "重试", onClick = onRefresh)
                        }
                    } else {
                        SelectionContainer {
                            Text(
                                text = filteredLines.joinToString("\n").ifBlank { "无匹配日志行" },
                                style = AppTheme.typography.monoBody,
                                color = Color(0xFFCDD9E5),
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class TrendSpec(
    val series: List<TrendSeries>,
    val yMax: Double,
    val unit: String,
    val thresholds: List<TrendThreshold>,
    val description: String
)

private fun buildTrendSpec(
    metric: TrendMetric,
    points: List<MetricPoint>,
    windowLabel: String,
    rules: List<AlertRule>,
    colors: AppColorTokens
): TrendSpec {
    val cpuRule = rules.firstOrNull { it.metricType == AlertMetricType.CPU && it.enabled }
    val diskRule = rules.firstOrNull { it.metricType == AlertMetricType.DISK && it.enabled }

    fun series(
        label: String,
        color: Color,
        dashed: Boolean = false,
        fill: Boolean = false,
        selector: (MetricPoint) -> Double?
    ): TrendSeries = TrendSeries(
        label = label,
        points = points.mapNotNull { p -> selector(p)?.let { TrendPoint(p.timestampMs, it) } },
        color = color,
        dashed = dashed,
        fill = fill
    )

    fun autoMax(vararg seriesList: TrendSeries): Double {
        val raw = seriesList.flatMap { it.points }.maxOfOrNull { it.value } ?: 1.0
        return niceMaxValue(raw)
    }

    return when (metric) {
        TrendMetric.CPU_MEM -> TrendSpec(
            series = listOf(
                series("CPU", colors.actionPrimary, fill = true) { it.cpuPercent },
                series("内存", colors.success, dashed = true) { it.memPercent }
            ),
            yMax = 100.0,
            unit = "%",
            thresholds = ruleThresholds(cpuRule, "%", "CPU", colors),
            description = "CPU 与内存使用率，最近 $windowLabel"
        )
        TrendMetric.DISK -> TrendSpec(
            series = listOf(
                series("磁盘使用率", colors.info, fill = true) { it.diskMaxPercent }
            ),
            yMax = 100.0,
            unit = "%",
            thresholds = ruleThresholds(diskRule, "%", "磁盘", colors),
            description = "磁盘使用率，最近 $windowLabel"
        )
        TrendMetric.TEMP -> TrendSpec(
            series = listOf(
                series("CPU 温度", colors.warning, fill = true) { it.cpuTempCelsius }
            ),
            yMax = 100.0,
            unit = "°C",
            thresholds = listOf(TrendThreshold(80.0, "高温 80°C", colors.error)),
            description = "CPU 温度，最近 $windowLabel"
        )
        TrendMetric.DISK_IO -> {
            val read = series("读", colors.actionPrimary, fill = true) {
                it.diskReadBytesPerSec / (1024.0 * 1024.0)
            }
            val write = series("写", colors.warning, dashed = true) {
                it.diskWriteBytesPerSec / (1024.0 * 1024.0)
            }
            TrendSpec(
                series = listOf(read, write),
                yMax = autoMax(read, write),
                unit = "MB/s",
                thresholds = emptyList(),
                description = "磁盘读写速率，最近 $windowLabel"
            )
        }
        TrendMetric.NETWORK -> {
            val rx = series("RX", colors.actionPrimary, fill = true) { it.netRxBytesPerSec / 1024.0 }
            val tx = series("TX", colors.warning, dashed = true) { it.netTxBytesPerSec / 1024.0 }
            TrendSpec(
                series = listOf(rx, tx),
                yMax = autoMax(rx, tx),
                unit = "KB/s",
                thresholds = emptyList(),
                description = "网络收发速率，最近 $windowLabel"
            )
        }
        TrendMetric.LOAD -> {
            val load1 = series("Load 1", colors.info, fill = true) { it.load1 }
            val load5 = series("Load 5", colors.success, dashed = true) { it.load5 }
            TrendSpec(
                series = listOf(load1, load5),
                yMax = autoMax(load1, load5),
                unit = "",
                thresholds = emptyList(),
                description = "系统负载，最近 $windowLabel"
            )
        }
    }
}

private fun ruleThresholds(
    rule: AlertRule?,
    unit: String,
    prefix: String,
    colors: AppColorTokens
): List<TrendThreshold> {
    if (rule == null) return emptyList()
    fun label(value: Double): String = if (unit == "%") {
        "${value.roundToInt()}%"
    } else {
        String.format(Locale.US, "%.1f%s", value, unit)
    }
    return buildList {
        if (rule.warningThreshold > 0.0) {
            add(
                TrendThreshold(
                    value = rule.warningThreshold,
                    label = "$prefix 警告 ${label(rule.warningThreshold)}",
                    color = colors.warning
                )
            )
        }
        if (rule.criticalThreshold > 0.0) {
            add(
                TrendThreshold(
                    value = rule.criticalThreshold,
                    label = "$prefix 严重 ${label(rule.criticalThreshold)}",
                    color = colors.error
                )
            )
        }
    }
}

private fun niceMaxValue(raw: Double): Double {
    val safe = raw.coerceAtLeast(1.0)
    var step = 1.0
    while (step * 10.0 <= safe) step *= 10.0
    val multipliers = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0)
    return step * (multipliers.firstOrNull { step * it >= safe } ?: 10.0)
}

private fun formatTrendLegendValue(value: Double?, unit: String): String {
    if (value == null) return "--"
    return when (unit) {
        "%" -> "${value.roundToInt()}%"
        "°C" -> String.format(Locale.US, "%.1f°C", value)
        "MB/s" -> String.format(Locale.US, "%.1f MB/s", value)
        "KB/s" -> String.format(Locale.US, "%.0f KB/s", value)
        else -> String.format(Locale.US, "%.2f", value)
    }
}

@Composable
private fun KpiCell(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.sm))
            .background(colors.surfaceSubtle)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = AppTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1
        )
        Text(
            text = value,
            style = AppTheme.typography.titleSmall.tabular(),
            color = if (highlight) colors.warning else colors.textPrimary,
            maxLines = 1
        )
        Text(
            text = sub,
            style = AppTheme.typography.labelSmall,
            color = colors.textDisabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrendLegend(spec: TrendSpec) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            spec.series.forEach { s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(s.color)
                    )
                    Text(
                        text = s.label,
                        style = AppTheme.typography.labelSmall,
                        color = s.color,
                        maxLines = 1
                    )
                    Text(
                        text = formatTrendLegendValue(s.points.lastOrNull()?.value, spec.unit),
                        style = AppTheme.typography.labelSmall.tabular(),
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                }
            }
        }
        if (spec.thresholds.isNotEmpty()) {
            Text(
                text = "虚线为告警阈值",
                style = AppTheme.typography.labelSmall,
                color = colors.textDisabled
            )
        }
    }
}

@Composable
private fun AlertTimelineRow(event: AlertEvent) {
    val colors = AppTheme.colors
    val isCritical = event.severity == AlertSeverity.CRITICAL
    val dotColor = when {
        event.isResolved -> colors.success
        isCritical -> colors.error
        else -> colors.warning
    }
    val label = when {
        event.isResolved -> "已恢复"
        isCritical -> "严重"
        else -> "警告"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.message,
                    style = AppTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                StatusChipRaw(
                    text = label,
                    icon = null,
                    backgroundColor = when {
                        event.isResolved -> colors.successSoft
                        isCritical -> colors.errorSoft
                        else -> colors.warningSoft
                    },
                    contentColor = dotColor
                )
            }
            val meta = buildString {
                append(Formatters.formatRelativeTime(event.timestampMs))
                append(" · 阈值 ")
                if (event.metricType == AlertMetricType.OFFLINE) {
                    append(String.format(Locale.US, "%.0f 次", event.thresholdValue))
                } else {
                    append("${event.thresholdValue.roundToInt()}%")
                }
                event.resolvedAtMs?.let {
                    append(" · 已恢复 ")
                    append(Formatters.formatRelativeTime(it))
                }
            }
            Text(
                text = meta,
                style = AppTheme.typography.labelSmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun ProcessRow(
    proc: ProcessSummary,
    sort: ProcessSort,
    maxValue: Double
) {
    val colors = AppTheme.colors
    val value = if (sort == ProcessSort.CPU) proc.cpuPercent else proc.memPercent
    val barColor = when {
        value >= 50.0 -> colors.error
        value >= 25.0 -> colors.warning
        else -> colors.actionPrimary
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${proc.name} (PID ${proc.pid})",
                style = AppTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format(
                    Locale.US,
                    "CPU %.1f%% · MEM %.1f%%",
                    proc.cpuPercent,
                    proc.memPercent
                ),
                style = AppTheme.typography.bodySmall.tabular(),
                color = colors.textSecondary,
                maxLines = 1
            )
        }
        val metaParts = buildList {
            if (proc.user.isNotBlank()) add(proc.user)
            if (proc.state.isNotBlank()) add("状态 ${proc.state}")
            if (proc.rssBytes > 0L) add("RSS ${Formatters.formatBytes(proc.rssBytes)}")
        }
        if (metaParts.isNotEmpty()) {
            Text(
                text = metaParts.joinToString(" · "),
                style = AppTheme.typography.bodySmall.tabular(),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.surfaceSubtle)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((value / maxValue).toFloat().coerceIn(0.03f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}
