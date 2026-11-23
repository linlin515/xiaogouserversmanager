package com.xiaogou.serverseyes.data.alert

import com.xiaogou.serverseyes.core.model.AlertEvent
import com.xiaogou.serverseyes.core.model.AlertMetricType
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.AlertSeverity
import com.xiaogou.serverseyes.core.model.ServerSnapshot
import com.xiaogou.serverseyes.core.model.ServerStatus
import com.xiaogou.serverseyes.data.local.AppSettings
import java.util.Calendar
import java.util.UUID
import kotlin.math.roundToInt

object DndSchedule {
    fun isInDndWindow(startMinute: Int, endMinute: Int, minuteOfDay: Int): Boolean {
        val start = startMinute.coerceIn(0, 1439)
        val end = endMinute.coerceIn(0, 1439)
        val now = minuteOfDay.coerceIn(0, 1439)
        return when {
            start == end -> true // Full-day DND if same minute and enabled
            start < end -> now in start until end
            else -> now >= start || now < end // Overnight window e.g. 23:00..08:00
        }
    }

    fun currentMinuteOfDay(nowMs: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun shouldSendAlertNotification(
        settings: AppSettings,
        severity: AlertSeverity,
        minuteOfDay: Int = currentMinuteOfDay()
    ): Boolean {
        if (!settings.notificationsEnabled) return false
        if (!settings.dndEnabled) return true
        if (!isInDndWindow(settings.dndStartMinute, settings.dndEndMinute, minuteOfDay)) return true
        return severity == AlertSeverity.CRITICAL && settings.dndAllowCritical
    }

    fun shouldSendRecoveryNotification(
        settings: AppSettings,
        minuteOfDay: Int = currentMinuteOfDay()
    ): Boolean {
        if (!settings.notificationsEnabled || !settings.notifyOnRecovery) return false
        if (!settings.dndEnabled) return true
        return !isInDndWindow(settings.dndStartMinute, settings.dndEndMinute, minuteOfDay)
    }
}

data class RecoveryNotice(
    val serverId: String,
    val serverName: String,
    val metricType: AlertMetricType,
    val message: String
)

object AlertEvaluator {

    fun effectiveRulesForServer(serverId: String, allRules: List<AlertRule>): Map<AlertMetricType, AlertRule> {
        val result = linkedMapOf<AlertMetricType, AlertRule>()
        allRules.filter { it.serverId == null && it.enabled }.forEach { result[it.metricType] = it }
        allRules.filter { it.serverId == serverId }.forEach { rule ->
            if (rule.enabled) {
                result[rule.metricType] = rule
            } else {
                result.remove(rule.metricType)
            }
        }
        return result
    }

    fun computeServerStatus(
        snapshot: ServerSnapshot?,
        consecutiveFailures: Int,
        rules: Map<AlertMetricType, AlertRule>
    ): ServerStatus {
        if (snapshot == null) {
            return if (consecutiveFailures > 0) ServerStatus.OFFLINE else ServerStatus.UNKNOWN
        }
        if (consecutiveFailures > 0) {
            val offlineGrace = (rules[AlertMetricType.OFFLINE]?.warningThreshold?.toInt() ?: 2).coerceAtLeast(1)
            return if (consecutiveFailures >= offlineGrace) ServerStatus.OFFLINE else ServerStatus.WARNING
        }
        var worst = ServerStatus.ONLINE

        fun check(type: AlertMetricType, value: Double) {
            val rule = rules[type] ?: return
            when {
                value >= rule.criticalThreshold -> worst = ServerStatus.CRITICAL
                value >= rule.warningThreshold && worst != ServerStatus.CRITICAL -> worst = ServerStatus.WARNING
            }
        }

        check(AlertMetricType.CPU, snapshot.cpuPercent)
        check(AlertMetricType.MEMORY, snapshot.memPercent)
        check(AlertMetricType.DISK, snapshot.maxDiskPercent)

        // Also elevate to WARNING if any running server has failed systemd services
        if (worst == ServerStatus.ONLINE && snapshot.services.any { it.activeState.equals("failed", ignoreCase = true) }) {
            worst = ServerStatus.WARNING
        }
        return worst
    }

    fun evaluateSnapshotAlerts(
        serverId: String,
        serverName: String,
        snapshot: ServerSnapshot,
        rules: Map<AlertMetricType, AlertRule>,
        lastAlertTimestampByMetric: Map<AlertMetricType, Long>,
        nowMs: Long
    ): List<AlertEvent> {
        val events = mutableListOf<AlertEvent>()

        fun checkMetric(type: AlertMetricType, value: Double, label: String) {
            val rule = rules[type] ?: return
            val severity: AlertSeverity
            val threshold: Double
            when {
                value >= rule.criticalThreshold -> {
                    severity = AlertSeverity.CRITICAL
                    threshold = rule.criticalThreshold
                }
                value >= rule.warningThreshold -> {
                    severity = AlertSeverity.WARNING
                    threshold = rule.warningThreshold
                }
                else -> return
            }
            val lastFired = lastAlertTimestampByMetric[type] ?: 0L
            val cooldownMs = rule.cooldownMinutes.coerceAtLeast(1) * 60_000L
            if (nowMs - lastFired < cooldownMs) return

            val valInt = value.roundToInt()
            val thInt = threshold.roundToInt()
            events += AlertEvent(
                id = UUID.randomUUID().toString(),
                serverId = serverId,
                serverName = serverName,
                metricType = type,
                severity = severity,
                currentValue = value,
                thresholdValue = threshold,
                message = "$serverName ${label}达到 $valInt%（阈值 $thInt%）",
                timestampMs = nowMs,
                acknowledged = false
            )
        }

        checkMetric(AlertMetricType.CPU, snapshot.cpuPercent, "CPU 使用率")
        checkMetric(AlertMetricType.MEMORY, snapshot.memPercent, "内存使用率")
        checkMetric(AlertMetricType.DISK, snapshot.maxDiskPercent, "磁盘使用率")
        return events
    }

    fun evaluateOfflineAlert(
        serverId: String,
        serverName: String,
        consecutiveFailures: Int,
        rules: Map<AlertMetricType, AlertRule>,
        lastOfflineAlertMs: Long,
        nowMs: Long
    ): AlertEvent? {
        val rule = rules[AlertMetricType.OFFLINE] ?: return null
        val severity = when {
            consecutiveFailures >= rule.criticalThreshold.toInt().coerceAtLeast(1) -> AlertSeverity.CRITICAL
            consecutiveFailures >= rule.warningThreshold.toInt().coerceAtLeast(1) -> AlertSeverity.WARNING
            else -> return null
        }
        val cooldownMs = rule.cooldownMinutes.coerceAtLeast(1) * 60_000L
        if (nowMs - lastOfflineAlertMs < cooldownMs) return null

        return AlertEvent(
            id = UUID.randomUUID().toString(),
            serverId = serverId,
            serverName = serverName,
            metricType = AlertMetricType.OFFLINE,
            severity = severity,
            currentValue = consecutiveFailures.toDouble(),
            thresholdValue = rule.warningThreshold,
            message = "$serverName 连续 $consecutiveFailures 次采集失败，节点可能离线",
            timestampMs = nowMs,
            acknowledged = false
        )
    }

    fun evaluateRecoveries(
        serverId: String,
        serverName: String,
        snapshot: ServerSnapshot,
        rules: Map<AlertMetricType, AlertRule>,
        activeUnresolvedMetrics: Set<AlertMetricType>,
        hysteresis: Double = 2.0
    ): List<RecoveryNotice> {
        if (activeUnresolvedMetrics.isEmpty()) return emptyList()
        val notices = mutableListOf<RecoveryNotice>()

        if (AlertMetricType.OFFLINE in activeUnresolvedMetrics) {
            notices += RecoveryNotice(
                serverId = serverId,
                serverName = serverName,
                metricType = AlertMetricType.OFFLINE,
                message = "$serverName 已恢复在线连接（延迟 ${snapshot.latencyMs}ms）"
            )
        }

        fun checkMetricRecovery(type: AlertMetricType, value: Double, label: String) {
            if (type !in activeUnresolvedMetrics) return
            val rule = rules[type]
            val recoverLine = if (rule != null) {
                (rule.warningThreshold - hysteresis).coerceAtLeast(0.0)
            } else {
                Double.MAX_VALUE
            }
            if (value < recoverLine) {
                notices += RecoveryNotice(
                    serverId = serverId,
                    serverName = serverName,
                    metricType = type,
                    message = "$serverName ${label}已回落至 ${value.roundToInt()}%，恢复正常"
                )
            }
        }

        checkMetricRecovery(AlertMetricType.CPU, snapshot.cpuPercent, "CPU 使用率")
        checkMetricRecovery(AlertMetricType.MEMORY, snapshot.memPercent, "内存使用率")
        checkMetricRecovery(AlertMetricType.DISK, snapshot.maxDiskPercent, "磁盘使用率")
        return notices
    }
}
