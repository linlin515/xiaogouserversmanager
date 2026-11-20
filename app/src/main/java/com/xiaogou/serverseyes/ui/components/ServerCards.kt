package com.xiaogou.serverseyes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaogou.serverseyes.core.designsystem.components.AppCard
import com.xiaogou.serverseyes.core.designsystem.primitives.SparklineChart
import com.xiaogou.serverseyes.core.designsystem.primitives.StatusBadge
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.tokens.AppRadius
import com.xiaogou.serverseyes.core.designsystem.tokens.AppSpacing
import com.xiaogou.serverseyes.core.designsystem.tokens.tabular
import com.xiaogou.serverseyes.core.model.CollectionMode
import com.xiaogou.serverseyes.core.model.MetricSource
import com.xiaogou.serverseyes.core.model.ServerStatus
import com.xiaogou.serverseyes.core.util.Formatters
import com.xiaogou.serverseyes.data.repository.ServerUiModel
import kotlin.math.roundToInt

/**
 * 方案 A · Ops Precision 核心服务器节点卡片
 * - 顶部：节点名称 + 采集源胶囊（Hybrid/Agent/SSH）
 * - 状态行：带图标+文字的 StatusBadge + 延迟
 * - 中部：实时 CPU 折线微图（Sparkline）
 * - 底部：CPU / MEM / DSK 等宽对齐指标 + 下行速率
 */
@Composable
fun ServerSummaryCard(
    item: ServerUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val snap = item.snapshot
    val borderColor = when {
        isSelected -> colors.actionPrimary
        item.status == ServerStatus.CRITICAL || item.status == ServerStatus.OFFLINE -> colors.error
        item.status == ServerStatus.WARNING -> colors.warning
        else -> colors.border
    }
    val sparkColor = when (item.status) {
        ServerStatus.CRITICAL, ServerStatus.OFFLINE -> colors.error
        ServerStatus.WARNING -> colors.warning
        else -> colors.actionPrimary
    }
    val sourceLabel = when (snap?.sourceUsed) {
        MetricSource.AGENT -> if (item.config.collectionMode == CollectionMode.HYBRID) "Hybrid" else "Agent"
        MetricSource.SSH -> "SSH"
        null -> when (item.config.collectionMode) {
            CollectionMode.HYBRID -> "Hybrid"
            CollectionMode.AGENT_ONLY -> "Agent"
            CollectionMode.SSH_ONLY -> "SSH"
        }
    }

    AppCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = borderColor,
        onClick = onClick,
        contentPadding = PaddingValues(AppSpacing.space3)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.space2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.config.name,
                    style = AppTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.full),
                    color = colors.surfaceSubtle
                ) {
                    Text(
                        text = sourceLabel,
                        style = AppTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = item.status)
                Text(
                    text = snap?.let { "${it.latencyMs}ms" } ?: item.config.host,
                    style = AppTheme.typography.bodySmall.tabular(),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.lastError != null && snap == null) {
                Text(
                    text = "${item.lastError.title}：${item.lastError.message}",
                    style = AppTheme.typography.bodySmall,
                    color = colors.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                SparklineChart(
                    values = item.sparklineCpu,
                    lineColor = sparkColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell(
                    label = "CPU",
                    value = snap?.let { "${it.cpuPercent.roundToInt()}%" } ?: "--",
                    highlight = (snap?.cpuPercent ?: 0.0) >= 85.0
                )
                MetricCell(
                    label = "MEM",
                    value = snap?.let { "${it.memPercent.roundToInt()}%" } ?: "--",
                    highlight = (snap?.memPercent ?: 0.0) >= 85.0
                )
                MetricCell(
                    label = "DSK",
                    value = snap?.let { "${it.maxDiskPercent.roundToInt()}%" } ?: "--",
                    highlight = (snap?.maxDiskPercent ?: 0.0) >= 85.0
                )
            }

            if (snap != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "↓ ${Formatters.formatBytesPerSec(snap.netRxBytesPerSec)}",
                        style = AppTheme.typography.bodySmall.tabular(),
                        color = colors.textSecondary,
                        maxLines = 1
                    )
                    Text(
                        text = "L ${String.format(java.util.Locale.US, "%.2f", snap.load1)}",
                        style = AppTheme.typography.bodySmall.tabular(),
                        color = colors.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    highlight: Boolean
) {
    val colors = AppTheme.colors
    Column {
        Text(
            text = label,
            style = AppTheme.typography.bodySmall,
            color = colors.textSecondary
        )
        Text(
            text = value,
            style = AppTheme.typography.labelLarge.tabular(),
            color = if (highlight) colors.error else colors.textPrimary
        )
    }
}
