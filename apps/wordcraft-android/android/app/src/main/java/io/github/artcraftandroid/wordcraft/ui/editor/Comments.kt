package io.github.artcraftandroid.wordcraft.ui.editor

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.LucideIcons
import io.github.artcraftandroid.wordcraft.ui.WordColors
import org.json.JSONObject

/**
 * The Comments pane, in the palette's place: each comment with its replies under it; tap one
 * to go there; reply, resolve or delete it; and a field for a new comment on the selection.
 */
@Composable
fun CommentsSheet(vm: AppViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { vm.loadComments() }
    var draft by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Int?>(null) }
    Column(modifier.background(WordColors.Surface)) {
        Row(Modifier.fillMaxWidth().height(50.dp).topEdge(WordColors.StripMore).bottomEdge(WordColors.Divider), verticalAlignment = Alignment.CenterVertically) {
            Text("Comments", color = WordColors.OnSurface, fontSize = 20.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
            BarButton(LucideIcons.X.glyph(), WordColors.OnStrip, { vm.commentsOpen = false }, width = 56.dp, height = 50.dp)
        }
        val threads = vm.comments.filter { it.parent == null }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (threads.isEmpty()) item {
                Text("No comments yet. Select some text and add one below.", color = WordColors.OnSurface, fontSize = 15.sp, modifier = Modifier.padding(16.dp))
            }
            items(threads, key = { it.id }) { c ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).border(1.dp, WordColors.Divider).background(if (c.resolved) WordColors.Divider else WordColors.Surface).clickable { vm.goToComment(c) }.padding(12.dp)) {
                    CommentBody(c)
                    for (r in vm.comments.filter { it.parent == c.id }) {
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Spacer(Modifier.width(16.dp))
                            Column { CommentBody(r) }
                        }
                    }
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Action("Reply") { replyTo = c.id }
                        Action(if (c.resolved) "Reopen" else "Resolve") { vm.commentCommand("review.resolveComment", JSONObject().put("id", c.id).put("value", !c.resolved)) }
                        Action("Delete") { vm.commentCommand("review.deleteComment", JSONObject().put("id", c.id)) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().topEdge(WordColors.Divider).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).border(1.dp, WordColors.Pressed).padding(10.dp)) {
                if (draft.isEmpty()) Text(if (replyTo != null) "Reply" else "New comment", color = WordColors.Disabled, fontSize = 16.sp)
                BasicTextField(draft, onValueChange = { draft = it }, textStyle = TextStyle(color = WordColors.OnSurface, fontSize = 16.sp), modifier = Modifier.fillMaxWidth())
            }
            Text(
                "Post",
                color = if (draft.isNotBlank()) WordColors.Accent else WordColors.Disabled,
                fontSize = 16.sp,
                modifier = Modifier.clickable(enabled = draft.isNotBlank()) {
                    val to = replyTo
                    if (to != null) vm.commentCommand("review.reply", JSONObject().put("id", to).put("text", draft))
                    else vm.commentCommand("review.newComment", JSONObject().put("text", draft))
                    draft = ""
                    replyTo = null
                }.padding(12.dp),
            )
        }
    }
}

@Composable
private fun CommentBody(c: AppViewModel.CommentItem) {
    Text(c.author.ifBlank { "Author" }, color = WordColors.OnPill, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    if (c.date.isNotBlank()) Text(c.date.take(10), color = WordColors.OnSurface, fontSize = 12.sp)
    Text(c.text, color = WordColors.OnPill, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Text(label, color = WordColors.Accent, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp))
}
