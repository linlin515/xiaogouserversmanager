package com.xiaogou.serverseyes.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xiaogou.serverseyes.core.designsystem.components.AppCard
import com.xiaogou.serverseyes.core.designsystem.components.AppTextField
import com.xiaogou.serverseyes.core.designsystem.components.ConfirmActionDialog
import com.xiaogou.serverseyes.core.designsystem.components.PrimaryButton
import com.xiaogou.serverseyes.core.designsystem.components.SecondaryButton
import com.xiaogou.serverseyes.core.designsystem.primitives.AppDivider
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.tokens.AppRadius
import com.xiaogou.serverseyes.core.designsystem.tokens.AppSpacing
import com.xiaogou.serverseyes.core.designsystem.tokens.tabular
import com.xiaogou.serverseyes.core.model.CollectionMode
import com.xiaogou.serverseyes.core.model.ServerConfig
import com.xiaogou.serverseyes.core.model.SshAuthType
import com.xiaogou.serverseyes.core.model.UserFacingError
import com.xiaogou.serverseyes.core.util.AgentDeployHelper
import com.xiaogou.serverseyes.data.repository.ConnectionTestSummary
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ServerEditorScreen(
    editingServerId: String?,
    loadExisting: suspend (String?) -> ServerConfig?,
    onTestConnection: suspend (ServerConfig, Boolean) -> ConnectionTestSummary,
    onSave: (ServerConfig) -> Unit,
    onTrustFingerprint: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var serverId by rememberSaveable(editingServerId) { mutableStateOf(editingServerId ?: UUID.randomUUID().toString()) }
    var name by rememberSaveable { mutableStateOf("") }
    var groupName by rememberSaveable { mutableStateOf("生产集群") }
    var host by rememberSaveable { mutableStateOf("") }
    var collectionMode by rememberSaveable { mutableStateOf(CollectionMode.HYBRID) }
    var sshPort by rememberSaveable { mutableStateOf("22") }
    var sshUsername by rememberSaveable { mutableStateOf("root") }
    var sshAuthType by rememberSaveable { mutableStateOf(SshAuthType.PASSWORD) }
    var sshPassword by rememberSaveable { mutableStateOf("") }
    var sshPrivateKey by rememberSaveable { mutableStateOf("") }
    var sshPassphrase by rememberSaveable { mutableStateOf("") }
    var trustedFingerprint by rememberSaveable { mutableStateOf<String?>(null) }
    var timeoutSeconds by rememberSaveable { mutableIntStateOf(10) }

    var jumpHostEnabled by rememberSaveable { mutableStateOf(false) }
    var jumpHost by rememberSaveable { mutableStateOf("") }
    var jumpPort by rememberSaveable { mutableStateOf("22") }
    var jumpUsername by rememberSaveable { mutableStateOf("root") }
    var jumpAuthType by rememberSaveable { mutableStateOf(SshAuthType.PASSWORD) }
    var jumpPassword by rememberSaveable { mutableStateOf("") }
    var jumpPrivateKey by rememberSaveable { mutableStateOf("") }
    var jumpKeyPassphrase by rememberSaveable { mutableStateOf("") }

    var agentUrl by rememberSaveable { mutableStateOf("") }
    var agentToken by rememberSaveable { mutableStateOf("") }
    var isPinned by rememberSaveable { mutableStateOf(false) }
    var sortOrder by rememberSaveable { mutableIntStateOf(0) }
    var enabled by rememberSaveable { mutableStateOf(true) }

    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var hostError by rememberSaveable { mutableStateOf<String?>(null) }
    var portError by rememberSaveable { mutableStateOf<String?>(null) }
    var jumpHostError by rememberSaveable { mutableStateOf<String?>(null) }
    var jumpPortError by rememberSaveable { mutableStateOf<String?>(null) }
    var agentUrlError by rememberSaveable { mutableStateOf<String?>(null) }

    var keyImportMeta by remember { mutableStateOf<String?>(null) }
    var jumpKeyImportMeta by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ConnectionTestSummary?>(null) }
    var pendingFingerprintConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    var hostKeyChangedActual by remember { mutableStateOf<String?>(null) }
    var copiedHint by remember { mutableStateOf<String?>(null) }

    fun readKeyFromUri(uri: Uri): Pair<String, String> {
        val resolver = context.contentResolver
        var displayName = "key_file"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) displayName = cursor.getString(idx) ?: displayName
            }
        }
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buf = input.readBytes()
            require(buf.size <= 131_072) { "私钥文件过大（超过 128 KB）" }
            buf
        } ?: error("无法读取所选私钥文件")
        val content = String(bytes, Charsets.UTF_8).trim()
        require(content.contains("PRIVATE KEY") || content.contains("RSA") || content.contains("OPENSSH")) {
            "文件内容不像合法的 PEM / OpenSSH 私钥"
        }
        return content to "已导入：$displayName（${bytes.size} 字节）"
    }

    val targetKeyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { readKeyFromUri(uri) }.onSuccess { (content, meta) ->
                sshAuthType = SshAuthType.PRIVATE_KEY
                sshPrivateKey = content
                keyImportMeta = meta
            }.onFailure { e ->
                keyImportMeta = "导入失败：${e.message ?: "未知错误"}"
            }
        }
    }

    val jumpKeyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { readKeyFromUri(uri) }.onSuccess { (content, meta) ->
                jumpAuthType = SshAuthType.PRIVATE_KEY
                jumpPrivateKey = content
                jumpKeyImportMeta = meta
            }.onFailure { e ->
                jumpKeyImportMeta = "导入失败：${e.message ?: "未知错误"}"
            }
        }
    }

    LaunchedEffect(editingServerId) {
        val existing = loadExisting(editingServerId)
        if (existing != null) {
            serverId = existing.id
            name = existing.name
            groupName = existing.groupName
            host = existing.host
            collectionMode = existing.collectionMode
            sshPort = existing.sshPort.toString()
            sshUsername = existing.sshUsername
            sshAuthType = existing.sshAuthType
            sshPassword = existing.sshPassword
            sshPrivateKey = existing.sshPrivateKey
            sshPassphrase = existing.sshKeyPassphrase
            trustedFingerprint = existing.trustedHostFingerprint
            timeoutSeconds = existing.timeoutSeconds
            jumpHostEnabled = existing.jumpHostEnabled
            jumpHost = existing.jumpHost
            jumpPort = existing.jumpPort.toString()
            jumpUsername = existing.jumpUsername
            jumpAuthType = existing.jumpAuthType
            jumpPassword = existing.jumpPassword
            jumpPrivateKey = existing.jumpPrivateKey
            jumpKeyPassphrase = existing.jumpKeyPassphrase
            agentUrl = existing.agentUrl
            agentToken = existing.agentToken
            isPinned = existing.isPinned
            sortOrder = existing.sortOrder
            enabled = existing.enabled
        }
    }

    fun copyToClipboard(text: String, hint: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("server-eyes-deploy", text))
        copiedHint = hint
    }

    fun validateAndBuildConfig(): ServerConfig? {
        nameError = if (name.isBlank()) "请输入服务器显示名称" else null
        val needSsh = collectionMode != CollectionMode.AGENT_ONLY
        val needAgent = collectionMode == CollectionMode.AGENT_ONLY
        hostError = if (needSsh && host.isBlank()) "请输入服务器 IP 或域名" else null
        val portNum = sshPort.toIntOrNull()
        portError = if (needSsh && (portNum == null || portNum !in 1..65535)) "端口需在 1 ~ 65535 之间" else null
        val jPortNum = jumpPort.toIntOrNull()
        jumpHostError = if (needSsh && jumpHostEnabled && jumpHost.isBlank()) "请输入跳板机 IP 或域名" else null
        jumpPortError = if (needSsh && jumpHostEnabled && (jPortNum == null || jPortNum !in 1..65535)) "跳板机端口需在 1 ~ 65535 之间" else null
        agentUrlError = if (needAgent && agentUrl.isBlank()) "仅 Agent 模式需要填写接口地址" else null

        if (listOf(nameError, hostError, portError, jumpHostError, jumpPortError, agentUrlError).any { it != null }) {
            return null
        }
        return ServerConfig(
            id = serverId,
            name = name.trim(),
            groupName = groupName.trim().ifBlank { "默认分组" },
            host = host.trim().ifBlank { agentUrl.trim() },
            collectionMode = collectionMode,
            sshPort = portNum ?: 22,
            sshUsername = sshUsername.trim().ifBlank { "root" },
            sshAuthType = sshAuthType,
            sshPassword = sshPassword,
            sshPrivateKey = sshPrivateKey,
            sshKeyPassphrase = sshPassphrase,
            trustedHostFingerprint = trustedFingerprint,
            timeoutSeconds = timeoutSeconds.coerceIn(3, 60),
            jumpHostEnabled = jumpHostEnabled,
            jumpHost = jumpHost.trim(),
            jumpPort = jPortNum ?: 22,
            jumpUsername = jumpUsername.trim().ifBlank { "root" },
            jumpAuthType = jumpAuthType,
            jumpPassword = jumpPassword,
            jumpPrivateKey = jumpPrivateKey,
            jumpKeyPassphrase = jumpKeyPassphrase,
            agentUrl = agentUrl.trim(),
            agentToken = agentToken.trim(),
            sortOrder = sortOrder,
            isPinned = isPinned,
            enabled = enabled
        )
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(AppSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.space3)
        ) {
            if (hostKeyChangedActual != null) {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = colors.warning,
                    backgroundColor = colors.warningSoft
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = colors.warning)
                            Text("主机指纹变更警告（HostKeyChanged）", style = AppTheme.typography.titleSmall, color = colors.warning)
                        }
                        Text(
                            "服务器 SSH 公钥指纹与之前保存的不一致。若服务器确已重装系统，请点击下方「重置并信任新指纹」，以更新指纹凭据。",
                            style = AppTheme.typography.bodySmall,
                            color = colors.textPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        SecondaryButton(
                            text = "重置并信任新指纹",
                            onClick = {
                                trustedFingerprint = hostKeyChangedActual
                                onTrustFingerprint(serverId, hostKeyChangedActual!!)
                                hostKeyChangedActual = null
                            }
                        )
                    }
                }
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.space3)) {
                    AppTextField(
                        label = "显示名称",
                        value = name,
                        onValueChange = { name = it; nameError = null },
                        placeholder = "例如：prod-api-01",
                        errorText = nameError
                    )
                    AppTextField(
                        label = "分组标签",
                        value = groupName,
                        onValueChange = { groupName = it },
                        placeholder = "例如：生产集群 / 数据库"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("采集模式", style = AppTheme.typography.labelLarge, color = colors.textPrimary)
                        Surface(
                            shape = RoundedCornerShape(AppRadius.md),
                            color = colors.surfaceSubtle,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    CollectionMode.HYBRID to "混合模式",
                                    CollectionMode.SSH_ONLY to "仅 SSH",
                                    CollectionMode.AGENT_ONLY to "仅 Agent"
                                ).forEach { (mode, label) ->
                                    val active = collectionMode == mode
                                    Surface(
                                        onClick = { collectionMode = mode },
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
                }
            }

            if (collectionMode != CollectionMode.AGENT_ONLY) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.space3)) {
                        Text("SSH 直连配置（免部署开箱即用）", style = AppTheme.typography.titleSmall, color = colors.textPrimary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)
                        ) {
                            AppTextField(
                                label = "主机 IP / 域名",
                                value = host,
                                onValueChange = { host = it; hostError = null },
                                placeholder = "10.0.2.2 或公网 IP",
                                errorText = hostError,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.weight(2f)
                            )
                            AppTextField(
                                label = "端口",
                                value = sshPort,
                                onValueChange = { sshPort = it; portError = null },
                                errorText = portError,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        AppTextField(
                            label = "SSH 用户名",
                            value = sshUsername,
                            onValueChange = { sshUsername = it },
                            placeholder = "root / ubuntu"
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)) {
                            listOf(SshAuthType.PASSWORD to "密码认证", SshAuthType.PRIVATE_KEY to "私钥认证").forEach { (auth, label) ->
                                val active = sshAuthType == auth
                                Surface(
                                    onClick = { sshAuthType = auth },
                                    modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                                    shape = RoundedCornerShape(AppRadius.sm),
                                    color = if (active) colors.infoSoft else colors.surfaceSubtle
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            style = AppTheme.typography.labelLarge,
                                            color = if (active) colors.actionPrimary else colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }

                        if (sshAuthType == SshAuthType.PASSWORD) {
                            AppTextField(
                                label = "SSH 密码",
                                value = sshPassword,
                                onValueChange = { sshPassword = it },
                                isSecret = true
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("私钥凭据", style = AppTheme.typography.labelLarge, color = colors.textPrimary)
                                SecondaryButton(
                                    text = "从文件导入私钥",
                                    icon = Icons.Rounded.FileOpen,
                                    onClick = { targetKeyLauncher.launch(arrayOf("*/*")) }
                                )
                            }
                            if (keyImportMeta != null) {
                                Text(
                                    text = keyImportMeta!!,
                                    style = AppTheme.typography.bodySmall,
                                    color = if (keyImportMeta!!.startsWith("导入失败")) colors.error else colors.success
                                )
                            }
                            AppTextField(
                                label = "OpenSSH / PEM 私钥内容",
                                value = sshPrivateKey,
                                onValueChange = { sshPrivateKey = it },
                                placeholder = "-----BEGIN OPENSSH PRIVATE KEY-----",
                                singleLine = false,
                                maxLines = 5
                            )
                            AppTextField(
                                label = "私钥口令（如未加密可留空）",
                                value = sshPassphrase,
                                onValueChange = { sshPassphrase = it },
                                isSecret = true
                            )
                        }

                        // Timeout picker
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("连接超时时间", style = AppTheme.typography.labelLarge, color = colors.textPrimary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(5, 10, 15, 30).forEach { sec ->
                                    val active = timeoutSeconds == sec
                                    Surface(
                                        onClick = { timeoutSeconds = sec },
                                        modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                                        shape = RoundedCornerShape(AppRadius.sm),
                                        color = if (active) colors.infoSoft else colors.surfaceSubtle,
                                        border = if (active) BorderStroke(1.dp, colors.actionPrimary) else null
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${sec}s",
                                                style = AppTheme.typography.labelSmall.tabular(),
                                                color = if (active) colors.actionPrimary else colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!trustedFingerprint.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "已信任指纹：${trustedFingerprint!!.take(24)}...",
                                    style = AppTheme.typography.bodySmall,
                                    color = colors.success
                                )
                                SecondaryButton(
                                    text = "重置指纹",
                                    onClick = { trustedFingerprint = null }
                                )
                            }
                        }

                        AppDivider()

                        // JumpHost / ProxyJump section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = AppSpacing.space2)) {
                                Text("经由跳板机 / 堡垒机直连（ProxyJump）", style = AppTheme.typography.labelLarge, color = colors.textPrimary)
                                Text("通过第一跳 SSH 端口转发访问内网目标主机", style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                            }
                            Switch(checked = jumpHostEnabled, onCheckedChange = { jumpHostEnabled = it })
                        }

                        if (jumpHostEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)
                            ) {
                                AppTextField(
                                    label = "跳板机 IP / 域名",
                                    value = jumpHost,
                                    onValueChange = { jumpHost = it; jumpHostError = null },
                                    placeholder = "bastion.example.com",
                                    errorText = jumpHostError,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    modifier = Modifier.weight(2f)
                                )
                                AppTextField(
                                    label = "端口",
                                    value = jumpPort,
                                    onValueChange = { jumpPort = it; jumpPortError = null },
                                    errorText = jumpPortError,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            AppTextField(
                                label = "跳板机用户名",
                                value = jumpUsername,
                                onValueChange = { jumpUsername = it },
                                placeholder = "root / jump-ops"
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)) {
                                listOf(SshAuthType.PASSWORD to "跳板机密码", SshAuthType.PRIVATE_KEY to "跳板机私钥").forEach { (auth, label) ->
                                    val active = jumpAuthType == auth
                                    Surface(
                                        onClick = { jumpAuthType = auth },
                                        modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                        shape = RoundedCornerShape(AppRadius.sm),
                                        color = if (active) colors.infoSoft else colors.surfaceSubtle
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                style = AppTheme.typography.labelLarge,
                                                color = if (active) colors.actionPrimary else colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                            if (jumpAuthType == SshAuthType.PASSWORD) {
                                AppTextField(
                                    label = "跳板机 SSH 密码",
                                    value = jumpPassword,
                                    onValueChange = { jumpPassword = it },
                                    isSecret = true
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("跳板机私钥", style = AppTheme.typography.labelLarge, color = colors.textPrimary)
                                    SecondaryButton(
                                        text = "从文件导入私钥",
                                        icon = Icons.Rounded.FileOpen,
                                        onClick = { jumpKeyLauncher.launch(arrayOf("*/*")) }
                                    )
                                }
                                if (jumpKeyImportMeta != null) {
                                    Text(
                                        text = jumpKeyImportMeta!!,
                                        style = AppTheme.typography.bodySmall,
                                        color = if (jumpKeyImportMeta!!.startsWith("导入失败")) colors.error else colors.success
                                    )
                                }
                                AppTextField(
                                    label = "跳板机 OpenSSH / PEM 私钥内容",
                                    value = jumpPrivateKey,
                                    onValueChange = { jumpPrivateKey = it },
                                    singleLine = false,
                                    maxLines = 4
                                )
                                AppTextField(
                                    label = "跳板机私钥口令（未加密可留空）",
                                    value = jumpKeyPassphrase,
                                    onValueChange = { jumpKeyPassphrase = it },
                                    isSecret = true
                                )
                            }
                        }
                    }
                }
            }

            if (collectionMode != CollectionMode.SSH_ONLY) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.space3)) {
                        Text(
                            text = if (collectionMode == CollectionMode.HYBRID) "轻量 Agent 配置（可选，提供 24h 历史曲线）" else "轻量 Agent 接口配置",
                            style = AppTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        AppTextField(
                            label = "Agent 地址",
                            value = agentUrl,
                            onValueChange = { agentUrl = it; agentUrlError = null },
                            placeholder = "http://10.0.2.2:19090",
                            errorText = agentUrlError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                        AppTextField(
                            label = "Bearer Token",
                            value = agentToken,
                            onValueChange = { agentToken = it },
                            placeholder = "启动 Agent 时设置的 --token",
                            isSecret = true
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("服务端 Agent 一键命令生成", style = AppTheme.typography.labelLarge, color = colors.textPrimary)
                            val tokenVal = agentToken.ifBlank { "my-secret-token" }
                            val runCmd = AgentDeployHelper.buildQuickRunCommand(19090, tokenVal)
                            val installCmd = AgentDeployHelper.buildSystemdInstallCommand(19090, tokenVal)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AppRadius.sm))
                                    .background(Color(0xFF0B0F14))
                                    .padding(AppSpacing.space3)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = installCmd,
                                        style = AppTheme.typography.monoBody,
                                        color = Color(0xFFCDD9E5)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)
                            ) {
                                SecondaryButton(
                                    text = "复制安装脚本",
                                    icon = Icons.Rounded.ContentCopy,
                                    onClick = { copyToClipboard(installCmd, "已复制 Systemd 一键安装命令") }
                                )
                                SecondaryButton(
                                    text = "复制前台运行命令",
                                    icon = Icons.Rounded.ContentCopy,
                                    onClick = { copyToClipboard(runCmd, "已复制 Python 前台运行命令") }
                                )
                                SecondaryButton(
                                    text = "生成新 Token",
                                    icon = Icons.Rounded.Refresh,
                                    onClick = {
                                        agentToken = AgentDeployHelper.generateSecureToken()
                                        copiedHint = "已生成高强度随机 Token"
                                    }
                                )
                            }
                            if (copiedHint != null) {
                                Text(copiedHint!!, style = AppTheme.typography.bodySmall, color = colors.success)
                            }
                        }
                    }
                }
            }

            testResult?.let { summary ->
                val ok = summary.isOverallSuccess
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (ok) colors.success else colors.error,
                    backgroundColor = if (ok) colors.successSoft else colors.errorSoft
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (ok) "连接测试通过" else (summary.error?.title ?: "连接测试未通过"),
                            style = AppTheme.typography.titleSmall,
                            color = if (ok) colors.success else colors.error
                        )
                        if (summary.agentDetail.isNotBlank()) {
                            Text("Agent：${summary.agentDetail}", style = AppTheme.typography.bodySmall, color = colors.textPrimary)
                        }
                        if (summary.sshDetail.isNotBlank()) {
                            Text("SSH：${summary.sshDetail}", style = AppTheme.typography.bodySmall, color = colors.textPrimary)
                        }
                        if (!ok && summary.error != null) {
                            Text(summary.error.suggestion, style = AppTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.space2)
            ) {
                SecondaryButton(
                    text = "测试连接",
                    loading = isTesting,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val cfg = validateAndBuildConfig()
                        if (cfg == null) {
                            scope.launch { scrollState.animateScrollTo(0) }
                            return@SecondaryButton
                        }
                        scope.launch {
                            isTesting = true
                            val res = onTestConnection(cfg, false)
                            isTesting = false
                            testResult = res
                            val err = res.error
                            if (err is UserFacingError.HostKeyUntrusted) {
                                pendingFingerprintConfirm = err.fingerprint to err.keyType
                            } else if (err is UserFacingError.HostKeyChanged) {
                                hostKeyChangedActual = err.actual
                            } else if (res.observedFingerprint != null && trustedFingerprint == null) {
                                trustedFingerprint = res.observedFingerprint
                            }
                        }
                    }
                )
                PrimaryButton(
                    text = "保存节点",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val cfg = validateAndBuildConfig()
                        if (cfg == null) {
                            scope.launch { scrollState.animateScrollTo(0) }
                            return@PrimaryButton
                        }
                        onSave(cfg)
                    }
                )
            }
            Spacer(Modifier.height(AppSpacing.space6))
        }
    }

    pendingFingerprintConfirm?.let { (fp, keyType) ->
        ConfirmActionDialog(
            title = "信任 SSH 主机指纹？",
            message = "首次连接此服务器，请核对公钥指纹（$keyType）：\n\n$fp",
            confirmText = "信任指纹",
            onConfirm = {
                trustedFingerprint = fp
                pendingFingerprintConfirm = null
                val cfg = validateAndBuildConfig()
                if (cfg != null) {
                    scope.launch {
                        isTesting = true
                        testResult = onTestConnection(cfg.copy(trustedHostFingerprint = fp), true)
                        isTesting = false
                    }
                }
            },
            onDismiss = { pendingFingerprintConfirm = null }
        )
    }
}
