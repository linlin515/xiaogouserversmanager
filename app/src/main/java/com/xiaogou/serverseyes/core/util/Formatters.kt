package com.xiaogou.serverseyes.core.util

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
}
