package com.xiaogou.serverseyes.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaogou.serverseyes.core.designsystem.components.AppCard
import com.xiaogou.serverseyes.core.designsystem.components.SecondaryButton
import com.xiaogou.serverseyes.core.designsystem.components.SectionHeader
import com.xiaogou.serverseyes.core.designsystem.patterns.EmptyState
import com.xiaogou.serverseyes.core.designsystem.primitives.AppDivider
import com.xiaogou.serverseyes.core.designsystem.primitives.StatusChipRaw
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.tokens.AppRadius
import com.xiaogou.serverseyes.core.designsystem.tokens.AppSpacing
import com.xiaogou.serverseyes.core.designsystem.tokens.tabular
import com.xiaogou.serverseyes.core.model.AlertEvent
import com.xiaogou.serverseyes.core.model.AlertMetricType
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.AlertSeverity
import com.xiaogou.serverseyes.core.util.Formatters
import com.xiaogou.serverseyes.data.repository.ServerUiModel
import java.util.UUID
import kotlin.math.roundToInt

private enum class AlertEventFilter(val label: String) {
    ALL("全部"),
    ACTIVE("进行中"),
    RESOLVED("已恢复")
}

@Composable
fun AlertsScreen(
    rules: List<AlertRule>,
    events: List<AlertEvent>,
    servers: List<ServerUiModel>,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onUpdateRule: (AlertRule) -> Unit,
    onDeleteRule: (String) -> Unit = {},
    onAcknowledge: (String) -> Unit,
    onAcknowledgeAll: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    var selectedScopeId by rememberSaveable { mutableStateOf<String?>(null) } // null = global
    var eventFilterName by rememberSaveable { mutableStateOf(AlertEventFilter.ALL.name) }
    val eventFilter = runCatching { AlertEventFilter.valueOf(eventFilterName) }.getOrDefault(AlertEventFilter.ALL)

    val currentScopeRules = remember(rules, selectedScopeId) {
        AlertMetricType.entries.associateWith { type ->
            rules.firstOrNull { it.serverId == selectedScopeId && it.metricType == type }
                ?: rules.firstOrNull { it.serverId == null && it.metricType == type }
        }
    }

    val filteredEvents = remember(events, eventFilter) {
        when (eventFilter) {
            AlertEventFilter.ALL -> events
            AlertEventFilter.ACTIVE -> events.filter { !it.isResolved }
            AlertEventFilter.RESOLVED -> events.filter { it.isResolved }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(AppSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.space3)
        ) {
            if (!hasNotificationPermission) {
                item {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = colors.warning,
                        backgroundColor = colors.warningSoft
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.space3),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "未授予系统通知权限",
                                    style = AppTheme.typography.titleSmall,
                                    color = colors.warning
                                )
                                Text(
                                    text = "开启通知后，服务器超阈值或离线时可实时推送提醒；拒绝权限仍可在应用内查看所有告警记录。",
                                    style = AppTheme.typography.bodySmall,
                                    color = colors.textPrimary
                                )
                            }
                            SecondaryButton(
                                text = "授权",
                                onClick = onRequestNotificationPermission
                            )
                        }
                    }
                }
            }

            // Scope picker
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(
                        title = "告警规则作用域",
                        trailing = {
                            StatusChipRaw(
                                text = if (hasNotificationPermission) "通知已开启" else "仅应用内记录",
                                icon = if (hasNotificationPermission) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                backgroundColor = if (hasNotificationPermission) colors.successSoft else colors.surfaceSubtle,
                                contentColor = if (hasNotificationPermission) colors.success else colors.textSecondary
                            )
                        }
                    )
                    Spacer(Modifier.height(AppSpacing.space2))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isGlobal = selectedScopeId == null
                        Surface(
                            onClick = { selectedScopeId = null },
                            shape = RoundedCornerShape(AppRadius.full),
                            color = if (isGlobal) colors.infoSoft else colors.surfaceSubtle,
                            border = if (isGlobal) androidx.compose.foundation.BorderStroke(1.dp, colors.actionPrimary) else null
                        ) {
                            Text(
                                text = "全局默认规则",
                                style = AppTheme.typography.labelSmall,
                                color = if (isGlobal) colors.actionPrimary else colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        servers.forEach { srv ->
                            val active = selectedScopeId == srv.config.id
                            val hasOverride = rules.any { it.serverId == srv.config.id }
                            Surface(
                                onClick = { selectedScopeId = srv.config.id },
                                shape = RoundedCornerShape(AppRadius.full),
                                color = if (active) colors.infoSoft else colors.surfaceSubtle,
                                border = if (active) androidx.compose.foundation.BorderStroke(1.dp, colors.actionPrimary) else null
                            ) {
                                Text(
                                    text = "${srv.config.name}${if (hasOverride) " (覆盖)" else ""}",
                                    style = AppTheme.typography.labelSmall,
                                    color = if (active) colors.actionPrimary else colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(AppSpacing.space2))
                    Text(
                        text = if (selectedScopeId == null) {
                            "全局默认阈值：应用于所有未单独配置告警规则的服务器。"
                        } else {
                            val sName = servers.firstOrNull { it.config.id == selectedScopeId }?.config?.name ?: ""
                            "正在配置 $sName 的独立告警规则（仅作用于该节点）。"
                        },
                        style = AppTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )

                    AppDivider(Modifier.padding(vertical = AppSpacing.space2))

                    AlertMetricType.entries.forEach { metricType ->
                        val rule = currentScopeRules[metricType]
                        val isOverridden = rule?.serverId == selectedScopeId && selectedScopeId != null
                        val label = when (metricType) {
                            AlertMetricType.CPU -> "CPU 使用率"
                            AlertMetricType.MEMORY -> "内存使用率"
                            AlertMetricType.DISK -> "磁盘使用率"
                            AlertMetricType.OFFLINE -> "连续离线次数"
                        }
                        val isOffline = metricType == AlertMetricType.OFFLINE

                        fun commitRule(mutator: (AlertRule) -> AlertRule) {
                            if (rule == null) return
                            val base = if (isOverridden || selectedScopeId == null) {
                                rule
                            } else {
                                rule.copy(id = UUID.randomUUID().toString(), serverId = selectedScopeId)
                            }
                            onUpdateRule(mutator(base))
                        }

                        // 滑块拖动过程中只更新本地预览值，松手后才提交，
                        // 否则每个拖动增量都会触发一次持久化与 snackbar。
                        var pendingEdit by remember(metricType, selectedScopeId) { mutableStateOf<AlertRule?>(null) }
                        val displayRule = pendingEdit ?: rule

                        if (displayRule != null) {
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(label, style = AppTheme.typography.labelLarge, color = colors.textPrimary)
                                        if (isOverridden) {
                                            StatusChipRaw(
                                                text = "独立配置",
                                                icon = null,
                                                backgroundColor = colors.infoSoft,
                                                contentColor = colors.actionPrimary
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)
                                    ) {
                                        val unit = if (isOffline) "次" else "%"
                                        Text(
                                            text = "警告 ${displayRule.warningThreshold.roundToInt()}$unit / 严重 ${displayRule.criticalThreshold.roundToInt()}$unit",
                                            style = AppTheme.typography.bodySmall.tabular(),
                                            color = colors.textSecondary
                                        )
                                        Switch(
                                            checked = displayRule.enabled,
                                            onCheckedChange = { enabled ->
                                                pendingEdit = null
                                                commitRule { it.copy(enabled = enabled) }
                                            }
                                        )
                                    }
                                }

                                if (displayRule.enabled) {
                                    if (!isOffline) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("警告阈值 (40% ~ 95%)", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                                            Text("${displayRule.warningThreshold.roundToInt()}%", style = AppTheme.typography.labelSmall.tabular(), color = colors.warning)
                                        }
                                        Slider(
                                            value = displayRule.warningThreshold.toFloat().coerceIn(40f, 95f),
                                            onValueChange = { newWarn ->
                                                val w = newWarn.roundToInt().coerceIn(40, 95).toDouble()
                                                val c = displayRule.criticalThreshold.coerceAtLeast(w + 1.0).coerceAtMost(99.0)
                                                pendingEdit = displayRule.copy(warningThreshold = w, criticalThreshold = c)
                                            },
                                            onValueChangeFinished = {
                                                pendingEdit?.let { committed -> commitRule { committed } }
                                                pendingEdit = null
                                            },
                                            valueRange = 40f..95f,
                                            colors = SliderDefaults.colors(thumbColor = colors.warning, activeTrackColor = colors.warning)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("严重阈值 (50% ~ 99%)", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                                            Text("${displayRule.criticalThreshold.roundToInt()}%", style = AppTheme.typography.labelSmall.tabular(), color = colors.error)
                                        }
                                        Slider(
                                            value = displayRule.criticalThreshold.toFloat().coerceIn(50f, 99f),
                                            onValueChange = { newCrit ->
                                                val c = newCrit.roundToInt().coerceIn(50, 99).toDouble()
                                                val w = displayRule.warningThreshold.coerceAtMost(c - 1.0).coerceAtLeast(40.0)
                                                pendingEdit = displayRule.copy(warningThreshold = w, criticalThreshold = c)
                                            },
                                            onValueChangeFinished = {
                                                pendingEdit?.let { committed -> commitRule { committed } }
                                                pendingEdit = null
                                            },
                                            valueRange = 50f..99f,
                                            colors = SliderDefaults.colors(thumbColor = colors.error, activeTrackColor = colors.error)
                                        )
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("连续失败判定离线次数", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                listOf(1, 2, 3, 5, 10).forEach { cnt ->
                                                    val active = displayRule.warningThreshold.toInt() == cnt
                                                    Surface(
                                                        onClick = {
                                                            commitRule {
                                                                it.copy(
                                                                    warningThreshold = cnt.toDouble(),
                                                                    criticalThreshold = (cnt + 1).toDouble()
                                                                )
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(AppRadius.sm),
                                                        color = if (active) colors.infoSoft else colors.surfaceSubtle
                                                    ) {
                                                        Text(
                                                            text = "${cnt}次",
                                                            style = AppTheme.typography.labelSmall.tabular(),
                                                            color = if (active) colors.actionPrimary else colors.textSecondary,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("告警冷却时间", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                                        Text("${displayRule.cooldownMinutes}分钟", style = AppTheme.typography.labelSmall.tabular(), color = colors.textPrimary)
                                    }
                                    Slider(
                                        value = displayRule.cooldownMinutes.toFloat(),
                                        onValueChange = { mins ->
                                            val m = mins.roundToInt().coerceIn(1, 60)
                                            pendingEdit = displayRule.copy(cooldownMinutes = m)
                                        },
                                        onValueChangeFinished = {
                                            pendingEdit?.let { committed -> commitRule { committed } }
                                            pendingEdit = null
                                        },
                                        valueRange = 1f..60f,
                                        colors = SliderDefaults.colors(thumbColor = colors.actionPrimary, activeTrackColor = colors.actionPrimary)
                                    )

                                    if (isOverridden) {
                                        TextButton(
                                            onClick = {
                                                pendingEdit = null
                                                displayRule?.let { onDeleteRule(it.id) }
                                            },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text("恢复为全局默认", color = colors.error, style = AppTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.space2)) {
                    SectionHeader(
                        title = "告警事件记录 (${filteredEvents.size})",
                        trailing = {
                            if (events.isNotEmpty()) {
                                Row {
                                    TextButton(onClick = onAcknowledgeAll) {
                                        Text("全部已读", color = colors.actionPrimary)
                                    }
                                    TextButton(onClick = onClearAll) {
                                        Text("清空", color = colors.error)
                                    }
                                }
                            }
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AlertEventFilter.entries.forEach { f ->
                            val selected = eventFilter == f
                            Surface(
                                onClick = { eventFilterName = f.name },
                                shape = RoundedCornerShape(AppRadius.full),
                                color = if (selected) colors.infoSoft else colors.surfaceSubtle,
                                border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, colors.actionPrimary) else null
                            ) {
                                Text(
                                    text = f.label,
                                    style = AppTheme.typography.labelSmall,
                                    color = if (selected) colors.actionPrimary else colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (filteredEvents.isEmpty()) {
                item {
                    EmptyState(
                        title = if (events.isEmpty()) "当前暂无告警记录" else "该筛选条件下暂无记录",
                        description = "所有监控节点均处于设定阈值范围之内。",
                        icon = Icons.Rounded.NotificationsActive
                    )
                }
            } else {
                items(filteredEvents, key = { it.id }) { ev ->
                    val isCritical = ev.severity == AlertSeverity.CRITICAL
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = when {
                            ev.isResolved -> colors.border
                            !ev.acknowledged && isCritical -> colors.error
                            else -> colors.border
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (ev.isResolved) {
                                        StatusChipRaw(
                                            text = "已恢复",
                                            icon = Icons.Rounded.CheckCircle,
                                            backgroundColor = colors.successSoft,
                                            contentColor = colors.success
                                        )
                                    } else {
                                        StatusChipRaw(
                                            text = if (isCritical) "严重" else "警告",
                                            icon = Icons.Rounded.Warning,
                                            backgroundColor = if (isCritical) colors.errorSoft else colors.warningSoft,
                                            contentColor = if (isCritical) colors.error else colors.warning
                                        )
                                    }
                                    Text(
                                        text = ev.serverName,
                                        style = AppTheme.typography.titleSmall,
                                        color = colors.textPrimary
                                    )
                                }
                                Text(
                                    text = ev.message,
                                    style = AppTheme.typography.bodyMedium,
                                    color = colors.textPrimary
                                )
                                val timeSubtitle = if (ev.isResolved && ev.resolvedAtMs != null) {
                                    "${Formatters.formatRelativeTime(ev.timestampMs)} · 持续 ${Formatters.formatDuration(ev.resolvedAtMs - ev.timestampMs)} 后已恢复"
                                } else {
                                    Formatters.formatRelativeTime(ev.timestampMs)
                                }
                                Text(
                                    text = timeSubtitle,
                                    style = AppTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            if (ev.acknowledged) {
                                Text(
                                    text = "已确认",
                                    style = AppTheme.typography.labelSmall,
                                    color = colors.textDisabled
                                )
                            } else {
                                SecondaryButton(
                                    text = "确认",
                                    onClick = { onAcknowledge(ev.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
