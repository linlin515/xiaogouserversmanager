package com.xiaogou.serverseyes.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
    private val networkAvailable = MutableStateFlow(true)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registerNetworkObserver()
    }

    private fun registerNetworkObserver() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val activeCaps = cm.getNetworkCapabilities(cm.activeNetwork)
        networkAvailable.value = activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkAvailable.value = true
            }

            override fun onLost(network: Network) {
                val currentCaps = cm.getNetworkCapabilities(cm.activeNetwork)
                networkAvailable.value =
                    currentCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                networkAvailable.value =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        networkCallback = callback
        runCatching { cm.registerDefaultNetworkCallback(callback) }
    }

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
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    if (!networkAvailable.value) {
                        val pausedNotif = app.container.alertNotifier.buildForegroundServiceNotification(
                            "无网络连接 · 已暂停轮询等待联网恢复"
                        )
                        nm.notify(AlertNotifier.FOREGROUND_NOTIFICATION_ID, pausedNotif)
                        networkAvailable.first { it }
                        continue
                    }

                    runCatching { app.container.repository.refreshAllServers() }
                    val servers = runCatching { app.container.repository.observeServers().first() }.getOrDefault(emptyList())
                    val onlineCount = servers.count { it.snapshot != null && it.lastError == null }
                    val alertCount = servers.count {
                        it.status.name == "WARNING" || it.status.name == "CRITICAL" || it.status.name == "OFFLINE"
                    }
                    val updatedNotif = app.container.alertNotifier.buildForegroundServiceNotification(
                        "在线 $onlineCount/${servers.size} 台 · 异常 $alertCount 台"
                    )
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
        networkCallback?.let { cb ->
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            runCatching { cm?.unregisterNetworkCallback(cb) }
        }
        networkCallback = null
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
