package com.xiaogou.serverseyes.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SaveAlt
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xiaogou.serverseyes.BuildConfig
import com.xiaogou.serverseyes.core.backup.BackupImportStrategy
import com.xiaogou.serverseyes.core.backup.BackupPreview
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
import com.xiaogou.serverseyes.core.designsystem.tokens.tabular
import com.xiaogou.serverseyes.core.model.ThemePreference
import com.xiaogou.serverseyes.core.util.Formatters
import com.xiaogou.serverseyes.data.local.AppSettings
import com.xiaogou.serverseyes.data.repository.BackupImportSummary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSetTheme: (ThemePreference) -> Unit,
    onSetPollInterval: (Int) -> Unit,
    onSetBackgroundWork: (Boolean) -> Unit,
    onSetBackgroundInterval: (Int) -> Unit = {},
    onSetForegroundService: (Boolean) -> Unit,
    onSetNotifications: (Boolean) -> Unit,
    onSetNotifyOnRecovery: (Boolean) -> Unit = {},
    onSetDndConfig: (Boolean, Int, Int, Boolean) -> Unit = { _, _, _, _ -> },
    onClearCache: () -> Unit,
    onExportBackup: suspend (Boolean, String?, Boolean) -> String = { _, _, _ -> "" },
    onPreviewBackup: (String, String?) -> Result<BackupPreview> = { _, _ ->
        Result.failure(IllegalStateException("不支持预览"))
    },
    onImportBackup: suspend (String, String?, BackupImportStrategy, Boolean) -> Result<BackupImportSummary> = { _, _, _, _ ->
        Result.failure(IllegalStateException("不支持导入"))
    },
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var includeSecrets by rememberSaveable { mutableStateOf(true) }
    var includeHistory by rememberSaveable { mutableStateOf(false) }
    var backupPassphrase by rememberSaveable { mutableStateOf("") }
    var backupJsonPreview by remember { mutableStateOf("") }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var importText by rememberSaveable { mutableStateOf("") }
    var importPassphrase by rememberSaveable { mutableStateOf("") }
    var importStrategyName by rememberSaveable { mutableStateOf(BackupImportStrategy.MERGE.name) }
    val importStrategy = runCatching { BackupImportStrategy.valueOf(importStrategyName) }.getOrDefault(BackupImportStrategy.MERGE)
    var importPreview by remember { mutableStateOf<BackupPreview?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = pendingExportJson
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                } ?: error("无法打开目标文件")
            }.onSuccess {
                statusIsError = false
                statusMessage = "已保存备份文件（${payload.length} 字符）"
            }.onFailure { e ->
                statusIsError = true
                statusMessage = "保存文件失败：${e.message ?: "未知错误"}"
            }
        }
        pendingExportJson = null
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("无法读取所选文件")
            }.onSuccess { content ->
                importText = content
                importPreview = null
                statusIsError = false
                statusMessage = "已读取备份文件（${content.length} 字符），请点击「解析预览」或直接导入"
            }.onFailure { e ->
                statusIsError = true
                statusMessage = "读取文件失败：${e.message ?: "未知错误"}"
            }
        }
    }

    fun copyToClipboard(text: String, hint: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("server-eyes-backup", text))
        statusIsError = false
        statusMessage = hint
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .imePadding()
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
                SectionHeader(title = "监控轮询与通知策略")
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
                    subtitle = "每 ${settings.backgroundIntervalMinutes} 分钟在后台低功耗检查服务器在线状态与阈值",
                    checked = settings.backgroundWorkEnabled,
                    onCheckedChange = onSetBackgroundWork
                )
                if (settings.backgroundWorkEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15 to "15m", 30 to "30m", 60 to "1h", 120 to "2h").forEach { (mins, label) ->
                            val active = settings.backgroundIntervalMinutes == mins
                            Surface(
                                onClick = { onSetBackgroundInterval(mins) },
                                modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                                shape = RoundedCornerShape(AppRadius.sm),
                                color = if (active) colors.infoSoft else colors.surfaceSubtle
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = AppTheme.typography.labelSmall.tabular(),
                                        color = if (active) colors.actionPrimary else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
                AppDivider(Modifier.padding(vertical = AppSpacing.space2))
                SettingSwitchRow(
                    title = "前台实时守护服务",
                    subtitle = "通知栏常驻秒级监控；无网络连接时自动挂起省电，联网即刻恢复",
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
                if (settings.notificationsEnabled) {
                    AppDivider(Modifier.padding(vertical = AppSpacing.space2))
                    SettingSwitchRow(
                        title = "指标恢复正常时发送通知",
                        subtitle = "回落阈值后自动撤销原告警弹窗并发送恢复提示",
                        checked = settings.notifyOnRecovery,
                        onCheckedChange = onSetNotifyOnRecovery
                    )
                    AppDivider(Modifier.padding(vertical = AppSpacing.space2))
                    SettingSwitchRow(
                        title = "夜间免打扰时段（DND）",
                        subtitle = "时段内静默普通警告与恢复通知（${Formatters.formatMinuteOfDay(settings.dndStartMinute)} - ${Formatters.formatMinuteOfDay(settings.dndEndMinute)}）",
                        checked = settings.dndEnabled,
                        onCheckedChange = { en ->
                            onSetDndConfig(en, settings.dndStartMinute, settings.dndEndMinute, settings.dndAllowCritical)
                        }
                    )
                    if (settings.dndEnabled) {
                        Spacer(Modifier.height(AppSpacing.space2))
                        Text("开始时间", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(21 * 60, 22 * 60, 23 * 60, 0).forEach { m ->
                                val active = settings.dndStartMinute == m
                                Surface(
                                    onClick = { onSetDndConfig(true, m, settings.dndEndMinute, settings.dndAllowCritical) },
                                    shape = RoundedCornerShape(AppRadius.full),
                                    color = if (active) colors.infoSoft else colors.surfaceSubtle
                                ) {
                                    Text(
                                        text = Formatters.formatMinuteOfDay(m),
                                        style = AppTheme.typography.labelSmall.tabular(),
                                        color = if (active) colors.actionPrimary else colors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(AppSpacing.space2))
                        Text("结束时间", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(6 * 60, 7 * 60, 8 * 60, 9 * 60).forEach { m ->
                                val active = settings.dndEndMinute == m
                                Surface(
                                    onClick = { onSetDndConfig(true, settings.dndStartMinute, m, settings.dndAllowCritical) },
                                    shape = RoundedCornerShape(AppRadius.full),
                                    color = if (active) colors.infoSoft else colors.surfaceSubtle
                                ) {
                                    Text(
                                        text = Formatters.formatMinuteOfDay(m),
                                        style = AppTheme.typography.labelSmall.tabular(),
                                        color = if (active) colors.actionPrimary else colors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(AppSpacing.space2))
                        SettingSwitchRow(
                            title = "免打扰期间严重告警仍推送",
                            subtitle = "Critical 级别或节点彻底离线时穿透免打扰",
                            checked = settings.dndAllowCritical,
                            onCheckedChange = { allow ->
                                onSetDndConfig(true, settings.dndStartMinute, settings.dndEndMinute, allow)
                            }
                        )
                    }
                    Spacer(Modifier.height(AppSpacing.space2))
                    SecondaryButton(
                        text = "系统通知渠道设置（声音/震动）",
                        icon = Icons.Rounded.Notifications,
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    )
                }
            }

            // Config backup
            AppCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader(title = "配置备份与多设备迁移")
                Spacer(Modifier.height(AppSpacing.space2))
                Text(
                    "导出服务器与告警规则 JSON。开启「包含敏感凭据」时必须设置至少 6 位口令并使用 PBKDF2 + AES-256-GCM 加密；关闭则脱敏导出。",
                    style = AppTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(AppSpacing.space2))
                SettingSwitchRow(
                    title = "导出包含敏感凭据",
                    subtitle = if (includeSecrets) "将包含 SSH 密码/私钥与 Agent Token（强制加密）" else "脱敏导出，不包含任何凭据",
                    checked = includeSecrets,
                    onCheckedChange = { includeSecrets = it }
                )
                AppDivider(Modifier.padding(vertical = AppSpacing.space2))
                SettingSwitchRow(
                    title = "附带近 48h 指标采样与告警历史",
                    subtitle = "换机迁移时保留曲线与历史记录（备份体积会相应增大）",
                    checked = includeHistory,
                    onCheckedChange = { includeHistory = it }
                )

                val passphraseMissingOrShort = includeSecrets && backupPassphrase.length < 6
                if (includeSecrets) {
                    Spacer(Modifier.height(AppSpacing.space2))
                    AppTextField(
                        label = "加密口令（必填）",
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        isSecret = true,
                        placeholder = "至少 6 位字符，用于加密敏感凭据",
                        errorText = if (backupPassphrase.isNotEmpty() && backupPassphrase.length < 6) {
                            "口令长度至少 6 位"
                        } else null
                    )
                }
                Spacer(Modifier.height(AppSpacing.space2))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)) {
                    PrimaryButton(
                        text = "复制备份",
                        icon = Icons.Rounded.ContentCopy,
                        loading = isBusy,
                        enabled = !passphraseMissingOrShort,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                isBusy = true
                                runCatching {
                                    onExportBackup(includeSecrets, backupPassphrase.takeIf { it.isNotBlank() }, includeHistory)
                                }.onSuccess { json ->
                                    backupJsonPreview = json
                                    copyToClipboard(json, "备份 JSON 已复制到剪贴板（${json.length} 字符）")
                                }.onFailure { e ->
                                    statusIsError = true
                                    statusMessage = "导出失败：${e.message ?: "未知错误"}"
                                }
                                isBusy = false
                            }
                        }
                    )
                    SecondaryButton(
                        text = "导出文件",
                        icon = Icons.Rounded.SaveAlt,
                        enabled = !isBusy && !passphraseMissingOrShort,
                        onClick = {
                            scope.launch {
                                isBusy = true
                                runCatching {
                                    onExportBackup(includeSecrets, backupPassphrase.takeIf { it.isNotBlank() }, includeHistory)
                                }.onSuccess { json ->
                                    backupJsonPreview = json
                                    pendingExportJson = json
                                    saveFileLauncher.launch("xiaogou_backup_${System.currentTimeMillis() / 1000}.json")
                                }.onFailure { e ->
                                    statusIsError = true
                                    statusMessage = "导出失败：${e.message ?: "未知错误"}"
                                }
                                isBusy = false
                            }
                        }
                    )
                }

                AppDivider(Modifier.padding(vertical = AppSpacing.space3))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("导入恢复备份", style = AppTheme.typography.labelLarge, color = colors.textPrimary)
                    SecondaryButton(
                        text = "从文件选择",
                        icon = Icons.Rounded.FileOpen,
                        onClick = { openFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                    )
                }
                Spacer(Modifier.height(AppSpacing.space2))
                AppTextField(
                    label = "备份 JSON 内容",
                    value = importText,
                    onValueChange = {
                        importText = it
                        importPreview = null
                    },
                    singleLine = false,
                    maxLines = 4,
                    placeholder = "粘贴备份 JSON 内容或从上方选择文件..."
                )
                Spacer(Modifier.height(AppSpacing.space2))
                AppTextField(
                    label = "解密口令（如备份已加密）",
                    value = importPassphrase,
                    onValueChange = {
                        importPassphrase = it
                        importPreview = null
                    },
                    isSecret = true,
                    placeholder = "未加密备份可留空"
                )
                Spacer(Modifier.height(AppSpacing.space2))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)) {
                    SecondaryButton(
                        text = "解析预览",
                        icon = Icons.Rounded.Download,
                        enabled = importText.isNotBlank() && !isBusy,
                        onClick = {
                            onPreviewBackup(importText, importPassphrase.takeIf { it.isNotBlank() }).fold(
                                onSuccess = { p ->
                                    importPreview = p
                                    statusIsError = false
                                    statusMessage = null
                                },
                                onFailure = { e ->
                                    importPreview = null
                                    statusIsError = true
                                    statusMessage = "预览失败：${e.message ?: "格式不正确"}"
                                }
                            )
                        }
                    )
                    PrimaryButton(
                        text = if (importStrategy == BackupImportStrategy.REPLACE) "覆盖导入" else "合并导入",
                        icon = Icons.Rounded.Upload,
                        loading = isBusy,
                        enabled = importText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                isBusy = true
                                onImportBackup(
                                    importText,
                                    importPassphrase.takeIf { it.isNotBlank() },
                                    importStrategy,
                                    true
                                ).fold(
                                    onSuccess = { summary ->
                                        statusIsError = false
                                        statusMessage = "导入成功：${summary.serverCount} 台服务器、${summary.ruleCount} 条规则" +
                                            if (summary.eventCount > 0 || summary.sampleCount > 0) {
                                                "、${summary.eventCount} 条告警、${summary.sampleCount} 条采样"
                                            } else ""
                                        importText = ""
                                        importPreview = null
                                    },
                                    onFailure = { e ->
                                        statusIsError = true
                                        statusMessage = "导入失败：${e.message ?: "文件格式不正确"}"
                                    }
                                )
                                isBusy = false
                            }
                        }
                    )
                }

                Spacer(Modifier.height(AppSpacing.space2))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        BackupImportStrategy.MERGE to "合并导入（保留现有）",
                        BackupImportStrategy.REPLACE to "覆盖替换（清空重建）"
                    ).forEach { (st, label) ->
                        val active = importStrategy == st
                        Surface(
                            onClick = { importStrategyName = st.name },
                            shape = RoundedCornerShape(AppRadius.full),
                            color = if (active) colors.infoSoft else colors.surfaceSubtle,
                            border = if (active) BorderStroke(1.dp, colors.actionPrimary) else null
                        ) {
                            Text(
                                text = label,
                                style = AppTheme.typography.labelSmall,
                                color = if (active) colors.actionPrimary else colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                importPreview?.let { p ->
                    Spacer(Modifier.height(AppSpacing.space2))
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = colors.actionPrimary,
                        backgroundColor = colors.infoSoft
                    ) {
                        Text(
                            text = "备份预览（${if (p.encrypted) "已加密" else "未加密"} · ${if (p.containsSecrets) "含凭据" else "已脱敏"}）",
                            style = AppTheme.typography.labelLarge,
                            color = colors.actionPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "包含 ${p.serverCount} 台节点（${p.serverNames.take(3).joinToString("、")}${if (p.serverCount > 3) " 等" else ""}）、${p.ruleCount} 条规则、${p.eventCount} 条告警、${p.sampleCount} 条历史采样",
                            style = AppTheme.typography.bodySmall,
                            color = colors.textPrimary
                        )
                    }
                }

                if (statusMessage != null) {
                    Spacer(Modifier.height(AppSpacing.space2))
                    Text(
                        text = statusMessage!!,
                        style = AppTheme.typography.bodySmall,
                        color = if (statusIsError) colors.error else colors.success
                    )
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
