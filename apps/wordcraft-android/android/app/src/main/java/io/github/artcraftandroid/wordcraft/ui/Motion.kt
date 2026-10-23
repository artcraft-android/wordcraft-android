package io.github.artcraftandroid.wordcraft.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * How the app moves, matched to the reference app: almost all of its chrome uses one sharp
 * ease-out for what appears or moves and its mirror for what leaves, over a few set durations.
 */
object Motion {
    /** A sharp ease-out: nine tenths of the way at half time. */
    val EaseOutSharp: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Its mirror, for what leaves. */
    val EaseInSharp: Easing = CubicBezierEasing(1f, 0f, 1f, 1f)

    /** A fling's slowing down. */
    val FlingEasing = Easing { t -> 1f - (1f - t).pow(2.8f) }

    const val QUICKEST = 50
    const val QUICK = 100
    const val SHORT = 167
    const val BASIC = 267
    const val FLOATING = 400

    /** A bottom sheet of the title bar's menu. */
    const val SHEET = 256

    /** Fastest fling, px/s; a fling takes at least [FLING_MIN_MS] and longer the faster it is. */
    const val FLING_MAX_VELOCITY = 11_000f
    const val FLING_MIN_MS = 1000
    const val FLING_MS_AT_MAX = 2000
}

/** A pressed control turns grey at once (and back when let go): no ripple. */
object PressedIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressedNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = 0x5052
}

private class PressedNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            var down = 0
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> down++
                    is PressInteraction.Release, is PressInteraction.Cancel -> down = (down - 1).coerceAtLeast(0)
                }
                if ((down > 0) != pressed) {
                    pressed = down > 0
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        // behind the control's icon or label
        if (pressed) drawRect(WordColors.Pressed)
        drawContent()
    }
}
