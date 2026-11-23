package com.xiaogou.serverseyes.data.alert

import com.xiaogou.serverseyes.core.model.AlertMetricType
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.AlertSeverity
import com.xiaogou.serverseyes.core.model.DiskPartition
import com.xiaogou.serverseyes.core.model.MetricSource
import com.xiaogou.serverseyes.core.model.ServerSnapshot
import com.xiaogou.serverseyes.core.model.ServerStatus
import com.xiaogou.serverseyes.data.local.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEvaluatorTest {

    private val defaultRules = listOf(
        AlertRule("g_cpu", null, AlertMetricType.CPU, 80.0, 95.0, cooldownMinutes = 10, enabled = true),
        AlertRule("g_mem", null, AlertMetricType.MEMORY, 85.0, 95.0, cooldownMinutes = 10, enabled = true),
        AlertRule("g_disk", null, AlertMetricType.DISK, 85.0, 95.0, cooldownMinutes = 30, enabled = true),
        AlertRule("g_off", null, AlertMetricType.OFFLINE, 2.0, 3.0, cooldownMinutes = 5, enabled = true)
    )

    @Test
    fun `effectiveRulesForServer applies per-server overrides and disabled deletions`() {
        val rules = defaultRules +
            AlertRule(
                id = "s1_cpu",
                serverId = "s1",
                metricType = AlertMetricType.CPU,
                warningThreshold = 60.0,
                criticalThreshold = 90.0,
                cooldownMinutes = 5,
                enabled = true
            ) +
            AlertRule(
                id = "s1_disk_off",
                serverId = "s1",
                metricType = AlertMetricType.DISK,
                warningThreshold = 85.0,
                criticalThreshold = 95.0,
                cooldownMinutes = 30,
                enabled = false
            )
        val effective = AlertEvaluator.effectiveRulesForServer("s1", rules)
        assertEquals(60.0, effective[AlertMetricType.CPU]!!.warningThreshold, 0.01)
        assertNull(effective[AlertMetricType.DISK])
    }

    @Test
    fun `computeServerStatus applies offline grace for existing snapshot`() {
        val effective = AlertEvaluator.effectiveRulesForServer("s1", defaultRules)
        val snap = ServerSnapshot(
            serverId = "s1",
            timestampMs = 1_000_000L,
            sourceUsed = MetricSource.AGENT,
            latencyMs = 12L,
            cpuPercent = 20.0,
            memTotalBytes = 1000L,
            memUsedBytes = 400L
        )
        // First transient failure -> WARNING (grace = 2), second failure -> OFFLINE
        assertEquals(ServerStatus.WARNING, AlertEvaluator.computeServerStatus(snap, 1, effective))
        assertEquals(ServerStatus.OFFLINE, AlertEvaluator.computeServerStatus(snap, 2, effective))
        // Without any prior snapshot, 1 failure immediately reports OFFLINE
        assertEquals(ServerStatus.OFFLINE, AlertEvaluator.computeServerStatus(null, 1, effective))
    }

    @Test
    fun `computeServerStatus and evaluateSnapshotAlerts respect thresholds and cooldown`() {
        val effective = AlertEvaluator.effectiveRulesForServer("s1", defaultRules)
        val snap = ServerSnapshot(
            serverId = "s1",
            timestampMs = 1_000_000L,
            sourceUsed = MetricSource.AGENT,
            latencyMs = 12L,
            cpuPercent = 96.2,
            memTotalBytes = 1000L,
            memUsedBytes = 860L,
            disks = listOf(DiskPartition("/dev/vda1", "/", 100, 50, 50, 50.0))
        )

        val status = AlertEvaluator.computeServerStatus(snap, consecutiveFailures = 0, rules = effective)
        assertEquals(ServerStatus.CRITICAL, status)

        val firstAlerts = AlertEvaluator.evaluateSnapshotAlerts(
            serverId = "s1",
            serverName = "Web-01",
            snapshot = snap,
            rules = effective,
            lastAlertTimestampByMetric = emptyMap(),
            nowMs = 1_000_000L
        )
        assertEquals(2, firstAlerts.size)
        assertEquals(AlertSeverity.CRITICAL, firstAlerts.first { it.metricType == AlertMetricType.CPU }.severity)
        assertEquals(AlertSeverity.WARNING, firstAlerts.first { it.metricType == AlertMetricType.MEMORY }.severity)

        // Within cooldown (2 minutes later): no duplicate alerts
        val cooledAlerts = AlertEvaluator.evaluateSnapshotAlerts(
            serverId = "s1",
            serverName = "Web-01",
            snapshot = snap,
            rules = effective,
            lastAlertTimestampByMetric = mapOf(
                AlertMetricType.CPU to 1_000_000L,
                AlertMetricType.MEMORY to 1_000_000L
            ),
            nowMs = 1_120_000L
        )
        assertTrue(cooledAlerts.isEmpty())
    }

    @Test
    fun `evaluateRecoveries detects offline recovery and hysteresis drop`() {
        val effective = AlertEvaluator.effectiveRulesForServer("s1", defaultRules)
        // CPU is at 79.0 (within 2% hysteresis of 80%), should NOT recover yet; OFFLINE & MEMORY (50%) SHOULD recover
        val snap = ServerSnapshot(
            serverId = "s1",
            timestampMs = 2_000_000L,
            sourceUsed = MetricSource.AGENT,
            latencyMs = 15L,
            cpuPercent = 79.0,
            memTotalBytes = 1000L,
            memUsedBytes = 500L
        )
        val rec = AlertEvaluator.evaluateRecoveries(
            serverId = "s1",
            serverName = "Web-01",
            snapshot = snap,
            rules = effective,
            activeUnresolvedMetrics = setOf(
                AlertMetricType.OFFLINE,
                AlertMetricType.CPU,
                AlertMetricType.MEMORY
            )
        )
        val recoveredTypes = rec.map { it.metricType }.toSet()
        assertEquals(setOf(AlertMetricType.OFFLINE, AlertMetricType.MEMORY), recoveredTypes)
    }

    @Test
    fun `dnd schedule handles overnight and daytime windows with critical bypass`() {
        // Overnight 23:00 (1380) .. 08:00 (480)
        assertTrue(DndSchedule.isInDndWindow(23 * 60, 8 * 60, 23 * 60 + 30))
        assertTrue(DndSchedule.isInDndWindow(23 * 60, 8 * 60, 3 * 60))
        assertFalse(DndSchedule.isInDndWindow(23 * 60, 8 * 60, 12 * 60))

        // Daytime 12:00 (720) .. 14:00 (840)
        assertTrue(DndSchedule.isInDndWindow(12 * 60, 14 * 60, 13 * 60))
        assertFalse(DndSchedule.isInDndWindow(12 * 60, 14 * 60, 15 * 60))

        val settings = AppSettings(
            notificationsEnabled = true,
            notifyOnRecovery = true,
            dndEnabled = true,
            dndStartMinute = 23 * 60,
            dndEndMinute = 8 * 60,
            dndAllowCritical = true
        )
        assertFalse(DndSchedule.shouldSendAlertNotification(settings, AlertSeverity.WARNING, minuteOfDay = 2 * 60))
        assertTrue(DndSchedule.shouldSendAlertNotification(settings, AlertSeverity.CRITICAL, minuteOfDay = 2 * 60))
        assertFalse(DndSchedule.shouldSendRecoveryNotification(settings, minuteOfDay = 2 * 60))
        assertTrue(DndSchedule.shouldSendRecoveryNotification(settings, minuteOfDay = 10 * 60))
    }
}
