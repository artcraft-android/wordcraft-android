package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.artcraftandroid.wordcraft.core.ShapeOutline
import io.github.artcraftandroid.wordcraft.core.shapeAspect
import io.github.artcraftandroid.wordcraft.core.shapeGallery
import io.github.artcraftandroid.wordcraft.core.shapeOutline
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Shapes gallery: the shapes inserted lately, the lines and connectors, then the preset
 * groups, each under its band, eight to a row. A gallery entry is a key: a preset's name, a line
 * as `kind:arrow`, or `textBox`.
 */
@Composable
internal fun ShapeGalleryPanel(vm: AppViewModel) {
    val groups = remember { runCatching { shapeGallery() }.getOrDefault(emptyList()) }
    Scrolling {
        GalleryGroup("Recent Shapes", vm.recentShapes, vm)
        GalleryGroup("Lines", LINE_ITEMS, vm)
        for (g in groups) {
            if (g.name == "Lines") continue
            // the text box opens the basic shapes, as a box to type in
            GalleryGroup(g.name, if (g.name == "Basic Shapes") listOf(TEXT_BOX) + g.presets else g.presets, vm)
        }
    }
}

/** The text box entry. */
internal const val TEXT_BOX = "textBox"

/** The lines and connectors: straight, elbow and curved, each plain, with an arrow, and with two. */
private val LINE_ITEMS = listOf("straightConnector", "elbowConnector", "curvedConnector").flatMap { k -> listOf("$k:none", "$k:end", "$k:both") }

/** The colours a new shape gets, which the icons show it in. */
private val SHAPE_FILL = Color(0xFF156082)
private val SHAPE_EDGE = Color(0xFF0E405A)

@Composable
private fun GalleryGroup(name: String, items: List<String>, vm: AppViewModel) {
    // the group's band, then rows of 61 × 56 dp cells with 45 dp icons
    SectionBand(name)
    Column(Modifier.padding(start = 16.dp, top = 19.dp, bottom = 16.dp)) {
        for (row in items.chunked(8)) {
            Row {
                for (key in row) {
                    Box(Modifier.size(61.3.dp, 56.dp).clickable { vm.insertShape(key) }, contentAlignment = Alignment.Center) {
                        ShapeIcon(key)
                    }
                }
            }
        }
    }
}

/** A gallery entry's icon: the shape drawn to fill a 45 dp box, in a new shape's colours. */
@Composable
private fun ShapeIcon(key: String) {
    val outline = remember(key) {
        if (key == TEXT_BOX || key.contains(':')) null
        else runCatching {
            val a = shapeAspect(key).coerceIn(0.2f, 5f)
            shapeOutline(key, if (a >= 1f) 100f else 100f * a, if (a >= 1f) 100f / a else 100f)
        }.getOrNull()
    }
    Canvas(Modifier.size(45.dp)) {
        when {
            key == TEXT_BOX -> textBoxIcon(1.5.dp.toPx())
            key.contains(':') -> lineIcon(key.substringBefore(':'), key.substringAfter(':'), 3.1.dp.toPx())
            outline != null -> presetIcon(outline, 2.4.dp.toPx())
        }
    }
}

/** A preset's outline, scaled to the box by what it draws (an arc or a half ring fills it too). */
private fun DrawScope.presetIcon(o: ShapeOutline, width: Float) {
    val all = o.fill + o.lines + o.faces.map { it.pts }
    var (x0, y0, x1, y1) = listOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    for (r in all) for (i in 0 until r.size - 1 step 2) {
        x0 = minOf(x0, r[i]); x1 = maxOf(x1, r[i]); y0 = minOf(y0, r[i + 1]); y1 = maxOf(y1, r[i + 1])
    }
    if (x1 <= x0 && y1 <= y0) return
    val room = size.minDimension - width
    val k = room / maxOf(x1 - x0, y1 - y0, 1e-3f)
    val dx = (size.width - (x1 - x0) * k) / 2f - x0 * k
    val dy = (size.height - (y1 - y0) * k) / 2f - y0 * k
    fun path(rings: List<List<Float>>, close: Boolean) = Path().apply {
        for (r in rings) {
            for (i in 0 until r.size - 1 step 2) {
                val (x, y) = r[i] * k + dx to r[i + 1] * k + dy
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            if (close && r.isNotEmpty()) close()
        }
    }
    val edge = Stroke(width, join = StrokeJoin.Round, cap = StrokeCap.Round)
    // brackets and braces are lines: no fill
    if (o.fill.isNotEmpty() && !o.lineLike) {
        val p = path(o.fill, true)
        drawPath(p, SHAPE_FILL)
        if (o.strokeFill) drawPath(p, SHAPE_EDGE, style = edge)
    }
    for (f in o.faces) {
        val p = path(listOf(f.pts), true)
        drawPath(p, if (f.lift >= 0) lerp(SHAPE_FILL, Color.White, f.lift) else lerp(SHAPE_FILL, Color.Black, -f.lift))
        if (o.strokeFill) drawPath(p, SHAPE_EDGE, style = edge)
    }
    // lines on their own (an arc, a bracket) take the fill's colour, as a line does
    if (o.lines.isNotEmpty()) drawPath(path(o.lines, false), if (o.lineLike) SHAPE_FILL else SHAPE_EDGE, style = if (o.lineLike) Stroke(width * 1.3f, join = StrokeJoin.Round, cap = StrokeCap.Round) else edge)
}

/** A line or connector from the top left to the bottom right, with its arrowheads. */
private fun DrawScope.lineIcon(kind: String, arrow: String, width: Float) {
    val (w, h) = size.width to size.height
    val a = Offset(w * 0.08f, h * 0.1f)
    val b = Offset(w * 0.92f, h * 0.9f)
    val p = Path().apply {
        moveTo(a.x, a.y)
        when (kind) {
            "elbowConnector" -> { lineTo(w * 0.5f, a.y); lineTo(w * 0.5f, b.y); lineTo(b.x, b.y) }
            "curvedConnector" -> cubicTo(w * 0.55f, a.y, w * 0.45f, b.y, b.x, b.y)
            else -> lineTo(b.x, b.y)
        }
    }
    drawPath(p, SHAPE_FILL, style = Stroke(width, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
    // the way the line runs at each end
    val (endFrom, startFrom) = when (kind) {
        "straightConnector" -> a to b
        else -> Offset(w * 0.5f, b.y) to Offset(w * 0.5f, a.y)
    }
    if (arrow == "end" || arrow == "both") arrowHead(endFrom, b, width)
    if (arrow == "both") arrowHead(startFrom, a, width)
}

/** A filled arrowhead at [tip], pointing away from [from]. */
private fun DrawScope.arrowHead(from: Offset, tip: Offset, width: Float) {
    val t = atan2(tip.y - from.y, tip.x - from.x)
    val len = width * 3.4f
    val half = width * 1.9f
    val bx = tip.x - len * cos(t)
    val by = tip.y - len * sin(t)
    val p = Path().apply {
        moveTo(tip.x + width * 0.4f * cos(t), tip.y + width * 0.4f * sin(t))
        lineTo(bx - half * sin(t), by + half * cos(t))
        lineTo(bx + half * sin(t), by - half * cos(t))
        close()
    }
    drawPath(p, SHAPE_FILL)
}

/** A text box: a frame with lines of text in it, the last one short. */
private fun DrawScope.textBoxIcon(width: Float) {
    val (w, h) = size.width to size.height
    val s = width
    drawRect(SHAPE_FILL, topLeft = Offset(s / 2f, s / 2f), size = androidx.compose.ui.geometry.Size(w - s, h - s), style = Stroke(s))
    for ((i, y) in listOf(0.3f, 0.5f, 0.7f).withIndex()) {
        drawLine(SHAPE_FILL, Offset(w * 0.2f, h * y), Offset(if (i == 2) w * 0.55f else w * 0.8f, h * y), s)
    }
}
