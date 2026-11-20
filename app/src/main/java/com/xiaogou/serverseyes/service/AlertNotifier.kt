package com.xiaogou.serverseyes.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xiaogou.serverseyes.ui.MainActivity
import com.xiaogou.serverseyes.R
import com.xiaogou.serverseyes.core.model.AlertEvent
import com.xiaogou.serverseyes.core.model.AlertSeverity

class AlertNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ALERTS = "server_alerts"
        const val CHANNEL_MONITOR_SERVICE = "monitor_foreground"
        const val FOREGROUND_NOTIFICATION_ID = 1001
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "服务器异常告警",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "当服务器 CPU、内存、磁盘超阈值或节点离线时发送提醒"
        }
        val serviceChannel = NotificationChannel(
            CHANNEL_MONITOR_SERVICE,
            "实时监控守护服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持后台高频监控多台服务器运行状态"
        }
        manager.createNotificationChannel(alertChannel)
        manager.createNotificationChannel(serviceChannel)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyAlert(event: AlertEvent) {
        if (!hasNotificationPermission()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            event.serverId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prefix = if (event.severity == AlertSeverity.CRITICAL) "严重告警" else "性能警告"
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$prefix · ${event.serverName}")
            .setContentText(event.message)
            .setPriority(
                if (event.severity == AlertSeverity.CRITICAL) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Ignored when system permission revoked
        }
    }

    fun buildForegroundServiceNotification(summary: String): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_MONITOR_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("小狗服务器 · 正在监控")
            .setContentText(summary)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
