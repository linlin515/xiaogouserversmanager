package com.xiaogou.serverseyes.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xiaogou.serverseyes.BuildConfig
import com.xiaogou.serverseyes.core.designsystem.components.AppCard
import com.xiaogou.serverseyes.core.designsystem.components.AppTextField
import com.xiaogou.serverseyes.core.designsystem.components.ConfirmActionDialog
import com.xiaogou.serverseyes.core.designsystem.components.PrimaryButton
import com.xiaogou.serverseyes.core.designsystem.components.SecondaryButton
import com.xiaogou.serverseyes.core.designsystem.components.SectionHeader
import com.xiaogou.serverseyes.core.designsystem.primitives.AppDivider
import com.xiaogou.serverseyes.core.designsystem.primitives.StatusChipRaw
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.tokens.AppRadius
import com.xiaogou.serverseyes.core.designsystem.tokens.AppSpacing
import com.xiaogou.serverseyes.core.model.ThemePreference
import com.xiaogou.serverseyes.data.local.AppSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSetTheme: (ThemePreference) -> Unit,
    onSetPollInterval: (Int) -> Unit,
    onSetBackgroundWork: (Boolean) -> Unit,
    onSetForegroundService: (Boolean) -> Unit,
    onSetNotifications: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onExportBackup: suspend (Boolean, String?) -> String = { _, _ -> "" },
    onImportBackup: suspend (String, String?) -> Result<Pair<Int, Int>> = { _, _ ->
        Result.failure(IllegalStateException("不支持导入"))
    },
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var includeSecrets by remember { mutableStateOf(true) }
    var backupPassphrase by remember { mutableStateOf("") }
    var backupJsonPreview by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var importPassphrase by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    fun copyToClipboard(text: String, hint: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("server-eyes-backup", text))
        statusMessage = hint
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
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

            // Config backup
            AppCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(title = "配置备份与多设备迁移")
                Spacer(Modifier.height(AppSpacing.space2))
                Text(
                    "可导出服务器列表与告警规则 JSON。开启「包含敏感凭据」并设置口令后将使用 PBKDF2 + AES-256-GCM 加密；关闭则脱敏导出（不含密码/私钥/Token）。",
                    style = AppTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(AppSpacing.space2))
                SettingSwitchRow(
                    title = "导出包含敏感凭据",
                    subtitle = if (includeSecrets) "将包含 SSH 密码/私钥与 Agent Token（需口令加密）" else "脱敏导出，不包含任何凭据",
                    checked = includeSecrets,
                    onCheckedChange = { includeSecrets = it }
                )
                if (includeSecrets) {
                    Spacer(Modifier.height(AppSpacing.space2))
                    AppTextField(
                        label = "加密口令",
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        isSecret = true,
                        placeholder = "至少 6 位，用于加密备份文件"
                    )
                }
                Spacer(Modifier.height(AppSpacing.space2))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)) {
                    PrimaryButton(
                        text = "生成并复制备份",
                        icon = Icons.Rounded.Download,
                        loading = isBusy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                isBusy = true
                                runCatching {
                                    onExportBackup(includeSecrets, backupPassphrase.takeIf { it.isNotBlank() })
                                }.onSuccess { json ->
                                    backupJsonPreview = json
                                    copyToClipboard(json, "备份 JSON 已复制到剪贴板（${json.length} 字符）")
                                }.onFailure { e ->
                                    statusMessage = "导出失败：${e.message ?: "未知错误"}"
                                }
                                isBusy = false
                            }
                        }
                    )
                    SecondaryButton(
                        text = "清空状态",
                        onClick = { statusMessage = null; backupJsonPreview = "" }
                    )
                }

                AppDivider(Modifier.padding(vertical = AppSpacing.space3))

                Text("导入恢复备份", style = AppTheme.typography.labelLarge, color = colors.textPrimary)
                Spacer(Modifier.height(AppSpacing.space2))
                AppTextField(
                    label = "备份 JSON 内容",
                    value = importText,
                    onValueChange = { importText = it },
                    singleLine = false,
                    maxLines = 4,
                    placeholder = "粘贴备份 JSON 文件内容..."
                )
                Spacer(Modifier.height(AppSpacing.space2))
                AppTextField(
                    label = "解密口令（如备份已加密）",
                    value = importPassphrase,
                    onValueChange = { importPassphrase = it },
                    isSecret = true,
                    placeholder = "未加密备份可留空"
                )
                Spacer(Modifier.height(AppSpacing.space2))
                SecondaryButton(
                    text = "导入并合并配置",
                    icon = Icons.Rounded.Upload,
                    loading = isBusy,
                    enabled = importText.isNotBlank(),
                    onClick = {
                        scope.launch {
                            isBusy = true
                            onImportBackup(importText, importPassphrase.takeIf { it.isNotBlank() }).fold(
                                onSuccess = { (serverCount, ruleCount) ->
                                    statusMessage = "导入成功：$serverCount 台服务器、$ruleCount 条告警规则"
                                    importText = ""
                                },
                                onFailure = { e ->
                                    statusMessage = "导入失败：${e.message ?: "文件格式不正确"}"
                                }
                            )
                            isBusy = false
                        }
                    }
                )

                if (statusMessage != null) {
                    Spacer(Modifier.height(AppSpacing.space2))
                    Text(statusMessage!!, style = AppTheme.typography.bodySmall, color = colors.success)
                }
                if (backupJsonPreview.isNotBlank()) {
                    Spacer(Modifier.height(AppSpacing.space2))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .clip(RoundedCornerShape(AppRadius.sm))
                            .background(Color(0xFF0B0F14))
                            .padding(AppSpacing.space2)
                    ) {
                        Text(
                            text = backupJsonPreview.take(600) + if (backupJsonPreview.length > 600) "\n..." else "",
                            style = AppTheme.typography.monoBody,
                            color = Color(0xFFCDD9E5)
                        )
                    }
                }
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
                        onClick = { showClearCacheConfirm = true }
                    )
                }
            }
        }
    }

    if (showClearCacheConfirm) {
        ConfirmActionDialog(
            title = "确认清理本地指标缓存？",
            message = "将删除本机保存的全部历史性能采样数据（最长 48 小时），不影响服务器端数据与节点配置。",
            confirmText = "确认清理",
            isDangerous = true,
            onConfirm = {
                showClearCacheConfirm = false
                onClearCache()
            },
            onDismiss = { showClearCacheConfirm = false }
        )
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
