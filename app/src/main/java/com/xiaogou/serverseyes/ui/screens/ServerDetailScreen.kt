package com.xiaogou.serverseyes.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
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
import com.xiaogou.serverseyes.core.designsystem.primitives.AppDivider
import com.xiaogou.serverseyes.core.designsystem.primitives.MetricProgressBar
import com.xiaogou.serverseyes.core.designsystem.primitives.StatusBadge
import com.xiaogou.serverseyes.core.designsystem.primitives.StatusChipRaw
import com.xiaogou.serverseyes.core.designsystem.primitives.TimeSeriesLineChart
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.tokens.AppRadius
import com.xiaogou.serverseyes.core.designsystem.tokens.AppSpacing
import com.xiaogou.serverseyes.core.designsystem.tokens.tabular
import com.xiaogou.serverseyes.core.model.CollectionMode
import com.xiaogou.serverseyes.core.model.DiagnosticPreset
import com.xiaogou.serverseyes.core.model.LogTargetType
import com.xiaogou.serverseyes.core.model.MetricSource
import com.xiaogou.serverseyes.core.model.WorkloadAction
import com.xiaogou.serverseyes.core.util.Formatters
import com.xiaogou.serverseyes.data.repository.ServerUiModel
import com.xiaogou.serverseyes.ui.DetailOpsState
import java.util.Locale
import kotlin.math.roundToInt

private enum class DetailTab(val label: String) {
    OVERVIEW("资源概览"),
    WORKLOADS("容器与服务"),
    OPS("日志与诊断")
}

private data class PendingWorkload(
    val targetType: LogTargetType,
    val targetName: String,
    val action: WorkloadAction
)

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
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    if (server == null) {
        Column(modifier = modifier.fillMaxSize().padding(AppSpacing.space4)) {
            EmptyState(
                title = "请选择一台服务器",
                description = "点击左侧服务器卡片查看 CPU/内存历史曲线、Docker 容器、Systemd 服务与实时日志。"
            )
        }
        return
    }

    var selectedTab by remember(server.config.id) { mutableStateOf(DetailTab.OVERVIEW) }
    var pendingAction by remember { mutableStateOf<PendingWorkload?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var customCmd by remember { mutableStateOf("") }

    val snap = server.snapshot
    val isEnabled = server.config.enabled

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.space3)
    ) {
        // Header card
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2),
                            verticalAlignment = Alignment.CenterVertically
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
                        }
                    }

                    Row {
                        IconButton(onClick = { onTogglePin(server.config.id, !server.config.isPinned) }) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = if (server.config.isPinned) "取消置顶" else "置顶",
                                tint = if (server.config.isPinned) colors.actionPrimary else colors.textSecondary
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
                        val active = tab == selectedTab
                        Surface(
                            onClick = { selectedTab = tab },
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

        when (selectedTab) {
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
                                    text = "CPU 与内存趋势",
                                    style = AppTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                    maxLines = 1
                                )
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    15 to "15m",
                                    60 to "1h",
                                    360 to "6h",
                                    1440 to "24h",
                                    2880 to "48h"
                                ).forEach { (mins, label) ->
                                    val active = opsState.historyMinutes == mins
                                    Surface(
                                        onClick = { onChangeHistoryRange(server.config.id, mins) },
                                        shape = RoundedCornerShape(AppRadius.full),
                                        color = if (active) colors.infoSoft else colors.surfaceSubtle
                                    ) {
                                        Text(
                                            text = label,
                                            style = AppTheme.typography.labelSmall,
                                            color = if (active) colors.actionPrimary else colors.textSecondary,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            }
                            Spacer(Modifier.height(AppSpacing.space2))
                            val cpuSeries = opsState.historyPoints.map { it.cpuPercent }
                                .ifEmpty { server.sparklineCpu.ifEmpty { listOf(snap.cpuPercent) } }
                            val memSeries = opsState.historyPoints.map { it.memPercent }
                                .ifEmpty { listOf(snap.memPercent) }

                            TimeSeriesLineChart(
                                values = cpuSeries,
                                lineColor = colors.actionPrimary,
                                secondaryValues = memSeries,
                                secondaryColor = colors.success
                            )
                            Spacer(Modifier.height(AppSpacing.space2))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "CPU ${snap.cpuPercent.roundToInt()}% (${snap.cpuCores}核)",
                                    style = AppTheme.typography.labelSmall.tabular(),
                                    color = colors.actionPrimary
                                )
                                Text(
                                    text = "内存 ${snap.memPercent.roundToInt()}%",
                                    style = AppTheme.typography.labelSmall.tabular(),
                                    color = colors.success
                                )
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "Load %.2f / %.2f / %.2f",
                                        snap.load1, snap.load5, snap.load15
                                    ),
                                    style = AppTheme.typography.labelSmall.tabular(),
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }

                    // Network & Load charts card
                    if (opsState.historyPoints.size >= 2) {
                        item {
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                SectionHeader(title = "网络吞吐 (RX/TX) 与 Load1 趋势")
                                Spacer(Modifier.height(AppSpacing.space2))
                                val rxSeries = opsState.historyPoints.map { it.netRxBytesPerSec.toDouble() / 1024.0 }
                                val txSeries = opsState.historyPoints.map { it.netTxBytesPerSec.toDouble() / 1024.0 }
                                val loadSeries = opsState.historyPoints.map { it.load1 }
                                val maxNetKb = (rxSeries + txSeries).maxOrNull()?.coerceAtLeast(10.0) ?: 100.0

                                Text("网络吞吐 (KB/s, 实线:RX / 虚线:TX)", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                                TimeSeriesLineChart(
                                    values = rxSeries,
                                    lineColor = colors.actionPrimary,
                                    secondaryValues = txSeries,
                                    secondaryColor = colors.warning,
                                    maxValue = maxNetKb,
                                    height = 100.dp
                                )
                                Spacer(Modifier.height(AppSpacing.space2))
                                Text("Load 1 均负载", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                                TimeSeriesLineChart(
                                    values = loadSeries,
                                    lineColor = colors.info,
                                    maxValue = loadSeries.maxOrNull()?.coerceAtLeast(1.0) ?: 2.0,
                                    height = 80.dp
                                )
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
                                        Text(
                                            text = "${disk.usagePercent.roundToInt()}% · ${Formatters.formatBytes(disk.usedBytes)} / ${Formatters.formatBytes(disk.totalBytes)}",
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

                    if (snap.topProcesses.isNotEmpty()) {
                        item {
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                SectionHeader(title = "Top 进程排行")
                                Spacer(Modifier.height(AppSpacing.space2))
                                snap.topProcesses.take(8).forEachIndexed { idx, proc ->
                                    if (idx > 0) AppDivider(Modifier.padding(vertical = 6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${proc.name} (PID ${proc.pid})",
                                            style = AppTheme.typography.bodyMedium,
                                            color = colors.textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = String.format(Locale.US, "CPU %.1f%% · MEM %.1f%%", proc.cpuPercent, proc.memPercent),
                                            style = AppTheme.typography.bodySmall.tabular(),
                                            color = colors.textSecondary
                                        )
                                    }
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
                            SectionHeader(title = "Docker 容器 (${snap.containers.size})")
                            Spacer(Modifier.height(AppSpacing.space2))
                            if (snap.containers.isEmpty()) {
                                Text(
                                    text = "未检测到 Docker 容器或宿主机未安装 Docker。",
                                    style = AppTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            } else {
                                snap.containers.forEachIndexed { idx, c ->
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
                            SectionHeader(title = "Systemd 服务 (${snap.services.size})")
                            Spacer(Modifier.height(AppSpacing.space2))
                            if (snap.services.isEmpty()) {
                                Text(
                                    text = "未获取到 Systemd 服务列表。",
                                    style = AppTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            } else {
                                snap.services.take(25).forEachIndexed { idx, sv ->
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
                        Spacer(Modifier.height(AppSpacing.space2))
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)) {
                            PrimaryButton(
                                text = "执行命令",
                                loading = opsState.isRunningDiag,
                                enabled = customCmd.isNotBlank() && server.config.collectionMode != CollectionMode.AGENT_ONLY,
                                icon = Icons.Rounded.Terminal,
                                modifier = Modifier.weight(1f),
                                onClick = { onRunDiagnostic(server.config.id, customCmd.trim()) }
                            )
                            SecondaryButton(
                                text = "系统日志",
                                onClick = { onOpenLogs(server.config.id, LogTargetType.SYSTEM_JOURNAL, "system", 150) }
                            )
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
