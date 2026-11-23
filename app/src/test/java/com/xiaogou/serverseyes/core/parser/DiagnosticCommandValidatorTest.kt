package com.xiaogou.serverseyes.core.parser

import com.xiaogou.serverseyes.core.model.DiagnosticPreset
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticCommandValidatorTest {

    @Test
    fun `all default diagnostic presets pass validation`() {
        DiagnosticPreset.DEFAULT_PRESETS.forEach { preset ->
            val res = DiagnosticCommandValidator.validate(preset.command)
            assertTrue("Preset ${preset.id} failed: ${res.exceptionOrNull()?.message}", res.isSuccess)
        }
    }

    @Test
    fun `safe readonly pipes pass validation`() {
        assertTrue(DiagnosticCommandValidator.validate("docker ps --format '{{.ID}}|{{.Names}}'").isSuccess)
        assertTrue(DiagnosticCommandValidator.validate("systemctl status nginx --no-pager").isSuccess)
        assertTrue(DiagnosticCommandValidator.validate("df -h | grep -v tmpfs").isSuccess)
        assertTrue(DiagnosticCommandValidator.validate("sensors && nvidia-smi").isSuccess)
    }

    @Test
    fun `destructive and injection commands are rejected`() {
        val dangerous = listOf(
            "rm -rf /",
            "reboot",
            "systemctl stop nginx",
            "docker rm -f api",
            "sed -i 's/a/b/' /etc/hosts",
            "echo test > /etc/passwd",
            "echo \$(whoami)",
            "echo `id`",
            "cat /etc/shadow",
            "/bin/sh -c id",
            "sleep 10 &"
        )
        dangerous.forEach { cmd ->
            val res = DiagnosticCommandValidator.validate(cmd)
            assertTrue("Command should have been rejected: $cmd", res.isFailure)
        }
    }
}
