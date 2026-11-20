package com.xiaogou.serverseyes

import android.app.Application
import com.xiaogou.serverseyes.core.network.AgentApiClient
import com.xiaogou.serverseyes.core.security.CryptoManager
import com.xiaogou.serverseyes.core.ssh.SshClientManager
import com.xiaogou.serverseyes.data.local.AppDatabase
import com.xiaogou.serverseyes.data.local.SettingsStore
import com.xiaogou.serverseyes.data.repository.ServerRepository
import com.xiaogou.serverseyes.service.AlertNotifier
import com.xiaogou.serverseyes.service.ForegroundMonitorService
import com.xiaogou.serverseyes.service.MonitoringWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    val database = AppDatabase.build(application)
    val settingsStore = SettingsStore(application)
    val cryptoManager = CryptoManager()
    val sshClient = SshClientManager()
    val agentClient = AgentApiClient()
    val alertNotifier = AlertNotifier(application)
    val repository = ServerRepository(
        serverDao = database.serverDao(),
        sampleDao = database.metricSampleDao(),
        alertRuleDao = database.alertRuleDao(),
        alertEventDao = database.alertEventDao(),
        settingsStore = settingsStore,
        cryptoManager = cryptoManager,
        sshClient = sshClient,
        agentClient = agentClient,
        alertNotifier = alertNotifier
    )
}

class ServersEyesApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.repository.ensureDefaultAlertRules()
            val settings = container.settingsStore.settingsFlow.first()
            MonitoringWorker.schedule(this@ServersEyesApplication, settings.backgroundWorkEnabled)
            if (settings.foregroundServiceEnabled) {
                runCatching { ForegroundMonitorService.setRunning(this@ServersEyesApplication, true) }
            }
        }
    }
}
