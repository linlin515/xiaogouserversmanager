package com.xiaogou.serverseyes.core.util

import java.security.SecureRandom

object AgentDeployHelper {
    private const val TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun generateSecureToken(length: Int = 24): String {
        val rng = SecureRandom()
        return buildString(length) {
            repeat(length) {
                append(TOKEN_ALPHABET[rng.nextInt(TOKEN_ALPHABET.length)])
            }
        }
    }

    fun buildQuickRunCommand(port: Int = 19090, token: String, enableActions: Boolean = true): String {
        val actionFlag = if (enableActions) " --enable-actions" else ""
        return "python3 server_eyes_agent.py --port $port --token ${token.ifBlank { "<YOUR_TOKEN>" }}$actionFlag"
    }

    fun buildSystemdInstallCommand(port: Int = 19090, token: String): String {
        return "sudo bash install.sh $port ${token.ifBlank { "<YOUR_TOKEN>" }}"
    }
}
