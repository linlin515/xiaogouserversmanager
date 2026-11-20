package com.xiaogou.serverseyes.core.backup

import com.xiaogou.serverseyes.core.model.AlertMetricType
import com.xiaogou.serverseyes.core.model.AlertRule
import com.xiaogou.serverseyes.core.model.CollectionMode
import com.xiaogou.serverseyes.core.model.ServerConfig
import com.xiaogou.serverseyes.core.model.SshAuthType
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

        val restored = ConfigBackupManager.importJson(exported, "my-strong-passphrase").getOrThrow()
        assertTrue(restored.containsSecrets)
        assertEquals(1, restored.servers.size)
        assertEquals("super-secret-password", restored.servers[0].sshPassword)
        assertEquals("tok-999", restored.servers[0].agentToken)
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
}
