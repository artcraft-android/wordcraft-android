package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.LucideIcons
import io.github.artcraftandroid.wordcraft.ui.WordColors

/**
 * Text Encoding: a text file that isn't Unicode is read in a guessed encoding, so before it opens
 * the encoding can be chosen, with the start of the text shown read in it. Open opens it so; the
 * cross or Back leaves it unopened.
 */
@Composable
fun TextEncodingScreen(vm: AppViewModel) {
    val choice = vm.encodingChoice ?: return
    BackHandler { vm.cancelEncoding() }
    val list = rememberLazyListState()
    // the guessed encoding in view
    LaunchedEffect(choice) {
        val i = choice.offer.encodings.indexOfFirst { it.id == choice.offer.detected }
        if (i > 2) list.scrollToItem(i - 2)
    }
    Column(Modifier.fillMaxSize().background(WordColors.StatusBand).statusBarsPadding().background(WordColors.SettingsBack).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).background(WordColors.Bar), verticalAlignment = Alignment.CenterVertically) {
            BarButton(LucideIcons.X.glyph(), WordColors.OnBar, { vm.cancelEncoding() }, width = 56.dp, height = 56.dp)
            Text("Text Encoding", color = WordColors.OnBar, fontSize = 20.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
            Text(
                "Open",
                color = WordColors.Accent,
                fontSize = 16.sp,
                modifier = Modifier.clickable { vm.openInEncoding() }.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
        Text(
            "Choose the encoding that makes the text read correctly.",
            color = WordColors.SettingsValue,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
        )
        Box(
            Modifier.fillMaxWidth().height(180.dp).padding(12.dp).background(Color.White).border(1.dp, WordColors.BarEdge),
        ) {
            Text(
                vm.encodingPreview,
                color = Color.Black,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
            )
        }
        LazyColumn(Modifier.weight(1f), state = list) {
            items(choice.offer.encodings, key = { it.id }) { enc ->
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clickable { vm.chooseEncoding(enc.id) }.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = enc.id == vm.encodingLabel,
                        onClick = { vm.chooseEncoding(enc.id) },
                        colors = RadioButtonDefaults.colors(selectedColor = WordColors.Accent),
                    )
                    Text(enc.name, color = WordColors.OnSurface, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
