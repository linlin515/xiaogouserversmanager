package com.xiaogou.serverseyes.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xiaogou.serverseyes.core.designsystem.components.AppCard
import com.xiaogou.serverseyes.core.designsystem.components.ConfirmActionDialog
import com.xiaogou.serverseyes.core.designsystem.components.DangerButton
import com.xiaogou.serverseyes.core.designsystem.components.SecondaryButton
import com.xiaogou.serverseyes.core.designsystem.patterns.EmptyState
import com.xiaogou.serverseyes.core.designsystem.patterns.ErrorBanner
import com.xiaogou.serverseyes.core.designsystem.patterns.FilterChipRow
import com.xiaogou.serverseyes.core.designsystem.patterns.KpiMetric
import com.xiaogou.serverseyes.core.designsystem.patterns.KpiPulseStrip
import com.xiaogou.serverseyes.core.designsystem.patterns.LoadingSkeletonList
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.tokens.AppRadius
import com.xiaogou.serverseyes.core.designsystem.tokens.AppSpacing
import com.xiaogou.serverseyes.core.model.ServerSortOption
import com.xiaogou.serverseyes.core.model.ServerStatus
import com.xiaogou.serverseyes.core.util.Formatters
import com.xiaogou.serverseyes.data.repository.ServerUiModel
import com.xiaogou.serverseyes.ui.components.ServerSummaryCard
import java.util.Locale

@Composable
fun DashboardPane(
    servers: List<ServerUiModel>,
    isInitialLoading: Boolean,
    groupFilter: String,
    searchQuery: String = "",
    sortOption: ServerSortOption = ServerSortOption.DEFAULT,
    selectedServerId: String?,
    onSelectFilter: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    onSelectSort: (ServerSortOption) -> Unit = {},
    onSelectServer: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit = { _, _ -> },
    onBatchPin: (Set<String>, Boolean) -> Unit = { _, _ -> },
    onBatchEnable: (Set<String>, Boolean) -> Unit = { _, _ -> },
    onBatchDelete: (Set<String>) -> Unit = {},
    onAddServer: () -> Unit,
    onRetryAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val fontScale = LocalDensity.current.fontScale

    var isBatchMode by remember { mutableStateOf(false) }
    var batchSelectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    if (isInitialLoading && servers.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.space4, vertical = AppSpacing.space3)
        ) {
            LoadingSkeletonList()
        }
        return
    }

    if (servers.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.space4, vertical = AppSpacing.space6)
        ) {
            EmptyState(
                title = "尚未添加任何服务器",
                description = "支持 SSH 免部署直连开箱监控，也可选配轻量 Agent 获取 24 小时历史曲线与低耗巡检。",
                actionLabel = "添加第一台服务器",
                onAction = onAddServer,
                icon = Icons.Rounded.Dns
            )
        }
        return
    }

    val onlineCount = servers.count { it.status != ServerStatus.OFFLINE && it.snapshot != null && it.config.enabled }
    val alertCount = servers.count {
        it.config.enabled && (it.status == ServerStatus.WARNING || it.status == ServerStatus.CRITICAL || it.status == ServerStatus.OFFLINE)
    }
    val totalRx = servers.filter { it.config.enabled }.sumOf { it.snapshot?.netRxBytesPerSec ?: 0L }
    val activeLoads = servers.filter { it.config.enabled }.mapNotNull { it.snapshot?.load1 }
    val avgLoad = if (activeLoads.isNotEmpty()) activeLoads.average() else 0.0
    val offlineServers = servers.filter { it.config.enabled && (it.status == ServerStatus.OFFLINE || (it.lastError != null && it.snapshot == null)) }

    val groups = listOf("全部", "异常优先") + servers.map { it.config.groupName }.filter { it.isNotBlank() }.distinct()
    val comparator = remember(sortOption) {
        when (sortOption) {
            ServerSortOption.DEFAULT ->
                compareByDescending<ServerUiModel> { it.config.isPinned }
                    .thenByDescending {
                        when (it.status) {
                            ServerStatus.CRITICAL -> 4
                            ServerStatus.OFFLINE -> 3
                            ServerStatus.WARNING -> 2
                            else -> 0
                        }
                    }
                    .thenBy { it.config.sortOrder }
                    .thenBy { it.config.name }
            ServerSortOption.CPU_DESC ->
                compareByDescending<ServerUiModel> { it.snapshot?.cpuPercent ?: -1.0 }
                    .thenBy { it.config.name }
            ServerSortOption.MEM_DESC ->
                compareByDescending<ServerUiModel> { it.snapshot?.memPercent ?: -1.0 }
                    .thenBy { it.config.name }
            ServerSortOption.NET_TX_DESC ->
                compareByDescending<ServerUiModel> { it.snapshot?.netRxBytesPerSec ?: -1L }
                    .thenBy { it.config.name }
            ServerSortOption.LATENCY_ASC ->
                compareBy<ServerUiModel> { it.snapshot?.latencyMs ?: Long.MAX_VALUE }
                    .thenBy { it.config.name }
            ServerSortOption.NAME_ASC ->
                compareBy<ServerUiModel> { it.config.name.lowercase(Locale.ROOT) }
        }
    }

    val filtered = servers.filter { item ->
        val matchesGroup = when (groupFilter) {
            "全部" -> true
            "异常优先" -> item.status == ServerStatus.WARNING || item.status == ServerStatus.CRITICAL || item.status == ServerStatus.OFFLINE
            else -> item.config.groupName == groupFilter
        }
        val matchesSearch = searchQuery.isBlank() ||
            item.config.name.contains(searchQuery, ignoreCase = true) ||
            item.config.host.contains(searchQuery, ignoreCase = true) ||
            item.config.groupName.contains(searchQuery, ignoreCase = true)
        matchesGroup && matchesSearch
    }.sortedWith(comparator)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = if (maxWidth < 340.dp || fontScale >= 1.35f) 1 else 2

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppSpacing.space4,
                end = AppSpacing.space4,
                top = AppSpacing.space3,
                bottom = 88.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.space3)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "搜索节点名称 / IP / 分组...",
                            style = AppTheme.typography.bodyMedium,
                            color = colors.textDisabled
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "搜索",
                            tint = colors.textSecondary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "清空搜索",
                                    tint = colors.textSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(AppRadius.md),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.actionPrimary,
                        unfocusedBorderColor = colors.border,
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
            }

            if (offlineServers.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val names = offlineServers.take(2).joinToString("、") { it.config.name }
                    val extra = if (offlineServers.size > 2) " 等 ${offlineServers.size} 台" else ""
                    ErrorBanner(
                        title = "${offlineServers.size} 台节点连接异常",
                        message = "$names$extra 暂时无法采集，请检查网络或凭据配置。",
                        onRetry = onRetryAll
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                KpiPulseStrip(
                    items = listOf(
                        KpiMetric("在线节点", "$onlineCount/${servers.size}", colors.success),
                        KpiMetric("异常告警", "$alertCount", if (alertCount > 0) colors.error else colors.textPrimary),
                        KpiMetric("总下行", Formatters.formatBytesPerSec(totalRx)),
                        KpiMetric("均负载", String.format(Locale.US, "%.2f", avgLoad))
                    )
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                FilterChipRow(
                    options = groups,
                    selected = groupFilter,
                    onSelect = onSelectFilter
                )
            }

            // Sort options & Batch mode trigger
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val sortLabels = listOf(
                            ServerSortOption.DEFAULT to "默认",
                            ServerSortOption.CPU_DESC to "CPU ↓",
                            ServerSortOption.MEM_DESC to "内存 ↓",
                            ServerSortOption.NET_TX_DESC to "下行 ↓",
                            ServerSortOption.LATENCY_ASC to "延迟 ↑",
                            ServerSortOption.NAME_ASC to "名称"
                        )
                        sortLabels.forEach { (opt, label) ->
                            val active = sortOption == opt
                            Surface(
                                onClick = { onSelectSort(opt) },
                                shape = RoundedCornerShape(AppRadius.full),
                                color = if (active) colors.infoSoft else colors.surfaceSubtle,
                                border = if (active) BorderStroke(1.dp, colors.actionPrimary) else null
                            ) {
                                Text(
                                    text = label,
                                    style = AppTheme.typography.labelSmall,
                                    color = if (active) colors.actionPrimary else colors.textSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    Surface(
                        onClick = {
                            isBatchMode = !isBatchMode
                            batchSelectedIds = emptySet()
                        },
                        shape = RoundedCornerShape(AppRadius.sm),
                        color = if (isBatchMode) colors.infoSoft else colors.surfaceSubtle,
                        border = if (isBatchMode) BorderStroke(1.dp, colors.actionPrimary) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Checklist,
                                contentDescription = "批量管理",
                                tint = if (isBatchMode) colors.actionPrimary else colors.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (isBatchMode) "退出" else "批量",
                                style = AppTheme.typography.labelSmall,
                                color = if (isBatchMode) colors.actionPrimary else colors.textSecondary
                            )
                        }
                    }
                }
            }

            if (isBatchMode) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = colors.actionPrimary
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "已选中 ${batchSelectedIds.size} 台节点",
                                    style = AppTheme.typography.titleSmall,
                                    color = colors.textPrimary
                                )
                                Row {
                                    TextButton(
                                        onClick = {
                                            val visibleIds = filtered.map { it.config.id }.toSet()
                                            batchSelectedIds = if (batchSelectedIds.containsAll(visibleIds)) {
                                                emptySet()
                                            } else {
                                                visibleIds
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = if (filtered.isNotEmpty() && batchSelectedIds.containsAll(filtered.map { it.config.id })) "取消全选" else "全选当前",
                                            color = colors.actionPrimary
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SecondaryButton(
                                    text = "置顶",
                                    icon = Icons.Rounded.PushPin,
                                    enabled = batchSelectedIds.isNotEmpty(),
                                    onClick = {
                                        onBatchPin(batchSelectedIds, true)
                                        isBatchMode = false
                                        batchSelectedIds = emptySet()
                                    }
                                )
                                SecondaryButton(
                                    text = "暂停采集",
                                    icon = Icons.Rounded.PauseCircleOutline,
                                    enabled = batchSelectedIds.isNotEmpty(),
                                    onClick = {
                                        onBatchEnable(batchSelectedIds, false)
                                        isBatchMode = false
                                        batchSelectedIds = emptySet()
                                    }
                                )
                                SecondaryButton(
                                    text = "恢复采集",
                                    icon = Icons.Rounded.PlayCircleOutline,
                                    enabled = batchSelectedIds.isNotEmpty(),
                                    onClick = {
                                        onBatchEnable(batchSelectedIds, true)
                                        isBatchMode = false
                                        batchSelectedIds = emptySet()
                                    }
                                )
                                DangerButton(
                                    text = "删除",
                                    enabled = batchSelectedIds.isNotEmpty(),
                                    onClick = { showBatchDeleteConfirm = true }
                                )
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "无匹配节点",
                        description = if (searchQuery.isNotEmpty()) "未找到与「$searchQuery」相关的服务器" else "当前筛选无节点数据。"
                    )
                }
            } else {
                items(filtered, key = { it.config.id }) { item ->
                    val checked = item.config.id in batchSelectedIds
                    ServerSummaryCard(
                        item = item,
                        isSelected = item.config.id == selectedServerId,
                        selectionMode = isBatchMode,
                        isBatchChecked = checked,
                        onClick = {
                            if (isBatchMode) {
                                batchSelectedIds = if (checked) {
                                    batchSelectedIds - item.config.id
                                } else {
                                    batchSelectedIds + item.config.id
                                }
                            } else {
                                onSelectServer(item.config.id)
                            }
                        },
                        onTogglePin = { onTogglePin(item.config.id, !item.config.isPinned) }
                    )
                }
            }
        }
    }

    if (showBatchDeleteConfirm && batchSelectedIds.isNotEmpty()) {
        ConfirmActionDialog(
            title = "确认批量删除 ${batchSelectedIds.size} 台服务器？",
            message = "将永久移除选中节点的配置、加密凭据及历史采样数据，此操作不可撤销。",
            confirmText = "确认删除",
            isDangerous = true,
            onConfirm = {
                onBatchDelete(batchSelectedIds)
                showBatchDeleteConfirm = false
                isBatchMode = false
                batchSelectedIds = emptySet()
            },
            onDismiss = { showBatchDeleteConfirm = false }
        )
    }
}
