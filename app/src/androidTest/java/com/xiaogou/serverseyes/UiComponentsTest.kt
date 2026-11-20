package com.xiaogou.serverseyes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.xiaogou.serverseyes.core.designsystem.theme.ServersEyesTheme
import com.xiaogou.serverseyes.core.model.CollectionMode
import com.xiaogou.serverseyes.core.model.ContainerInfo
import com.xiaogou.serverseyes.core.model.MetricSource
import com.xiaogou.serverseyes.core.model.ServerConfig
import com.xiaogou.serverseyes.core.model.ServerSnapshot
import com.xiaogou.serverseyes.core.model.ServerStatus
import com.xiaogou.serverseyes.core.model.ThemePreference
import com.xiaogou.serverseyes.core.model.UserFacingError
import com.xiaogou.serverseyes.data.repository.ConnectionTestSummary
import com.xiaogou.serverseyes.data.repository.ServerUiModel
import com.xiaogou.serverseyes.ui.DetailOpsState
import com.xiaogou.serverseyes.ui.screens.DashboardPane
import com.xiaogou.serverseyes.ui.screens.ServerDetailPane
import com.xiaogou.serverseyes.ui.screens.ServerEditorScreen
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
        composeRule.onNodeWithText("保存节点").performScrollTo().performClick()
        composeRule.onNodeWithText("请输入服务器显示名称").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("请输入服务器 IP 或域名").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun serverDetailWorkloadsShowsStopAndRestartControls() {
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
                containers = listOf(
                    ContainerInfo("abc", "api-gateway", "nginx:1.27", "running", "Up 2d", "0.0.0.0:443->443/tcp")
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
        composeRule.onNodeWithText("容器与服务").performClick()
        composeRule.onNodeWithText("api-gateway").assertIsDisplayed()
        composeRule.onNodeWithText("端口: 0.0.0.0:443->443/tcp").assertIsDisplayed()
        composeRule.onNodeWithText("停止").performClick()
        composeRule.onNodeWithText("确认停止目标？").assertIsDisplayed()
    }
}
