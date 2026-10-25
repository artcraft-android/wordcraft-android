package io.github.artcraftandroid.wordcraft.ui.editor

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.core.DocumentInfo
import io.github.artcraftandroid.wordcraft.core.PageSize
import io.github.artcraftandroid.wordcraft.core.SelectionView
import io.github.artcraftandroid.wordcraft.engine.Engine
import io.github.artcraftandroid.wordcraft.engine.toBitmap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Pages draw in square tiles this many pixels a side: only the tiles that show are drawn. */
private const val TILE_PX = 1024

/** How far out and in the pages zoom, relative to fitting the page width. */
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 5f

/**
 * Where the pages sit on screen. The document is laid out in points: the pages stacked down the
 * middle of a column as wide as the widest page plus [pad] each side, [gap] apart. The screen
 * shows it at [scale] pixels per point, scrolled by ([scrollX], [scrollY]) points, centred
 * across when it is narrower than the view. Mobile View has no padding or gaps: its one page is
 * the screen's width. [padTop] is the room above the first page (less than none hides some of
 * its top margin).
 */
@Stable
class DocumentViewport(private val pad: Float = 4f, private val gap: Float = 5.5f, private val padTop: Float = pad) {
    var scale by mutableFloatStateOf(0f)
        private set
    var scrollX by mutableFloatStateOf(0f)
        private set
    var scrollY by mutableFloatStateOf(0f)
        private set
    /** Two fingers are zooming: the tiles wait for the zoom to settle. */
    var zooming by mutableStateOf(false)

    /** A finger is on the pages or a fling is running: the caret doesn't pull the view back. */
    var gesturing by mutableStateOf(false)

    var viewW by mutableFloatStateOf(0f)
        private set
    var viewH by mutableFloatStateOf(0f)
        private set
    private var pages: List<PageSize> = emptyList()
    private var tops = FloatArray(0)
    private var contentW = 0f
    private var contentH = 0f

    val fitScale: Float get() = if (contentW > 0f && viewW > 0f) viewW / contentW else 1f

    fun setPages(list: List<PageSize>) {
        val wasFit = scale <= 0f || abs(scale - fitScale) < 0.001f
        pages = list
        val maxW = list.maxOfOrNull { it.width + it.markup } ?: 612f
        contentW = maxW + 2 * pad
        tops = FloatArray(list.size)
        var y = padTop
        list.forEachIndexed { i, p ->
            tops[i] = y
            y += p.height + gap
        }
        contentH = y - gap + pad
        if (wasFit && viewW > 0f) scale = fitScale
        clamp()
    }

    fun setView(w: Float, h: Float) {
        val wasFit = scale <= 0f || abs(scale - fitScale) < 0.001f
        viewW = w
        viewH = h
        if (wasFit && contentW > 0f) scale = fitScale
        clamp()
    }

    private fun shiftX(): Float = max(0f, (viewW - contentW * scale) / 2f)

    fun pageLeft(i: Int): Float {
        val maxW = contentW - 2 * pad
        val p = pages.getOrNull(i) ?: return pad
        return pad + (maxW - (p.width + p.markup)) / 2f
    }

    fun pageTop(i: Int): Float = tops.getOrNull(i) ?: padTop

    fun toScreen(xPt: Float, yPt: Float): Offset = Offset((xPt - scrollX) * scale + shiftX(), (yPt - scrollY) * scale)

    /** Page [i]'s rectangle on screen. */
    fun pageRect(i: Int): Rect {
        val p = pages.getOrNull(i) ?: return Rect.Zero
        val tl = toScreen(pageLeft(i), pageTop(i))
        return Rect(tl, Size(p.width * scale, p.height * scale))
    }

    /** A point on page [i] (points) on screen. */
    fun onPage(i: Int, x: Float, y: Float): Offset = toScreen(pageLeft(i) + x, pageTop(i) + y)

    /** The pages any part of which shows. */
    fun visiblePages(): List<Int> {
        if (scale <= 0f) return emptyList()
        val top = scrollY
        val bottom = scrollY + viewH / scale
        return pages.indices.filter { i -> pageTop(i) < bottom && pageTop(i) + pages[i].height > top }
    }

    /** The part of page [i] that shows, in its own points: (x0, y0, x1, y1), or null. */
    fun visiblePart(i: Int): FloatArray? {
        val p = pages.getOrNull(i) ?: return null
        if (scale <= 0f) return null
        val left = (0f - shiftX()) / scale + scrollX - pageLeft(i)
        val right = (viewW - shiftX()) / scale + scrollX - pageLeft(i)
        val top = scrollY - pageTop(i)
        val bottom = scrollY + viewH / scale - pageTop(i)
        val x0 = left.coerceIn(0f, p.width)
        val x1 = right.coerceIn(0f, p.width)
        val y0 = top.coerceIn(0f, p.height)
        val y1 = bottom.coerceIn(0f, p.height)
        return if (x1 > x0 && y1 > y0) floatArrayOf(x0, y0, x1, y1) else null
    }

    /** A screen point on page [i]'s own points, not clamped to the page (a stroke may run off it). */
    fun toPage(i: Int, at: Offset): Pair<Float, Float> =
        ((at.x - shiftX()) / scale + scrollX - pageLeft(i)) to (at.y / scale + scrollY - pageTop(i))

    /** The page under a screen point and the point on it in points (the nearest page between pages). */
    fun hit(at: Offset): Triple<Int, Float, Float>? {
        if (pages.isEmpty() || scale <= 0f) return null
        val xPt = (at.x - shiftX()) / scale + scrollX
        val yPt = at.y / scale + scrollY
        var best = 0
        var bestDist = Float.MAX_VALUE
        pages.indices.forEach { i ->
            val t = pageTop(i)
            val b = t + pages[i].height
            val d = if (yPt < t) t - yPt else if (yPt > b) yPt - b else 0f
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        val p = pages[best]
        val x = (xPt - pageLeft(best)).coerceIn(0f, p.width)
        val y = (yPt - pageTop(best)).coerceIn(0f, p.height)
        return Triple(best, x, y)
    }

    fun panBy(dx: Float, dy: Float) {
        if (scale <= 0f) return
        scrollX -= dx / scale
        scrollY -= dy / scale
        clamp()
    }

    /** Zoom by [factor] keeping the document point under [focus] where it is. */
    fun zoomBy(factor: Float, focus: Offset) {
        if (scale <= 0f || !factor.isFinite() || factor <= 0f) return
        val docX = (focus.x - shiftX()) / scale + scrollX
        val docY = focus.y / scale + scrollY
        scale = (scale * factor).coerceIn(fitScale * MIN_ZOOM, fitScale * MAX_ZOOM)
        scrollX = docX - (focus.x - shiftX()) / scale
        scrollY = docY - focus.y / scale
        clamp()
    }

    /** How far down the document the view is, 0 to 1 (the scrubber's place). */
    val scrollFraction: Float get() {
        if (scale <= 0f) return 0f
        val range = contentH - viewH / scale
        return if (range > 0f) (scrollY / range).coerceIn(0f, 1f) else 0f
    }

    /** Scroll to [f] (0 to 1) of the way down: dragging the scrubber. */
    fun scrollToFraction(f: Float) {
        if (scale <= 0f || !f.isFinite()) return
        scrollY = f.coerceIn(0f, 1f) * max(0f, contentH - viewH / scale)
        clamp()
    }

    /** Zoom to [s] pixels per point about the middle of the view. */
    fun zoomTo(s: Float) {
        if (scale > 0f && s.isFinite() && s > 0f) zoomBy(s / scale, Offset(viewW / 2f, viewH / 2f))
    }

    /** The page width fills the view (Page Width). */
    fun fitWidth() = zoomTo(fitScale)

    /** Zoom so a [w] × [h] point area at ([x], [y]) of page [i] fills the view's width (Zoom In on a chart), centred on it. */
    fun fitArea(i: Int, x: Float, y: Float, w: Float, h: Float) {
        if (scale <= 0f || i !in pages.indices || w <= 0f || h <= 0f) return
        zoomTo(min(viewW * 0.94f / w, viewH * 0.8f / h))
        scrollX = pageLeft(i) + x + w / 2f - (viewW / 2f - shiftX()) / scale
        scrollY = pageTop(i) + y + h / 2f - viewH / 2f / scale
        clamp()
    }

    /** Page [i] fits whole in the view, its top at the top (One Page). */
    fun fitPage(i: Int) {
        val p = pages.getOrNull(i) ?: return
        zoomTo(min(viewW / (p.width + 2 * pad), viewH / (p.height + 2 * pad)))
        scrollY = pageTop(i) - pad
        clamp()
    }

    /** Scroll so that a point on page [i] (points), [height] tall, shows with some room around it. */
    /** Scroll so that point ([x], [y]) of page [i] is [fromTop] pixels below the view's top (and in view sideways). */
    fun placeAt(i: Int, x: Float, y: Float, fromTop: Float) {
        if (scale <= 0f || i !in pages.indices) return
        scrollY = pageTop(i) + y - fromTop / scale
        val docX = pageLeft(i) + x
        val visibleW = viewW / scale
        if (contentW * scale > viewW && (docX < scrollX || docX > scrollX + visibleW)) scrollX = docX - visibleW / 2f
        clamp()
    }

    fun reveal(i: Int, x: Float, y: Float, height: Float, margin: Float, bottomMargin: Float = margin) {
        if (scale <= 0f || i !in pages.indices) return
        val docY = pageTop(i) + y
        val docX = pageLeft(i) + x
        val m = margin / scale
        val visibleH = viewH / scale
        if (docY - m < scrollY) scrollY = docY - m
        val mb = bottomMargin / scale
        if (docY + height + mb > scrollY + visibleH) scrollY = docY + height + mb - visibleH
        val visibleW = viewW / scale
        if (contentW * scale > viewW) {
            if (docX - m < scrollX) scrollX = docX - m
            if (docX + m > scrollX + visibleW) scrollX = docX + m - visibleW
        }
        clamp()
    }

    private fun clamp() {
        if (scale <= 0f) return
        scrollX = scrollX.coerceIn(0f, max(0f, contentW - viewW / scale))
        scrollY = scrollY.coerceIn(0f, max(0f, contentH - viewH / scale))
    }
}

/** How a finger draws: `pen`, `highlighter` or `eraser`, the colour, the width in points. */
data class InkSettings(val tool: String, val color: Color, val width: Float)

/** Which tile: page, column and row of the grid at a scale. */
private data class TileKey(val page: Int, val tx: Int, val ty: Int, val scale: Float)

/**
 * A tile's picture, the area of its page it shows (points), and the revision and page size it was
 * drawn for (Mobile View lays the text out again at a new width without a new revision).
 */
private class Tile(val bitmap: Bitmap, val x: Float, val y: Float, val w: Float, val h: Float, val revision: ULong, val pageW: Float, val pageH: Float)

/**
 * The document's pages: scrolled and pinch-zoomed by touch, drawn by the engine in tiles, with the
 * caret, the selection and the keyboard's composing text over them.
 */
@Composable
fun DocumentView(
    engine: Engine,
    info: DocumentInfo,
    selection: SelectionView?,
    composing: String,
    viewport: DocumentViewport,
    mobile: Boolean,
    selectionColor: Color,
    caretColor: Color,
    showCaret: Boolean,
    onTap: (page: Int, x: Float, y: Float) -> Unit,
    onDoubleTap: (page: Int, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    ink: InkSettings? = null,
    onStroke: (page: Int, points: List<Pair<Float, Float>>) -> Unit = { _, _ -> },
    onErase: (page: Int, x: Float, y: Float) -> Unit = { _, _, _ -> },
    handleColor: Color = Color.Unspecified,
    onHandleDrag: (end: String, page: Int, x: Float, y: Float) -> Unit = { _, _, _, _ -> },
    highlights: List<io.github.artcraftandroid.wordcraft.core.PageRect> = emptyList(),
    wordHighlights: List<io.github.artcraftandroid.wordcraft.core.PageRect> = emptyList(),
    highlightColor: Color = Color(0x4D4C6EF5),
    dark: Boolean = false,
    findMatches: List<io.github.artcraftandroid.wordcraft.core.PageRect> = emptyList(),
    findCurrent: List<io.github.artcraftandroid.wordcraft.core.PageRect> = emptyList(),
    objectHandles: Boolean = false,
    objectMovable: Boolean = false,
    keepRatio: Boolean = false,
    cropping: Boolean = false,
    onObjectEdit: (ObjectEdit) -> Unit = {},
) {
    val selectionNow by androidx.compose.runtime.rememberUpdatedState(selection)
    val objectHandlesNow by androidx.compose.runtime.rememberUpdatedState(objectHandles)
    val objectMovableNow by androidx.compose.runtime.rememberUpdatedState(objectMovable)
    val keepRatioNow by androidx.compose.runtime.rememberUpdatedState(keepRatio)
    val croppingNow by androidx.compose.runtime.rememberUpdatedState(cropping)
    val objectEditNow by androidx.compose.runtime.rememberUpdatedState(onObjectEdit)
    // the frame being dragged (screen pixels) and its angle, until the engine has the change
    var dragPreview by remember { mutableStateOf<Pair<Rect, Float>?>(null) }
    // where a selection end is being dragged to: the text there shows enlarged above the finger
    var magnifyAt by remember { mutableStateOf<Offset?>(null) }
    val handleDragNow by androidx.compose.runtime.rememberUpdatedState(onHandleDrag)
    val handlesOn = handleColor != Color.Unspecified
    // the stroke being drawn, in screen pixels, until the engine has it
    val live = remember { androidx.compose.runtime.mutableStateListOf<Offset>() }
    val inkNow by androidx.compose.runtime.rememberUpdatedState(ink)
    val strokeNow by androidx.compose.runtime.rememberUpdatedState(onStroke)
    val eraseNow by androidx.compose.runtime.rememberUpdatedState(onErase)
    // dark pages are drawn by the engine: a change of theme draws every tile again
    val tiles = remember(info.documentId, mobile, dark) { mutableStateMapOf<TileKey, Tile>() }
    LaunchedEffect(info.pages) { viewport.setPages(info.pages) }

    // The tiles that show, the caret's page first: this list changes only when a tile comes into
    // or goes out of view, or the zoom settles (while two fingers zoom, the tiles at the old scale
    // are stretched instead).
    val needed by remember(info) {
        derivedStateOf {
            val scale = viewport.scale
            if (viewport.zooming || scale <= 0f) return@derivedStateOf null
            val caretPage = selection?.focus?.page?.toInt()
            val keys = mutableListOf<TileKey>()
            for (i in viewport.visiblePages().sortedBy { if (it == caretPage) -1 else it }) {
                val p = info.pages.getOrNull(i) ?: continue
                val v = viewport.visiblePart(i) ?: continue
                val cols = ceil(p.width * scale / TILE_PX).toInt().coerceAtLeast(1)
                val rows = ceil(p.height * scale / TILE_PX).toInt().coerceAtLeast(1)
                val tx0 = floor(v[0] * scale / TILE_PX).toInt().coerceIn(0, cols - 1)
                val tx1 = floor((v[2] * scale - 1f) / TILE_PX).toInt().coerceIn(0, cols - 1)
                val ty0 = floor(v[1] * scale / TILE_PX).toInt().coerceIn(0, rows - 1)
                val ty1 = floor((v[3] * scale - 1f) / TILE_PX).toInt().coerceIn(0, rows - 1)
                for (ty in ty0..ty1) for (tx in tx0..tx1) keys += TileKey(i, tx, ty, scale)
            }
            keys
        }
    }
    LaunchedEffect(needed, info.revision, info.pages, dark) {
        val keys = needed ?: return@LaunchedEffect
        val scale = keys.firstOrNull()?.scale ?: return@LaunchedEffect
        for (key in keys) {
            val p = info.pages.getOrNull(key.page) ?: continue
            val have = tiles[key]
            if (have != null && have.revision == info.revision && have.pageW == p.width && have.pageH == p.height) continue
            val x = key.tx * TILE_PX / scale
            val y = key.ty * TILE_PX / scale
            val w = min(TILE_PX / scale, p.width - x)
            val h = min(TILE_PX / scale, p.height - y)
            if (w <= 0f || h <= 0f) continue
            val img = runCatching { engine.render { renderArea(key.page.toUInt(), x, y, w, h, scale) } }.getOrNull() ?: continue
            val bmp = img.toBitmap() ?: continue
            tiles[key] = Tile(bmp, x, y, w, h, info.revision, p.width, p.height)
        }
        // the visible area is covered at this scale: let the other scales go, and keep this
        // scale's tiles one row and column around the view for scrolling back
        tiles.keys.filter { k ->
            k.scale != scale || keys.none { n -> n.page == k.page && abs(n.tx - k.tx) <= 1 && abs(n.ty - k.ty) <= 1 }
        }.forEach { tiles.remove(it) }
    }

    val blink = rememberInfiniteTransition(label = "caret")
    val caretAlpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = InfiniteRepeatableSpec(
            keyframes {
                durationMillis = 1060
                1f at 0
                1f at 529
                0f at 530
                0f at 1059
            },
            RepeatMode.Restart,
        ),
        label = "caretAlpha",
    )
    val measurer = rememberTextMeasurer()
    // the pages are what the engine draws: dark, or light (also in a dark theme, Switch to Light Background)
    val pageColor = if (dark) io.github.artcraftandroid.wordcraft.ui.WordColors.DarkPage else Color.White
    val canvasColor = if (mobile) pageColor else io.github.artcraftandroid.wordcraft.ui.WordColors.Canvas
    val borderColor = io.github.artcraftandroid.wordcraft.ui.WordColors.PageBorder

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { viewport.setView(it.width.toFloat(), it.height.toFloat()) }
            .magnifier(
                sourceCenter = { magnifyAt ?: Offset.Unspecified },
                magnifierCenter = { magnifyAt?.let { it - Offset(0f, MAGNIFIER_RISE_DP.dp.toPx()) } ?: Offset.Unspecified },
                zoom = 1.25f,
                size = androidx.compose.ui.unit.DpSize(MAGNIFIER_WIDTH_DP.dp, MAGNIFIER_HEIGHT_DP.dp),
                cornerRadius = (MAGNIFIER_HEIGHT_DP / 2).dp,
            )
            .pointerInput(viewport, handlesOn) {
                val r = HANDLE_RADIUS_DP * density
                panAndZoom(
                    viewport,
                    ink = { inkNow },
                    live = live,
                    onStroke = { p, pts -> strokeNow(p, pts) },
                    onErase = { p, x, y -> eraseNow(p, x, y) },
                    handleAt = { at ->
                        if (!handlesOn) null
                        else handles(viewport, selectionNow).firstOrNull { h -> (at - handleCentre(h.tip, h.lean, r)).getDistance() < r * 2.6f }
                            // the end keeps its place relative to the finger: the middle of its line
                            ?.let { h -> h.end to (h.mid - at) }
                    },
                    onHandle = { end, target ->
                        viewport.hit(target)?.let { (page, x, y) -> handleDragNow(end, page, x, y) }
                    },
                    onMagnify = { magnifyAt = it },
                    gripAt = { at ->
                        val f = selectionNow?.objectFrame
                        if (!objectHandlesNow || f == null) null else gripAt(at, frameOnScreen(viewport, f), density, croppingNow, objectMovableNow)
                    },
                    onGrip = grip@{ g, start, now, done ->
                        val f = selectionNow?.objectFrame ?: return@grip
                        val page = f.page.toInt()
                        val frame = frameOnScreen(viewport, f)
                        if (g.kind == Grip.Kind.Rotate) {
                            val a = angleAt(now, frame.center)
                            dragPreview = if (done) null else frame to a
                            if (done) objectEditNow(ObjectEdit.Rotate(a))
                            return@grip
                        }
                        val moved = dragged(g, frame, now - start, keepRatioNow && !croppingNow, 12f * density)
                        dragPreview = if (done) null else moved to 0f
                        if (!done) return@grip
                        val (x, y) = viewport.toPage(page, moved.topLeft)
                        val w = moved.width / viewport.scale
                        val h = moved.height / viewport.scale
                        objectEditNow(
                            if (croppingNow) ObjectEdit.Crop(page, x, y, w, h, Rect(f.x, f.y, f.x + f.width, f.y + f.height))
                            else ObjectEdit.Frame(page, x, y, w, h, moved = g.kind == Grip.Kind.Move, fromTopLeft = g.fx == 0f || g.fy == 0f),
                        )
                    },
                )
            }
            .pointerInput(viewport, onTap, onDoubleTap) {
                detectTapGestures(
                    onTap = { at -> if (inkNow == null) viewport.hit(at)?.let { (page, x, y) -> onTap(page, x, y) } },
                    onDoubleTap = { at -> if (inkNow == null) viewport.hit(at)?.let { (page, x, y) -> onDoubleTap(page, x, y) } },
                    onLongPress = { at -> if (inkNow == null) viewport.hit(at)?.let { (page, x, y) -> onDoubleTap(page, x, y) } },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(canvasColor)
            if (viewport.scale <= 0f) return@Canvas
            val currentScale = viewport.scale
            for (i in viewport.visiblePages()) {
                val r = viewport.pageRect(i)
                drawRect(pageColor, topLeft = r.topLeft, size = r.size)
                // tiles of other scales first, so the current scale's lie on top
                val pageTiles = tiles.entries.filter { it.key.page == i }.sortedBy { if (it.key.scale == currentScale) 1 else 0 }
                for ((_, t) in pageTiles) {
                    val tl = viewport.onPage(i, t.x, t.y)
                    val br = viewport.onPage(i, t.x + t.w, t.y + t.h)
                    val left = tl.x.roundToInt()
                    val top = tl.y.roundToInt()
                    drawImage(
                        t.bitmap.asImageBitmap(),
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(t.bitmap.width, t.bitmap.height),
                        dstOffset = IntOffset(left, top),
                        dstSize = IntSize((br.x.roundToInt() - left).coerceAtLeast(1), (br.y.roundToInt() - top).coerceAtLeast(1)),
                        filterQuality = FilterQuality.Medium,
                    )
                }
                if (!mobile) drawRect(borderColor, topLeft = r.topLeft, size = r.size, style = Stroke(1f))
            }
            // Find's matches, under the text: multiplied on a white page, screened on a dark one
            val blend = if (dark) androidx.compose.ui.graphics.BlendMode.Screen else androidx.compose.ui.graphics.BlendMode.Multiply
            for ((rects, color) in listOf(findMatches to io.github.artcraftandroid.wordcraft.ui.WordColors.MatchOther, findCurrent to io.github.artcraftandroid.wordcraft.ui.WordColors.MatchCurrent)) {
                for (r in rects) {
                    drawRect(color, topLeft = viewport.onPage(r.page.toInt(), r.x, r.y), size = Size(r.width * viewport.scale, r.height * viewport.scale), blendMode = blend)
                }
            }
            // the sentence Read Aloud is saying, and the word in it, darker
            for (r in highlights + wordHighlights) {
                drawRect(highlightColor, topLeft = viewport.onPage(r.page.toInt(), r.x, r.y), size = Size(r.width * viewport.scale, r.height * viewport.scale))
            }
            val pen = ink
            if (pen != null && pen.tool != "eraser" && live.size >= 2) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(live[0].x, live[0].y)
                    for (i in 1 until live.size) lineTo(live[i].x, live[i].y)
                }
                val color = if (pen.tool == "highlighter") pen.color.copy(alpha = 0.45f) else pen.color
                drawPath(path, color, style = Stroke(width = (pen.width * viewport.scale).coerceAtLeast(1f), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            if (handlesOn && ink == null && composing.isEmpty()) {
                val r = HANDLE_RADIUS_DP * density
                for ((_, tip, _, lean) in handles(viewport, selection)) {
                    val c = handleCentre(tip, lean, r)
                    if (lean == 0) {
                        val p = androidx.compose.ui.graphics.Path().apply {
                            moveTo(tip.x, tip.y)
                            lineTo(c.x + r * 0.72f, c.y - r * 0.72f)
                            lineTo(c.x - r * 0.72f, c.y - r * 0.72f)
                            close()
                        }
                        drawPath(p, handleColor)
                    } else {
                        // the square corner at the tip, the ball leaning out from the selection
                        drawRect(handleColor, topLeft = Offset(if (lean < 0) tip.x - r else tip.x, tip.y), size = Size(r, r))
                    }
                    drawCircle(handleColor, r, c)
                }
            }
            drawSelection(viewport, selection, composing, selectionColor, caretColor, if (showCaret && ink == null) caretAlpha else 0f, dragPreview, cropping) { text, at, px ->
                val layout = measurer.measure(text, TextStyle(fontSize = (px / density).sp, textDecoration = TextDecoration.Underline, color = caretColor))
                drawRect(pageColor, topLeft = at, size = Size(layout.size.width.toFloat(), layout.size.height.toFloat()))
                drawText(layout, topLeft = at)
            }
        }
    }
}

/** The selection handles' radius (a 20 dp teardrop under the caret). */
/** The magnifier over a dragged selection end: its size and how far above the text it floats. */
private const val MAGNIFIER_WIDTH_DP = 100f
private const val MAGNIFIER_HEIGHT_DP = 48f
private const val MAGNIFIER_RISE_DP = 64f

private const val HANDLE_RADIUS_DP = 10f

/**
 * Where the handles' tips are on screen (the bottom of each end of the selection): `anchor` and
 * `focus`, or `caret` alone when nothing is selected.
 */
/**
 * A selection handle: which end it moves (`anchor`, `focus`, or `caret` when nothing is
 * selected), its tip (the bottom of that end's caret line), the middle of that line, and which
 * way it leans: -1 the start (its ball down-left), 1 the end, 0 the caret (straight down).
 */
private data class Handle(val end: String, val tip: Offset, val mid: Offset, val lean: Int)

private fun handles(viewport: DocumentViewport, selection: SelectionView?): List<Handle> {
    val sel = selection ?: return emptyList()
    if (sel.objectFrame != null) return emptyList()
    fun at(c: io.github.artcraftandroid.wordcraft.core.CaretBox, f: Float) = viewport.onPage(c.page.toInt(), c.x, c.top + c.height * f)
    val focus = sel.focus ?: return emptyList()
    if (sel.collapsed) return listOf(Handle("caret", at(focus, 1f), at(focus, 0.5f), 0))
    val anchor = sel.anchor ?: return listOf(Handle("focus", at(focus, 1f), at(focus, 0.5f), 1))
    val a = at(anchor, 1f)
    val f = at(focus, 1f)
    val anchorFirst = a.y < f.y - 1f || (kotlin.math.abs(a.y - f.y) <= 1f && a.x <= f.x)
    return listOf(Handle("anchor", a, at(anchor, 0.5f), if (anchorFirst) -1 else 1), Handle("focus", f, at(focus, 0.5f), if (anchorFirst) 1 else -1))
}

/** The selected object's frame on screen. */
private fun frameOnScreen(viewport: DocumentViewport, f: io.github.artcraftandroid.wordcraft.core.PageRect): Rect =
    Rect(viewport.onPage(f.page.toInt(), f.x, f.y), Size(f.width * viewport.scale, f.height * viewport.scale))

/** Where a handle's ball sits for its tip and lean. */
private fun handleCentre(tip: Offset, lean: Int, r: Float): Offset = if (lean == 0) Offset(tip.x, tip.y + r + 2f) else Offset(tip.x + lean * r, tip.y + r)

/** The selection's rectangles, the caret, and the composing text at the caret. */
private fun DrawScope.drawSelection(
    viewport: DocumentViewport,
    selection: SelectionView?,
    composing: String,
    selectionColor: Color,
    caretColor: Color,
    caretAlpha: Float,
    preview: Pair<Rect, Float>?,
    cropping: Boolean,
    drawComposing: DrawScope.(String, Offset, Float) -> Unit,
) {
    val sel = selection ?: return
    val frame = sel.objectFrame
    if (frame != null) {
        val ring = Color(0xFF616161)
        if (preview != null) {
            // the frame being dragged, turned by the angle being set
            val (r, angle) = preview
            rotate(angle, r.center) {
                drawRect(Color.White.copy(alpha = 0.25f), topLeft = r.topLeft, size = r.size)
                drawRect(ring, topLeft = r.topLeft, size = r.size, style = Stroke(1.5f * density))
            }
            return
        }
        val tl = viewport.onPage(frame.page.toInt(), frame.x, frame.y)
        val w = frame.width * viewport.scale
        val h = frame.height * viewport.scale
        drawRect(ring, topLeft = tl, size = Size(w, h), style = Stroke(1.5f * density))
        if (cropping) {
            // crop handles: a black corner at each corner and a black bar at each side's middle
            val len = 14f * density
            val thick = 4f * density
            for (fx in listOf(0f, 0.5f, 1f)) for (fy in listOf(0f, 0.5f, 1f)) {
                if (fx == 0.5f && fy == 0.5f) continue
                val c = Offset(tl.x + w * fx, tl.y + h * fy)
                val sx = if (fx == 1f) -len else len
                val sy = if (fy == 1f) -len else len
                val segments = when {
                    fx == 0.5f -> listOf(Offset(c.x - len / 2f, c.y) to Offset(c.x + len / 2f, c.y))
                    fy == 0.5f -> listOf(Offset(c.x, c.y - len / 2f) to Offset(c.x, c.y + len / 2f))
                    else -> listOf(c to Offset(c.x + sx, c.y), c to Offset(c.x, c.y + sy))
                }
                // white under black, so the handles show on dark pictures too
                for ((color, t) in listOf(Color.White to thick + 2f * density, Color.Black to thick)) {
                    for ((a, b) in segments) drawLine(color, a, b, t)
                }
            }
            return
        }
        // the rotate handle on a short stem above the top's middle
        val top = Offset(tl.x + w / 2f, tl.y)
        val knob = Offset(top.x, top.y - ROTATE_STEM_DP * density)
        drawLine(ring, top, knob, 1.5f * density)
        drawCircle(Color.White, 8f * density, knob)
        drawCircle(ring, 8f * density, knob, style = Stroke(1.5f * density))
        for (fx in listOf(0f, 0.5f, 1f)) for (fy in listOf(0f, 0.5f, 1f)) {
            if (fx == 0.5f && fy == 0.5f) continue
            val c = Offset(tl.x + w * fx, tl.y + h * fy)
            drawCircle(Color.White, 5f * density, c)
            drawCircle(ring, 5f * density, c, style = Stroke(1.5f * density))
        }
        return
    }
    // the equation being edited, shaded in the caret's colour (dark on a light page, light on a dark one)
    sel.equation?.let { e ->
        val tl = viewport.onPage(e.page.toInt(), e.x, e.y)
        drawRect(caretColor.copy(alpha = 0.09f), topLeft = tl, size = Size(e.width * viewport.scale, e.height * viewport.scale))
    }
    for (r in sel.rects) {
        val tl = viewport.onPage(r.page.toInt(), r.x, r.y)
        drawRect(selectionColor, topLeft = tl, size = Size(r.width * viewport.scale, r.height * viewport.scale))
    }
    val caret = sel.focus ?: return
    if (!sel.collapsed) return
    val top = viewport.onPage(caret.page.toInt(), caret.x, caret.top)
    val h = caret.height * viewport.scale
    if (composing.isNotEmpty()) {
        drawComposing(composing, top, h * 0.72f)
        return
    }
    if (caretAlpha <= 0f) return
    val w = max(2f, 1.5f * density)
    drawRect(caretColor.copy(alpha = caretColor.alpha * caretAlpha), topLeft = Offset(top.x - w / 2f, top.y), size = Size(w, h))
}

/**
 * One finger scrolls (and flings), two fingers zoom about their centre and scroll. While [ink]
 * gives a tool, one finger draws instead: the points go to [onStroke] when it lifts (or to
 * [onErase] as it moves, for the eraser), and [live] holds them meanwhile for the preview.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.panAndZoom(
    viewport: DocumentViewport,
    ink: () -> InkSettings?,
    live: MutableList<Offset>,
    onStroke: (Int, List<Pair<Float, Float>>) -> Unit,
    onErase: (Int, Float, Float) -> Unit,
    handleAt: (Offset) -> Pair<String, Offset>? = { null },
    onHandle: (String, Offset) -> Unit = { _, _ -> },
    onMagnify: (Offset?) -> Unit = {},
    gripAt: (Offset) -> Grip? = { null },
    onGrip: (Grip, Offset, Offset, Boolean) -> Unit = { _, _, _, _ -> },
) = coroutineScope {
    val decay = splineBasedDecay<Offset>(this@panAndZoom)
    var fling: kotlinx.coroutines.Job? = null
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        fling?.cancel()
        // a drag that starts on the selected object's handles resizes, turns or crops it; one
        // inside it moves it (a tap there stays a tap)
        val grip = if (ink() == null) gripAt(down.position) else null
        if (grip != null) {
            if (grip.kind != Grip.Kind.Move) down.consume()
            var started = grip.kind != Grip.Kind.Move
            var last = down.position
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) break
                val c = event.changes.firstOrNull { it.id == down.id }
                if (c != null && c.pressed) {
                    if (!started && (c.position - down.position).getDistance() > viewConfiguration.touchSlop) started = true
                    if (started && (c.position - last).getDistance() > 2f) {
                        last = c.position
                        onGrip(grip, down.position, c.position, false)
                    }
                }
                if (started) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
            if (started) onGrip(grip, down.position, last, true)
            return@awaitEachGesture
        }
        // a drag that starts on a selection handle moves that end of the selection
        val handle = if (ink() == null) handleAt(down.position) else null
        if (handle != null) {
            val (end, delta) = handle
            down.consume()
            var last = down.position
            do {
                val event = awaitPointerEvent()
                val c = event.changes.firstOrNull { it.id == down.id }
                if (c != null && c.pressed && (c.position - last).getDistance() > 4f) {
                    last = c.position
                    onHandle(end, c.position + delta)
                    onMagnify(c.position + delta)
                }
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
            onMagnify(null)
            return@awaitEachGesture
        }
        val tool = ink()
        val strokePage = if (tool != null) viewport.hit(down.position)?.first ?: -1 else -1
        val stroke = mutableListOf<Pair<Float, Float>>()
        var lastErase = down.position
        if (strokePage >= 0 && tool != null) {
            down.consume()
            live.clear()
            live.add(down.position)
            stroke += viewport.toPage(strokePage, down.position)
            if (tool.tool == "eraser") viewport.toPage(strokePage, down.position).let { (x, y) -> onErase(strokePage, x, y) }
        }
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var moved = Offset.Zero
        var dragging = false
        var zoomed = false
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                zoomed = true
                dragging = true
                live.clear()
                viewport.gesturing = true
                viewport.zooming = true
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val centre = event.calculateCentroid(useCurrent = true)
                if (centre != Offset.Unspecified) viewport.zoomBy(zoom, centre)
                viewport.panBy(pan.x, pan.y)
                event.changes.forEach { it.consume() }
            } else if (pressed.size == 1 && strokePage >= 0 && tool != null && !zoomed) {
                val c = pressed[0]
                c.consume()
                live.add(c.position)
                stroke += viewport.toPage(strokePage, c.position)
                if (tool.tool == "eraser" && (c.position - lastErase).getDistance() > 12f) {
                    lastErase = c.position
                    viewport.toPage(strokePage, c.position).let { (x, y) -> onErase(strokePage, x, y) }
                }
            } else if (pressed.size == 1) {
                val c = pressed[0]
                tracker.addPosition(c.uptimeMillis, c.position)
                val d = c.positionChange()
                if (!dragging) {
                    moved += d
                    if (moved.getDistance() > viewConfiguration.touchSlop) dragging = true
                }
                if (dragging) {
                    viewport.gesturing = true
                    if (!zoomed) viewport.panBy(d.x, d.y)
                    c.consume()
                }
            }
            if (strokePage >= 0) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
        viewport.gesturing = false
        viewport.zooming = false
        if (strokePage >= 0 && tool != null && !zoomed) {
            if (tool.tool != "eraser") onStroke(strokePage, stroke.toList())
            live.clear()
            return@awaitEachGesture
        }
        if (dragging && !zoomed) {
            val v = tracker.calculateVelocity()
            // capped; as far as the platform's fling would go, over a time that grows with the speed
            val raw = Offset(v.x, v.y)
            val speed = raw.getDistance()
            val start = if (speed > io.github.artcraftandroid.wordcraft.ui.Motion.FLING_MAX_VELOCITY) raw * (io.github.artcraftandroid.wordcraft.ui.Motion.FLING_MAX_VELOCITY / speed) else raw
            if (start.getDistance() > 50f) {
                fling = launch {
                    viewport.gesturing = true
                    try {
                        val target = decay.calculateTargetValue(Offset.VectorConverter, Offset.Zero, start)
                        val ms = maxOf(
                            io.github.artcraftandroid.wordcraft.ui.Motion.FLING_MIN_MS,
                            (start.getDistance() * io.github.artcraftandroid.wordcraft.ui.Motion.FLING_MS_AT_MAX / io.github.artcraftandroid.wordcraft.ui.Motion.FLING_MAX_VELOCITY).toInt(),
                        )
                        val anim = Animatable(Offset.Zero, Offset.VectorConverter)
                        var last = Offset.Zero
                        anim.animateTo(target, androidx.compose.animation.core.tween(ms, easing = io.github.artcraftandroid.wordcraft.ui.Motion.FlingEasing)) {
                            val step = value - last
                            last = value
                            viewport.panBy(step.x, step.y)
                        }
                    } finally {
                        viewport.gesturing = false
                    }
                }
            }
        }
    }
}
