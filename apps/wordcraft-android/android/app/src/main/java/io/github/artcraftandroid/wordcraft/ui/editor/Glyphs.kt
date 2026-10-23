package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** An icon: a Lucide vector, a letter (Bold, Italic…), or one drawn here. */
sealed interface Glyph {
    data class Vector(val image: ImageVector) : Glyph

    /** A letter or two set in type: the formatting toggles show what they do. */
    data class Letters(
        val text: String,
        val weight: FontWeight = FontWeight.Normal,
        val italic: Boolean = false,
        val decoration: TextDecoration? = null,
        val family: FontFamily = FontFamily.SansSerif,
    ) : Glyph

    /** An icon with a colour bar under it (highlight, font colour). */
    data class WithBar(val base: Glyph, val bar: Color) : Glyph

    /** A small filled triangle pointing up or down (open and close the palette). */
    data class Triangle(val up: Boolean) : Glyph

    /** A filled dot of a colour (the pen presets). */
    data class Dot(val color: Color) : Glyph

    /** Three short lines at the top, middle or bottom of a cell (vertical alignment). */
    data class CellLines(val where: String) : Glyph

    /**
     * A small chart of a family (`column`, `line`, `pie`, `bar`, `area`, `scatter`, `other`) or of
     * a kind listed greyed (`maps`, `treemap`, `sunburst`, `histogram`, `boxWhisker`, `waterfall`,
     * `funnel`), in the accent and the tint; all in the tint when that's the disabled colour.
     */
    data class Chart(val family: String) : Glyph
}

fun ImageVector.glyph(): Glyph = Glyph.Vector(this)

@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    when (glyph) {
        is Glyph.Vector -> Icon(glyph.image, contentDescription = null, tint = tint, modifier = modifier.size(size))
        is Glyph.Letters -> Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Text(
                glyph.text,
                color = tint,
                // two letters (ab, Aa, ›¶) share the box
                fontSize = (size.value * if (glyph.text.length > 1) 0.7f else 0.92f).sp,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                fontWeight = glyph.weight,
                fontStyle = if (glyph.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = glyph.decoration,
                fontFamily = glyph.family,
                maxLines = 1,
            )
        }
        is Glyph.WithBar -> Box(modifier.size(size)) {
            GlyphIcon(glyph.base, tint, Modifier.align(Alignment.TopCenter), size * 0.86f)
            Canvas(Modifier.align(Alignment.BottomCenter).size(size, size * 0.18f)) {
                drawRect(glyph.bar, topLeft = Offset(size.toPx() * 0.04f, 0f), size = Size(this.size.width * 0.92f, this.size.height))
                if (glyph.bar.red > 0.95f && glyph.bar.green > 0.95f && glyph.bar.blue > 0.95f) {
                    // a white bar shows its edge
                    drawRect(Color(0xFFC6C6C6), topLeft = Offset(size.toPx() * 0.04f, 0f), size = Size(this.size.width * 0.92f, this.size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                }
            }
        }
        is Glyph.Dot -> Canvas(modifier.size(size)) {
            drawCircle(glyph.color, radius = this.size.minDimension / 2f)
        }
        is Glyph.CellLines -> Canvas(modifier.size(size)) {
            val w = this.size.width
            val gap = this.size.height * 0.16f
            val block = gap * 2f
            val top = when (glyph.where) {
                "top" -> this.size.height * 0.12f
                "bottom" -> this.size.height * 0.88f - block
                else -> (this.size.height - block) / 2f
            }
            val stroke = this.size.height / 16f
            for (i in 0..2) {
                val y = top + i * gap
                val inset = if (i == 1) w * 0.3f else w * 0.22f
                drawLine(tint, Offset(inset, y), Offset(w - inset, y), stroke)
            }
        }
        is Glyph.Chart -> Canvas(modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val accent = if (tint == io.github.artcraftandroid.wordcraft.ui.WordColors.Disabled) tint else io.github.artcraftandroid.wordcraft.ui.WordColors.Accent
            val line = h / 14f
            val outline = androidx.compose.ui.graphics.drawscope.Stroke(line)
            when (glyph.family) {
                "line" -> {
                    val p = Path().apply { moveTo(w * 0.1f, h * 0.75f); lineTo(w * 0.38f, h * 0.4f); lineTo(w * 0.62f, h * 0.6f); lineTo(w * 0.9f, h * 0.2f) }
                    drawPath(p, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(line * 1.5f))
                    for ((x, y) in listOf(0.1f to 0.75f, 0.38f to 0.4f, 0.62f to 0.6f, 0.9f to 0.2f)) drawCircle(tint, line * 1.3f, Offset(w * x, h * y))
                }
                "pie" -> {
                    drawCircle(tint, w * 0.4f, Offset(w / 2f, h / 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(line))
                    drawArc(accent, -90f, 110f, true, Offset(w * 0.1f, h * 0.1f), Size(w * 0.8f, h * 0.8f))
                }
                "bar" -> {
                    for ((i, f) in listOf(0.55f, 0.85f, 0.4f).withIndex()) {
                        drawRect(if (i == 1) accent else tint, Offset(w * 0.12f, h * (0.18f + i * 0.24f)), Size(w * 0.76f * f, h * 0.16f))
                    }
                }
                "area" -> {
                    val p = Path().apply { moveTo(w * 0.1f, h * 0.88f); lineTo(w * 0.1f, h * 0.55f); lineTo(w * 0.4f, h * 0.3f); lineTo(w * 0.65f, h * 0.5f); lineTo(w * 0.9f, h * 0.2f); lineTo(w * 0.9f, h * 0.88f); close() }
                    drawPath(p, accent)
                }
                "other" -> {
                    // a radar's five-spoke web with a series round it
                    fun spoke(k: Int, f: Float): Offset {
                        val a = (-90f + k * 72f) * (Math.PI.toFloat() / 180f)
                        return Offset(w / 2f + kotlin.math.cos(a) * w * 0.42f * f, h * 0.53f + kotlin.math.sin(a) * h * 0.42f * f)
                    }
                    val web = Path().apply { moveTo(spoke(0, 1f).x, spoke(0, 1f).y); for (k in 1..4) lineTo(spoke(k, 1f).x, spoke(k, 1f).y); close() }
                    drawPath(web, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(line))
                    for (k in 0..4) drawLine(tint, Offset(w / 2f, h * 0.53f), spoke(k, 1f), line * 0.7f)
                    val vals = listOf(0.85f, 0.55f, 0.7f, 0.45f, 0.65f)
                    val series = Path().apply { moveTo(spoke(0, vals[0]).x, spoke(0, vals[0]).y); for (k in 1..4) lineTo(spoke(k, vals[k]).x, spoke(k, vals[k]).y); close() }
                    drawPath(series, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(line * 1.4f))
                }
                "maps" -> {
                    // a map folded in three, its middle panel in the accent
                    val xs = listOf(0.1f, 0.37f, 0.63f, 0.9f)
                    val top = listOf(0.22f, 0.12f, 0.22f, 0.12f)
                    drawPath(Path().apply { moveTo(w * xs[1], h * top[1]); lineTo(w * xs[2], h * top[2]); lineTo(w * xs[2], h * (top[2] + 0.66f)); lineTo(w * xs[1], h * (top[1] + 0.66f)); close() }, accent)
                    val edge = Path().apply {
                        moveTo(w * xs[0], h * top[0])
                        for (k in 1..3) lineTo(w * xs[k], h * top[k])
                        for (k in 3 downTo 0) lineTo(w * xs[k], h * (top[k] + 0.66f))
                        close()
                    }
                    drawPath(edge, tint, style = outline)
                }
                "treemap" -> {
                    drawRect(accent, Offset(w * 0.1f, h * 0.15f), Size(w * 0.44f, h * 0.7f))
                    drawRect(tint, Offset(w * 0.58f, h * 0.15f), Size(w * 0.32f, h * 0.33f))
                    drawRect(tint, Offset(w * 0.58f, h * 0.52f), Size(w * 0.14f, h * 0.33f))
                    drawRect(tint, Offset(w * 0.76f, h * 0.52f), Size(w * 0.14f, h * 0.33f))
                }
                "sunburst" -> {
                    drawCircle(accent, w * 0.16f, Offset(w / 2f, h / 2f))
                    // the outer ring in segments with gaps between them
                    val ring = w * 0.31f
                    for ((k, sweep) in listOf(110f, 70f, 95f, 55f).withIndex()) {
                        val start = -90f + listOf(0f, 110f, 180f, 275f)[k]
                        drawArc(if (k == 0) accent else tint, start + 4f, sweep - 8f, false, Offset(w / 2f - ring, h / 2f - ring), Size(ring * 2f, ring * 2f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.13f))
                    }
                }
                "histogram" -> {
                    // bars side by side, no gaps
                    for ((i, f) in listOf(0.3f, 0.6f, 0.9f, 0.55f, 0.25f).withIndex()) {
                        val bh = h * 0.74f * f
                        drawRect(if (i == 2) accent else tint, Offset(w * (0.12f + i * 0.152f), h * 0.86f - bh), Size(w * 0.152f - line * 0.5f, bh))
                    }
                }
                "boxWhisker" -> {
                    for ((i, b) in listOf(floatArrayOf(0.12f, 0.35f, 0.62f, 0.85f, 0.5f), floatArrayOf(0.18f, 0.28f, 0.5f, 0.7f, 0.4f)).withIndex()) {
                        val (lo, q1, q3, hi, mid) = listOf(b[0], b[1], b[2], b[3], b[4]).map { h * (1f - it) }
                        val x = w * (0.32f + i * 0.36f)
                        val c = if (i == 1) accent else tint
                        drawLine(c, Offset(x, lo), Offset(x, hi), line)
                        drawLine(c, Offset(x - w * 0.07f, lo), Offset(x + w * 0.07f, lo), line)
                        drawLine(c, Offset(x - w * 0.07f, hi), Offset(x + w * 0.07f, hi), line)
                        drawRect(io.github.artcraftandroid.wordcraft.ui.WordColors.Surface, Offset(x - w * 0.12f, q3), Size(w * 0.24f, q1 - q3))
                        drawRect(c, Offset(x - w * 0.12f, q3), Size(w * 0.24f, q1 - q3), style = outline)
                        drawLine(c, Offset(x - w * 0.12f, mid), Offset(x + w * 0.12f, mid), line)
                    }
                }
                "waterfall" -> {
                    // a start, a rise, a fall, and the total
                    val bars = listOf(0.86f to 0.5f, 0.5f to 0.25f, 0.25f to 0.42f, 0.86f to 0.42f)
                    for ((i, b) in bars.withIndex()) {
                        val (a, z) = b
                        drawRect(if (i == 1) accent else tint, Offset(w * (0.1f + i * 0.21f), h * minOf(a, z)), Size(w * 0.17f, h * kotlin.math.abs(a - z)))
                    }
                }
                "funnel" -> {
                    for ((i, f) in listOf(0.8f, 0.6f, 0.42f, 0.26f).withIndex()) {
                        drawRect(if (i == 1) accent else tint, Offset(w * (1f - f) / 2f, h * (0.14f + i * 0.19f)), Size(w * f, h * 0.15f))
                    }
                }
                "scatter" -> {
                    drawLine(tint, Offset(w * 0.12f, h * 0.1f), Offset(w * 0.12f, h * 0.88f), line)
                    drawLine(tint, Offset(w * 0.12f, h * 0.88f), Offset(w * 0.92f, h * 0.88f), line)
                    for ((x, y) in listOf(0.3f to 0.6f, 0.45f to 0.4f, 0.6f to 0.55f, 0.75f to 0.25f, 0.85f to 0.45f)) drawCircle(accent, line * 1.4f, Offset(w * x, h * y))
                }
                else -> {
                    for ((i, f) in listOf(0.5f, 0.85f, 0.65f).withIndex()) {
                        val bh = h * 0.72f * f
                        drawRect(if (i == 1) accent else tint, Offset(w * (0.16f + i * 0.25f), h * 0.86f - bh), Size(w * 0.18f, bh))
                    }
                }
            }
        }
        is Glyph.Triangle -> Canvas(modifier.size(size)) {
            val w = this.size.width * 0.42f
            val h = w * 0.55f
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val p = Path().apply {
                if (glyph.up) {
                    moveTo(cx - w / 2f, cy + h / 2f); lineTo(cx + w / 2f, cy + h / 2f); lineTo(cx, cy - h / 2f)
                } else {
                    moveTo(cx - w / 2f, cy - h / 2f); lineTo(cx + w / 2f, cy - h / 2f); lineTo(cx, cy + h / 2f)
                }
                close()
            }
            drawPath(p, tint)
        }
    }
}

/** The formatting toggles' letters. */
object Letters {
    val Bold = Glyph.Letters("B", weight = FontWeight.Bold)
    val Italic = Glyph.Letters("I", italic = true, family = FontFamily.Serif)
    val Underline = Glyph.Letters("U", decoration = TextDecoration.Underline)
    val Strike = Glyph.Letters("ab", decoration = TextDecoration.LineThrough)
    val FontColor = Glyph.Letters("A")
    val ChangeCase = Glyph.Letters("Aa")
}
