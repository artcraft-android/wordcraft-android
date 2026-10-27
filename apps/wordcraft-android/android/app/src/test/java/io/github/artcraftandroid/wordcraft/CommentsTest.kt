package io.github.artcraftandroid.wordcraft

import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.editor.avatarColor
import io.github.artcraftandroid.wordcraft.ui.editor.commentDate
import io.github.artcraftandroid.wordcraft.ui.editor.initialsOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class CommentsTest {
    private fun comment(author: String, initials: String = "") = AppViewModel.CommentItem(0, author, initials, "", "", "", false, null, null)

    @Test
    fun aUtcDateReadsInTheLocalZone() {
        val denver = ZoneId.of("America/Denver")
        // the UTC time, shown in the phone's zone: six hours behind, the evening before
        assertEquals("October 26, 2026 at 12:18 AM", commentDate("2026-10-26T06:18:45Z", "2026-10-26T06:18:45Z", denver))
        assertEquals("October 25, 2026 at 6:18 PM", commentDate("2026-10-26T00:18:45Z", "2026-10-26T00:18:45Z", denver))
        // the UTC time wins over the file's own date
        assertEquals("October 26, 2026 at 12:18 AM", commentDate("2026-10-27T15:04:00Z", "2026-10-26T06:18:45Z", denver))
    }

    @Test
    fun aFilesOwnDateReadsAsWritten() {
        // the file's own date is its writer's local time, a Z or not
        assertEquals("October 27, 2026 at 3:02 PM", commentDate("2026-10-27T15:02:10Z", "", ZoneId.of("America/Denver")))
        assertEquals("March 1, 2026 at 9:05 AM", commentDate("2026-03-01T09:05:00", "", ZoneId.of("Asia/Tokyo")))
        assertEquals("", commentDate(""))
        assertEquals("not a date", commentDate("not a date"))
    }

    @Test
    fun aNameKeepsItsAvatarColour() {
        assertEquals(avatarColor("Ada Lane"), avatarColor("ada lane "))
    }

    @Test
    fun initialsComeFromTheFileElseTheName() {
        assertEquals("TW", initialsOf(comment("Test Writer", "TW")))
        assertEquals("AB", initialsOf(comment("ada byron lovelace")))
        assertEquals("Q", initialsOf(comment("Quill")))
        assertEquals("", initialsOf(comment("  ")))
    }
}
