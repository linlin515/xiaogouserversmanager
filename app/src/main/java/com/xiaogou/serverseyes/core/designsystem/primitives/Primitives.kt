package com.xiaogou.serverseyes.core.designsystem.primitives

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.tokens.AppMotion
import com.xiaogou.serverseyes.core.designsystem.tokens.AppRadius
import com.xiaogou.serverseyes.core.designsystem.tokens.AppSpacing
import com.xiaogou.serverseyes.core.model.ServerStatus

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = AppTheme.colors.border
    )
}

@Composable
fun StatusBadge(
    status: ServerStatus,
    modifier: Modifier = Modifier,
    overrideLabel: String? = null
) {
    val colors = AppTheme.colors
    val (bg, fg, icon, defaultLabel) = when (status) {
        ServerStatus.ONLINE -> Quad(colors.successSoft, colors.success, Icons.Rounded.CheckCircle, "运行正常")
        ServerStatus.WARNING -> Quad(colors.warningSoft, colors.warning, Icons.Rounded.Warning, "性能警告")
        ServerStatus.CRITICAL -> Quad(colors.errorSoft, colors.error, Icons.Rounded.Error, "严重告警")
        ServerStatus.OFFLINE -> Quad(colors.errorSoft, colors.error, Icons.Rounded.CloudOff, "离线")
        ServerStatus.CONNECTING -> Quad(colors.infoSoft, colors.info, Icons.Rounded.Sync, "采集中")
        ServerStatus.UNKNOWN -> Quad(colors.surfaceSubtle, colors.textSecondary, Icons.AutoMirrored.Rounded.HelpOutline, "待采集")
    }
    val text = overrideLabel ?: defaultLabel
    StatusChipRaw(
        text = text,
        icon = icon,
        backgroundColor = bg,
        contentColor = fg,
        modifier = modifier
    )
}

@Composable
fun StatusChipRaw(
    text: String,
    icon: ImageVector?,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.full))
            .background(backgroundColor)
            .padding(horizontal = AppSpacing.space2, vertical = 3.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = text
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.space1)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = text,
            style = AppTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MetricProgressBar(
    percent: Double,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp
) {
    val colors = AppTheme.colors
    val clamped = (percent / 100.0).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = AppMotion.smallMs),
        label = "metricBar"
    )
    val fillColor = when {
        percent >= 90.0 -> colors.error
        percent >= 75.0 -> colors.warning
        else -> colors.success
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(AppRadius.full))
            .background(colors.surfaceSubtle)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(AppRadius.full))
                .background(fillColor)
        )
    }
}

@Composable
fun SparklineChart(
    values: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp
) {
    val safeValues = if (values.size >= 2) values else listOf(values.firstOrNull() ?: 0.0, values.firstOrNull() ?: 0.0)
    val subtleTrack = AppTheme.colors.border
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "近期资源趋势折线" }
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        drawLine(
            color = subtleTrack.copy(alpha = 0.45f),
            start = Offset(0f, h * 0.5f),
            end = Offset(w, h * 0.5f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )
        val stepX = w / (safeValues.size - 1).coerceAtLeast(1)
        val path = Path()
        val fillPath = Path()
        safeValues.forEachIndexed { idx, v ->
            val x = idx * stepX
            val norm = (v.toFloat() / 100f).coerceIn(0f, 1f)
            val y = h - (norm * (h - 4.dp.toPx())) - 2.dp.toPx()
            if (idx == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo((safeValues.size - 1) * stepX, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent)
            )
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun TimeSeriesLineChart(
    values: List<Double>,
    lineColor: Color,
    secondaryValues: List<Double>? = null,
    secondaryColor: Color = AppTheme.colors.success,
    maxValue: Double = 100.0,
    modifier: Modifier = Modifier,
    height: Dp = 136.dp
) {
    val gridColor = AppTheme.colors.border
    val safeMax = maxValue.coerceAtLeast(1.0)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "节点历史性能曲线" }
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Horizontal dashed gridlines at 25%, 50%, 75%
        listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
            val y = h * frac
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        }

        fun drawSeries(series: List<Double>, color: Color, fill: Boolean) {
            if (series.isEmpty()) return
            val pts = if (series.size == 1) listOf(series.first(), series.first()) else series
            val stepX = w / (pts.size - 1).coerceAtLeast(1)
            val strokePath = Path()
            val fillPath = Path()
            pts.forEachIndexed { idx, v ->
                val x = idx * stepX
                val norm = (v / safeMax).toFloat().coerceIn(0f, 1f)
                val y = h - norm * (h - 8.dp.toPx()) - 4.dp.toPx()
                if (idx == 0) {
                    strokePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            if (fill) {
                fillPath.lineTo((pts.size - 1) * stepX, h)
                fillPath.close()
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.24f), Color.Transparent)
                    )
                )
            }
            drawPath(
                path = strokePath,
                color = color,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        secondaryValues?.let { drawSeries(it, secondaryColor, fill = false) }
        drawSeries(values, lineColor, fill = true)
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
