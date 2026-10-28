package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.LucideIcons
import io.github.artcraftandroid.wordcraft.ui.WordColors

/**
 * The header's overflow: a bottom sheet sliding up over a scrim, with a grab handle, then 48 dp
 * rows (a 24 dp icon at 20 dp, the label at 73 dp), a line, and Settings. Reading lists what the
 * floating pill doesn't have; editing adds drawing, sharing, Read Aloud, Headings and Dictation.
 */
@Composable
fun OverflowSheet(vm: AppViewModel, visible: Boolean, onSaveAs: () -> Unit, modifier: Modifier = Modifier) {
    val close = { vm.overflowOpen = false }
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(io.github.artcraftandroid.wordcraft.ui.Motion.SHEET)), exit = fadeOut(tween(io.github.artcraftandroid.wordcraft.ui.Motion.SHEET))) {
            Box(Modifier.fillMaxSize().background(WordColors.Scrim).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close))
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(io.github.artcraftandroid.wordcraft.ui.Motion.SHEET, easing = FastOutSlowInEasing)) { it },
            exit = slideOutVertically(tween(io.github.artcraftandroid.wordcraft.ui.Motion.SHEET, easing = FastOutSlowInEasing)) { it },
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)).background(WordColors.Sheet)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                // the grip 7.8 dp under the sheet's edge, the rows 19.5 dp under it, as the reference's
                Box(Modifier.fillMaxWidth().height(19.5.dp), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.padding(top = 7.8.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(WordColors.SheetGrip))
                }
                val editing = vm.editing
                // drawing is on the pages only, not in Mobile View
                if (editing && vm.viewMode != io.github.artcraftandroid.wordcraft.ui.ViewMode.Mobile) {
                    OverflowRow("Draw with Pen", LucideIcons.PenTool.glyph(), on = vm.inkOn) {
                        vm.setInk(!vm.inkOn)
                        close()
                    }
                }
                if (editing) {
                    OverflowRow("Share", LucideIcons.Share2.glyph()) {
                        close()
                        vm.share(asPdf = false)
                    }
                }
                OverflowRow("Share as PDF", LucideIcons.FileDown.glyph()) {
                    close()
                    vm.share(asPdf = true)
                }
                OverflowRow("Save", LucideIcons.Save.glyph()) {
                    close()
                    vm.save()
                }
                OverflowRow("Save As", LucideIcons.FilePenLine.glyph()) {
                    close()
                    onSaveAs()
                }
                // in a dark theme the pages can be shown light (and back)
                if (WordColors.dark) {
                    val light = vm.lightBackground
                    OverflowRow(if (light) "Switch to Dark Background" else "Switch to Light Background", LucideIcons.SunMoon.glyph()) {
                        close()
                        vm.lightBackground = !light
                    }
                }
                if (editing) {
                    OverflowRow("Read Aloud", LucideIcons.AudioLines.glyph()) {
                        close()
                        vm.startReadAloud()
                    }
                    OverflowRow("Headings", LucideIcons.ListTree.glyph()) {
                        close()
                        vm.headingsOpen = true
                    }
                }
                OverflowRow("History", LucideIcons.History.glyph(), enabled = false) {}
                OverflowRow("Print", LucideIcons.Printer.glyph()) {
                    close()
                    vm.print()
                }
                if (editing) {
                    OverflowRow("Dictation", LucideIcons.Mic.glyph(), switch = false) {
                        close()
                        vm.dictationRequests.trySend(Unit)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(WordColors.Divider))
                OverflowRow("Settings", LucideIcons.Settings.glyph()) {
                    close()
                    vm.openSettings()
                }
            }
        }
    }
}

/** A row of the overflow: its icon, its label, and a switch when [switch] is given (on or off). */
@Composable
private fun OverflowRow(label: String, glyph: Glyph, enabled: Boolean = true, on: Boolean = false, switch: Boolean? = null, onClick: () -> Unit) {
    val tint = if (enabled) WordColors.Accent else WordColors.Disabled
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(if (on) WordColors.Divider else Color.Transparent).clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(20.dp))
        GlyphIcon(glyph, tint)
        Spacer(Modifier.width(29.dp))
        Text(label, color = if (enabled) WordColors.OnPill else WordColors.Disabled, fontSize = 16.sp)
        if (switch != null) {
            Spacer(Modifier.width(28.dp))
            SmallSwitch(switch)
        }
    }
}

/** A small switch: a rounded track, accent when on, with a round thumb. */
@Composable
internal fun SmallSwitch(on: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp, 24.dp).background(if (on) WordColors.Accent else WordColors.Pressed, RoundedCornerShape(12.dp)),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(3.dp).size(18.dp).background(if (on) Color.White else WordColors.Disabled, androidx.compose.foundation.shape.CircleShape))
    }
}

/**
 * Settings: Display Preferences (the theme), General (how documents open, AutoSave) and About
 * (the version, what WordCraft is, the open material it ships with). Sections have accent titles;
 * an item is its title over its value, with lines between the sections.
 */
@Composable
fun SettingsScreen(vm: AppViewModel, engineVersion: String) {
    androidx.activity.compose.BackHandler { vm.closeSettings() }
    var picking by remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(WordColors.StatusBand).statusBarsPadding().background(WordColors.SettingsBack).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).background(WordColors.Bar), verticalAlignment = Alignment.CenterVertically) {
            BarButton(LucideIcons.ArrowLeft.glyph(), WordColors.OnBar, { vm.closeSettings() }, width = 56.dp, height = 56.dp)
            Text("Settings", color = WordColors.OnBar, fontSize = 20.sp, modifier = Modifier.padding(start = 16.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SettingsSection("Display Preferences")
            SettingsItem("Theme", THEMES[vm.themeChoice] ?: "System Default") { picking = true }
            SettingsLine()
            SettingsSection("General")
            SettingsSwitch("Default View", "Start WordCraft in Mobile View", vm.startInMobileView, vm::chooseStartInMobileView)
            SettingsSwitch("AutoSave", "Automatically save files", vm.autoSaveOn, vm::chooseAutoSave)
            SettingsLine()
            SettingsSection("About")
            SettingsItem("WordCraft", "Version $engineVersion")
            Text(
                "An open-source word processor for Android, built on the WordCraft engine. It is not affiliated with or endorsed by Microsoft; Word is a trademark of Microsoft.",
                color = WordColors.SettingsValue,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
            SettingsItem("Open material", OPEN_MATERIAL.joinToString("\n"))
            Spacer(Modifier.height(24.dp))
        }
    }
    if (picking) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Theme") },
            text = {
                Column {
                    for ((key, label) in THEMES) {
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).clickable {
                                vm.chooseTheme(key)
                                picking = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = vm.themeChoice == key,
                                onClick = {
                                    vm.chooseTheme(key)
                                    picking = false
                                },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = WordColors.Accent),
                            )
                            Text(label, color = WordColors.OnSurface, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { picking = false }) { Text("Cancel") } },
            containerColor = WordColors.Surface,
            titleContentColor = WordColors.OnSurface,
        )
    }
}

/** The themes Settings offers. */
private val THEMES = linkedMapOf("system" to "System Default", "light" to "Light", "dark" to "Dark")

/** What WordCraft ships with that others made, and their licences. */
private val OPEN_MATERIAL = listOf(
    "Inter typeface: SIL Open Font License 1.1",
    "Lucide icons: ISC License",
    "Carlito and Caladea typefaces: SIL Open Font License 1.1",
    "STIX Two Math typeface: SIL Open Font License 1.1",
    "Courier Prime typeface: SIL Open Font License 1.1",
    "Liberation typefaces: SIL Open Font License 1.1",
    "Source Sans 3, Source Serif 4, JetBrains Mono: SIL Open Font License 1.1",
)

@Composable
private fun SettingsSection(title: String) {
    Text(title, color = WordColors.Accent, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 14.dp, bottom = 6.dp))
}

@Composable
private fun SettingsLine() {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(WordColors.BarEdge))
}

/** An item: its title over its value; tappable when [onClick] is given. */
@Composable
private fun SettingsItem(title: String, value: String, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(title, color = WordColors.OnSettings, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        Text(value, color = WordColors.SettingsValue, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A switch: the group's title, what it does, then On or Off beside the switch. */
@Composable
private fun SettingsSwitch(title: String, what: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(title, color = WordColors.OnSettings, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        Text(what, color = WordColors.OnSettings, fontSize = 14.sp, modifier = Modifier.padding(start = 5.dp, top = 14.dp))
        Row(Modifier.padding(start = 5.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (on) "On" else "Off", color = WordColors.SettingsValue, fontSize = 14.sp, modifier = Modifier.width(56.dp))
            SmallSwitch(on)
        }
    }
}
