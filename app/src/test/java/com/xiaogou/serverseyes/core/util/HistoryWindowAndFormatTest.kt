package com.xiaogou.serverseyes.core.util

import com.xiaogou.serverseyes.core.model.HistoryWindow
import com.xiaogou.serverseyes.core.model.MetricPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryWindowAndFormatTest {

    @Test
    fun historyWindowMapsMinutesToExpectedBucket() {
        assertEquals(HistoryWindow.LAST_15M, HistoryWindow.fromMinutes(15))
        assertEquals(HistoryWindow.LAST_1H, HistoryWindow.fromMinutes(60))
        assertEquals(HistoryWindow.LAST_6H, HistoryWindow.fromMinutes(360))
        assertEquals(HistoryWindow.LAST_24H, HistoryWindow.fromMinutes(1440))
        assertEquals(HistoryWindow.LAST_48H, HistoryWindow.fromMinutes(2880))

        assertEquals(10_000L, HistoryWindow.LAST_15M.bucketMs)
        assertEquals(600_000L, HistoryWindow.LAST_48H.bucketMs)
        // 未知窗口回退到 1 小时，避免图表拿到 0 桶宽
        assertEquals(HistoryWindow.LAST_1H, HistoryWindow.fromMinutes(999))
    }

    @Test
    fun everyWindowStaysWithinPointBudget() {
        // 分桶后单序列应稳定在 400 点以内，避免长窗口逐点绘制。
        HistoryWindow.entries.forEach { window ->
            val bucketCount = window.minutes * 60_000L / window.bucketMs
            assertTrue("${window.label} 桶数过多: $bucketCount", bucketCount <= 400)
        }
    }

    @Test
    fun formatAxisTimeUsesShortPatternForShortWindows() {
        val ts = 1_700_000_000_000L // 2023-11-15 06:13:20 UTC
        val short = Formatters.formatAxisTime(ts, 60)
        val long = Formatters.formatAxisTime(ts, 1440)
        assertEquals(5, short.length)
        assertTrue(short.contains(":"))
        assertTrue(long.length > short.length)
        assertTrue(long.contains("-"))
    }

    @Test
    fun formatPercentRoundsAsRequested() {
        assertEquals("42%", Formatters.formatPercent(42.4))
        assertEquals("42.4%", Formatters.formatPercent(42.36, decimals = 1))
        assertEquals("0%", Formatters.formatPercent(0.0))
    }

    @Test
    fun metricPointSwapPercentHandlesDisabledSwap() {
        val point = MetricPoint(
            timestampMs = 1L,
            cpuPercent = 10.0,
            memPercent = 20.0,
            netRxBytesPerSec = 0L,
            netTxBytesPerSec = 0L,
            load1 = 0.5
        )
        assertEquals(0.0, point.swapPercent, 0.0001)

        val withSwap = point.copy(swapUsedBytes = 256L, swapTotalBytes = 1024L)
        assertEquals(25.0, withSwap.swapPercent, 0.0001)
    }

    @Test
    fun formatClockTimeIsStable() {
        val ts = 1_700_000_000_000L
        val expected = SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date(ts))
        assertEquals(expected, Formatters.formatClockTime(ts))
        assertNotNull(expected)
    }
}

class AxisTimeRegressionTest {
    @org.junit.Test
    fun axisLabelsAdvanceAcrossMidnight() {
        // 2026-11-22 20:43 / 11-23 04:43 / 11-23 12:43 (Asia/Shanghai)
        org.junit.Assert.assertEquals("11-22 20:43", Formatters.formatAxisTime(1795351380000L, 1440))
        org.junit.Assert.assertEquals("11-23 04:43", Formatters.formatAxisTime(1795380180000L, 1440))
        org.junit.Assert.assertEquals("11-23 12:43", Formatters.formatAxisTime(1795408980000L, 1440))
    }
}
