package com.xiaogou.serverseyes.core.model

import kotlinx.serialization.Serializable

enum class CollectionMode {
    SSH_ONLY,
    AGENT_ONLY,
    HYBRID
}

enum class SshAuthType {
    PASSWORD,
    PRIVATE_KEY
}

enum class ServerStatus {
    ONLINE,
    WARNING,
    CRITICAL,
    OFFLINE,
    CONNECTING,
    UNKNOWN
}

enum class MetricSource {
    SSH,
    AGENT
}

enum class LogTargetType {
    DOCKER_CONTAINER,
    SYSTEMD_SERVICE,
    SYSTEM_JOURNAL
}

enum class WorkloadAction(val apiValue: String, val label: String) {
    START("start", "启动"),
    STOP("stop", "停止"),
    RESTART("restart", "重启")
}

enum class AlertMetricType {
    CPU,
    MEMORY,
    DISK,
    OFFLINE
}

enum class AlertSeverity {
    WARNING,
    CRITICAL
}

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK
}

enum class ServerSortOption(val label: String) {
    DEFAULT("默认（置顶+异常）"),
    CPU_DESC("CPU 使用率最高"),
    MEM_DESC("内存使用率最高"),
    NET_TX_DESC("网络下行最高"),
    LATENCY_ASC("响应最快"),
    NAME_ASC("名称字母序")
}

data class ServerConfig(
    val id: String,
    val name: String,
    val groupName: String = "默认分组",
    val host: String,
    val collectionMode: CollectionMode = CollectionMode.HYBRID,
    val sshPort: Int = 22,
    val sshUsername: String = "root",
    val sshAuthType: SshAuthType = SshAuthType.PASSWORD,
    val sshPassword: String = "",
    val sshPrivateKey: String = "",
    val sshKeyPassphrase: String = "",
    val trustedHostFingerprint: String? = null,
    val timeoutSeconds: Int = 10,
    val jumpHostEnabled: Boolean = false,
    val jumpHost: String = "",
    val jumpPort: Int = 22,
    val jumpUsername: String = "root",
    val jumpAuthType: SshAuthType = SshAuthType.PASSWORD,
    val jumpPassword: String = "",
    val jumpPrivateKey: String = "",
    val jumpKeyPassphrase: String = "",
    val agentUrl: String = "",
    val agentToken: String = "",
    val sortOrder: Int = 0,
    val isPinned: Boolean = false,
    val enabled: Boolean = true
)

data class CpuJiffies(
    val user: Long,
    val nice: Long,
    val system: Long,
    val idle: Long,
    val iowait: Long,
    val irq: Long,
    val softirq: Long,
    val steal: Long
) {
    val total: Long get() = user + nice + system + idle + iowait + irq + softirq + steal
    val idleAll: Long get() = idle + iowait

    companion object {
        fun computePercent(prev: CpuJiffies?, curr: CpuJiffies?): Double {
            if (prev == null || curr == null) return 0.0
            val totalDelta = curr.total - prev.total
            val idleDelta = curr.idleAll - prev.idleAll
            if (totalDelta <= 0L) return 0.0
            val busy = (totalDelta - idleDelta).coerceAtLeast(0L)
            return ((busy.toDouble() / totalDelta.toDouble()) * 100.0).coerceIn(0.0, 100.0)
        }
    }
}

data class NetBytes(
    val timestampMs: Long,
    val rxBytes: Long,
    val txBytes: Long
) {
    companion object {
        fun computeRates(prev: NetBytes?, curr: NetBytes?): Pair<Long, Long> {
            if (prev == null || curr == null) return 0L to 0L
            val elapsedMs = (curr.timestampMs - prev.timestampMs).coerceAtLeast(1L)
            val rxDelta = (curr.rxBytes - prev.rxBytes).coerceAtLeast(0L)
            val txDelta = (curr.txBytes - prev.txBytes).coerceAtLeast(0L)
            val rxPerSec = (rxDelta * 1000.0 / elapsedMs).toLong()
            val txPerSec = (txDelta * 1000.0 / elapsedMs).toLong()
            return rxPerSec to txPerSec
        }
    }
}

data class DiskIoSectors(
    val timestampMs: Long,
    val readSectors: Long,
    val writeSectors: Long
) {
    companion object {
        private const val SECTOR_BYTES = 512L

        fun computeRates(prev: DiskIoSectors?, curr: DiskIoSectors?): Pair<Long, Long> {
            if (prev == null || curr == null) return 0L to 0L
            val elapsedMs = (curr.timestampMs - prev.timestampMs).coerceAtLeast(1L)
            val readDelta = (curr.readSectors - prev.readSectors).coerceAtLeast(0L)
            val writeDelta = (curr.writeSectors - prev.writeSectors).coerceAtLeast(0L)
            val readPerSec = (readDelta * SECTOR_BYTES * 1000.0 / elapsedMs).toLong()
            val writePerSec = (writeDelta * SECTOR_BYTES * 1000.0 / elapsedMs).toLong()
            return readPerSec to writePerSec
        }
    }
}

@Serializable
data class DiskPartition(
    val filesystem: String,
    val mountPoint: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val availBytes: Long,
    val usagePercent: Double,
    val inodeUsedPercent: Double? = null
)

@Serializable
data class ProcessSummary(
    val pid: Int,
    val name: String,
    val cpuPercent: Double,
    val memPercent: Double,
    val user: String = "",
    val rssBytes: Long = 0L,
    val state: String = ""
)

@Serializable
data class ContainerInfo(
    val id: String,
    val name: String,
    val image: String,
    val state: String,
    val statusText: String,
    val ports: String = "",
    val cpuPercent: Double? = null,
    val memUsageText: String = "",
    val memPercent: Double? = null
) {
    val isRunning: Boolean
        get() = state.equals("running", ignoreCase = true) ||
            statusText.startsWith("Up", ignoreCase = true)
}

@Serializable
data class SystemServiceInfo(
    val unit: String,
    val loadState: String,
    val activeState: String,
    val subState: String,
    val description: String = ""
) {
    val isHealthy: Boolean
        get() = activeState.equals("active", ignoreCase = true)
}

@Serializable
data class ListeningPort(
    val protocol: String,
    val localAddress: String,
    val port: Int,
    val processName: String = ""
)

data class ServerSnapshot(
    val serverId: String,
    val timestampMs: Long,
    val sourceUsed: MetricSource,
    val latencyMs: Long,
    val hostname: String = "",
    val osName: String = "",
    val kernel: String = "",
    val uptimeSeconds: Long = 0L,
    val cpuCores: Int = 1,
    val cpuPercent: Double = 0.0,
    val load1: Double = 0.0,
    val load5: Double = 0.0,
    val load15: Double = 0.0,
    val memTotalBytes: Long = 0L,
    val memUsedBytes: Long = 0L,
    val swapTotalBytes: Long = 0L,
    val swapUsedBytes: Long = 0L,
    val disks: List<DiskPartition> = emptyList(),
    val netRxBytesPerSec: Long = 0L,
    val netTxBytesPerSec: Long = 0L,
    val diskReadBytesPerSec: Long = 0L,
    val diskWriteBytesPerSec: Long = 0L,
    val cpuTempCelsius: Double? = null,
    val topProcesses: List<ProcessSummary> = emptyList(),
    val containers: List<ContainerInfo> = emptyList(),
    val services: List<SystemServiceInfo> = emptyList(),
    val ports: List<ListeningPort> = emptyList(),
    val rawCpuJiffies: CpuJiffies? = null,
    val rawNetBytes: NetBytes? = null,
    val rawDiskSectors: DiskIoSectors? = null
) {
    val memPercent: Double
        get() = if (memTotalBytes > 0L) {
            ((memUsedBytes.toDouble() / memTotalBytes.toDouble()) * 100.0).coerceIn(0.0, 100.0)
        } else 0.0

    val maxDiskPercent: Double
        get() = disks.maxOfOrNull { it.usagePercent } ?: 0.0
}

@Serializable
data class MetricPoint(
    val timestampMs: Long,
    val cpuPercent: Double,
    val memPercent: Double,
    val netRxBytesPerSec: Long,
    val netTxBytesPerSec: Long,
    val load1: Double
)

data class AlertRule(
    val id: String,
    val serverId: String?, // null = global default rule
    val metricType: AlertMetricType,
    val warningThreshold: Double,
    val criticalThreshold: Double,
    val cooldownMinutes: Int = 10,
    val enabled: Boolean = true
)

data class AlertEvent(
    val id: String,
    val serverId: String,
    val serverName: String,
    val metricType: AlertMetricType,
    val severity: AlertSeverity,
    val currentValue: Double,
    val thresholdValue: Double,
    val message: String,
    val timestampMs: Long,
    val acknowledged: Boolean = false,
    val isResolved: Boolean = false,
    val resolvedAtMs: Long? = null
)

data class DiagnosticPreset(
    val id: String,
    val title: String,
    val description: String,
    val command: String
) {
    companion object {
        val DEFAULT_PRESETS = listOf(
            DiagnosticPreset(
                id = "sys_info",
                title = "系统与内核概览",
                description = "查看内核版本、CPU 架构与核心数",
                command = "uname -a && nproc"
            ),
            DiagnosticPreset(
                id = "mem_detail",
                title = "内存与交换区详情",
                description = "以人类可读格式查看内存使用情况",
                command = "free -h"
            ),
            DiagnosticPreset(
                id = "disk_inode",
                title = "磁盘空间与 Inode",
                description = "检查各挂载点容量及 inode 占用",
                command = "df -hT && df -i"
            ),
            DiagnosticPreset(
                id = "top_io",
                title = "高负载进程排行",
                description = "查看 CPU 与内存占用最高的 15 个进程",
                command = "ps -eo pid,user,%cpu,%mem,stat,comm --sort=-%cpu | head -n 16"
            ),
            DiagnosticPreset(
                id = "journal_err",
                title = "最近系统错误日志",
                description = "读取 systemd 最近 30 条警告/错误日志",
                command = "journalctl -p warning -n 30 --no-pager 2>/dev/null || tail -n 30 /var/log/syslog 2>/dev/null || echo '无可用系统日志'"
            ),
            DiagnosticPreset(
                id = "net_conn",
                title = "网络连接状态统计",
                description = "汇总当前 TCP/UDP 监听与连接状态",
                command = "ss -s"
            )
        )
    }
}

sealed class UserFacingError(
    val title: String,
    val message: String,
    val suggestion: String
) {
    class AuthFailed(detail: String = "") : UserFacingError(
        title = "认证失败",
        message = "服务器拒绝了当前的用户名、密码或私钥凭据${if (detail.isNotBlank()) "（$detail）" else ""}。",
        suggestion = "请核对用户名、密码或私钥口令后重试。"
    )

    class HostKeyUntrusted(val fingerprint: String, val keyType: String) : UserFacingError(
        title = "需要确认主机指纹",
        message = "首次连接此服务器，请核对 SSH 公钥指纹（$keyType）。",
        suggestion = "确认指纹 $fingerprint 无误后点击信任并继续。"
    )

    class HostKeyChanged(val expected: String, val actual: String) : UserFacingError(
        title = "主机指纹变更警告",
        message = "服务器 SSH 公钥指纹与之前记录不一致，可能存在重装系统或中间人风险。",
        suggestion = "如确认服务器已重装，请在编辑页面重新信任新指纹。"
    )

    class NetworkUnreachable : UserFacingError(
        title = "无法连接服务器",
        message = "连接超时或目标端口不可达。",
        suggestion = "请检查服务器地址、端口、防火墙或内网穿透/VPN 状态。"
    )

    class AgentUnauthorized : UserFacingError(
        title = "Agent 鉴权失败",
        message = "Agent 接口返回 401/403，Token 不匹配。",
        suggestion = "请检查服务器设置中的 Agent Bearer Token。"
    )

    class Unknown(summary: String) : UserFacingError(
        title = "连接异常",
        message = summary,
        suggestion = "请稍后重试，或切换采集模式进行诊断。"
    )
}
