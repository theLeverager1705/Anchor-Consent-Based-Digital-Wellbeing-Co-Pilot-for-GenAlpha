package com.anchor.copilot.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.copilot.data.AppCategory
import com.anchor.copilot.data.MoodPoint
import com.anchor.copilot.ui.theme.Anchor

@Composable
private fun rememberReveal(key: Any?): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(700)) }
    return a.value
}

/** Mood trend, 1 = rough day … 5 = great day, with the latest point in accent. */
@Composable
fun MoodTrendChart(points: List<MoodPoint>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val reveal = rememberReveal(points.size)
    Canvas(modifier.fillMaxWidth().aspectRatio(560f / 220f)) {
        val pad = 28.dp.toPx()
        val w = size.width
        val h = size.height
        fun y(v: Double) = (h - pad - ((v - 1) / 4.0) * (h - pad * 2)).toFloat()
        for (v in 1..5) {
            val yy = y(v.toDouble())
            drawLine(Anchor.Line, Offset(pad, yy), Offset(w - pad, yy), 1.dp.toPx())
            drawText(measurer, "$v", Offset(4.dp.toPx(), yy - 7.dp.toPx()), TextStyle(fontSize = 9.sp, color = Anchor.Muted))
        }
        if (points.size < 2) {
            drawText(measurer, "Trend appears after a couple of check-ins", Offset(pad, h / 2 - 20), TextStyle(fontSize = 12.sp, color = Anchor.Muted))
            return@Canvas
        }
        val step = (w - pad * 2) / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = pad + i * step
            if (i == 0) path.moveTo(x, y(p.value)) else path.lineTo(x, y(p.value))
        }
        val visible = pad + (w - pad * 2) * reveal
        drawContext.canvas.save()
        drawContext.canvas.clipRect(0f, 0f, visible + 6f, h)
        drawPath(path, Anchor.Primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = points.last()
        drawCircle(Anchor.Accent, 4.5.dp.toPx(), Offset(pad + (points.size - 1) * step, y(last.value)))
        drawContext.canvas.restore()
    }
}

/** Category mix donut (prototype style) with a legend. */
@Composable
fun CategoryDonut(mix: List<Pair<AppCategory, Int>>, modifier: Modifier = Modifier) {
    val reveal = rememberReveal(mix.hashCode())
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(128.dp)) {
            val total = mix.sumOf { it.second }.coerceAtLeast(1)
            var angle = -90f
            val r = size.minDimension / 2
            mix.forEach { (cat, pct) ->
                val sweep = 360f * pct / total * reveal
                drawArc(Color(cat.color), angle, sweep, useCenter = true, topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2))
                angle += sweep
            }
            drawCircle(Anchor.Card, r * 0.5f, center)
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            mix.take(6).forEach { (cat, pct) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(Color(cat.color), 10.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("${cat.label} · $pct%", fontSize = 12.sp, color = Anchor.Muted)
                }
            }
        }
    }
}

data class Bar(val value: Double, val label: String, val highlight: Boolean)

/** Transition reaction intensity bars (0–5), accent when intensity >= 3.5. */
@Composable
fun TransitionBars(bars: List<Bar>, modifier: Modifier = Modifier, max: Double = 5.0, height: Float = 180f) {
    val measurer = rememberTextMeasurer()
    val reveal = rememberReveal(bars.size)
    Canvas(modifier.fillMaxWidth().aspectRatio(560f / height)) {
        val pad = 24.dp.toPx()
        val labelH = 16.dp.toPx()
        val w = size.width
        val h = size.height - labelH
        if (bars.isEmpty()) {
            drawText(measurer, "No transition checks yet", Offset(pad, h / 2 - 10), TextStyle(fontSize = 12.sp, color = Anchor.Muted))
            return@Canvas
        }
        val slot = (w - pad * 2) / bars.size
        val barW = slot - 8.dp.toPx()
        bars.forEachIndexed { i, b ->
            val x = pad + i * slot + 4.dp.toPx()
            val bh = ((b.value / max) * (h - pad)).toFloat() * reveal
            drawRoundRect(if (b.highlight) Anchor.Accent else Anchor.Secondary, Offset(x, h - bh), Size(barW, bh), CornerRadius(4.dp.toPx()))
            val text = measurer.measure(b.label, TextStyle(fontSize = 9.sp, color = Anchor.Muted), maxLines = 1)
            if (text.size.width <= slot) drawText(text, topLeft = Offset(x + (barW - text.size.width) / 2, h + 3.dp.toPx()))
        }
        drawLine(Anchor.Line, Offset(pad, h), Offset(w - pad, h), 1.dp.toPx())
    }
}

@Composable
fun Legend(items: List<Pair<Color, String>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (c, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(c, 10.dp)
                Spacer(Modifier.width(5.dp))
                Text(l, fontSize = 12.sp, color = Anchor.Muted)
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}
