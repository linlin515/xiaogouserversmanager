package com.xiaogou.serverseyes.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var idx = 0
        while (value >= 1024.0 && idx < units.lastIndex) {
            value /= 1024.0
            idx++
        }
        return if (idx <= 1) {
            String.format(Locale.US, "%.0f %s", value, units[idx])
        } else {
            String.format(Locale.US, "%.1f %s", value, units[idx])
        }
    }

    fun formatBytesPerSec(bps: Long): String {
        if (bps <= 0L) return "0 B/s"
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        var value = bps.toDouble()
        var idx = 0
        while (value >= 1024.0 && idx < units.lastIndex) {
            value /= 1024.0
            idx++
        }
        return if (idx == 0) {
            String.format(Locale.US, "%.0f %s", value, units[idx])
        } else {
            String.format(Locale.US, "%.1f %s", value, units[idx])
        }
    }

    fun formatUptime(seconds: Long): String {
        if (seconds <= 0L) return "未知"
        val days = seconds / 86_400
        val hours = (seconds % 86_400) / 3_600
        val mins = (seconds % 3_600) / 60
        return when {
            days > 0 -> "${days}天 ${hours}小时"
            hours > 0 -> "${hours}小时 ${mins}分"
            else -> "${mins.coerceAtLeast(1)}分钟"
        }
    }

    fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (timestampMs <= 0L) return "未采集"
        val diffSec = ((nowMs - timestampMs) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 5 -> "刚刚"
            diffSec < 60 -> "${diffSec}秒前"
            diffSec < 3600 -> "${diffSec / 60}分钟前"
            diffSec < 86_400 -> "${diffSec / 3600}小时前"
            else -> "${diffSec / 86_400}天前"
        }
    }

    fun formatMinuteOfDay(minuteOfDay: Int): String {
        val clamped = minuteOfDay.coerceIn(0, 1439)
        val h = clamped / 60
        val m = clamped % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun formatDuration(durationMs: Long): String {
        val totalMin = (durationMs / 60_000L).coerceAtLeast(0L)
        return when {
            totalMin < 1 -> "不足 1 分钟"
            totalMin < 60 -> "${totalMin} 分钟"
            else -> {
                val h = totalMin / 60
                val m = totalMin % 60
                if (m == 0L) "${h} 小时" else "${h} 小时 ${m} 分"
            }
        }
    }

    /** 趋势图 X 轴标签：6 小时以内只显示时刻，更长窗口补上日期。 */
    fun formatAxisTime(timestampMs: Long, spanMinutes: Int): String {
        val pattern = if (spanMinutes <= 360) "HH:mm" else "MM-dd HH:mm"
        return SimpleDateFormat(pattern, Locale.US).format(Date(timestampMs))
    }

    /** 趋势图触摸读数需要精确到秒。 */
    fun formatClockTime(timestampMs: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestampMs))

    fun formatPercent(value: Double, decimals: Int = 0): String =
        String.format(Locale.US, "%.${decimals.coerceIn(0, 2)}f%%", value)
}
