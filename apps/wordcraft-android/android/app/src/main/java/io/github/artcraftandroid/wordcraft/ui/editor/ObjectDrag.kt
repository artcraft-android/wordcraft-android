package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

/** What a drag on the selected object did, in its page's points. */
sealed interface ObjectEdit {
    /** Resized from a handle or moved: the new frame. */
    data class Frame(val page: Int, val x: Float, val y: Float, val width: Float, val height: Float, val moved: Boolean, val fromTopLeft: Boolean) : ObjectEdit

    /** Turned by the rotate handle: the angle clockwise, whole degrees. */
    data class Rotate(val degrees: Float) : ObjectEdit

    /** Cropped: the part of the picture left showing, against the frame before ([before]). */
    data class Crop(val page: Int, val x: Float, val y: Float, val width: Float, val height: Float, val before: Rect) : ObjectEdit
}

/** Where a drag on the selected object starts: a handle at ([fx], [fy]) of the frame (0, ½ or 1), the rotate handle, or the object itself. */
internal data class Grip(val fx: Float, val fy: Float, val kind: Kind) {
    enum class Kind { Resize, Rotate, Move }

    val corner get() = fx != 0.5f && fy != 0.5f
}

/** How far above the frame's top the rotate handle sits, dp. */
internal const val ROTATE_STEM_DP = 22f

/**
 * The grip under [at] for a frame at [frame] (screen pixels): the nearest handle within reach, the
 * rotate handle (not while cropping), or the object's inside (moving it, when [movable]).
 */
internal fun gripAt(at: Offset, frame: Rect, density: Float, cropping: Boolean, movable: Boolean): Grip? {
    val reach = 22f * density
    if (!cropping) {
        val knob = Offset(frame.center.x, frame.top - ROTATE_STEM_DP * density)
        if ((at - knob).getDistance() < reach) return Grip(0.5f, 0f, Grip.Kind.Rotate)
    }
    var best: Grip? = null
    var bestD = reach
    for (fx in listOf(0f, 0.5f, 1f)) for (fy in listOf(0f, 0.5f, 1f)) {
        if (fx == 0.5f && fy == 0.5f) continue
        val d = (at - Offset(frame.left + frame.width * fx, frame.top + frame.height * fy)).getDistance()
        if (d < bestD) {
            bestD = d
            best = Grip(fx, fy, Grip.Kind.Resize)
        }
    }
    if (best != null) return best
    if (movable && !cropping && frame.contains(at)) return Grip(0.5f, 0.5f, Grip.Kind.Move)
    return null
}

/**
 * The frame after dragging [g] by [d] (screen pixels): a side handle moves its side, a corner its
 * two sides (keeping the proportions when [keepRatio], the opposite corner fixed), the inside
 * moves the whole frame. Never smaller than [min] across.
 */
internal fun dragged(g: Grip, frame: Rect, d: Offset, keepRatio: Boolean, min: Float): Rect {
    if (g.kind == Grip.Kind.Move) return frame.translate(d)
    var l = frame.left
    var t = frame.top
    var r = frame.right
    var b = frame.bottom
    if (g.fx == 0f) l = (l + d.x).coerceAtMost(r - min)
    if (g.fx == 1f) r = (r + d.x).coerceAtLeast(l + min)
    if (g.fy == 0f) t = (t + d.y).coerceAtMost(b - min)
    if (g.fy == 1f) b = (b + d.y).coerceAtLeast(t + min)
    if (keepRatio && g.corner && frame.width > 0f && frame.height > 0f) {
        // the larger of the two changes sets the size; the opposite corner stays put
        val f = max((r - l) / frame.width, (b - t) / frame.height)
        val w = max(frame.width * f, min)
        val h = max(frame.height * f, min)
        if (g.fx == 0f) l = r - w else r = l + w
        if (g.fy == 0f) t = b - h else b = t + h
    }
    return Rect(l, t, r, b)
}

/** The angle, clockwise from straight up, of [at] around [centre]: whole degrees 0–359, within 3° of a quarter turn snapping to it. */
internal fun angleAt(at: Offset, centre: Offset): Float {
    val deg = Math.toDegrees(atan2((at.x - centre.x).toDouble(), (centre.y - at.y).toDouble())).toFloat()
    val whole = ((deg.roundToInt() % 360) + 360) % 360
    val quarter = ((whole + 45) / 90 * 90) % 360
    val off = abs(whole - quarter).let { if (it > 180) 360 - it else it }
    return (if (off <= 3) quarter else whole).toFloat()
}
