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
import com.xiaogou.serverseyes.R
import com.xiaogou.serverseyes.core.model.AlertEvent
import com.xiaogou.serverseyes.core.model.AlertMetricType
import com.xiaogou.serverseyes.core.model.AlertSeverity
import com.xiaogou.serverseyes.ui.MainActivity

class AlertNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ALERTS = "server_alerts"
        const val CHANNEL_ALERTS_WARNING = "server_alerts_warning"
        const val CHANNEL_ALERT_RECOVERY = "server_alert_recovery"
        const val CHANNEL_MONITOR_SERVICE = "monitor_foreground"
        const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val GROUP_KEY_ALERTS = "xiaogou_alerts_group"
        private const val SUMMARY_NOTIFICATION_ID = 2001
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val criticalChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "严重告警通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "服务器达到严重阈值或持续离线时高优先级提醒"
        }
        val warningChannel = NotificationChannel(
            CHANNEL_ALERTS_WARNING,
            "性能警告通知",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "服务器 CPU、内存、磁盘达到警告阈值时提醒"
        }
        val recoveryChannel = NotificationChannel(
            CHANNEL_ALERT_RECOVERY,
            "告警恢复通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "服务器指标回落至正常范围或重新上线时提醒"
        }
        val serviceChannel = NotificationChannel(
            CHANNEL_MONITOR_SERVICE,
            "实时监控守护服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持后台高频监控多台服务器运行状态"
        }
        manager.createNotificationChannel(criticalChannel)
        manager.createNotificationChannel(warningChannel)
        manager.createNotificationChannel(recoveryChannel)
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

    private fun activeAlertNotificationId(serverId: String, metricType: AlertMetricType): Int =
        "alert:$serverId:${metricType.name}".hashCode()

    private fun recoveryNotificationId(serverId: String, metricType: AlertMetricType): Int =
        "recovery:$serverId:${metricType.name}".hashCode()

    fun notifyAlert(event: AlertEvent) {
        if (!hasNotificationPermission()) return
        val notifId = activeAlertNotificationId(event.serverId, event.metricType)
        val channelId = if (event.severity == AlertSeverity.CRITICAL) CHANNEL_ALERTS else CHANNEL_ALERTS_WARNING

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            event.serverId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ackIntent = Intent(context, AlertActionReceiver::class.java).apply {
            action = AlertActionReceiver.ACTION_ACKNOWLEDGE_ALERT
            putExtra(AlertActionReceiver.EXTRA_EVENT_ID, event.id)
            putExtra(AlertActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val ackPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefix = if (event.severity == AlertSeverity.CRITICAL) "严重告警" else "性能警告"
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$prefix · ${event.serverName}")
            .setContentText(event.message)
            .setPriority(
                if (event.severity == AlertSeverity.CRITICAL) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setGroup(GROUP_KEY_ALERTS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "标记已读", ackPendingIntent)
            .build()

        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("小狗服务器 · 监控告警")
            .setContentText("存在待处理的服务器异常")
            .setGroup(GROUP_KEY_ALERTS)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        postNotification(notifId, notification)
        postNotification(SUMMARY_NOTIFICATION_ID, summary)
    }

    fun cancelActiveAlert(serverId: String, metricType: AlertMetricType) {
        try {
            NotificationManagerCompat.from(context).cancel(activeAlertNotificationId(serverId, metricType))
        } catch (_: SecurityException) {
            // Ignored
        }
    }

    fun notifyResolved(
        serverId: String,
        serverName: String,
        metricType: AlertMetricType,
        message: String,
        sendRecoveryBanner: Boolean
    ) {
        cancelActiveAlert(serverId, metricType)
        if (!sendRecoveryBanner || !hasNotificationPermission()) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            recoveryNotificationId(serverId, metricType),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT_RECOVERY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("已恢复 · $serverName")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(GROUP_KEY_ALERTS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        postNotification(recoveryNotificationId(serverId, metricType), notification)
    }

    private fun postNotification(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
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
