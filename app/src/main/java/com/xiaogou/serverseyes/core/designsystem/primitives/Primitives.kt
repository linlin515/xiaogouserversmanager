package com.xiaogou.serverseyes.core.designsystem.primitives

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xiaogou.serverseyes.core.designsystem.theme.AppTheme
import com.xiaogou.serverseyes.core.designsystem.tokens.AppMotion
import com.xiaogou.serverseyes.core.designsystem.tokens.AppRadius
import com.xiaogou.serverseyes.core.designsystem.tokens.AppSpacing
import com.xiaogou.serverseyes.core.designsystem.tokens.tabular
import com.xiaogou.serverseyes.core.model.ServerStatus
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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

/** 趋势图上的一个采样点，带真实时间戳。 */
data class TrendPoint(val timestampMs: Long, val value: Double)

/** 趋势图的一条序列。dashed 用于区分次要指标，fill 为主序列的渐变填充。 */
data class TrendSeries(
    val label: String,
    val points: List<TrendPoint>,
    val color: Color,
    val dashed: Boolean = false,
    val fill: Boolean = false
)

/** 画在趋势图上的告警阈值线。 */
data class TrendThreshold(
    val value: Double,
    val label: String,
    val color: Color
)

/**
 * 详情页趋势图：按真实时间比例绘制，带 Y 轴刻度、X 轴时间标签、告警阈值线，
 * 并支持横向拖动查看十字准线与读数。
 *
 * 数据量由调用方提前聚合（单序列建议 ≤ 400 点），组件内不再做降采样。
 */
@Composable
fun MetricTrendChart(
    series: List<TrendSeries>,
    yMax: Double,
    unit: String,
    windowMinutes: Int,
    thresholds: List<TrendThreshold> = emptyList(),
    modifier: Modifier = Modifier,
    height: Dp = 196.dp,
    contentDescription: String = "节点历史性能曲线"
) {
    val colors = AppTheme.colors
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val safeMax = yMax.coerceAtLeast(1.0)
    val primary = series.firstOrNull { it.points.isNotEmpty() }
    val axisStyle = AppTheme.typography.labelSmall.copy(color = colors.textDisabled)
    val thresholdStyle = AppTheme.typography.labelSmall
    val gridColor = colors.border

    val allTimestamps = remember(series) {
        series.flatMap { s -> s.points.map { it.timestampMs } }
    }
    val minTs = allTimestamps.minOrNull() ?: 0L
    val maxTs = allTimestamps.maxOrNull() ?: 0L
    val span = (maxTs - minTs).coerceAtLeast(1L)
    val pointCount = primary?.points?.size ?: 0

    var selectedIndex by remember(series) { mutableIntStateOf(-1) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            delay(1500)
            selectedIndex = -1
        }
    }

    val padLeft = with(density) { 40.dp.toPx() }
    val padRight = with(density) { 10.dp.toPx() }
    val padTop = with(density) { 14.dp.toPx() }
    val padBottom = with(density) { 22.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val plotW = (widthPx - padLeft - padRight).coerceAtLeast(1f)
        val plotH = (heightPx - padTop - padBottom).coerceAtLeast(1f)

        fun xFor(ts: Long): Float =
            padLeft + ((ts - minTs).toFloat() / span.toFloat()) * plotW

        fun yFor(value: Double): Float =
            padTop + plotH - (value.coerceIn(0.0, safeMax) / safeMax).toFloat() * plotH

        fun xLabels(): List<Pair<Float, String>> {
            val count = 4
            return (0 until count).map { i ->
                val ts = minTs + (span * i / (count - 1))
                padLeft + plotW * (i.toFloat() / (count - 1)) to
                    com.xiaogou.serverseyes.core.util.Formatters.formatAxisTime(ts, windowMinutes)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(pointCount, minTs, maxTs) {
                    if (pointCount < 1) return@pointerInput
                    // 只拦截横向拖动，纵向滑动继续交给父级 LazyColumn 滚动。
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            selectedIndex = nearestIndex(offset.x, padLeft, plotW, pointCount)
                        },
                        onHorizontalDrag = { change, _ ->
                            selectedIndex = nearestIndex(change.position.x, padLeft, plotW, pointCount)
                        }
                    )
                }
        ) {
            if (pointCount == 0) return@Canvas

            // Y 轴刻度与横向网格
            val ticks = 4
            for (i in 0..ticks) {
                val frac = i.toFloat() / ticks
                val y = padTop + plotH - frac * plotH
                drawLine(
                    color = gridColor,
                    start = Offset(padLeft, y),
                    end = Offset(padLeft + plotW, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                )
                val value = safeMax * frac
                val label = when {
                    value <= 0.0 -> "0"
                    value >= 100 -> value.roundToInt().toString()
                    value >= 10 -> String.format(java.util.Locale.US, "%.0f", value)
                    else -> String.format(java.util.Locale.US, "%.1f", value)
                }
                val layout = textMeasurer.measure(label, axisStyle)
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(padLeft - 6.dp.toPx() - layout.size.width, y - layout.size.height / 2f)
                )
            }

            // 阈值线
            thresholds.forEach { threshold ->
                if (threshold.value <= safeMax) {
                    val y = yFor(threshold.value)
                    drawLine(
                        color = threshold.color,
                        start = Offset(padLeft, y),
                        end = Offset(padLeft + plotW, y),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))
                    )
                    val layout = textMeasurer.measure(
                        threshold.label,
                        thresholdStyle.copy(color = threshold.color)
                    )
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            padLeft + plotW - layout.size.width,
                            (y - layout.size.height - 1.dp.toPx()).coerceAtLeast(0f)
                        )
                    )
                }
            }

            // 序列
            series.forEach { s ->
                if (s.points.isEmpty()) return@forEach
                val strokePath = Path()
                val fillPath = Path()
                s.points.forEachIndexed { idx, point ->
                    val x = xFor(point.timestampMs)
                    val y = yFor(point.value)
                    if (idx == 0) {
                        strokePath.moveTo(x, y)
                        fillPath.moveTo(x, padTop + plotH)
                        fillPath.lineTo(x, y)
                    } else {
                        strokePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                if (s.fill) {
                    fillPath.lineTo(xFor(s.points.last().timestampMs), padTop + plotH)
                    fillPath.close()
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(s.color.copy(alpha = 0.22f), Color.Transparent),
                            startY = padTop,
                            endY = padTop + plotH
                        )
                    )
                }
                drawPath(
                    path = strokePath,
                    color = s.color,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = if (s.dashed) {
                            PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                        } else null
                    )
                )
            }

            // X 轴时间标签
            val labels = xLabels()
            labels.forEachIndexed { index, (x, text) ->
                val layout = textMeasurer.measure(text, axisStyle)
                val dx = when (index) {
                    0 -> x
                    labels.lastIndex -> x - layout.size.width
                    else -> x - layout.size.width / 2f
                }
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(dx, heightPx - padBottom + 5.dp.toPx())
                )
            }

            // 十字准线
            val selected = selectedIndex
            if (selected in 0 until pointCount) {
                val ts = primary?.points?.getOrNull(selected)?.timestampMs ?: return@Canvas
                val x = xFor(ts)
                drawLine(
                    color = colors.borderStrong,
                    start = Offset(x, padTop),
                    end = Offset(x, padTop + plotH),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f))
                )
                series.forEach { s ->
                    val point = s.points.getOrNull(selected) ?: return@forEach
                    drawCircle(
                        color = s.color,
                        radius = 3.2.dp.toPx(),
                        center = Offset(xFor(point.timestampMs), yFor(point.value))
                    )
                    drawCircle(
                        color = colors.surface,
                        radius = 3.2.dp.toPx(),
                        center = Offset(xFor(point.timestampMs), yFor(point.value)),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }

        // 读数气泡
        val selected = selectedIndex
        if (selected in 0 until pointCount) {
            val point = primary?.points?.getOrNull(selected)
            if (point != null) {
                val tooltipWidth = 150.dp
                val tooltipWidthPx = with(density) { tooltipWidth.toPx() }
                val anchorX = xFor(point.timestampMs)
                val anchorY = series
                    .mapNotNull { it.points.getOrNull(selected)?.value }
                    .minOrNull()
                    ?.let { yFor(it) } ?: padTop
                val gapPx = with(density) { 10.dp.toPx() }
                val edgePx = with(density) { 2.dp.toPx() }
                val tooltipHeightPx = with(density) { 58.dp.toPx() }
                val tooltipOffsetY = with(density) { 54.dp.toPx() }
                val left = (anchorX + gapPx)
                    .coerceAtMost(widthPx - tooltipWidthPx - edgePx)
                    .coerceAtLeast(edgePx)
                val top = (anchorY - tooltipOffsetY)
                    .coerceAtLeast(edgePx)
                    .coerceAtMost(heightPx - tooltipHeightPx)

                Surface(
                    color = colors.surfaceSubtle,
                    shape = RoundedCornerShape(AppRadius.sm),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderStrong),
                    modifier = Modifier
                        .width(tooltipWidth)
                        .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = com.xiaogou.serverseyes.core.util.Formatters.formatClockTime(point.timestampMs),
                            style = AppTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        series.forEach { s ->
                            val value = s.points.getOrNull(selected)?.value ?: return@forEach
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = s.label,
                                    style = AppTheme.typography.labelSmall,
                                    color = s.color,
                                    maxLines = 1
                                )
                                Text(
                                    text = formatTrendValue(value, unit),
                                    style = AppTheme.typography.labelSmall.tabular(),
                                    color = colors.textPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun nearestIndex(x: Float, padLeft: Float, plotW: Float, count: Int): Int {
    if (count <= 1) return 0
    val ratio = ((x - padLeft) / plotW).coerceIn(0f, 1f)
    return (ratio * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

private fun formatTrendValue(value: Double, unit: String): String = when (unit) {
    "%" -> String.format(java.util.Locale.US, "%.0f%%", value)
    "°C" -> String.format(java.util.Locale.US, "%.1f°C", value)
    "MB/s" -> String.format(java.util.Locale.US, "%.1f MB/s", value)
    "KB/s" -> String.format(java.util.Locale.US, "%.0f KB/s", value)
    else -> String.format(java.util.Locale.US, "%.2f", value)
}
