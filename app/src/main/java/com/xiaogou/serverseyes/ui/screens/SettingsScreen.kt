package com.xiaogou.serverseyes.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xiaogou.serverseyes.BuildConfig
import com.xiaogou.serverseyes.core.designsystem.components.AppCard
import com.xiaogou.serverseyes.core.designsystem.components.SecondaryButton
import com.xiaogou.serverseyes.core.designsystem.components.SectionHeader
import com.xiaogou.serverseyes.core.designsystem.primitives.AppDivider
import com.xiaogou.serverseyes.core.designsystem.primitives.StatusChipRaw
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.tokens.AppRadius
import com.xiaogou.serverseyes.core.designsystem.tokens.AppSpacing
import com.xiaogou.serverseyes.core.model.ThemePreference
import com.xiaogou.serverseyes.data.local.AppSettings

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSetTheme: (ThemePreference) -> Unit,
    onSetPollInterval: (Int) -> Unit,
    onSetBackgroundWork: (Boolean) -> Unit,
    onSetForegroundService: (Boolean) -> Unit,
    onSetNotifications: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.space3)
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "外观与主题")
            Spacer(Modifier.height(AppSpacing.space2))
            Surface(
                shape = RoundedCornerShape(AppRadius.md),
                color = colors.surfaceSubtle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        ThemePreference.SYSTEM to "跟随系统",
                        ThemePreference.LIGHT to "浅色模式",
                        ThemePreference.DARK to "深色模式"
                    ).forEach { (pref, label) ->
                        val active = settings.themePreference == pref
                        Surface(
                            onClick = { onSetTheme(pref) },
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                            shape = RoundedCornerShape(AppRadius.sm),
                            color = if (active) colors.surface else Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = AppTheme.typography.labelLarge,
                                    color = if (active) colors.textPrimary else colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(title = "监控轮询与后台巡检")
            Spacer(Modifier.height(AppSpacing.space2))
            Text("前台自动刷新频率", style = AppTheme.typography.labelLarge, color = colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)) {
                listOf(5, 10, 30, 60).forEach { sec ->
                    val active = settings.pollIntervalSeconds == sec
                    Surface(
                        onClick = { onSetPollInterval(sec) },
                        modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                        shape = RoundedCornerShape(AppRadius.sm),
                        color = if (active) colors.infoSoft else colors.surfaceSubtle
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${sec}s",
                                style = AppTheme.typography.labelLarge,
                                color = if (active) colors.actionPrimary else colors.textSecondary
                            )
                        }
                    }
                }
            }

            AppDivider(Modifier.padding(vertical = AppSpacing.space3))

            SettingSwitchRow(
                title = "定时后台巡检（WorkManager）",
                subtitle = "每 15 分钟在后台低功耗检查服务器在线状态与阈值",
                checked = settings.backgroundWorkEnabled,
                onCheckedChange = onSetBackgroundWork
            )
            AppDivider(Modifier.padding(vertical = AppSpacing.space2))
            SettingSwitchRow(
                title = "前台实时守护服务",
                subtitle = "在通知栏常驻保持秒级监控与即时告警（会增加电量消耗）",
                checked = settings.foregroundServiceEnabled,
                onCheckedChange = onSetForegroundService
            )
            AppDivider(Modifier.padding(vertical = AppSpacing.space2))
            SettingSwitchRow(
                title = "触发阈值时推送系统通知",
                subtitle = "关闭后仅保留应用内告警记录",
                checked = settings.notificationsEnabled,
                onCheckedChange = onSetNotifications
            )
        }

        AppCard(modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                title = "安全与存储",
                trailing = {
                    StatusChipRaw(
                        text = "AES-256-GCM",
                        icon = Icons.Rounded.Shield,
                        backgroundColor = colors.successSoft,
                        contentColor = colors.success
                    )
                }
            )
            Spacer(Modifier.height(AppSpacing.space2))
            Text(
                text = "所有 SSH 密码、私钥、私钥口令与 Agent Token 均通过 Android Keystore 硬件级密钥加密后存储于本机，不上传任何第三方服务器。",
                style = AppTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            Spacer(Modifier.height(AppSpacing.space3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_CHANNEL})",
                    style = AppTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                SecondaryButton(
                    text = "清理指标缓存",
                    onClick = onClearCache
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = AppSpacing.space2)) {
            Text(title, style = AppTheme.typography.labelLarge, color = colors.textPrimary)
            Text(subtitle, style = AppTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
