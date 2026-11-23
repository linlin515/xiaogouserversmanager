package com.xiaogou.serverseyes.core.parser

import com.xiaogou.serverseyes.core.model.CpuJiffies
import com.xiaogou.serverseyes.core.model.DiskIoSectors
import com.xiaogou.serverseyes.core.model.NetBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxMetricParserTest {

    @Test
    fun `parseSshOutput computes cpu, memory, disk, inode, diskio, temp, net, proc, docker, systemd, ports`() {
        val sampleOutput = """
            ===META===
            prod-web-01
            Linux 6.8.0-45-generic
            PRETTY_NAME="Ubuntu 24.04.1 LTS"
            4
            ===UPTIME===
            123456.78 450000.12
            ===LOAD===
            1.25 0.98 0.72 2/412 19283
            ===STAT===
            cpu  2000 100 1000 10000 200 0 100 0 0 0
            ===MEM===
            MemTotal:       16384000 kB
            MemFree:         2048000 kB
            MemAvailable:    8192000 kB
            SwapTotal:       2097152 kB
            SwapFree:        1572864 kB
            ===DISK===
            Filesystem     1024-blocks      Used Available Capacity Mounted on
            /dev/vda1        104857600  41943040  62914560      40% /
            tmpfs              1638400      1200   1637200       1% /run
            /dev/vdb1        209715200 167772160  41943040      80% /data
            ===INODE===
            Filesystem       Inodes  IUsed   IFree IUse% Mounted on
            /dev/vda1       6553600 983040 5570560   15% /
            /dev/vdb1      13107200 655360 12451840   5% /data
            ===DISKIO===
               8       0 vda 1000 0 20480 10 500 0 10240 20 0 30 30
               8       1 vda1 500 0 10240 5 250 0 5120 10 0 15 15
            ===TEMP===
            acpitz:35000
            x86_pkg_temp:54500
            ===NET===
            Inter-|   Receive                                                |  Transmit
             face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
                lo: 99999999 1000 0 0 0 0 0 0 99999999 1000 0 0 0 0 0 0
              eth0: 20000000 5000 0 0 0 0 0 0 10000000 4000 0 0 0 0 0 0
            ===PROC===
              PID USER     %CPU %MEM   RSS STAT COMMAND
             1234 www-data 12.5  3.2 65536 S    nginx
             5678 postgres  8.1 14.0 262144 R   postgres
            ===DOCKER===
            abcdef1234567890|api-gateway|nginx:1.27|running|Up 3 days|0.0.0.0:443->443/tcp
            1122334455667788|worker-old|python:3.12|exited|Exited (0) 2 hours ago|
            ===DOCKER_STATS===
            abcdef1234567890|12.45%|64.2MiB / 512MiB|12.54%
            ===SYSTEMD===
            nginx.service          loaded active running A high performance web server
            postgresql.service     loaded active running PostgreSQL Cluster
            ● crash.service        loaded failed failed Broken background worker
            ===PORTS===
            tcp   LISTEN 0      511          0.0.0.0:80        0.0.0.0:*
            tcp   LISTEN 0      128             [::]:22           [::]:*
        """.trimIndent()

        val prevCpu = CpuJiffies(
            user = 1500, nice = 100, system = 800, idle = 9300,
            iowait = 200, irq = 0, softirq = 100, steal = 0
        ) // total=12000, idle=9500; curr total=13400, idle=10200 -> deltaTotal=1400, deltaIdle=700 -> 50%
        val prevNet = NetBytes(timestampMs = 1_000_000L, rxBytes = 19_000_000L, txBytes = 9_500_000L)
        val prevDisk = DiskIoSectors(timestampMs = 1_000_000L, readSectors = 16_480L, writeSectors = 8_240L)

        val snapshot = LinuxMetricParser.parseSshOutput(
            serverId = "srv-1",
            rawOutput = sampleOutput,
            nowMs = 1_002_000L,
            latencyMs = 45L,
            prevCpu = prevCpu,
            prevNet = prevNet,
            prevDisk = prevDisk
        )

        assertEquals("prod-web-01", snapshot.hostname)
        assertEquals("Ubuntu 24.04.1 LTS", snapshot.osName)
        assertEquals(4, snapshot.cpuCores)
        assertEquals(123456L, snapshot.uptimeSeconds)
        assertEquals(1.25, snapshot.load1, 0.001)
        assertEquals(50.0, snapshot.cpuPercent, 0.01)
        assertEquals(50.0, snapshot.memPercent, 0.01)
        assertEquals(2, snapshot.disks.size) // tmpfs excluded
        assertEquals("/", snapshot.disks[0].mountPoint)
        assertEquals(15.0, snapshot.disks[0].inodeUsedPercent!!, 0.01)
        assertEquals(80.0, snapshot.maxDiskPercent, 0.01)
        // 4000 sectors * 512 bytes / 2s = 1_024_000 B/s read (only vda counted, vda1 excluded)
        assertEquals(1_024_000L, snapshot.diskReadBytesPerSec)
        assertEquals(512_000L, snapshot.diskWriteBytesPerSec)
        assertNotNull(snapshot.cpuTempCelsius)
        assertEquals(54.5, snapshot.cpuTempCelsius!!, 0.01)
        // 1_000_000 bytes over 2s = 500_000 B/s (lo excluded)
        assertEquals(500_000L, snapshot.netRxBytesPerSec)
        assertEquals(250_000L, snapshot.netTxBytesPerSec)
        assertEquals(2, snapshot.topProcesses.size)
        assertEquals("nginx", snapshot.topProcesses[0].name)
        assertEquals("www-data", snapshot.topProcesses[0].user)
        assertEquals(65536L * 1024L, snapshot.topProcesses[0].rssBytes)
        assertEquals("S", snapshot.topProcesses[0].state)
        assertEquals(2, snapshot.containers.size)
        assertTrue(snapshot.containers[0].isRunning)
        assertEquals(12.45, snapshot.containers[0].cpuPercent!!, 0.01)
        assertEquals("64.2MiB / 512MiB", snapshot.containers[0].memUsageText)
        assertEquals(12.54, snapshot.containers[0].memPercent!!, 0.01)
        assertTrue(!snapshot.containers[1].isRunning)
        assertEquals(3, snapshot.services.size)
        assertEquals("crash.service", snapshot.services[2].unit)
        assertEquals("failed", snapshot.services[2].activeState)
        assertEquals(2, snapshot.ports.size)
        assertEquals(80, snapshot.ports[0].port)
    }

    @Test
    fun `parsePsOutput remains backwards compatible with 4-column output`() {
        val legacy = """
              PID COMMAND         %CPU %MEM
             1234 nginx           12.5  3.2
        """.trimIndent()
        val parsed = LinuxMetricParser.parsePsOutput(legacy)
        assertEquals(1, parsed.size)
        assertEquals("nginx", parsed[0].name)
        assertEquals("", parsed[0].user)
    }
}
