package com.xiaogou.serverseyes.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.xiaogou.serverseyes.ServersEyesApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ForegroundMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ServersEyesApplication
        val notification = app.container.alertNotifier.buildForegroundServiceNotification("初始化节点采集...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AlertNotifier.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(AlertNotifier.FOREGROUND_NOTIFICATION_ID, notification)
        }

        if (monitorJob?.isActive != true) {
            _isServiceRunning.value = true
            monitorJob = serviceScope.launch {
                while (isActive) {
                    runCatching { app.container.repository.refreshAllServers() }
                    val servers = runCatching { app.container.repository.observeServers().first() }.getOrDefault(emptyList())
                    val onlineCount = servers.count { it.snapshot != null && it.lastError == null }
                    val alertCount = servers.count {
                        it.status.name == "WARNING" || it.status.name == "CRITICAL" || it.status.name == "OFFLINE"
                    }
                    val updatedNotif = app.container.alertNotifier.buildForegroundServiceNotification(
                        "在线 $onlineCount/${servers.size} 台 · 异常 $alertCount 台"
                    )
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    nm.notify(AlertNotifier.FOREGROUND_NOTIFICATION_ID, updatedNotif)
                    val intervalSec = app.container.settingsStore.settingsFlow.first().pollIntervalSeconds
                    delay(intervalSec * 1000L)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        _isServiceRunning.value = false
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        fun setRunning(context: Context, running: Boolean) {
            val intent = Intent(context, ForegroundMonitorService::class.java)
            if (running) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
