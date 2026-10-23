package io.github.artcraftandroid.wordcraft.ui

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.R
import io.github.artcraftandroid.wordcraft.engine.RecentFile
import io.github.artcraftandroid.wordcraft.ui.editor.bottomEdge
import io.github.artcraftandroid.wordcraft.ui.editor.topEdge
import java.util.Calendar

/**
 * The landing page: the app's header (search, create new), the Recent list in date groups, and
 * a bottom bar with Recent and Open.
 */
@Composable
fun StartScreen(vm: AppViewModel, onOpen: () -> Unit) {
    var tab by remember { mutableStateOf("Recent") }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    // Back ends a search before it leaves the app
    androidx.activity.compose.BackHandler(enabled = searching) {
        searching = false
        query = ""
    }
    Box(Modifier.fillMaxSize().background(WordColors.Surface).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_launcher_tile), contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape))
                Spacer(Modifier.width(14.dp))
                if (searching) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = WordColors.OnBar, fontSize = 18.sp),
                        modifier = Modifier.weight(1f),
                    )
                    StartIcon(LucideIcons.X) {
                        searching = false
                        query = ""
                    }
                } else {
                    Text(tab, color = WordColors.OnPill, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    StartIcon(LucideIcons.Search) { searching = true }
                    StartIcon(LucideIcons.Plus) { creating = true }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    "Recent" -> RecentList(vm, vm.recent.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }, onOpen)
                    else -> OpenList(vm, onOpen)
                }
            }
            Row(Modifier.fillMaxWidth().height(56.dp).topEdge(WordColors.BarEdge)) {
                BottomTab("Recent", LucideIcons.Clock, tab == "Recent", Modifier.weight(1f)) { tab = "Recent" }
                BottomTab("Open", LucideIcons.FolderOpen, tab == "Open", Modifier.weight(1f)) { tab = "Open" }
            }
        }
        if (creating) NewSheet(vm, onClose = { creating = false })
    }
}

@Composable
private fun StartIcon(icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = WordColors.OnBar)
    }
}

@Composable
private fun BottomTab(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (selected) WordColors.Accent else WordColors.OnBar
    Column(modifier.clickable(onClick = onClick).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = 12.sp)
    }
}

/** Today, Yesterday, This week, Earlier. */
private fun groupOf(time: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val days = ((now.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis - then.timeInMillis) / DateUtils.DAY_IN_MILLIS).toInt()
    return when {
        then.timeInMillis >= now.timeInMillis -> "Today"
        days < 1 -> "Yesterday"
        days < 7 -> "This week"
        else -> "Earlier"
    }
}

@Composable
private fun RecentList(vm: AppViewModel, files: List<RecentFile>, onOpen: () -> Unit) {
    if (files.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Documents you open show here.", color = WordColors.OnSurface, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Open a document",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(WordColors.Accent).clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        return
    }
    val groups = files.groupBy { groupOf(it.opened) }
    LazyColumn(Modifier.fillMaxSize()) {
        for (g in listOf("Today", "Yesterday", "This week", "Earlier")) {
            val list = groups[g] ?: continue
            item(key = "h-$g") {
                Text(g, color = WordColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
            }
            items(list, key = { it.uri }) { r -> RecentRow(vm, r) }
        }
    }
}

@Composable
private fun RecentRow(vm: AppViewModel, r: RecentFile) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(64.dp).clickable { vm.openRecent(r) }.bottomEdge(WordColors.Divider), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(16.dp))
        Icon(LucideIcons.FileText, contentDescription = null, tint = WordColors.Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name.substringBeforeLast('.', r.name), color = WordColors.OnPill, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                r.place,
                color = WordColors.OnSurface,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            StartIcon(LucideIcons.EllipsisVertical) { menu = true }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Add to Home screen") }, onClick = {
                    menu = false
                    vm.pinToHome(r)
                })
                DropdownMenuItem(text = { Text("Remove from list") }, onClick = {
                    menu = false
                    vm.removeRecent(r.uri)
                })
            }
        }
    }
}

@Composable
private fun OpenList(vm: AppViewModel, onOpen: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        OpenRow(LucideIcons.FolderOpen, "Browse", "Documents on this phone and in your file apps", onClick = onOpen)
        OpenRow(LucideIcons.FilePlus2, "New blank document", "Start writing", enabled = vm.engine != null) { vm.newDocument("blank") }
    }
}

@Composable
private fun OpenRow(icon: ImageVector, title: String, detail: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).clickable(enabled = enabled, onClick = onClick).bottomEdge(WordColors.Divider), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(16.dp))
        Icon(icon, contentDescription = null, tint = WordColors.Accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = WordColors.OnPill, fontSize = 16.sp)
            Text(detail, color = WordColors.OnSurface, fontSize = 13.sp)
        }
    }
}

/** Create new: a blank document or one of WordCraft's own templates. */
@Composable
private fun NewSheet(vm: AppViewModel, onClose: () -> Unit) {
    // Back closes the sheet, not the app
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x99000000)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(WordColors.Surface).clickable {}.padding(bottom = 12.dp),
        ) {
            Text("New", color = WordColors.OnPill, fontSize = 20.sp, modifier = Modifier.padding(16.dp))
            for ((label, template) in listOf("Blank document" to "blank", "Sample" to "sample", "Letter" to "letter", "Resume" to "resume", "Report" to "report")) {
                OpenRow(if (template == "blank") LucideIcons.FilePlus2 else LucideIcons.FileText, label, if (template == "blank") "An empty page" else "A WordCraft template", enabled = vm.engine != null) {
                    onClose()
                    vm.newDocument(template)
                }
            }
        }
    }
}
