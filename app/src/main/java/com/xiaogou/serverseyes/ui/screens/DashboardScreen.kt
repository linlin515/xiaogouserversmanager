package com.xiaogou.serverseyes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xiaogou.serverseyes.core.designsystem.patterns.EmptyState
import com.xiaogou.serverseyes.core.designsystem.patterns.ErrorBanner
import com.xiaogou.serverseyes.core.designsystem.patterns.FilterChipRow
import com.xiaogou.serverseyes.core.designsystem.patterns.KpiMetric
import com.xiaogou.serverseyes.core.designsystem.patterns.KpiPulseStrip
import com.xiaogou.serverseyes.core.designsystem.patterns.LoadingSkeletonList
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.tokens.AppSpacing
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
    selectedServerId: String?,
    onSelectFilter: (String) -> Unit,
    onSelectServer: (String) -> Unit,
    onAddServer: () -> Unit,
    onRetryAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val fontScale = LocalDensity.current.fontScale

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

    val onlineCount = servers.count { it.status != ServerStatus.OFFLINE && it.snapshot != null }
    val alertCount = servers.count {
        it.status == ServerStatus.WARNING || it.status == ServerStatus.CRITICAL || it.status == ServerStatus.OFFLINE
    }
    val totalRx = servers.sumOf { it.snapshot?.netRxBytesPerSec ?: 0L }
    val activeLoads = servers.mapNotNull { it.snapshot?.load1 }
    val avgLoad = if (activeLoads.isNotEmpty()) activeLoads.average() else 0.0
    val offlineServers = servers.filter { it.status == ServerStatus.OFFLINE || (it.lastError != null && it.snapshot == null) }

    val groups = listOf("全部", "异常优先") + servers.map { it.config.groupName }.filter { it.isNotBlank() }.distinct()
    val filtered = when (groupFilter) {
        "全部" -> servers
        "异常优先" -> servers.filter {
            it.status == ServerStatus.WARNING || it.status == ServerStatus.CRITICAL || it.status == ServerStatus.OFFLINE
        }
        else -> servers.filter { it.config.groupName == groupFilter }
    }.sortedWith(
        compareByDescending<ServerUiModel> {
            when (it.status) {
                ServerStatus.CRITICAL -> 4
                ServerStatus.OFFLINE -> 3
                ServerStatus.WARNING -> 2
                else -> 0
            }
        }.thenByDescending { it.config.isPinned }.thenBy { it.config.sortOrder }
    )

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

            if (filtered.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "当前筛选无匹配节点",
                        description = "所有服务器目前运行状态良好，可切换回“全部”查看全部节点。"
                    )
                }
            } else {
                items(filtered, key = { it.config.id }) { item ->
                    ServerSummaryCard(
                        item = item,
                        isSelected = item.config.id == selectedServerId,
                        onClick = { onSelectServer(item.config.id) }
                    )
                }
            }
        }
    }
}
