package com.xiaogou.serverseyes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaogou.serverseyes.core.designsystem.components.AppCard
import com.xiaogou.serverseyes.core.designsystem.components.SecondaryButton
import com.xiaogou.serverseyes.core.designsystem.components.SectionHeader
import com.xiaogou.serverseyes.core.designsystem.patterns.EmptyState
import com.xiaogou.serverseyes.core.designsystem.primitives.StatusChipRaw
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.tokens.AppSpacing
import com.xiaogou.serverseyes.core.designsystem.tokens.tabular
import com.xiaogou.serverseyes.core.model.AlertEvent
import com.xiaogou.serverseyes.core.model.AlertMetricType
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.AlertSeverity
import com.xiaogou.serverseyes.core.util.Formatters
import kotlin.math.roundToInt

@Composable
fun AlertsScreen(
    rules: List<AlertRule>,
    events: List<AlertEvent>,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onUpdateRule: (AlertRule) -> Unit,
    onAcknowledge: (String) -> Unit,
    onAcknowledgeAll: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val globalRules = rules.filter { it.serverId == null }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
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

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(
                    title = "全局默认告警阈值",
                    trailing = {
                        StatusChipRaw(
                            text = if (hasNotificationPermission) "通知已开启" else "仅应用内记录",
                            icon = if (hasNotificationPermission) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                            backgroundColor = if (hasNotificationPermission) colors.successSoft else colors.surfaceSubtle,
                            contentColor = if (hasNotificationPermission) colors.success else colors.textSecondary
                        )
                    }
                )
                Spacer(Modifier.height(AppSpacing.space3))

                globalRules.forEach { rule ->
                    val label = when (rule.metricType) {
                        AlertMetricType.CPU -> "CPU 使用率"
                        AlertMetricType.MEMORY -> "内存使用率"
                        AlertMetricType.DISK -> "磁盘使用率"
                        AlertMetricType.OFFLINE -> "连续离线次数"
                    }
                    val isOffline = rule.metricType == AlertMetricType.OFFLINE
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = AppTheme.typography.labelLarge, color = colors.textPrimary)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)
                            ) {
                                val unit = if (isOffline) "次" else "%"
                                Text(
                                    text = "警告 ${rule.warningThreshold.roundToInt()}$unit / 严重 ${rule.criticalThreshold.roundToInt()}$unit",
                                    style = AppTheme.typography.bodySmall.tabular(),
                                    color = colors.textSecondary
                                )
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { onUpdateRule(rule.copy(enabled = it)) }
                                )
                            }
                        }
                        if (rule.enabled && !isOffline) {
                            Slider(
                                value = rule.warningThreshold.toFloat(),
                                onValueChange = { newWarn ->
                                    val w = newWarn.roundToInt().toDouble()
                                    val c = rule.criticalThreshold.coerceAtLeast(w + 5.0).coerceAtMost(99.0)
                                    onUpdateRule(rule.copy(warningThreshold = w, criticalThreshold = c))
                                },
                                valueRange = 50f..95f,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.actionPrimary,
                                    activeTrackColor = colors.actionPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "告警事件记录 (${events.size})",
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
        }

        if (events.isEmpty()) {
            item {
                EmptyState(
                    title = "当前暂无告警记录",
                    description = "所有监控节点均处于设定阈值范围之内。",
                    icon = Icons.Rounded.NotificationsActive
                )
            }
        } else {
            items(events, key = { it.id }) { ev ->
                val isCritical = ev.severity == AlertSeverity.CRITICAL
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (!ev.acknowledged && isCritical) colors.error else colors.border
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
                                StatusChipRaw(
                                    text = if (isCritical) "严重" else "警告",
                                    icon = Icons.Rounded.Warning,
                                    backgroundColor = if (isCritical) colors.errorSoft else colors.warningSoft,
                                    contentColor = if (isCritical) colors.error else colors.warning
                                )
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
                            Text(
                                text = Formatters.formatRelativeTime(ev.timestampMs),
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
