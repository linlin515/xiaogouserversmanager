package com.xiaogou.serverseyes.core.network

import com.xiaogou.serverseyes.core.model.ContainerInfo
import com.xiaogou.serverseyes.core.model.DiskPartition
import com.xiaogou.serverseyes.core.model.ListeningPort
import com.xiaogou.serverseyes.core.model.LogTargetType
import com.xiaogou.serverseyes.core.model.MetricPoint
import com.xiaogou.serverseyes.core.model.MetricSource
import com.xiaogou.serverseyes.core.model.ProcessSummary
import com.xiaogou.serverseyes.core.model.ServerSnapshot
import com.xiaogou.serverseyes.core.model.SystemServiceInfo
import com.xiaogou.serverseyes.core.model.WorkloadAction
import com.xiaogou.serverseyes.core.parser.AgentAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class AgentHealthDto(
    val status: String = "ok",
    val version: String = "1.0.0",
    val hostname: String = "",
    val osName: String = ""
)

@Serializable
data class AgentSnapshotDto(
    val timestampMs: Long = 0L,
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
    val topProcesses: List<ProcessSummary> = emptyList(),
    val containers: List<ContainerInfo> = emptyList(),
    val services: List<SystemServiceInfo> = emptyList(),
    val ports: List<ListeningPort> = emptyList()
)

@Serializable
data class AgentHistoryResponseDto(
    val points: List<MetricPoint> = emptyList()
)

@Serializable
data class AgentLogsResponseDto(
    val lines: List<String> = emptyList()
)

@Serializable
data class AgentActionRequestDto(
    val action: String,
    @SerialName("targetType") val targetType: String,
    @SerialName("targetName") val targetName: String
)

@Serializable
data class AgentActionResponseDto(
    val ok: Boolean = false,
    val message: String = ""
)

class AgentApiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
            .replace('：', ':')
            .replace('／', '/')
            .replace('。', '.')
            .trimEnd('/')
        return when {
            trimmed.startsWith("https://", ignoreCase = true) ->
                "https://" + trimmed.substring(8)
            trimmed.startsWith("http://", ignoreCase = true) ->
                "http://" + trimmed.substring(7)
            else -> "http://$trimmed"
        }
    }

    private fun buildRequest(url: String, token: String): Request.Builder {
        val builder = Request.Builder().url(url)
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    private fun executeGet(url: String, token: String): String {
        val request = buildRequest(url, token).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw AgentAuthException(response.code)
            }
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    suspend fun testHealth(baseUrl: String, token: String): Result<Pair<AgentHealthDto, Long>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.currentTimeMillis()
                val body = executeGet("${normalizeBaseUrl(baseUrl)}/api/v1/health", token)
                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                json.decodeFromString<AgentHealthDto>(body) to latency
            }
        }

    suspend fun fetchSnapshot(
        serverId: String,
        baseUrl: String,
        token: String
    ): Result<ServerSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.currentTimeMillis()
            val body = executeGet("${normalizeBaseUrl(baseUrl)}/api/v1/snapshot", token)
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val dto = json.decodeFromString<AgentSnapshotDto>(body)
            ServerSnapshot(
                serverId = serverId,
                timestampMs = if (dto.timestampMs > 0) dto.timestampMs else System.currentTimeMillis(),
                sourceUsed = MetricSource.AGENT,
                latencyMs = latency,
                hostname = dto.hostname,
                osName = dto.osName,
                kernel = dto.kernel,
                uptimeSeconds = dto.uptimeSeconds,
                cpuCores = dto.cpuCores.coerceAtLeast(1),
                cpuPercent = dto.cpuPercent,
                load1 = dto.load1,
                load5 = dto.load5,
                load15 = dto.load15,
                memTotalBytes = dto.memTotalBytes,
                memUsedBytes = dto.memUsedBytes,
                swapTotalBytes = dto.swapTotalBytes,
                swapUsedBytes = dto.swapUsedBytes,
                disks = dto.disks,
                netRxBytesPerSec = dto.netRxBytesPerSec,
                netTxBytesPerSec = dto.netTxBytesPerSec,
                topProcesses = dto.topProcesses,
                containers = dto.containers,
                services = dto.services,
                ports = dto.ports
            )
        }
    }

    suspend fun fetchHistory(
        baseUrl: String,
        token: String,
        minutes: Int = 60
    ): Result<List<MetricPoint>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = executeGet("${normalizeBaseUrl(baseUrl)}/api/v1/history?minutes=$minutes", token)
            json.decodeFromString<AgentHistoryResponseDto>(body).points
        }
    }

    suspend fun fetchLogs(
        baseUrl: String,
        token: String,
        targetType: LogTargetType,
        targetName: String,
        lines: Int = 120
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val typeParam = when (targetType) {
                LogTargetType.DOCKER_CONTAINER -> "docker"
                LogTargetType.SYSTEMD_SERVICE -> "systemd"
                LogTargetType.SYSTEM_JOURNAL -> "journal"
            }
            val encodedTarget = java.net.URLEncoder.encode(targetName, "UTF-8")
            val body = executeGet(
                "${normalizeBaseUrl(baseUrl)}/api/v1/logs?type=$typeParam&target=$encodedTarget&lines=$lines",
                token
            )
            json.decodeFromString<AgentLogsResponseDto>(body).lines
        }
    }

    suspend fun restartTarget(
        baseUrl: String,
        token: String,
        targetType: LogTargetType,
        targetName: String
    ): Result<String> = performWorkloadAction(baseUrl, token, targetType, targetName, WorkloadAction.RESTART)

    suspend fun performWorkloadAction(
        baseUrl: String,
        token: String,
        targetType: LogTargetType,
        targetName: String,
        action: WorkloadAction
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val typeStr = when (targetType) {
                LogTargetType.DOCKER_CONTAINER -> "docker"
                LogTargetType.SYSTEMD_SERVICE -> "systemd"
                LogTargetType.SYSTEM_JOURNAL -> error("系统日志不支持启停或重启")
            }
            val payload = json.encodeToString(
                AgentActionRequestDto(
                    action = action.apiValue,
                    targetType = typeStr,
                    targetName = targetName
                )
            )
            val request = buildRequest("${normalizeBaseUrl(baseUrl)}/api/v1/action", token)
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    throw AgentAuthException(response.code)
                }
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: $body")
                }
                val dto = json.decodeFromString<AgentActionResponseDto>(body)
                if (!dto.ok) error(dto.message.ifBlank { "执行失败" })
                dto.message.ifBlank { "已${action.label} $targetName" }
            }
        }
    }
}
