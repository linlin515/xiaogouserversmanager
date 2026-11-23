package com.xiaogou.serverseyes.core.parser

import com.xiaogou.serverseyes.core.model.ContainerInfo
import com.xiaogou.serverseyes.core.model.CpuJiffies
import com.xiaogou.serverseyes.core.model.DiskIoSectors
import com.xiaogou.serverseyes.core.model.DiskPartition
import com.xiaogou.serverseyes.core.model.ListeningPort
import com.xiaogou.serverseyes.core.model.MetricSource
import com.xiaogou.serverseyes.core.model.NetBytes
import com.xiaogou.serverseyes.core.model.ProcessSummary
import com.xiaogou.serverseyes.core.model.ServerSnapshot
import com.xiaogou.serverseyes.core.model.SystemServiceInfo

object LinuxMetricParser {

    private val WHOLE_DISK_DEVICE_RE = Regex("^(sd[a-z]+|vd[a-z]+|xvd[a-z]+|nvme\\d+n\\d+|mmcblk\\d+)$")

    /**
     * Single batched read-only probe command minimizing SSH round-trips.
     * Uses explicit section markers and safely handles missing tools (docker/systemctl/ss).
     */
    val SSH_PROBE_COMMAND: String = """
        echo "===META==="
        hostname 2>/dev/null || true
        uname -sr 2>/dev/null || true
        grep -E '^PRETTY_NAME=' /etc/os-release 2>/dev/null | head -n1 || true
        nproc 2>/dev/null || grep -c ^processor /proc/cpuinfo 2>/dev/null || echo 1
        echo "===UPTIME==="
        cat /proc/uptime 2>/dev/null || true
        echo "===LOAD==="
        cat /proc/loadavg 2>/dev/null || true
        echo "===STAT==="
        head -n1 /proc/stat 2>/dev/null || true
        echo "===MEM==="
        cat /proc/meminfo 2>/dev/null || true
        echo "===DISK==="
        df -kP 2>/dev/null || true
        echo "===INODE==="
        df -iP 2>/dev/null || true
        echo "===DISKIO==="
        cat /proc/diskstats 2>/dev/null || true
        echo "===TEMP==="
        for z in /sys/class/thermal/thermal_zone*; do [ -r "${'$'}z/temp" ] && echo "${'$'}(cat "${'$'}z/type" 2>/dev/null):${'$'}(cat "${'$'}z/temp" 2>/dev/null)"; done 2>/dev/null || true
        echo "===NET==="
        cat /proc/net/dev 2>/dev/null || true
        echo "===PROC==="
        ps -eo pid,user,%cpu,%mem,rss,stat,comm --sort=-%cpu 2>/dev/null | head -n 11 || true
        echo "===DOCKER==="
        if command -v docker >/dev/null 2>&1; then docker ps -a --no-trunc --format '{{.ID}}|{{.Names}}|{{.Image}}|{{.State}}|{{.Status}}|{{.Ports}}' 2>/dev/null || true; fi
        echo "===DOCKER_STATS==="
        if command -v docker >/dev/null 2>&1; then docker stats --no-stream --no-trunc --format '{{.ID}}|{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}' 2>/dev/null || true; fi
        echo "===SYSTEMD==="
        if command -v systemctl >/dev/null 2>&1; then systemctl list-units --type=service --all --no-pager --no-legend 2>/dev/null | head -n 60 || true; fi
        echo "===PORTS==="
        if command -v ss >/dev/null 2>&1; then ss -tulnH 2>/dev/null | head -n 50 || true; fi
    """.trimIndent().replace("\n", "; ")

    /**
     * Quick second CPU/Net/DiskIO sample when there is no cached prior sample yet.
     */
    const val SSH_DELTA_SAMPLE_COMMAND: String =
        "sleep 0.35; echo '===STAT==='; head -n1 /proc/stat; echo '===NET==='; cat /proc/net/dev; echo '===DISKIO==='; cat /proc/diskstats 2>/dev/null || true"

    fun splitSections(rawOutput: String): Map<String, String> {
        val sections = linkedMapOf<String, StringBuilder>()
        var currentKey: String? = null
        rawOutput.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("===") && trimmed.endsWith("===") && trimmed.length > 6) {
                currentKey = trimmed.removePrefix("===").removeSuffix("===").trim()
                sections.getOrPut(currentKey!!) { StringBuilder() }
            } else if (currentKey != null) {
                sections[currentKey!!]?.appendLine(line)
            }
        }
        return sections.mapValues { it.value.toString().trim() }
    }

    fun parseSshOutput(
        serverId: String,
        rawOutput: String,
        nowMs: Long,
        latencyMs: Long,
        prevCpu: CpuJiffies? = null,
        prevNet: NetBytes? = null,
        prevDisk: DiskIoSectors? = null
    ): ServerSnapshot {
        val sections = splitSections(rawOutput)

        // META
        val metaLines = sections["META"]?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val hostname = metaLines.getOrNull(0).orEmpty()
        val kernel = metaLines.getOrNull(1).orEmpty()
        val osLine = metaLines.firstOrNull { it.startsWith("PRETTY_NAME=") }.orEmpty()
        val osName = osLine.removePrefix("PRETTY_NAME=").trim().trim('"')
        val cpuCores = metaLines.lastOrNull()?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        // UPTIME
        val uptimeSeconds = sections["UPTIME"]
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.toDoubleOrNull()
            ?.toLong()
            ?: 0L

        // LOAD
        val loadParts = sections["LOAD"]?.trim()?.split(Regex("\\s+")).orEmpty()
        val load1 = loadParts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val load5 = loadParts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val load15 = loadParts.getOrNull(2)?.toDoubleOrNull() ?: 0.0

        // STAT
        val currCpu = parseProcStat(sections["STAT"].orEmpty())
        val cpuPercent = CpuJiffies.computePercent(prevCpu, currCpu)

        // MEM
        val (memTotal, memUsed, swapTotal, swapUsed) = parseProcMemInfo(sections["MEM"].orEmpty())

        // DISK + INODE
        val inodeByMount = parseDfInodeOutput(sections["INODE"].orEmpty())
        val disks = parseDfOutput(sections["DISK"].orEmpty()).map { d ->
            val inodePct = inodeByMount[d.mountPoint]
            if (inodePct != null) d.copy(inodeUsedPercent = inodePct) else d
        }

        // DISKIO
        val currDisk = parseProcDiskstats(sections["DISKIO"].orEmpty(), nowMs)
        val (diskReadRate, diskWriteRate) = DiskIoSectors.computeRates(prevDisk, currDisk)

        // TEMP
        val cpuTemp = parseThermalZones(sections["TEMP"].orEmpty())

        // NET
        val currNet = parseProcNetDev(sections["NET"].orEmpty(), nowMs)
        val (rxRate, txRate) = NetBytes.computeRates(prevNet, currNet)

        // PROC
        val processes = parsePsOutput(sections["PROC"].orEmpty())

        // DOCKER + DOCKER_STATS
        val dockerStatsById = parseDockerStatsOutput(sections["DOCKER_STATS"].orEmpty())
        val containers = parseDockerPsOutput(sections["DOCKER"].orEmpty()).map { c ->
            val st = dockerStatsById[c.id]
            if (st != null) {
                c.copy(
                    cpuPercent = st.cpuPercent,
                    memUsageText = st.memUsageText,
                    memPercent = st.memPercent
                )
            } else c
        }

        // SYSTEMD
        val services = parseSystemdUnits(sections["SYSTEMD"].orEmpty())

        // PORTS
        val ports = parseSsListeningPorts(sections["PORTS"].orEmpty())

        return ServerSnapshot(
            serverId = serverId,
            timestampMs = nowMs,
            sourceUsed = MetricSource.SSH,
            latencyMs = latencyMs,
            hostname = hostname,
            osName = osName.ifEmpty { kernel },
            kernel = kernel,
            uptimeSeconds = uptimeSeconds,
            cpuCores = cpuCores,
            cpuPercent = cpuPercent,
            load1 = load1,
            load5 = load5,
            load15 = load15,
            memTotalBytes = memTotal,
            memUsedBytes = memUsed,
            swapTotalBytes = swapTotal,
            swapUsedBytes = swapUsed,
            disks = disks,
            netRxBytesPerSec = rxRate,
            netTxBytesPerSec = txRate,
            diskReadBytesPerSec = diskReadRate,
            diskWriteBytesPerSec = diskWriteRate,
            cpuTempCelsius = cpuTemp,
            topProcesses = processes,
            containers = containers,
            services = services,
            ports = ports,
            rawCpuJiffies = currCpu,
            rawNetBytes = currNet,
            rawDiskSectors = currDisk
        )
    }

    fun parseProcStat(statText: String): CpuJiffies? {
        val cpuLine = statText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("cpu ") }
            ?: return null
        val parts = cpuLine.split(Regex("\\s+")).drop(1)
        if (parts.size < 4) return null
        fun at(index: Int): Long = parts.getOrNull(index)?.toLongOrNull() ?: 0L
        return CpuJiffies(
            user = at(0),
            nice = at(1),
            system = at(2),
            idle = at(3),
            iowait = at(4),
            irq = at(5),
            softirq = at(6),
            steal = at(7)
        )
    }

    data class MemResult(
        val memTotalBytes: Long,
        val memUsedBytes: Long,
        val swapTotalBytes: Long,
        val swapUsedBytes: Long
    )

    fun parseProcMemInfo(memText: String): MemResult {
        val valuesKb = hashMapOf<String, Long>()
        memText.lineSequence().forEach { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@forEach
            val key = line.substring(0, colon).trim()
            val num = line.substring(colon + 1).trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
            if (num != null) valuesKb[key] = num
        }
        val totalKb = valuesKb["MemTotal"] ?: 0L
        val availableKb = valuesKb["MemAvailable"] ?: run {
            val free = valuesKb["MemFree"] ?: 0L
            val buffers = valuesKb["Buffers"] ?: 0L
            val cached = valuesKb["Cached"] ?: 0L
            val sreclaim = valuesKb["SReclaimable"] ?: 0L
            free + buffers + cached + sreclaim
        }
        val usedKb = (totalKb - availableKb).coerceAtLeast(0L)
        val swapTotalKb = valuesKb["SwapTotal"] ?: 0L
        val swapFreeKb = valuesKb["SwapFree"] ?: 0L
        val swapUsedKb = (swapTotalKb - swapFreeKb).coerceAtLeast(0L)
        return MemResult(
            memTotalBytes = totalKb * 1024L,
            memUsedBytes = usedKb * 1024L,
            swapTotalBytes = swapTotalKb * 1024L,
            swapUsedBytes = swapUsedKb * 1024L
        )
    }

    fun parseDfOutput(dfText: String): List<DiskPartition> {
        val ignoredFs = setOf("tmpfs", "devtmpfs", "udev", "overlay", "squashfs", "efivarfs")
        val seenMounts = hashSetOf<String>()
        val result = mutableListOf<DiskPartition>()
        dfText.lineSequence()
            .drop(1) // header
            .forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 6) return@forEach
                val fs = parts[0]
                if (fs in ignoredFs || fs.startsWith("none")) return@forEach
                val mount = parts.drop(5).joinToString(" ")
                if (mount.startsWith("/snap") || mount.startsWith("/sys") || mount.startsWith("/proc")) return@forEach
                if (!seenMounts.add(mount)) return@forEach
                val totalKb = parts[1].toLongOrNull() ?: return@forEach
                val usedKb = parts[2].toLongOrNull() ?: 0L
                val availKb = parts[3].toLongOrNull() ?: 0L
                val pctStr = parts[4].removeSuffix("%")
                val pct = pctStr.toDoubleOrNull() ?: if (totalKb > 0) (usedKb * 100.0 / totalKb) else 0.0
                result += DiskPartition(
                    filesystem = fs,
                    mountPoint = mount,
                    totalBytes = totalKb * 1024L,
                    usedBytes = usedKb * 1024L,
                    availBytes = availKb * 1024L,
                    usagePercent = pct.coerceIn(0.0, 100.0)
                )
            }
        return result
    }

    fun parseProcNetDev(netText: String, nowMs: Long): NetBytes {
        var totalRx = 0L
        var totalTx = 0L
        netText.lineSequence().forEach { raw ->
            val colon = raw.indexOf(':')
            if (colon <= 0) return@forEach
            val iface = raw.substring(0, colon).trim()
            if (iface == "lo" || iface.startsWith("veth") || iface.startsWith("br-") || iface == "docker0") {
                return@forEach
            }
            val nums = raw.substring(colon + 1).trim().split(Regex("\\s+"))
            if (nums.size >= 9) {
                totalRx += nums[0].toLongOrNull() ?: 0L
                totalTx += nums[8].toLongOrNull() ?: 0L
            }
        }
        return NetBytes(timestampMs = nowMs, rxBytes = totalRx, txBytes = totalTx)
    }

    fun parseDfInodeOutput(inodeText: String): Map<String, Double> {
        val result = hashMapOf<String, Double>()
        inodeText.lineSequence()
            .drop(1)
            .forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) return@forEach
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 6) return@forEach
                val mount = parts.drop(5).joinToString(" ")
                val pctStr = parts[4].removeSuffix("%")
                val pct = pctStr.toDoubleOrNull() ?: return@forEach
                result[mount] = pct.coerceIn(0.0, 100.0)
            }
        return result
    }

    fun parseProcDiskstats(diskstatsText: String, nowMs: Long): DiskIoSectors? {
        var totalReadSectors = 0L
        var totalWriteSectors = 0L
        var matched = false
        diskstatsText.lineSequence().forEach { raw ->
            val parts = raw.trim().split(Regex("\\s+"))
            if (parts.size < 10) return@forEach
            val devName = parts[2]
            if (!WHOLE_DISK_DEVICE_RE.matches(devName)) return@forEach
            val sectorsRead = parts[5].toLongOrNull() ?: 0L
            val sectorsWritten = parts[9].toLongOrNull() ?: 0L
            totalReadSectors += sectorsRead
            totalWriteSectors += sectorsWritten
            matched = true
        }
        return if (matched) DiskIoSectors(nowMs, totalReadSectors, totalWriteSectors) else null
    }

    fun parseThermalZones(tempText: String): Double? {
        val zones = tempText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains(':') }
            .mapNotNull { line ->
                val idx = line.lastIndexOf(':')
                val type = line.substring(0, idx).trim().lowercase()
                val rawTemp = line.substring(idx + 1).trim().toDoubleOrNull() ?: return@mapNotNull null
                val celsius = if (rawTemp > 1000.0) rawTemp / 1000.0 else rawTemp
                if (celsius in 1.0..125.0) type to celsius else null
            }
            .toList()
        if (zones.isEmpty()) return null
        val preferred = zones.firstOrNull { (type, _) ->
            type.contains("x86_pkg") || type.contains("cpu") || type.contains("core") || type.contains("k10temp")
        }
        return (preferred?.second ?: zones.maxOf { it.second })
    }

    fun parsePsOutput(psText: String): List<ProcessSummary> {
        val lines = psText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.size <= 1) return emptyList()
        val header = lines.first().uppercase()
        val hasExtendedCols = header.contains("USER") && header.contains("RSS") && header.contains("STAT")
        return lines.drop(1).mapNotNull { raw ->
            if (hasExtendedCols) {
                val parts = raw.split(Regex("\\s+"), limit = 7)
                if (parts.size < 7) return@mapNotNull null
                val pid = parts[0].toIntOrNull() ?: return@mapNotNull null
                val user = parts[1]
                val cpu = parts[2].toDoubleOrNull() ?: 0.0
                val mem = parts[3].toDoubleOrNull() ?: 0.0
                val rssKb = parts[4].toLongOrNull() ?: 0L
                val state = parts[5]
                val name = parts[6]
                ProcessSummary(
                    pid = pid,
                    name = name,
                    cpuPercent = cpu,
                    memPercent = mem,
                    user = user,
                    rssBytes = rssKb * 1024L,
                    state = state
                )
            } else {
                val parts = raw.split(Regex("\\s+"), limit = 4)
                if (parts.size < 4) return@mapNotNull null
                val pid = parts[0].toIntOrNull() ?: return@mapNotNull null
                val name = parts[1]
                val cpu = parts[2].toDoubleOrNull() ?: 0.0
                val mem = parts[3].toDoubleOrNull() ?: 0.0
                ProcessSummary(pid = pid, name = name, cpuPercent = cpu, memPercent = mem)
            }
        }
    }

    data class DockerStatRow(
        val cpuPercent: Double,
        val memUsageText: String,
        val memPercent: Double
    )

    fun parseDockerStatsOutput(statsText: String): Map<String, DockerStatRow> {
        val map = hashMapOf<String, DockerStatRow>()
        statsText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val parts = line.split('|')
                if (parts.size < 4) return@forEach
                val shortId = parts[0].take(12)
                val cpuPct = parts[1].removeSuffix("%").trim().toDoubleOrNull() ?: return@forEach
                val memUsage = parts[2].trim()
                val memPct = parts[3].removeSuffix("%").trim().toDoubleOrNull() ?: 0.0
                map[shortId] = DockerStatRow(
                    cpuPercent = cpuPct.coerceAtLeast(0.0),
                    memUsageText = memUsage,
                    memPercent = memPct.coerceIn(0.0, 100.0)
                )
            }
        return map
    }

    fun parseDockerPsOutput(dockerText: String): List<ContainerInfo> {
        return dockerText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 5) return@mapNotNull null
                ContainerInfo(
                    id = parts[0].take(12),
                    name = parts[1],
                    image = parts[2],
                    state = parts[3],
                    statusText = parts[4],
                    ports = parts.getOrNull(5).orEmpty()
                )
            }
            .toList()
    }

    fun parseSystemdUnits(systemdText: String): List<SystemServiceInfo> {
        return systemdText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("UNIT") }
            .mapNotNull { line ->
                val cleaned = line.removePrefix("●").trim()
                val parts = cleaned.split(Regex("\\s+"), limit = 5)
                if (parts.size < 4) return@mapNotNull null
                val unit = parts[0]
                if (!unit.endsWith(".service")) return@mapNotNull null
                SystemServiceInfo(
                    unit = unit,
                    loadState = parts[1],
                    activeState = parts[2],
                    subState = parts[3],
                    description = parts.getOrNull(4).orEmpty()
                )
            }
            .toList()
    }

    fun parseSsListeningPorts(ssText: String): List<ListeningPort> {
        val seen = hashSetOf<String>()
        return ssText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Netid") }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 5) return@mapNotNull null
                val proto = parts[0]
                val localAddrPort = parts[4]
                val lastColon = localAddrPort.lastIndexOf(':')
                if (lastColon <= 0) return@mapNotNull null
                val addr = localAddrPort.substring(0, lastColon).trim('[', ']')
                val port = localAddrPort.substring(lastColon + 1).toIntOrNull() ?: return@mapNotNull null
                val key = "$proto:$addr:$port"
                if (!seen.add(key)) return@mapNotNull null
                ListeningPort(protocol = proto, localAddress = addr, port = port)
            }
            .toList()
    }
}
