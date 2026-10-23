package io.github.artcraftandroid.wordcraft

import androidx.compose.ui.geometry.Offset
import io.github.artcraftandroid.wordcraft.core.PageSize
import io.github.artcraftandroid.wordcraft.ui.editor.DocumentViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentViewportTest {
    private val letter = PageSize(612f, 792f, 0f)

    private fun viewport(pages: Int = 3, w: Float = 1080f, h: Float = 2000f) = DocumentViewport(pad = 8f, gap = 8f).apply {
        setView(w, h)
        setPages(List(pages) { letter })
    }

    @Test
    fun pagesFitTheWidthAtFirst() {
        val v = viewport()
        // 612 pt page plus 8 pt each side across 1080 px
        assertEquals(1080f / 628f, v.scale, 1e-4f)
        val r = v.pageRect(0)
        assertEquals(8f * v.scale, r.left, 0.5f)
        assertEquals(1080f - 8f * v.scale, r.right, 0.5f)
    }

    @Test
    fun zoomKeepsThePointUnderTheFingers() {
        val v = viewport()
        val focus = Offset(300f, 700f)
        val before = v.hit(focus)
        assertNotNull(before)
        v.zoomBy(2f, focus)
        val after = v.hit(focus)
        assertNotNull(after)
        assertEquals(before!!.first, after!!.first)
        assertEquals(before.second, after.second, 0.5f)
        assertEquals(before.third, after.third, 0.5f)
    }

    @Test
    fun zoomStaysWithinItsLimits() {
        val v = viewport()
        val fit = v.fitScale
        repeat(20) { v.zoomBy(2f, Offset(10f, 10f)) }
        assertEquals(fit * 5f, v.scale, 1e-3f)
        repeat(40) { v.zoomBy(0.5f, Offset(10f, 10f)) }
        assertEquals(fit * 0.5f, v.scale, 1e-3f)
        v.zoomBy(Float.NaN, Offset.Zero)
        v.zoomBy(-1f, Offset.Zero)
        assertTrue(v.scale.isFinite() && v.scale > 0f)
    }

    @Test
    fun scrollingStopsAtTheEnds() {
        val v = viewport()
        v.panBy(0f, 100_000f)
        assertEquals(0f, v.scrollY, 0f)
        v.panBy(0f, -100_000f)
        val contentH = 8f + 3 * 792f + 2 * 8f + 8f
        assertEquals(contentH - 2000f / v.scale, v.scrollY, 0.01f)
        // narrower than the view: nothing to scroll across
        v.panBy(-500f, 0f)
        assertEquals(0f, v.scrollX, 0f)
    }

    @Test
    fun aTapBetweenPagesGoesToTheNearestPage() {
        val v = viewport()
        val gapY = v.pageRect(0).bottom + 2f
        val hit = v.hit(Offset(540f, gapY))!!
        assertEquals(0, hit.first)
        assertEquals(792f, hit.third, 0.01f)
        val second = v.hit(Offset(540f, v.pageRect(1).top + 10f))!!
        assertEquals(1, second.first)
    }

    @Test
    fun revealScrollsTheCaretIntoView() {
        val v = viewport()
        v.reveal(2, 100f, 700f, 14f, 24f)
        val caret = v.onPage(2, 100f, 700f)
        assertTrue(caret.y >= 0f && caret.y + 14f * v.scale <= 2000f)
    }
}
