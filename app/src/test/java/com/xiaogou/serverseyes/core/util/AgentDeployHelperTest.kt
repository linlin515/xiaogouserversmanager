package com.xiaogou.serverseyes.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentDeployHelperTest {

    @Test
    fun `generateSecureToken produces expected length alphanumeric string`() {
        val t = AgentDeployHelper.generateSecureToken(24)
        assertEquals(24, t.length)
        assertTrue(t.all { it.isLetterOrDigit() })
    }

    @Test
    fun `buildQuickRunCommand and buildSystemdInstallCommand format flags properly`() {
        val cmd = AgentDeployHelper.buildQuickRunCommand(19090, "abc123", enableActions = true)
        assertTrue(cmd.contains("--port 19090"))
        assertTrue(cmd.contains("--token abc123"))
        assertTrue(cmd.contains("--enable-actions"))

        val systemdCmd = AgentDeployHelper.buildSystemdInstallCommand(19091, "tok")
        assertEquals("sudo bash install.sh 19091 tok", systemdCmd)
    }
}
