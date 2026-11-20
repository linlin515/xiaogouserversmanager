package com.xiaogou.serverseyes.data.alert

import com.xiaogou.serverseyes.core.model.AlertMetricType
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.AlertSeverity
import com.xiaogou.serverseyes.core.model.DiskPartition
import com.xiaogou.serverseyes.core.model.MetricSource
import com.xiaogou.serverseyes.core.model.ServerSnapshot
import com.xiaogou.serverseyes.core.model.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEvaluatorTest {

    private val defaultRules = listOf(
        AlertRule("g_cpu", null, AlertMetricType.CPU, 80.0, 95.0, cooldownMinutes = 10, enabled = true),
        AlertRule("g_mem", null, AlertMetricType.MEMORY, 85.0, 95.0, cooldownMinutes = 10, enabled = true),
        AlertRule("g_disk", null, AlertMetricType.DISK, 85.0, 95.0, cooldownMinutes = 30, enabled = true),
        AlertRule("g_off", null, AlertMetricType.OFFLINE, 1.0, 2.0, cooldownMinutes = 5, enabled = true)
    )

    @Test
    fun `effectiveRulesForServer applies per-server overrides`() {
        val rules = defaultRules + AlertRule(
            id = "s1_cpu",
            serverId = "s1",
            metricType = AlertMetricType.CPU,
            warningThreshold = 60.0,
            criticalThreshold = 90.0,
            cooldownMinutes = 5,
            enabled = true
        )
        val effective = AlertEvaluator.effectiveRulesForServer("s1", rules)
        assertEquals(60.0, effective[AlertMetricType.CPU]!!.warningThreshold, 0.01)
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
}
