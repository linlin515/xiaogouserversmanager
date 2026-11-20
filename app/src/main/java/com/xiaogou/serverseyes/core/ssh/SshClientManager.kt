package com.xiaogou.serverseyes.core.ssh

import android.util.Base64
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.xiaogou.serverseyes.core.model.CpuJiffies
import com.xiaogou.serverseyes.core.model.LogTargetType
import com.xiaogou.serverseyes.core.model.NetBytes
import com.xiaogou.serverseyes.core.model.ServerConfig
import com.xiaogou.serverseyes.core.model.ServerSnapshot
import com.xiaogou.serverseyes.core.model.SshAuthType
import com.xiaogou.serverseyes.core.model.WorkloadAction
import com.xiaogou.serverseyes.core.parser.HostKeyMismatchException
import com.xiaogou.serverseyes.core.parser.HostKeyUntrustedException
import com.xiaogou.serverseyes.core.parser.LinuxMetricParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class SshTestResult(
    val hostname: String,
    val kernel: String,
    val fingerprint: String,
    val keyType: String,
    val latencyMs: Long
)

class SshClientManager {

    private val sessionPool = ConcurrentHashMap<String, Session>()
    private val serverLocks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(serverId: String): Mutex =
        serverLocks.getOrPut(serverId) { Mutex() }

    suspend fun testConnection(
        config: ServerConfig,
        trustUnknownHostKey: Boolean = false
    ): Result<SshTestResult> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.currentTimeMillis()
            val (session, fingerprint, keyType) = openSession(config, trustUnknownHostKey)
            try {
                val output = execCommand(session, "hostname; uname -sr", timeoutMs = 10_000)
                val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                SshTestResult(
                    hostname = lines.getOrNull(0).orEmpty().ifEmpty { config.host },
                    kernel = lines.getOrNull(1).orEmpty(),
                    fingerprint = fingerprint,
                    keyType = keyType,
                    latencyMs = latency
                )
            } finally {
                session.disconnect()
            }
        }
    }

    suspend fun collectSnapshot(
        config: ServerConfig,
        prevCpu: CpuJiffies?,
        prevNet: NetBytes?
    ): Result<ServerSnapshot> = withContext(Dispatchers.IO) {
        lockFor(config.id).withLock {
            runCatching {
                val start = System.currentTimeMillis()
                val session = getOrCreatePooledSession(config)
                var effectivePrevCpu = prevCpu
                var effectivePrevNet = prevNet

                val rawOutput = try {
                    execCommand(session, LinuxMetricParser.SSH_PROBE_COMMAND, timeoutMs = 15_000)
                } catch (e: Exception) {
                    sessionPool.remove(config.id)?.disconnect()
                    throw e
                }

                val firstPass = LinuxMetricParser.parseSshOutput(
                    serverId = config.id,
                    rawOutput = rawOutput,
                    nowMs = System.currentTimeMillis(),
                    latencyMs = System.currentTimeMillis() - start,
                    prevCpu = effectivePrevCpu,
                    prevNet = effectivePrevNet
                )

                // On first poll without prior CPU/Net sample, grab a quick 350ms delta sample
                if (effectivePrevCpu == null && firstPass.rawCpuJiffies != null) {
                    effectivePrevCpu = firstPass.rawCpuJiffies
                    effectivePrevNet = firstPass.rawNetBytes
                    val deltaRaw = execCommand(
                        session,
                        LinuxMetricParser.SSH_DELTA_SAMPLE_COMMAND,
                        timeoutMs = 10_000
                    )
                    val sections = LinuxMetricParser.splitSections(deltaRaw)
                    val secondNow = System.currentTimeMillis()
                    val secondCpu = LinuxMetricParser.parseProcStat(sections["STAT"].orEmpty())
                    val secondNet = LinuxMetricParser.parseProcNetDev(sections["NET"].orEmpty(), secondNow)
                    val cpuPct = CpuJiffies.computePercent(effectivePrevCpu, secondCpu)
                    val (rxRate, txRate) = NetBytes.computeRates(effectivePrevNet, secondNet)
                    firstPass.copy(
                        timestampMs = secondNow,
                        latencyMs = (secondNow - start).coerceAtLeast(1L),
                        cpuPercent = cpuPct,
                        netRxBytesPerSec = rxRate,
                        netTxBytesPerSec = txRate,
                        rawCpuJiffies = secondCpu ?: firstPass.rawCpuJiffies,
                        rawNetBytes = secondNet
                    )
                } else {
                    firstPass
                }
            }
        }
    }

    suspend fun fetchLogs(
        config: ServerConfig,
        targetType: LogTargetType,
        targetName: String,
        lines: Int = 120
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        lockFor(config.id).withLock {
            runCatching {
                val safeLines = lines.coerceIn(10, 500)
                val quotedTarget = shellQuote(targetName)
                val command = when (targetType) {
                    LogTargetType.DOCKER_CONTAINER ->
                        "docker logs --tail $safeLines $quotedTarget 2>&1"
                    LogTargetType.SYSTEMD_SERVICE ->
                        "journalctl -u $quotedTarget -n $safeLines --no-pager 2>&1"
                    LogTargetType.SYSTEM_JOURNAL ->
                        "journalctl -n $safeLines --no-pager 2>&1 || tail -n $safeLines /var/log/syslog 2>&1"
                }
                val session = getOrCreatePooledSession(config)
                val out = execCommand(session, command, timeoutMs = 15_000)
                out.lines()
            }
        }
    }

    suspend fun restartTarget(
        config: ServerConfig,
        targetType: LogTargetType,
        targetName: String
    ): Result<String> = performWorkloadAction(config, targetType, targetName, WorkloadAction.RESTART)

    suspend fun performWorkloadAction(
        config: ServerConfig,
        targetType: LogTargetType,
        targetName: String,
        action: WorkloadAction
    ): Result<String> = withContext(Dispatchers.IO) {
        lockFor(config.id).withLock {
            runCatching {
                val quotedTarget = shellQuote(targetName)
                val verb = action.apiValue
                val command = when (targetType) {
                    LogTargetType.DOCKER_CONTAINER -> "docker $verb $quotedTarget"
                    LogTargetType.SYSTEMD_SERVICE -> "sudo -n systemctl $verb $quotedTarget || systemctl $verb $quotedTarget"
                    LogTargetType.SYSTEM_JOURNAL -> error("系统日志不支持启停或重启操作")
                }
                val session = getOrCreatePooledSession(config)
                val out = execCommand(session, command, timeoutMs = 20_000)
                out.ifBlank { "已完成${action.label}指令：$targetName" }
            }
        }
    }

    suspend fun runDiagnosticCommand(
        config: ServerConfig,
        command: String
    ): Result<String> = withContext(Dispatchers.IO) {
        lockFor(config.id).withLock {
            runCatching {
                val session = getOrCreatePooledSession(config)
                execCommand(session, command, timeoutMs = 20_000)
            }
        }
    }

    fun invalidateSession(serverId: String) {
        sessionPool.remove(serverId)?.disconnect()
    }

    fun disconnectAll() {
        sessionPool.values.forEach { runCatching { it.disconnect() } }
        sessionPool.clear()
    }

    private fun getOrCreatePooledSession(config: ServerConfig): Session {
        val existing = sessionPool[config.id]
        if (existing != null && existing.isConnected) {
            return existing
        }
        sessionPool.remove(config.id)?.disconnect()
        val (session, _, _) = openSession(config, trustUnknownHostKey = false)
        sessionPool[config.id] = session
        return session
    }

    private fun openSession(
        config: ServerConfig,
        trustUnknownHostKey: Boolean
    ): Triple<Session, String, String> {
        val jsch = JSch()
        if (config.sshAuthType == SshAuthType.PRIVATE_KEY && config.sshPrivateKey.isNotBlank()) {
            val passphraseBytes = config.sshKeyPassphrase.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
            jsch.addIdentity(
                "key_${config.id}",
                config.sshPrivateKey.toByteArray(Charsets.UTF_8),
                null,
                passphraseBytes
            )
        }

        var observedFingerprint = ""
        var observedKeyType = ""
        var hostKeyFailure: Exception? = null

        val repo = object : HostKeyRepository {
            override fun check(host: String?, key: ByteArray?): Int {
                if (key == null) return HostKeyRepository.NOT_INCLUDED
                val hk = HostKey(host ?: config.host, key)
                observedKeyType = hk.type ?: "ssh"
                observedFingerprint = computeSha256Fingerprint(key)
                val expected = config.trustedHostFingerprint?.takeIf { it.isNotBlank() }
                return when {
                    expected == null && trustUnknownHostKey -> HostKeyRepository.OK
                    expected == null -> {
                        hostKeyFailure = HostKeyUntrustedException(observedFingerprint, observedKeyType)
                        HostKeyRepository.NOT_INCLUDED
                    }
                    expected != observedFingerprint -> {
                        hostKeyFailure = HostKeyMismatchException(expected, observedFingerprint)
                        HostKeyRepository.CHANGED
                    }
                    else -> HostKeyRepository.OK
                }
            }

            override fun add(hostkey: HostKey?, ui: UserInfo?) {}
            override fun remove(host: String?, type: String?) {}
            override fun remove(host: String?, type: String?, key: ByteArray?) {}
            override fun getKnownHostsRepositoryID(): String = "memory"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        }
        jsch.hostKeyRepository = repo

        val session = jsch.getSession(config.sshUsername, config.host, config.sshPort)
        if (config.sshAuthType == SshAuthType.PASSWORD) {
            session.setPassword(config.sshPassword)
        }
        session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
        session.serverAliveInterval = 15_000
        session.serverAliveCountMax = 2
        session.timeout = 12_000

        try {
            session.connect(10_000)
        } catch (e: Exception) {
            runCatching { session.disconnect() }
            hostKeyFailure?.let { throw it }
            throw e
        }
        return Triple(session, observedFingerprint, observedKeyType)
    }

    private fun execCommand(session: Session, command: String, timeoutMs: Int): String {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        channel.inputStream = null
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        channel.outputStream = stdout
        channel.setErrStream(stderr)
        val input = channel.inputStream
        channel.connect(timeoutMs)

        val buffer = ByteArray(4096)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            while (input.available() > 0) {
                val read = input.read(buffer, 0, buffer.size)
                if (read < 0) break
                stdout.write(buffer, 0, read)
            }
            if (channel.isClosed) {
                if (input.available() > 0) continue
                break
            }
            if (System.currentTimeMillis() > deadline) {
                channel.disconnect()
                throw java.net.SocketTimeoutException("SSH command timed out")
            }
            Thread.sleep(25)
        }
        channel.disconnect()
        val outStr = stdout.toString(Charsets.UTF_8.name())
        val errStr = stderr.toString(Charsets.UTF_8.name())
        return if (outStr.isNotBlank()) outStr else errStr
    }

    private fun computeSha256Fingerprint(key: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        val encoded = Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
        return "SHA256:$encoded"
    }

    companion object {
        fun shellQuote(input: String): String {
            return "'" + input.replace("'", "'\"'\"'") + "'"
        }
    }
}
