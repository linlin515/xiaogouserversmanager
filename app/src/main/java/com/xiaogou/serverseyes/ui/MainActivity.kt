package com.xiaogou.serverseyes.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaogou.serverseyes.core.designsystem.primitives.AppDivider
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.theme.ServersEyesTheme
import com.xiaogou.serverseyes.core.model.ThemePreference
import com.xiaogou.serverseyes.core.util.Formatters
import com.xiaogou.serverseyes.ui.screens.AlertsScreen
import com.xiaogou.serverseyes.ui.screens.DashboardPane
import com.xiaogou.serverseyes.ui.screens.ServerDetailPane
import com.xiaogou.serverseyes.ui.screens.ServerEditorScreen
import com.xiaogou.serverseyes.ui.screens.SettingsScreen
import android.graphics.Color as AndroidColor

private enum class TopDestination(val label: String) {
    SERVERS("看板"),
    ALERTS("告警"),
    SETTINGS("设置")
}

private sealed interface ActiveRoute {
    data object Top : ActiveRoute
    data class Detail(val serverId: String) : ActiveRoute
    data class Editor(val serverId: String?) : ActiveRoute

    fun encode(): String = when (this) {
        Top -> "TOP"
        is Detail -> "DETAIL:$serverId"
        is Editor -> "EDITOR:${serverId.orEmpty()}"
    }

    companion object {
        fun decode(raw: String?): ActiveRoute {
            if (raw.isNullOrBlank()) return Top
            val parts = raw.split(":", limit = 2)
            return when (parts[0]) {
                "DETAIL" -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { Detail(it) } ?: Top
                "EDITOR" -> Editor(parts.getOrNull(1)?.takeIf { it.isNotBlank() })
                else -> Top
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val sysDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themePreference) {
                ThemePreference.SYSTEM -> sysDark
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                )
                onDispose {}
            }

            ServersEyesTheme(themePreference = settings.themePreference) {
                ServersEyesApp(vm = vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServersEyesApp(vm: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = AppTheme.colors

    val servers by vm.servers.collectAsStateWithLifecycle()
    val alertEvents by vm.alertEvents.collectAsStateWithLifecycle()
    val alertRules by vm.alertRules.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isInitialLoading by vm.isInitialLoading.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val lastRefreshMs by vm.lastRefreshMs.collectAsStateWithLifecycle()
    val selectedServerId by vm.selectedServerId.collectAsStateWithLifecycle()
    val groupFilter by vm.groupFilter.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val detailOps by vm.detailOps.collectAsStateWithLifecycle()

    var currentTabName by rememberSaveable { mutableStateOf(TopDestination.SERVERS.name) }
    val currentTab = runCatching { TopDestination.valueOf(currentTabName) }.getOrDefault(TopDestination.SERVERS)

    var activeRouteEncoded by rememberSaveable { mutableStateOf("TOP") }
    val activeRoute = ActiveRoute.decode(activeRouteEncoded)
    fun setRoute(route: ActiveRoute) {
        activeRouteEncoded = route.encode()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.startPolling()
                Lifecycle.Event.ON_STOP -> vm.stopPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        vm.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // System back: leave Detail/Editor before exiting the app
    BackHandler(enabled = activeRoute != ActiveRoute.Top) {
        setRoute(ActiveRoute.Top)
    }

    val unreadAlerts = alertEvents.count { !it.acknowledged }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 700.dp

        val topTitle = when (val r = activeRoute) {
            is ActiveRoute.Detail -> "节点监控详情"
            is ActiveRoute.Editor -> if (r.serverId == null) "添加服务器" else "编辑服务器"
            ActiveRoute.Top -> when (currentTab) {
                TopDestination.SERVERS -> "小狗服务器"
                TopDestination.ALERTS -> "告警中心"
                TopDestination.SETTINGS -> "设置"
            }
        }

        val showBack = activeRoute != ActiveRoute.Top

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,
            // Exclude IME so inner padding never double-counts the keyboard height.
            // Child screens apply a single imePadding() where needed.
            contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = topTitle,
                                    style = AppTheme.typography.titleLarge,
                                    color = colors.textPrimary
                                )
                                if (activeRoute == ActiveRoute.Top && currentTab == TopDestination.SERVERS) {
                                    val sub = if (servers.isEmpty()) {
                                        "等待添加监控节点"
                                    } else {
                                        "更新于 ${Formatters.formatRelativeTime(lastRefreshMs)} · ${servers.size} 台节点"
                                    }
                                    Text(
                                        text = sub,
                                        style = AppTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            if (showBack) {
                                IconButton(onClick = { setRoute(ActiveRoute.Top) }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "返回",
                                        tint = colors.textPrimary
                                    )
                                }
                            }
                        },
                        actions = {
                            if (activeRoute == ActiveRoute.Top && currentTab == TopDestination.SERVERS) {
                                IconButton(
                                    onClick = { vm.refreshNow() },
                                    enabled = !isRefreshing
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "立即刷新",
                                        tint = colors.textPrimary
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = colors.background,
                            titleContentColor = colors.textPrimary
                        )
                    )
                    AppDivider()
                }
            },
            floatingActionButton = {
                if (activeRoute == ActiveRoute.Top && currentTab == TopDestination.SERVERS && servers.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = { setRoute(ActiveRoute.Editor(null)) },
                        containerColor = colors.actionPrimary,
                        contentColor = colors.onActionPrimary,
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text("添加节点", style = AppTheme.typography.labelLarge) }
                    )
                }
            },
            bottomBar = {
                if (!isWide && activeRoute == ActiveRoute.Top) {
                    Column {
                        AppDivider()
                        NavigationBar(containerColor = colors.navBackground) {
                            TopDestination.entries.forEach { dest ->
                                val selected = currentTab == dest
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTabName = dest.name },
                                    icon = {
                                        val iconVec = when (dest) {
                                            TopDestination.SERVERS -> Icons.Rounded.GridView
                                            TopDestination.ALERTS -> Icons.Rounded.Notifications
                                            TopDestination.SETTINGS -> Icons.Rounded.Settings
                                        }
                                        if (dest == TopDestination.ALERTS && unreadAlerts > 0) {
                                            BadgedBox(badge = { Badge { Text("$unreadAlerts") } }) {
                                                Icon(iconVec, contentDescription = dest.label)
                                            }
                                        } else {
                                            Icon(iconVec, contentDescription = dest.label)
                                        }
                                    },
                                    label = { Text(dest.label, style = AppTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colors.actionPrimary,
                                        selectedTextColor = colors.actionPrimary,
                                        indicatorColor = colors.infoSoft,
                                        unselectedIconColor = colors.textSecondary,
                                        unselectedTextColor = colors.textSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                if (isWide && activeRoute == ActiveRoute.Top) {
                    NavigationRail(
                        containerColor = colors.navBackground,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        TopDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTabName = dest.name },
                                icon = {
                                    val iconVec = when (dest) {
                                        TopDestination.SERVERS -> Icons.Rounded.GridView
                                        TopDestination.ALERTS -> Icons.Rounded.Notifications
                                        TopDestination.SETTINGS -> Icons.Rounded.Settings
                                    }
                                    if (dest == TopDestination.ALERTS && unreadAlerts > 0) {
                                        BadgedBox(badge = { Badge { Text("$unreadAlerts") } }) {
                                            Icon(iconVec, contentDescription = dest.label)
                                        }
                                    } else {
                                        Icon(iconVec, contentDescription = dest.label)
                                    }
                                },
                                label = { Text(dest.label, style = AppTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = colors.actionPrimary,
                                    selectedTextColor = colors.actionPrimary,
                                    indicatorColor = colors.infoSoft,
                                    unselectedIconColor = colors.textSecondary,
                                    unselectedTextColor = colors.textSecondary
                                )
                            )
                        }
                    }
                    VerticalDivider(thickness = 1.dp, color = colors.border)
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (val route = activeRoute) {
                        is ActiveRoute.Editor -> {
                            ServerEditorScreen(
                                editingServerId = route.serverId,
                                loadExisting = { id -> vm.getServerForEdit(id) },
                                onTestConnection = { cfg, trust -> vm.testConnection(cfg, trust) },
                                onTrustFingerprint = { id, fp -> vm.trustFingerprint(id, fp) },
                                onSave = { cfg ->
                                    vm.saveServer(cfg) {
                                        setRoute(ActiveRoute.Top)
                                    }
                                }
                            )
                        }

                        is ActiveRoute.Detail -> {
                            val server = servers.firstOrNull { it.config.id == route.serverId }
                            ServerDetailPane(
                                server = server,
                                opsState = detailOps,
                                onRefreshServer = { vm.refreshNow() },
                                onChangeHistoryRange = { id, mins -> vm.changeHistoryRange(id, mins) },
                                onOpenLogs = { id, t, name, lines -> vm.openLogs(id, t, name, lines) },
                                onRefreshLogs = { lines -> vm.refreshOpenLogs(lines) },
                                onCloseLogs = { vm.closeLogs() },
                                onPerformWorkloadAction = { id, t, name, action ->
                                    vm.performWorkloadAction(id, t, name, action)
                                },
                                onRunDiagnostic = { id, cmd -> vm.runDiagnostic(id, cmd) },
                                onTogglePin = { id, pinned -> vm.setPinned(id, pinned) },
                                onToggleEnable = { id, en -> vm.setEnabled(id, en) },
                                onEditServer = { id -> setRoute(ActiveRoute.Editor(id)) },
                                onDeleteServer = { id ->
                                    vm.deleteServer(id) { setRoute(ActiveRoute.Top) }
                                }
                            )
                        }

                        ActiveRoute.Top -> {
                            when (currentTab) {
                                TopDestination.SERVERS -> {
                                    if (isWide && servers.isNotEmpty()) {
                                        Row(modifier = Modifier.fillMaxSize()) {
                                            DashboardPane(
                                                servers = servers,
                                                isInitialLoading = isInitialLoading,
                                                groupFilter = groupFilter,
                                                searchQuery = searchQuery,
                                                selectedServerId = selectedServerId,
                                                onSelectFilter = { vm.selectGroupFilter(it) },
                                                onSearchQueryChange = { vm.setSearchQuery(it) },
                                                onSelectServer = { id -> vm.selectServer(id) },
                                                onTogglePin = { id, pinned -> vm.setPinned(id, pinned) },
                                                onAddServer = { setRoute(ActiveRoute.Editor(null)) },
                                                onRetryAll = { vm.refreshNow() },
                                                modifier = Modifier.weight(1.05f).fillMaxHeight()
                                            )
                                            VerticalDivider(thickness = 1.dp, color = colors.border)
                                            val activeServer = servers.firstOrNull { it.config.id == selectedServerId }
                                                ?: servers.firstOrNull()
                                            ServerDetailPane(
                                                server = activeServer,
                                                opsState = detailOps,
                                                onRefreshServer = { vm.refreshNow() },
                                                onChangeHistoryRange = { id, mins -> vm.changeHistoryRange(id, mins) },
                                                onOpenLogs = { id, t, name, lines -> vm.openLogs(id, t, name, lines) },
                                                onRefreshLogs = { lines -> vm.refreshOpenLogs(lines) },
                                                onCloseLogs = { vm.closeLogs() },
                                                onPerformWorkloadAction = { id, t, name, action ->
                                                    vm.performWorkloadAction(id, t, name, action)
                                                },
                                                onRunDiagnostic = { id, cmd -> vm.runDiagnostic(id, cmd) },
                                                onTogglePin = { id, pinned -> vm.setPinned(id, pinned) },
                                                onToggleEnable = { id, en -> vm.setEnabled(id, en) },
                                                onEditServer = { id -> setRoute(ActiveRoute.Editor(id)) },
                                                onDeleteServer = { id -> vm.deleteServer(id) {} },
                                                modifier = Modifier.weight(1f).fillMaxHeight()
                                            )
                                        }
                                    } else {
                                        DashboardPane(
                                            servers = servers,
                                            isInitialLoading = isInitialLoading,
                                            groupFilter = groupFilter,
                                            searchQuery = searchQuery,
                                            selectedServerId = selectedServerId,
                                            onSelectFilter = { vm.selectGroupFilter(it) },
                                            onSearchQueryChange = { vm.setSearchQuery(it) },
                                            onSelectServer = { id ->
                                                vm.selectServer(id)
                                                setRoute(ActiveRoute.Detail(id))
                                            },
                                            onTogglePin = { id, pinned -> vm.setPinned(id, pinned) },
                                            onAddServer = { setRoute(ActiveRoute.Editor(null)) },
                                            onRetryAll = { vm.refreshNow() }
                                        )
                                    }
                                }

                                TopDestination.ALERTS -> {
                                    AlertsScreen(
                                        rules = alertRules,
                                        events = alertEvents,
                                        servers = servers,
                                        hasNotificationPermission = hasNotifPermission,
                                        onRequestNotificationPermission = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        },
                                        onUpdateRule = { vm.saveAlertRule(it) },
                                        onDeleteRule = { vm.deleteAlertRule(it) },
                                        onAcknowledge = { vm.acknowledgeAlert(it) },
                                        onAcknowledgeAll = { vm.acknowledgeAllAlerts() },
                                        onClearAll = { vm.clearAlerts() }
                                    )
                                }

                                TopDestination.SETTINGS -> {
                                    SettingsScreen(
                                        settings = settings,
                                        onSetTheme = { vm.setThemePreference(it) },
                                        onSetPollInterval = { vm.setPollIntervalSeconds(it) },
                                        onSetBackgroundWork = { vm.setBackgroundWorkEnabled(it) },
                                        onSetForegroundService = { vm.setForegroundServiceEnabled(it) },
                                        onSetNotifications = { vm.setNotificationsEnabled(it) },
                                        onClearCache = { vm.clearLocalCache() },
                                        onExportBackup = { includeSecrets, pass ->
                                            vm.exportBackupJson(includeSecrets, pass)
                                        },
                                        onImportBackup = { json, pass ->
                                            vm.importBackupJson(json, pass)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
