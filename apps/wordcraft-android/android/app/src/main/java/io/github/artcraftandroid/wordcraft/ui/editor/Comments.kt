package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.LucideIcons
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Comments pane's colours: the phone app keeps the pane light in both themes. */
private object Pane {
    val Ground = Color(0xFFFFFFFF)
    val Rule = Color(0xFFE6E6E6)
    val Text = Color(0xFF2A2A2A)
    val Soft = Color(0xFF505050)
    val Hint = Color(0xFF666666)
    /** Under a thread with replies, the ground round its card. */
    val Under = Color(0xFFF3F3F3)
    val SendOff = Color(0xFFBDBDBD)
    val Send = Color(0xFF2B579A)
}

/** The ground the Comments pane draws on, for the bars around it while it takes the whole screen. */
val COMMENTS_GROUND = Color(0xFFFFFFFF)

/**
 * The Comments pane, in the palette's place, as the phone app has it: one thread at a time under
 * a pager ("1 of 3", previous and next), each comment with its writer's initials, name, text and
 * date; the thread's ⋯ deletes or resolves it; the Reply bar under it opens the pane over the
 * whole screen to write (Cancel goes back). Opened to write with no thread, it writes a new
 * comment on the selection.
 */
@Composable
fun CommentsSheet(vm: AppViewModel, modifier: Modifier = Modifier) {
    val threads = vm.comments.filter { it.parent == null }
    val index = threads.indexOfFirst { it.id == vm.commentFocus }.takeIf { it >= 0 } ?: 0
    val thread = threads.getOrNull(index)
    // writing with no thread in view: a new comment on the selection
    val newComment = vm.commentWriting && vm.commentFocus == null
    var draft by remember(vm.commentWriting, vm.commentFocus) { mutableStateOf("") }
    Column(modifier.background(Pane.Ground).semantics { contentDescription = "Comments Pane" }) {
        if (vm.commentWriting) {
            Row(Modifier.fillMaxWidth().height(48.dp).bottomEdge(Pane.Rule), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.fillMaxHeight().clickable { if (newComment) vm.closeComments() else vm.commentWriting = false }
                        .semantics { contentDescription = "Cancel" }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel", color = Pane.Soft, fontSize = 16.sp, modifier = Modifier.clearAndSetSemantics { })
                }
            }
        } else {
            Pager(
                at = index,
                count = threads.size,
                onPrevious = { threads.getOrNull(index - 1)?.let { vm.commentFocus = it.id; vm.goToComment(it) } },
                onNext = { threads.getOrNull(index + 1)?.let { vm.commentFocus = it.id; vm.goToComment(it) } },
                onClose = vm::closeComments,
            )
        }
        val replies = if (thread != null) vm.comments.filter { it.parent == thread.id } else emptyList()
        Column(Modifier.weight(1f).fillMaxWidth().background(if (replies.isEmpty()) Pane.Ground else Pane.Under).verticalScroll(rememberScrollState())) {
            if (thread != null && !newComment) {
                Thread(vm, thread, replies, menu = !vm.commentWriting)
            } else if (!vm.commentWriting) {
                Text("No comments yet. Select some text and add one.", color = Pane.Soft, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
            }
        }
        if (vm.commentWriting) {
            Writer(
                draft = draft,
                onDraft = { draft = it },
                hint = if (newComment) "New comment" else "Reply",
                post = if (newComment) "Post comment" else "Post reply",
                onPost = {
                    if (newComment) vm.postComment(draft) else if (thread != null) vm.postReply(thread.id, draft)
                    draft = ""
                },
            )
        } else if (thread != null) {
            Box(
                Modifier.fillMaxWidth().height(48.dp).topEdge(Pane.Rule).clickable {
                    vm.commentFocus = thread.id
                    vm.commentWriting = true
                }.semantics { contentDescription = "Reply" },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Reply", color = Pane.Hint, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

/** The header: "‹ 1 of 3 ›" in the middle, the close button at the right. */
@Composable
private fun Pager(at: Int, count: Int, onPrevious: () -> Unit, onNext: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(50.dp).bottomEdge(Pane.Rule)) {
        val label = if (count == 0) "0 of 0" else "${at + 1} of $count"
        Box(Modifier.align(Alignment.Center).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Text(label, color = Pane.Soft, fontSize = 16.sp, modifier = Modifier.clearAndSetSemantics { })
        }
        // the arrows 56 dp either side of the middle
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            BarButton(LucideIcons.ChevronLeft.glyph(), Pane.Soft, onPrevious, enabled = at > 0, width = 50.dp, height = 50.dp, iconSize = 22.dp, disabledTint = Pane.SendOff, label = "Previous comment")
            Spacer(Modifier.width(62.dp))
            BarButton(LucideIcons.ChevronRight.glyph(), Pane.Soft, onNext, enabled = at + 1 < count, width = 50.dp, height = 50.dp, iconSize = 22.dp, disabledTint = Pane.SendOff, label = "Next comment")
        }
        BarButton(LucideIcons.X.glyph(), Pane.Soft, onClose, Modifier.align(Alignment.CenterEnd), width = 45.dp, height = 50.dp, iconSize = 22.dp, label = "Close the comment pane")
    }
}

/** A thread: its first comment, then each reply under a rule, each with its writer's initials in a circle; ⋯ at the top right. */
@Composable
private fun Thread(vm: AppViewModel, c: AppViewModel.CommentItem, replies: List<AppViewModel.CommentItem>, menu: Boolean) {
    var open by remember { mutableStateOf(false) }
    val count = if (replies.size == 1) "1 reply" else "${replies.size} replies"
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(Pane.Ground)) {
            Entry(c, "Threaded comment from ${c.author}: ${c.text}, on ${commentDate(c.date, c.dateUtc)} with $count.") { vm.goToComment(c) }
            for (r in replies) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Pane.Rule))
                Entry(r, "Reply from ${r.author}: ${r.text}, on ${commentDate(r.date, r.dateUtc)}.") { vm.goToComment(c) }
            }
        }
        if (menu) {
            Box(Modifier.align(Alignment.TopEnd)) {
                BarButton(LucideIcons.Ellipsis.glyph(), Pane.Soft, { open = true }, width = 40.dp, height = 36.dp, iconSize = 22.dp, label = "More thread actions")
                DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Pane.Ground) {
                    // rows 37 dp tall, as the reference's
                    DropdownMenuItem(text = { Text("Delete thread", color = Pane.Text, fontSize = 16.sp) }, modifier = Modifier.height(37.dp), onClick = {
                        open = false
                        vm.deleteThread(c.id)
                    })
                    DropdownMenuItem(text = { Text(if (c.resolved) "Reopen thread" else "Resolve thread", color = Pane.Text, fontSize = 16.sp) }, modifier = Modifier.height(37.dp), onClick = {
                        open = false
                        vm.commentCommand("review.resolveComment", org.json.JSONObject().put("id", c.id).put("value", !c.resolved))
                    })
                }
            }
        }
    }
}

/** One comment, a screen reader's [label]: the initials in a 30 dp circle, then the name, the text a line under it, and the date. */
@Composable
private fun Entry(c: AppViewModel.CommentItem, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).semantics { contentDescription = label }.padding(start = 12.dp, end = 48.dp, top = 12.dp, bottom = 12.dp)) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(avatarColor(c.author)).semantics { contentDescription = "Picture of ${c.author}" },
            contentAlignment = Alignment.Center,
        ) {
            Text(initialsOf(c), color = Color.White, fontSize = 13.sp, modifier = Modifier.clearAndSetSemantics { })
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(c.author.ifBlank { "Author" }, color = Pane.Soft, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            // the text starts a line lower, as the phone app sets it
            Spacer(Modifier.height(19.5.dp))
            Text(c.text, color = Pane.Text, fontSize = 15.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(2.dp))
            Text(commentDate(c.date, c.dateUtc), color = Pane.Soft, fontSize = 14.sp, lineHeight = 18.sp)
        }
    }
}

/** The field a reply or a new comment is written in, with its send button; the keyboard comes up for it. */
@Composable
private fun Writer(draft: String, onDraft: (String) -> Unit, hint: String, post: String, onPost: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(Modifier.fillMaxWidth().height(48.dp).topEdge(Pane.Rule), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (draft.isEmpty()) Text(hint, color = Pane.Hint, fontSize = 15.sp)
            BasicTextField(
                draft,
                onValueChange = onDraft,
                textStyle = TextStyle(color = Pane.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(Pane.Text),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = hint },
            )
        }
        BarButton(LucideIcons.SendHorizontal.glyph(), Pane.Send, onPost, enabled = draft.isNotBlank(), width = 48.dp, height = 48.dp, iconSize = 22.dp, disabledTint = Pane.SendOff, label = post)
    }
}

/** A comment's initials: the ones the file gives, else the first letters of the first two words of its writer's name. */
internal fun initialsOf(c: AppViewModel.CommentItem): String =
    c.initials.ifBlank { c.author.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) } }.uppercase(Locale.getDefault()).take(3)

/**
 * A comment's date as the phone app writes it: "October 26, 2026 at 12:18 AM". A UTC time
 * ([utc], the file's `dateUtc`, or a comment made here) is shown in the phone's zone; a file's own
 * `date` alone is its writer's local time, shown as written (whatever zone letter it carries).
 */
internal fun commentDate(date: String, utc: String = "", zone: ZoneId = ZoneId.systemDefault()): String {
    val at = utc.takeIf { it.isNotBlank() }?.let { u -> runCatching { Instant.parse(u).atZone(zone).toLocalDateTime() }.getOrNull() }
        ?: runCatching { LocalDateTime.parse(date.trim().removeSuffix("Z")) }.getOrNull()
        ?: return date
    return at.format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.US))
}

/** The circles behind writers' initials: each name gets one of these, the same one each time (the first two as the reference showed two writers'). */
private val AVATARS = listOf(0xFF6BA5E6, 0xFFDA532C, 0xFF5E9E6E, 0xFF9A6BB8, 0xFFC28A2E, 0xFF4F8FA8, 0xFFB5577A, 0xFF7A7F3A)

internal fun avatarColor(name: String): Color {
    // a stable hash of the name (String.hashCode is the same on every phone)
    val h = name.trim().lowercase(Locale.ROOT).hashCode()
    return Color(AVATARS[Math.floorMod(h, AVATARS.size)])
}
