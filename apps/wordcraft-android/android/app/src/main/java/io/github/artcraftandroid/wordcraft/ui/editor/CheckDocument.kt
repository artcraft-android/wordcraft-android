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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.LucideIcons
import io.github.artcraftandroid.wordcraft.ui.WordColors

/**
 * Check Document, in the palette's place as the reference's checker pane is: a bar with its
 * name, Settings and Close; the number of issues; a card for each (tap it to go there, a
 * suggestion to make it). Settings has the Spelling and Grammar switches.
 */
@Composable
fun CheckSheet(vm: AppViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(vm.checkSettings, vm.checkSpelling, vm.markGrammar) { if (!vm.checkSettings) vm.loadIssues() }
    Column(modifier.background(WordColors.Surface)) {
        if (vm.checkSettings) {
            Row(Modifier.fillMaxWidth().height(50.dp).background(WordColors.Bar), verticalAlignment = Alignment.CenterVertically) {
                BarButton(LucideIcons.ArrowLeft.glyph(), WordColors.OnBar, { vm.checkSettings = false }, width = 56.dp, height = 50.dp)
                Text("Settings", color = WordColors.OnBar, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                BarButton(LucideIcons.X.glyph(), WordColors.OnBar, { vm.checkOpen = false }, width = 56.dp, height = 50.dp)
            }
            CheckSwitch("Spelling", vm.checkSpelling) { vm.setProofing(spelling = it) }
            CheckSwitch("Grammar", vm.markGrammar) { vm.setProofing(grammar = it) }
            return@Column
        }
        Row(Modifier.fillMaxWidth().height(50.dp).background(WordColors.Bar), verticalAlignment = Alignment.CenterVertically) {
            Text("Proofing", color = WordColors.OnBar, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
            BarButton(LucideIcons.Settings.glyph(), WordColors.OnBar, { vm.checkSettings = true }, width = 56.dp, height = 50.dp)
            BarButton(LucideIcons.X.glyph(), WordColors.OnBar, { vm.checkOpen = false }, width = 56.dp, height = 50.dp)
        }
        // the count, a pill, over the cards
        Row(Modifier.padding(top = 1.dp).fillMaxWidth().height(43.dp).background(WordColors.Bar), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(start = 10.dp).size(44.dp, 22.dp).border(1.dp, WordColors.OnBar, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Text(vm.issues.size.toString(), color = WordColors.OnBar, fontSize = 15.sp)
            }
            Text("All issues", color = WordColors.OnBar, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.height(0.dp)) }
            items(vm.issues) { i -> IssueCard(vm, i) }
        }
    }
}

/** An issue: what kind it is, its text, and its suggestions (Ignore All for a word). */
@Composable
private fun IssueCard(vm: AppViewModel, i: AppViewModel.ProofIssue) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).border(1.dp, WordColors.Divider).clickable { vm.goToIssue(i) }.padding(12.dp)) {
        Text(if (i.spelling) "Spelling" else i.message, color = WordColors.Disabled, fontSize = 13.sp)
        Text(i.text, color = WordColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (s in i.suggestions.take(4)) {
                Text(s, color = WordColors.Accent, fontSize = 15.sp, modifier = Modifier.clickable { vm.fixIssue(i, s) })
            }
            if (i.spelling) Text("Ignore All", color = WordColors.OnSurface, fontSize = 15.sp, modifier = Modifier.clickable { vm.fixIssue(i, null) })
        }
    }
}

/** A switch row of the Settings page: its label at 16 dp, the switch at the right. */
@Composable
private fun CheckSwitch(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(39.dp).clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = WordColors.OnSurface, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
        SmallSwitch(on, Modifier.padding(end = 16.dp))
    }
}
