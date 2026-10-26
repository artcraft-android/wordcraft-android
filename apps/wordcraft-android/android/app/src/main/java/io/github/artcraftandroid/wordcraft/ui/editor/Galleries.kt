package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.core.StyleLook
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.LucideIcons
import io.github.artcraftandroid.wordcraft.ui.WordColors
import java.util.Locale

// Styles.

/** A style's 40 dp row: its name drawn as the style draws text (kind of font, size, weight, colour, indent). */
@Composable
internal fun StyleRow(l: StyleLook, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(40.dp).background(if (selected) WordColors.Pressed else WordColors.Surface).clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            l.name,
            color = l.color?.let(::styleColor) ?: WordColors.OnSurface,
            fontSize = (l.size * 1.6f).coerceIn(10f, 26f).sp,
            fontFamily = fontKind(l.font),
            fontWeight = if (l.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (l.italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (l.underline) TextDecoration.Underline else null,
            style = TextStyle(fontFeatureSettings = if (l.smallCaps) "smcp" else null),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(start = 9.dp + (l.indent * 1.1f).dp),
        )
    }
}

/** The generic kind of a font, by its name: what the phone has to draw a preview in. */
private fun fontKind(name: String): FontFamily {
    val n = name.lowercase(Locale.ROOT)
    return when {
        listOf("mono", "courier", "consolas").any { it in n } -> FontFamily.Monospace
        "sans" !in n && listOf("times", "cambria", "georgia", "garamond", "serif", "caladea", "tinos", "book", "palatino", "baskerville").any { it in n } -> FontFamily.Serif
        else -> FontFamily.SansSerif
    }
}

/** A style's colour as the list shows it: dark colours lifted on a dark theme, as the pages are. */
private fun styleColor(hex: String): Color {
    val c = runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrDefault(Color.Unspecified)
    return if (WordColors.dark && c.luminance() < 0.3f) lerp(c, Color.White, 0.55f) else c
}

// Bullets and numbering.

/** A list gallery's tile: a preview, framed when it is the current choice. */
@Composable
internal fun ListTile(w: Dp, h: Dp, selected: Boolean, onClick: () -> Unit, label: String? = null, content: @Composable () -> Unit) {
    Box(
        Modifier.size(w, h).border(if (selected) 4.dp else 1.dp, if (selected) WordColors.OnStrip.copy(alpha = 0.45f) else WordColors.Pressed)
            .background(WordColors.Surface).clickable(onClick = onClick).semantics(mergeDescendants = label == null) {
                if (label != null) contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A tile's label: None, or a bullet. */
@Composable
internal fun TileText(text: String, size: TextUnit) {
    Text(text, color = WordColors.OnSurface, fontSize = size, textAlign = TextAlign.Center)
}

/** A numbered list in small: three numbers, each with a line, the numbers right-aligned in their column. */
@Composable
internal fun NumberPreview(labels: List<String>) {
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (l in labels) {
            Row(Modifier.height(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l, color = WordColors.OnSurface, fontSize = 6.sp, lineHeight = 6.sp, textAlign = TextAlign.End, modifier = Modifier.width(12.dp))
                Spacer(Modifier.width(2.dp))
                Box(Modifier.weight(1f).height(1.dp).background(WordColors.OnSurface.copy(alpha = 0.6f)))
            }
        }
    }
}

// Margins and paper sizes.

/** A margin preset's row: a page with its margins marked, the name, then top and bottom, left and right. */
@Composable
internal fun MarginRow(name: String, m: List<Float>, mirrored: Boolean, selected: Boolean, length: (Float) -> String, onClick: () -> Unit) {
    PresetRow(selected, onClick, icon = { MarginIcon(m, mirrored) }) {
        Text(name, color = WordColors.OnSurface, fontSize = 14.sp, lineHeight = 18.sp)
        val (l, r) = if (mirrored) "Inside" to "Outside" else "Left" to "Right"
        TwoColumns("Top: ${length(m[0])}", "Bottom: ${length(m[1])}")
        TwoColumns("$l: ${length(m[2])}", "$r: ${length(m[3])}")
    }
}

/** A paper size's row: the page in proportion, the name, then its size. */
@Composable
internal fun PaperRow(name: String, w: Float, h: Float, selected: Boolean, size: String, onClick: () -> Unit) {
    PresetRow(selected, onClick, icon = { PaperIcon(w, h) }) {
        Text(name, color = WordColors.OnSurface, fontSize = 14.sp, lineHeight = 18.sp)
        Text(size, color = WordColors.OnSurface, fontSize = 14.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun PresetRow(selected: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit, text: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).background(if (selected) WordColors.Pressed else WordColors.Surface).bottomEdge(WordColors.Divider).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(74.dp), contentAlignment = Alignment.Center) { icon() }
        Column { text() }
    }
}

@Composable
private fun TwoColumns(a: String, b: String) {
    Row {
        Text(a, color = WordColors.OnSurface, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.width(219.dp))
        Text(b, color = WordColors.OnSurface, fontSize = 14.sp, lineHeight = 18.sp)
    }
}

/** A letter page with blue guides where its margins are (two facing pages for mirrored ones). */
@Composable
private fun MarginIcon(m: List<Float>, mirrored: Boolean) {
    val line = WordColors.OnStrip
    val guide = WordColors.AccentLight
    Canvas(Modifier.size(if (mirrored) 46.dp else 32.dp, 43.dp)) {
        val pages = if (mirrored) 2 else 1
        val pw = size.width / pages
        val s = pw / 612f
        val ph = 792f * s
        val top = (size.height - ph) / 2f
        for (i in 0 until pages) {
            val x0 = i * pw
            // a mirrored pair: the inside margin by the spine
            val (left, right) = if (mirrored && i == 0) m[3] to m[2] else m[2] to m[3]
            drawRect(line, Offset(x0, top), Size(pw, ph), style = Stroke(1.dp.toPx()))
            val w = 1.dp.toPx()
            drawLine(guide, Offset(x0 + left * s, top), Offset(x0 + left * s, top + ph), w)
            drawLine(guide, Offset(x0 + pw - right * s, top), Offset(x0 + pw - right * s, top + ph), w)
            drawLine(guide, Offset(x0, top + m[0] * s), Offset(x0 + pw, top + m[0] * s), w)
            drawLine(guide, Offset(x0, top + ph - m[1] * s), Offset(x0 + pw, top + ph - m[1] * s), w)
        }
    }
}

/** A page outline in its paper's proportions, bigger for bigger paper. */
@Composable
private fun PaperIcon(w: Float, h: Float) {
    val line = WordColors.OnStrip
    val hDp = minOf(43f, h / 72f * 3f)
    val wDp = hDp * w / h
    Canvas(Modifier.size(wDp.dp, hDp.dp)) {
        drawRect(line, style = Stroke(1.dp.toPx()))
    }
}

// Picture shadows.

/** The outer shadows, as the gallery lays them out: three rows of three, by direction. */
private val OUTER_SHADOWS = listOf(
    "offsetBottomRight", "offsetBottom", "offsetBottomLeft",
    "offsetRight", "offsetCenter", "offsetLeft",
    "offsetTopRight", "offsetTop", "offsetTopLeft",
)

/** The inner shadows: along the named edges, three to a row; then the perspective ones. */
private val INNER_SHADOWS = listOf(
    "insideTopLeft", "insideTop", "insideTopRight",
    "insideLeft", "insideCenter", "insideRight",
    "insideBottomLeft", "insideBottom", "insideBottomRight",
)
private val PERSPECTIVE_SHADOWS = listOf("perspectiveUpperLeft", "perspectiveUpperRight", "perspectiveBelow", "perspectiveLowerLeft", "perspectiveLowerRight")

/**
 * Shadow: No Shadow, then the outer, inner and perspective shadows, each shown on a small
 * picture. A picture has one shadow: choosing one kind takes the other away.
 */
@Composable
internal fun ShadowGallery(vm: AppViewModel) {
    fun apply(outer: String, inner: String) = vm.command("shape.effects", org.json.JSONObject().put("shadow", outer).put("innerShadow", inner).toString())
    Scrolling {
        SectionBand("No Shadow")
        ShadowRow(listOf(null)) { apply("none", "none") }
        SectionBand("Outer")
        for (row in OUTER_SHADOWS.chunked(3)) ShadowRow(row) { apply(it, "none") }
        SectionBand("Inner")
        for (row in INNER_SHADOWS.chunked(3)) ShadowRow(row) { apply("none", it) }
        SectionBand("Perspective")
        for (row in PERSPECTIVE_SHADOWS.chunked(3)) ShadowRow(row) { apply(it, "none") }
    }
}

@Composable
private fun ShadowRow(presets: List<String?>, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(61.dp)) {
        for (p in presets) {
            Box(Modifier.weight(1f).height(61.dp).clickable { onPick(p ?: "none") }, contentAlignment = Alignment.Center) {
                ShadowThumb(p)
            }
        }
        repeat(3 - presets.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** A small landscape picture with its shadow cast the preset's way, inside it, or flattened behind or below it. */
@Composable
private fun ShadowThumb(preset: String?) {
    // a dark shadow can't be seen on a dark theme: there it is a light one
    val tone = if (WordColors.dark) Color.White else Color.Black
    Canvas(Modifier.size(34.dp, 28.dp)) {
        val w = 24.dp.toPx()
        val h = 18.dp.toPx()
        val x = (size.width - w) / 2f
        val y = (size.height - h) / 2f
        val d = 2.5.dp.toPx()
        if (preset != null && preset.startsWith("offset")) {
            val dx = when { preset.endsWith("Right") -> d; preset.endsWith("Left") -> -d; else -> 0f }
            val dy = when { preset.startsWith("offsetBottom") -> d; preset.startsWith("offsetTop") -> -d; else -> 0f }
            // a soft edge: three widening bands, fainter outward
            for ((grow, a) in listOf(2.dp.toPx() to 0.12f, 1.dp.toPx() to 0.2f, 0f to 0.3f)) {
                drawRect(tone.copy(alpha = a), Offset(x + dx - grow, y + dy - grow), Size(w + 2 * grow, h + 2 * grow))
            }
        }
        if (preset != null && preset.startsWith("perspective")) {
            // the picture's outline flattened from its bottom edge, leaning, or turned down below it
            val bottom = y + h
            val lean = when { preset.endsWith("Left") -> if (preset.contains("Upper")) -1f else 1f; preset.endsWith("Right") -> if (preset.contains("Upper")) 1f else -1f; else -> 0f }
            val down = !preset.contains("Upper")
            val height = if (preset.endsWith("Below")) h * 0.25f else h * 0.4f
            val top = if (down) bottom + height else bottom - height
            val shift = lean * height
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(x, bottom)
                lineTo(x + w, bottom)
                lineTo(x + w + shift, top)
                lineTo(x + shift, top)
                close()
            }
            drawPath(path, tone.copy(alpha = 0.3f))
        }
        // sky over a hill
        drawRect(Color(0xFF7FB3E6), Offset(x, y), Size(w, h * 0.6f))
        drawRect(Color(0xFF5EA35A), Offset(x, y + h * 0.6f), Size(w, h * 0.4f))
        if (preset != null && preset.startsWith("inside")) {
            // dark along the named edges, inside the picture
            val e = 3.dp.toPx()
            val shade = Color.Black.copy(alpha = 0.35f)
            val all = preset == "insideCenter"
            if (all || preset.contains("Top")) drawRect(shade, Offset(x, y), Size(w, e))
            if (all || preset.contains("Bottom")) drawRect(shade, Offset(x, y + h - e), Size(w, e))
            if (all || preset.endsWith("Left")) drawRect(shade, Offset(x, y), Size(e, h))
            if (all || preset.endsWith("Right")) drawRect(shade, Offset(x + w - e, y), Size(e, h))
        }
    }
}

/** The reflections across (tight, half, full) and down (touching, 4 pt and 8 pt away). */
private val REFLECTIONS = listOf("tightTouching", "halfTouching", "fullTouching", "tight4pt", "half4pt", "full4pt", "tight8pt", "half8pt", "full8pt")

/** Reflection: No Reflection, then the reflections, each shown on a small picture. */
@Composable
internal fun ReflectionGallery(vm: AppViewModel) {
    Scrolling {
        SectionBand("No Reflection")
        ReflectionRow(listOf(null)) { vm.command("shape.effects", json("reflection" to "none")) }
        SectionBand("Reflection Variations")
        for (row in REFLECTIONS.chunked(3)) ReflectionRow(row) { vm.command("shape.effects", json("reflection" to it)) }
    }
}

/** The Picture Styles thumbnails' row height: each style drawn on a sample picture by the engine. */
private val PICTURE_STYLE_ROW = 64.dp

/** Styles (Picture): every picture style drawn on a sample picture, three to a row. */
@Composable
internal fun PictureStyleGallery(vm: AppViewModel) {
    // the engine's thumbnail is 90 x 72 points: as wide as it is shown in a row's height
    val widthPx = with(LocalDensity.current) { (PICTURE_STYLE_ROW * (90f / 72f)).roundToPx() }
    var pictures by remember { mutableStateOf<List<Pair<String, ImageBitmap>>>(emptyList()) }
    LaunchedEffect(Unit) { vm.pictureStyleGallery(widthPx) { list -> pictures = list.map { (id, bmp) -> id to bmp.asImageBitmap() } } }
    Scrolling {
        Spacer(Modifier.height(10.dp))
        for (row in pictures.chunked(3)) {
            Row(Modifier.fillMaxWidth().height(PICTURE_STYLE_ROW)) {
                for ((id, bmp) in row) {
                    Box(Modifier.weight(1f).height(PICTURE_STYLE_ROW).clickable { vm.command("picture.style", json("style" to id)) }, contentAlignment = Alignment.Center) {
                        Image(bmp, contentDescription = styleName(id), modifier = Modifier.height(PICTURE_STYLE_ROW))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** A style id in words: "simpleFrameWhite" is "Simple Frame White". */
private fun styleName(id: String): String =
    id.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.titlecase(Locale.ROOT) }

@Composable
private fun ReflectionRow(presets: List<String?>, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(70.dp)) {
        for (p in presets) {
            Box(Modifier.weight(1f).height(70.dp).clickable { onPick(p ?: "none") }, contentAlignment = Alignment.Center) {
                ReflectionThumb(p)
            }
        }
        repeat(3 - presets.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** A small landscape picture with its reflection the preset's size and distance below it, fading out. */
@Composable
private fun ReflectionThumb(preset: String?) {
    Canvas(Modifier.size(34.dp, 44.dp)) {
        val w = 24.dp.toPx()
        val h = 18.dp.toPx()
        val x = (size.width - w) / 2f
        val y = 3.dp.toPx()
        drawRect(Color(0xFF7FB3E6), Offset(x, y), Size(w, h * 0.6f))
        drawRect(Color(0xFF5EA35A), Offset(x, y + h * 0.6f), Size(w, h * 0.4f))
        if (preset == null) return@Canvas
        val part = when { preset.startsWith("tight") -> 0.35f; preset.startsWith("half") -> 0.55f; else -> 0.9f }
        val gap = when { preset.endsWith("4pt") -> 1.5.dp.toPx(); preset.endsWith("8pt") -> 3.dp.toPx(); else -> 0f }
        // the mirrored hill, then the sky, fading downward in strips
        val top = y + h + gap
        val len = h * part
        val strips = 6
        for (k in 0 until strips) {
            val a0 = k / strips.toFloat()
            val y0 = top + len * a0
            val alpha = 0.5f * (1f - (k + 0.5f) / strips)
            val color = if (len * a0 < h * 0.4f) Color(0xFF5EA35A) else Color(0xFF7FB3E6)
            drawRect(color.copy(alpha = alpha), Offset(x, y0), Size(w, len / strips + 0.5f))
        }
    }
}

// Size.

/** Size: the selected object's height and width, each typed in inches. */
@Composable
internal fun ObjectSizeFields(vm: AppViewModel) {
    val frame = vm.selection?.objectFrame ?: return
    // a picture keeps its proportions, as it does when dragged by a corner
    val lock = vm.format?.`object` == "picture"
    DisposableEffect(Unit) { onDispose { vm.paletteTyping = false } }
    Scrolling {
        SizeField("Height", frame.height, { vm.paletteTyping = it }) { vm.command("picture.size", org.json.JSONObject().put("height", it).put("lockAspect", lock).toString()) }
        SizeField("Width", frame.width, { vm.paletteTyping = it }) { vm.command("picture.size", org.json.JSONObject().put("width", it).put("lockAspect", lock).toString()) }
    }
}

/**
 * Picture Position (the whole picture's size and how far its left and top lie before the part
 * showing) or Crop Position (the part showing: its size and where it starts on the picture), in
 * inches. Each change crops the picture again (`picture.crop`, at most 45% off a side).
 */
@Composable
internal fun CropFields(vm: AppViewModel, picture: Boolean) {
    val frame = vm.selection?.objectFrame ?: return
    var crop by remember { mutableStateOf<List<Float>?>(null) }
    LaunchedEffect(frame) { vm.pictureCrop { crop = it } }
    DisposableEffect(Unit) { onDispose { vm.paletteTyping = false } }
    val c = crop?.takeIf { it.size >= 4 } ?: return
    // the whole picture on the page, and where the part showing starts on it (points)
    val fullW = frame.width / (1f - c[0] - c[2]).coerceAtLeast(0.1f)
    val fullH = frame.height / (1f - c[1] - c[3]).coerceAtLeast(0.1f)
    val left = c[0] * fullW
    val top = c[1] * fullH
    // the picture `w` x `h` with the part showing `bw` x `bh` from (`l`, `t`) on it
    fun crop(w: Float, h: Float, l: Float, t: Float, bw: Float, bh: Float) {
        fun side(v: Float) = v.coerceIn(0f, 0.45f)
        val cl = side(l / w)
        val cr = side(1f - cl - bw / w)
        val ct = side(t / h)
        val cb = side(1f - ct - bh / h)
        vm.command(
            "picture.crop",
            org.json.JSONObject().put("left", cl.toDouble()).put("top", ct.toDouble()).put("right", cr.toDouble()).put("bottom", cb.toDouble())
                .put("width", (w * (1f - cl - cr)).toDouble()).put("height", (h * (1f - ct - cb)).toDouble()).toString(),
        )
    }
    val typing = { on: Boolean -> vm.paletteTyping = on }
    Scrolling {
        if (picture) {
            SizeField("Width", fullW, typing) { crop(it, fullH, left, top, frame.width, frame.height) }
            SizeField("Height", fullH, typing) { crop(fullW, it, left, top, frame.width, frame.height) }
            SizeField("Offset X", -left, typing, -22f..22f) { crop(fullW, fullH, -it, top, frame.width, frame.height) }
            SizeField("Offset Y", -top, typing, -22f..22f) { crop(fullW, fullH, left, -it, frame.width, frame.height) }
        } else {
            SizeField("Width", frame.width, typing) { crop(fullW, fullH, left, top, it, frame.height) }
            SizeField("Height", frame.height, typing) { crop(fullW, fullH, left, top, frame.width, it) }
            SizeField("Left", left, typing, 0f..22f) { crop(fullW, fullH, it, top, frame.width, frame.height) }
            SizeField("Top", top, typing, 0f..22f) { crop(fullW, fullH, left, it, frame.width, frame.height) }
        }
    }
}

/** A length typed in inches (`1.8 in`) within [range], applied when the keyboard's Done is pressed; the X clears it. */
@Composable
private fun SizeField(label: String, points: Float, onFocus: (Boolean) -> Unit, range: ClosedFloatingPointRange<Float> = 0.01f..22f, onSet: (Float) -> Unit) {
    fun shown(pt: Float) = String.format(Locale.US, "%.2f", pt / 72f).trimEnd('0').trimEnd('.') + " in"
    var text by remember(points) { mutableStateOf(shown(points)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, color = WordColors.OnSurface, fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
        Row(Modifier.fillMaxWidth().height(32.dp).border(1.dp, WordColors.CheckEdge).background(WordColors.FindField), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = WordColors.OnSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(WordColors.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    text.replace("in", "").replace("\"", "").trim().toFloatOrNull()?.takeIf { it in range }?.let { onSet(it * 72f) }
                }),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).onFocusChanged { onFocus(it.isFocused) },
            )
            BarButton(LucideIcons.X.glyph(), WordColors.OnStrip, { text = "" }, width = 36.dp, height = 32.dp, iconSize = 18.dp)
        }
    }
}
