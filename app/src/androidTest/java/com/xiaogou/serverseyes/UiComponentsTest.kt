package com.xiaogou.serverseyes

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.xiaogou.serverseyes.core.designsystem.theme.ServersEyesTheme
import com.xiaogou.serverseyes.core.model.AlertEvent
import com.xiaogou.serverseyes.core.model.AlertMetricType
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.AlertSeverity
import com.xiaogou.serverseyes.core.model.CollectionMode
import com.xiaogou.serverseyes.core.model.ContainerInfo
import com.xiaogou.serverseyes.core.model.DiskPartition
import com.xiaogou.serverseyes.core.model.MetricSource
import com.xiaogou.serverseyes.core.model.ProcessSummary
import com.xiaogou.serverseyes.core.model.ServerConfig
import com.xiaogou.serverseyes.core.model.ServerSnapshot
import com.xiaogou.serverseyes.core.model.ServerStatus
import com.xiaogou.serverseyes.core.model.ThemePreference
import com.xiaogou.serverseyes.core.model.UserFacingError
import com.xiaogou.serverseyes.data.local.AppSettings
import com.xiaogou.serverseyes.data.repository.ConnectionTestSummary
import com.xiaogou.serverseyes.data.repository.ServerUiModel
import com.xiaogou.serverseyes.ui.DetailOpsState
import com.xiaogou.serverseyes.ui.screens.AlertsScreen
import com.xiaogou.serverseyes.ui.screens.DashboardPane
import com.xiaogou.serverseyes.ui.screens.ServerDetailPane
import com.xiaogou.serverseyes.ui.screens.ServerEditorScreen
import com.xiaogou.serverseyes.ui.screens.SettingsScreen
import org.junit.Rule
import org.junit.Test

class UiComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dashboardShowsEmptyStateAndActionButton() {
        composeRule.setContent {
            ServersEyesTheme(themePreference = ThemePreference.DARK) {
                DashboardPane(
                    servers = emptyList(),
                    isInitialLoading = false,
                    groupFilter = "全部",
                    selectedServerId = null,
                    onSelectFilter = {},
                    onSelectServer = {},
                    onAddServer = {},
                    onRetryAll = {}
                )
            }
        }
        composeRule.onNodeWithText("尚未添加任何服务器").assertIsDisplayed()
        composeRule.onNodeWithText("添加第一台服务器").assertIsDisplayed()
    }

    @Test
    fun dashboardShowsSearchPinnedBadgeAndErrorBanner() {
        val sampleServers = listOf(
            ServerUiModel(
                config = ServerConfig(
                    id = "1", name = "prod-api-01", groupName = "生产集群", host = "10.0.0.1",
                    collectionMode = CollectionMode.HYBRID, isPinned = true
                ),
                status = ServerStatus.CRITICAL,
                snapshot = ServerSnapshot(
                    serverId = "1",
                    timestampMs = 1_000L,
                    sourceUsed = MetricSource.AGENT,
                    latencyMs = 12L,
                    cpuPercent = 94.0,
                    memTotalBytes = 1000L,
                    memUsedBytes = 820L,
                    netRxBytesPerSec = 2_048_000L
                ),
                sparklineCpu = listOf(50.0, 75.0, 94.0),
                isRefreshing = false,
                lastError = null,
                lastSeenAtMs = 1_000L
            ),
            ServerUiModel(
                config = ServerConfig("2", "edge-hk-01", "边缘节点", "10.0.0.2", CollectionMode.SSH_ONLY),
                status = ServerStatus.OFFLINE,
                snapshot = null,
                sparklineCpu = emptyList(),
                isRefreshing = false,
                lastError = UserFacingError.NetworkUnreachable(),
                lastSeenAtMs = 0L
            )
        )

        composeRule.setContent {
            ServersEyesTheme(themePreference = ThemePreference.LIGHT) {
                DashboardPane(
                    servers = sampleServers,
                    isInitialLoading = false,
                    groupFilter = "全部",
                    selectedServerId = null,
                    onSelectFilter = {},
                    onSelectServer = {},
                    onAddServer = {},
                    onRetryAll = {}
                )
            }
        }
        composeRule.onNodeWithText("搜索节点名称 / IP / 分组...").assertIsDisplayed()
        composeRule.onNodeWithText("CPU ↓").assertIsDisplayed()
        composeRule.onNodeWithText("批量").performClick()
        composeRule.onNodeWithText("已选中 0 台节点").assertIsDisplayed()
        composeRule.onNodeWithText("prod-api-01").assertIsDisplayed()
        composeRule.onNodeWithText("edge-hk-01").assertIsDisplayed()
        composeRule.onNodeWithText("严重告警").assertIsDisplayed()
        composeRule.onNodeWithText("1 台节点连接异常").assertIsDisplayed()
    }

    @Test
    fun serverEditorValidatesRequiredFields() {
        composeRule.setContent {
            ServersEyesTheme(themePreference = ThemePreference.DARK) {
                ServerEditorScreen(
                    editingServerId = null,
                    loadExisting = { null },
                    onTestConnection = { _, _ -> ConnectionTestSummary() },
                    onSave = {}
                )
            }
        }
        composeRule.onNodeWithText("经由跳板机 / 堡垒机直连（ProxyJump）").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("保存节点").performScrollTo().performClick()
        composeRule.onNodeWithText("请输入服务器显示名称").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("请输入服务器 IP 或域名").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun serverDetailOverviewAndWorkloadsRenderV12MetricsAndControls() {
        val model = ServerUiModel(
            config = ServerConfig("1", "prod-api-01", "生产集群", "10.0.0.1", CollectionMode.HYBRID),
            status = ServerStatus.ONLINE,
            snapshot = ServerSnapshot(
                serverId = "1",
                timestampMs = 1_000L,
                sourceUsed = MetricSource.AGENT,
                latencyMs = 10L,
                cpuPercent = 32.0,
                memTotalBytes = 1000L,
                memUsedBytes = 400L,
                cpuTempCelsius = 54.5,
                diskReadBytesPerSec = 1_048_576L,
                diskWriteBytesPerSec = 524_288L,
                disks = listOf(DiskPartition("/dev/vda1", "/", 1000L, 400L, 600L, 40.0, inodeUsedPercent = 15.0)),
                topProcesses = listOf(ProcessSummary(1234, "nginx", 12.5, 3.2, user = "www-data", rssBytes = 134_217_728L, state = "S")),
                containers = listOf(
                    ContainerInfo(
                        id = "abc",
                        name = "api-gateway",
                        image = "nginx:1.27",
                        state = "running",
                        statusText = "Up 2d",
                        ports = "0.0.0.0:443->443/tcp",
                        cpuPercent = 14.2,
                        memUsageText = "128MiB / 512MiB",
                        memPercent = 25.0
                    )
                )
            ),
            sparklineCpu = listOf(30.0, 32.0),
            isRefreshing = false,
            lastError = null,
            lastSeenAtMs = 1_000L
        )
        composeRule.setContent {
            ServersEyesTheme(themePreference = ThemePreference.DARK) {
                ServerDetailPane(
                    server = model,
                    opsState = DetailOpsState(),
                    onRefreshServer = {},
                    onChangeHistoryRange = { _, _ -> },
                    onOpenLogs = { _, _, _, _ -> },
                    onRefreshLogs = {},
                    onCloseLogs = {},
                    onPerformWorkloadAction = { _, _, _, _ -> },
                    onRunDiagnostic = { _, _ -> },
                    onEditServer = {},
                    onDeleteServer = {}
                )
            }
        }
        composeRule.onNodeWithText("54.5°C").assertIsDisplayed()
        composeRule.onNodeWithText("实时磁盘 I/O").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("40% · Inode 15% · 400 B / 1000 B").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("容器与服务").performClick()
        composeRule.onNodeWithText("api-gateway").assertIsDisplayed()
        composeRule.onNodeWithText("CPU 14.2%").assertIsDisplayed()
        composeRule.onNodeWithText("端口: 0.0.0.0:443->443/tcp").assertIsDisplayed()
        composeRule.onNodeWithText("停止").performClick()
        composeRule.onNodeWithText("确认停止目标？").assertIsDisplayed()
    }

    @Test
    fun alertsScreenFiltersResolvedAndActiveEvents() {
        val rules = listOf(
            AlertRule("g_cpu", null, AlertMetricType.CPU, 80.0, 95.0, cooldownMinutes = 10, enabled = true)
        )
        val events = listOf(
            AlertEvent(
                id = "e1", serverId = "1", serverName = "edge-hk-01",
                metricType = AlertMetricType.CPU, severity = AlertSeverity.CRITICAL,
                currentValue = 96.0, thresholdValue = 95.0,
                message = "edge-hk-01 CPU 达到 96%", timestampMs = 10_000L, isResolved = false
            ),
            AlertEvent(
                id = "e2", serverId = "2", serverName = "prod-api-01",
                metricType = AlertMetricType.MEMORY, severity = AlertSeverity.WARNING,
                currentValue = 88.0, thresholdValue = 85.0,
                message = "prod-api-01 内存达到 88%", timestampMs = 5_000L,
                isResolved = true, resolvedAtMs = 305_000L
            )
        )
        composeRule.setContent {
            ServersEyesTheme(themePreference = ThemePreference.DARK) {
                AlertsScreen(
                    rules = rules,
                    events = events,
                    servers = emptyList(),
                    hasNotificationPermission = true,
                    onRequestNotificationPermission = {},
                    onUpdateRule = {},
                    onAcknowledge = {},
                    onAcknowledgeAll = {},
                    onClearAll = {}
                )
            }
        }
        composeRule.onNodeWithText("严重阈值 (50% ~ 99%)").assertIsDisplayed()
        composeRule.onNodeWithText("进行中").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("已恢复").assertCountEquals(2)
        composeRule.onNodeWithText("进行中").performClick()
        composeRule.onNodeWithText("edge-hk-01 CPU 达到 96%").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsScreenShowsDndAndFileBackupControls() {
        composeRule.setContent {
            ServersEyesTheme(themePreference = ThemePreference.LIGHT) {
                SettingsScreen(
                    settings = AppSettings(dndEnabled = true),
                    onSetTheme = {},
                    onSetPollInterval = {},
                    onSetBackgroundWork = {},
                    onSetForegroundService = {},
                    onSetNotifications = {},
                    onClearCache = {}
                )
            }
        }
        composeRule.onNodeWithText("30m").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("夜间免打扰时段（DND）").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("导出文件").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("从文件选择").performScrollTo().assertIsDisplayed()
    }
}
