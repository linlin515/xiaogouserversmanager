package com.xiaogou.serverseyes.core.backup

import com.xiaogou.serverseyes.core.model.AlertEvent
import com.xiaogou.serverseyes.core.model.AlertMetricType
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.AlertSeverity
import com.xiaogou.serverseyes.core.model.CollectionMode
import com.xiaogou.serverseyes.core.model.ServerConfig
import com.xiaogou.serverseyes.core.model.SshAuthType
import com.xiaogou.serverseyes.data.local.MetricSampleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigBackupManagerTest {

    private val sampleServer = ServerConfig(
        id = "srv-1",
        name = "prod-api-01",
        groupName = "生产集群",
        host = "10.0.0.10",
        collectionMode = CollectionMode.HYBRID,
        sshPort = 2222,
        sshUsername = "ops",
        sshAuthType = SshAuthType.PASSWORD,
        sshPassword = "super-secret-password",
        trustedHostFingerprint = "SHA256:abc123",
        timeoutSeconds = 20,
        jumpHostEnabled = true,
        jumpHost = "bastion.example.com",
        jumpPort = 22022,
        jumpUsername = "jumpuser",
        jumpAuthType = SshAuthType.PASSWORD,
        jumpPassword = "jump-secret-password",
        agentUrl = "https://10.0.0.10:19090",
        agentToken = "tok-999",
        isPinned = true,
        enabled = true
    )

    private val sampleRule = AlertRule(
        id = "srv-1_cpu",
        serverId = "srv-1",
        metricType = AlertMetricType.CPU,
        warningThreshold = 70.0,
        criticalThreshold = 90.0,
        cooldownMinutes = 15,
        enabled = true
    )

    private val sampleEvent = AlertEvent(
        id = "ev-1",
        serverId = "srv-1",
        serverName = "prod-api-01",
        metricType = AlertMetricType.CPU,
        severity = AlertSeverity.CRITICAL,
        currentValue = 96.0,
        thresholdValue = 90.0,
        message = "CPU 高负载",
        timestampMs = 1_700_000_000_000L,
        acknowledged = true,
        isResolved = true,
        resolvedAtMs = 1_700_000_060_000L
    )

    private val sampleMetric = MetricSampleEntity(
        serverId = "srv-1",
        timestampMs = 1_700_000_000_000L,
        cpuPercent = 42.5,
        memPercent = 61.0,
        diskMaxPercent = 55.0,
        netRxBytesPerSec = 1024L,
        netTxBytesPerSec = 512L,
        load1 = 0.85,
        sourceUsed = "AGENT"
    )

    @Test
    fun `encrypted backup round-trip restores secrets and rejects wrong passphrase`() {
        val exported = ConfigBackupManager.exportJson(
            servers = listOf(sampleServer),
            alertRules = listOf(sampleRule),
            includeSecrets = true,
            passphrase = "my-strong-passphrase"
        )
        assertFalse(exported.contains("super-secret-password"))
        assertFalse(exported.contains("tok-999"))

        val wrongPass = ConfigBackupManager.importJson(exported, "wrong-pass")
        assertTrue(wrongPass.isFailure)

        val preview = ConfigBackupManager.previewJson(exported, "my-strong-passphrase").getOrThrow()
        assertEquals(1, preview.serverCount)
        assertEquals(listOf("prod-api-01"), preview.serverNames)
        assertTrue(preview.encrypted)
        assertTrue(preview.containsSecrets)

        val restored = ConfigBackupManager.importJson(exported, "my-strong-passphrase").getOrThrow()
        assertTrue(restored.containsSecrets)
        assertEquals(1, restored.servers.size)
        assertEquals("super-secret-password", restored.servers[0].sshPassword)
        assertEquals("tok-999", restored.servers[0].agentToken)
        assertTrue(restored.servers[0].jumpHostEnabled)
        assertEquals("bastion.example.com", restored.servers[0].jumpHost)
        assertEquals(22022, restored.servers[0].jumpPort)
        assertEquals("jump-secret-password", restored.servers[0].jumpPassword)
        assertEquals(20, restored.servers[0].timeoutSeconds)
        assertTrue(restored.servers[0].isPinned)
        assertEquals(1, restored.alertRules.size)
        assertEquals(70.0, restored.alertRules[0].warningThreshold, 0.01)
    }

    @Test
    fun `desensitized plaintext export strips secrets`() {
        val exported = ConfigBackupManager.exportJson(
            servers = listOf(sampleServer),
            alertRules = listOf(sampleRule),
            includeSecrets = false,
            passphrase = null
        )
        assertFalse(exported.contains("super-secret-password"))
        assertFalse(exported.contains("tok-999"))

        val imported = ConfigBackupManager.importJson(exported, null).getOrThrow()
        assertFalse(imported.containsSecrets)
        assertEquals("prod-api-01", imported.servers[0].name)
        assertEquals("", imported.servers[0].sshPassword)
        assertEquals("", imported.servers[0].agentToken)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `exporting secrets without passphrase is rejected`() {
        ConfigBackupManager.exportJson(
            servers = listOf(sampleServer),
            alertRules = listOf(sampleRule),
            includeSecrets = true,
            passphrase = null
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `exporting secrets with short passphrase is rejected`() {
        ConfigBackupManager.exportJson(
            servers = listOf(sampleServer),
            alertRules = listOf(sampleRule),
            includeSecrets = true,
            passphrase = "12345"
        )
    }

    @Test
    fun `v2 backup preserves alert events and metric samples`() {
        val exported = ConfigBackupManager.exportJson(
            servers = listOf(sampleServer),
            alertRules = listOf(sampleRule),
            includeSecrets = true,
            passphrase = "strong-password-1",
            alertEvents = listOf(sampleEvent),
            metricSamples = listOf(sampleMetric)
        )
        val preview = ConfigBackupManager.previewJson(exported, "strong-password-1").getOrThrow()
        assertEquals(1, preview.eventCount)
        assertEquals(1, preview.sampleCount)

        val restored = ConfigBackupManager.importJson(exported, "strong-password-1").getOrThrow()
        assertEquals(1, restored.alertEvents.size)
        assertTrue(restored.alertEvents[0].isResolved)
        assertEquals(1, restored.metricSamples.size)
        assertEquals(42.5, restored.metricSamples[0].cpuPercent, 0.01)
    }
}
