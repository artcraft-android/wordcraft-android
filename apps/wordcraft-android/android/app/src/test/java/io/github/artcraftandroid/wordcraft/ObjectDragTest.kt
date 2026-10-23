package io.github.artcraftandroid.wordcraft

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import io.github.artcraftandroid.wordcraft.ui.editor.Grip
import io.github.artcraftandroid.wordcraft.ui.editor.angleAt
import io.github.artcraftandroid.wordcraft.ui.editor.dragged
import io.github.artcraftandroid.wordcraft.ui.editor.gripAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObjectDragTest {
    private val frame = Rect(100f, 200f, 300f, 300f)

    @Test
    fun handlesTheRotateKnobAndTheInsideAreFound() {
        val d = 2f
        assertEquals(Grip(1f, 1f, Grip.Kind.Resize), gripAt(Offset(305f, 302f), frame, d, cropping = false, movable = true))
        assertEquals(Grip(0f, 0.5f, Grip.Kind.Resize), gripAt(Offset(98f, 250f), frame, d, cropping = false, movable = true))
        // the knob above the top's middle
        assertEquals(Grip.Kind.Rotate, gripAt(Offset(200f, 200f - 44f), frame, d, cropping = false, movable = true)?.kind)
        assertEquals(Grip.Kind.Move, gripAt(Offset(200f, 250f), frame, d, cropping = false, movable = true)?.kind)
        // an object in the line doesn't move; cropping has no knob and no move
        assertNull(gripAt(Offset(200f, 250f), frame, d, cropping = false, movable = false))
        assertNull(gripAt(Offset(200f, 150f), frame, d, cropping = true, movable = true))
        assertNull(gripAt(Offset(200f, 250f), frame, d, cropping = true, movable = true))
    }

    @Test
    fun cornersKeepTheProportionsAndSidesMoveOneEdge() {
        val corner = dragged(Grip(1f, 1f, Grip.Kind.Resize), frame, Offset(100f, 0f), keepRatio = true, min = 10f)
        assertEquals(Rect(100f, 200f, 400f, 350f), corner)
        val free = dragged(Grip(1f, 1f, Grip.Kind.Resize), frame, Offset(100f, 0f), keepRatio = false, min = 10f)
        assertEquals(Rect(100f, 200f, 400f, 300f), free)
        // from the top-left: the bottom-right corner stays put
        val tl = dragged(Grip(0f, 0f, Grip.Kind.Resize), frame, Offset(-200f, 0f), keepRatio = true, min = 10f)
        assertEquals(300f, tl.right)
        assertEquals(300f, tl.bottom)
        assertEquals(tl.width / tl.height, frame.width / frame.height, 0.001f)
        val side = dragged(Grip(0.5f, 0f, Grip.Kind.Resize), frame, Offset(50f, 30f), keepRatio = true, min = 10f)
        assertEquals(Rect(100f, 230f, 300f, 300f), side)
        // never turned inside out
        val tiny = dragged(Grip(1f, 0.5f, Grip.Kind.Resize), frame, Offset(-500f, 0f), keepRatio = false, min = 10f)
        assertEquals(10f, tiny.width)
        assertEquals(Rect(110f, 230f, 310f, 330f), dragged(Grip(0.5f, 0.5f, Grip.Kind.Move), frame, Offset(10f, 30f), keepRatio = true, min = 10f))
    }

    @Test
    fun anglesAreClockwiseFromUpAndSnapToQuarterTurns() {
        val c = Offset(0f, 0f)
        assertEquals(0f, angleAt(Offset(0f, -10f), c))
        assertEquals(90f, angleAt(Offset(10f, 0f), c))
        assertEquals(180f, angleAt(Offset(0f, 10f), c))
        assertEquals(270f, angleAt(Offset(-10f, 0f), c))
        assertEquals(45f, angleAt(Offset(10f, -10f), c))
        // 2° off a quarter turn snaps to it, 10° off doesn't
        assertEquals(90f, angleAt(Offset(10f, -0.35f), c))
        assertEquals(80f, angleAt(Offset(10f, -1.763f), c))
    }
}
