package io.github.artcraftandroid.wordcraft

import io.github.artcraftandroid.wordcraft.ui.editor.TellMeItem
import io.github.artcraftandroid.wordcraft.ui.editor.tellMeMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TellMeTest {
    private fun item(label: String, words: String = "") = TellMeItem(label, "Home", null, words) { _, _ -> }

    private val items = listOf(
        item("Font Color", "text colour color"),
        item("Highlight", "marker colour color background"),
        item("Track Changes", "revisions edits"),
        item("Page Number", "numbers count"),
        item("Numbering", "numbered list"),
    )

    private fun names(q: String) = tellMeMatches(q, items).map { it.label }

    @Test
    fun namesStartingWithTheQueryComeFirst() {
        // "Numbering" starts with it; "Page Number" only contains it
        assertEquals(listOf("Numbering", "Page Number"), names("numb"))
    }

    @Test
    fun otherWordsFindACommand() {
        assertEquals(listOf("Track Changes"), names("revision"))
        assertEquals(listOf("Font Color", "Highlight"), names("colour"))
        // every word typed has to match
        assertEquals(listOf("Highlight"), names("colour marker"))
    }

    @Test
    fun nothingTypedOrNothingMatchingFindsNothing() {
        assertTrue(names("   ").isEmpty())
        assertTrue(names("spreadsheet").isEmpty())
    }
}
