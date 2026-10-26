package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import io.github.artcraftandroid.wordcraft.ui.Motion
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.core.Swatch
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.LucideIcons
import io.github.artcraftandroid.wordcraft.ui.WordColors
import org.json.JSONObject

/** The palette's height, its rows (47 dp and a 1 dp line), list rows and group bands. */
val PALETTE_HEIGHT = 419.dp
internal val ROW = 47.dp
internal val LIST_ROW = 40.dp
internal val BAND = 8.dp

/** The tabs the palette offers, in order. */
val PALETTE_TABS = listOf("Home", "Insert", "Draw", "Layout", "Review", "View")

/** Font sizes the size list offers. */
private val SIZES = listOf(8f, 9f, 10f, 11f, 12f, 14f, 16f, 18f, 20f, 22f, 24f, 26f, 28f, 36f, 48f, 72f)

private val PANEL_TITLES = mapOf(
    "font" to "Font",
    "size" to "Font Size",
    "highlight" to "Highlight",
    "fontColor" to "Font Color",
    "moreColors" to "More Colors",
    "moreFormatting" to "More Formatting",
    "changeCase" to "Change Case",
    "bullets" to "Bullets",
    "numbering" to "Numbering",
    "paragraph" to "Paragraph Formatting",
    "specialIndent" to "Special Indent",
    "lineSpacing" to "Line Spacing",
    "styles" to "Styles",
)

/**
 * The expanded command surface: a tab of commands, or one of their pages. A page comes in from
 * the side as the one it leaves slides a little the other way and fades; another tab's commands
 * come up from below as the last tab's go up.
 */
@Composable
fun PaletteSheet(vm: AppViewModel, onFind: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    AnimatedContent(
        targetState = vm.panels,
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            // deeper comes in from the end side; back, from the start side
            val dir = (if (targetState.size >= initialState.size) 1 else -1) * (if (rtl) -1 else 1)
            val inSpec = tween<IntOffset>(Motion.BASIC, delayMillis = Motion.QUICKEST, easing = Motion.EaseOutSharp)
            val outSpec = tween<IntOffset>(Motion.QUICK, easing = Motion.EaseInSharp)
            (slideInHorizontally(inSpec) { (60 * density).roundToInt() * dir } + fadeIn(tween(Motion.BASIC, delayMillis = Motion.QUICKEST, easing = Motion.EaseOutSharp))) togetherWith
                (slideOutHorizontally(outSpec) { -(40 * density).roundToInt() * dir } + fadeOut(tween(Motion.QUICK, easing = Motion.EaseInSharp)))
        },
        label = "palettePages",
    ) { panels ->
        val panel = panels.lastOrNull()
        Column(Modifier.fillMaxSize().background(WordColors.Surface)) {
            if (panel == null && vm.tellMeOpen) {
                TellMeSheet(vm, onFind, Modifier.weight(1f))
            } else if (panel == null) {
                TabHeader(vm, vm.paletteTab)
                AnimatedContent(
                    targetState = vm.paletteTab,
                    modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                    transitionSpec = {
                        (slideInVertically(tween(Motion.SHORT, delayMillis = Motion.QUICKEST, easing = Motion.EaseOutSharp)) { TAB_IN_PX } + fadeIn(tween(Motion.SHORT, delayMillis = Motion.QUICKEST, easing = Motion.EaseOutSharp))) togetherWith
                            (slideOutVertically(tween(Motion.SHORT, easing = Motion.EaseInSharp)) { -TAB_OUT_PX } + fadeOut(tween(Motion.SHORT, easing = Motion.EaseInSharp)))
                    },
                    label = "paletteTabs",
                ) { tab ->
                    Box(Modifier.fillMaxSize().background(WordColors.Surface)) {
                        when (tab) {
                            "Home" -> HomeTab(vm, onFind)
                            else -> if (!TabRows(vm, tab) && !ContextRows(vm, tab)) TabNotYet(tab)
                        }
                    }
                }
            } else {
                PanelHeader(PANEL_TITLES[panel] ?: TAB_PANEL_TITLES[panel] ?: CONTEXT_PANEL_TITLES[panel] ?: chartPanelTitle(panel) ?: panel, onBack = vm::popPanel, onClose = vm::collapsePalette)
                Box(Modifier.weight(1f).fillMaxWidth()) { PanelBody(vm, panel) }
            }
        }
    }
}

/** How far another tab's commands come up from, and how far the last tab's go up (pixels). */
private const val TAB_IN_PX = 200
private const val TAB_OUT_PX = 187

@Composable
private fun PanelBody(vm: AppViewModel, panel: String) {
    if (TabPanel(vm, panel) || ContextPanel(vm, panel)) return
    when (panel) {
        "font" -> FontPanel(vm)
        "size" -> SizePanel(vm)
        "highlight" -> HighlightPanel(vm)
        "fontColor" -> FontColorPanel(vm)
        "moreColors" -> MoreColorsPanel(vm)
        "moreFormatting" -> MoreFormattingPanel(vm)
        "changeCase" -> ChangeCasePanel(vm)
        "bullets" -> BulletsPanel(vm)
        "numbering" -> NumberingPanel(vm)
        "paragraph" -> ParagraphPanel(vm)
        "specialIndent" -> SpecialIndentPanel(vm)
        "lineSpacing" -> LineSpacingPanel(vm)
        "styles" -> StylesPanel(vm)
    }
}

/** The tab switcher's list, over the pages at the left edge, ending above the palette. */
@Composable
fun TabSwitcher(vm: AppViewModel, modifier: Modifier = Modifier) {
    val tabs = PALETTE_TABS + listOfNotNull(contextTab(vm.format))
    Column(modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).widthIn(min = 112.dp).shadow(6.dp)) {
        for (tab in tabs) {
            val current = tab == vm.paletteTab
            Box(
                Modifier.fillMaxWidth().height(ROW).background(if (current) WordColors.PopupCurrent else WordColors.PopupFill).clickable { vm.showTab(tab) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(tab, color = if (current) WordColors.OnPopupCurrent else WordColors.OnPopup, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun TabHeader(vm: AppViewModel, tab: String) {
    val info = vm.info
    Row(
        Modifier.fillMaxWidth().height(ROW).background(WordColors.Strip).topEdge(WordColors.StripMore).bottomEdge(WordColors.StripMore),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.widthIn(min = 112.dp).fillMaxHeight().clickable { vm.toggleTabs() }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tab, color = WordColors.Accent, fontSize = 18.sp, maxLines = 1)
            Spacer(Modifier.width(12.dp).weight(1f, fill = false))
            GlyphIcon(LucideIcons.ChevronsUpDown.glyph(), WordColors.SwitchIcon, size = 20.dp)
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.width(1.dp).height(32.dp).background(WordColors.SwitcherDivider))
        Spacer(Modifier.weight(1f))
        BarButton(LucideIcons.Lightbulb.glyph(), WordColors.HeaderIcon, { vm.tellMeOpen = true }, width = 48.dp, height = ROW)
        BarButton(LucideIcons.Undo2.glyph(), WordColors.HeaderIcon, vm::undo, enabled = info?.canUndo == true, width = 48.dp, height = ROW, disabledTint = WordColors.HeaderIconDisabled)
        BarButton(LucideIcons.Redo2.glyph(), WordColors.HeaderIcon, vm::redo, enabled = info?.canRedo == true, width = 48.dp, height = ROW, disabledTint = WordColors.HeaderIconDisabled)
        BarButton(Glyph.Triangle(up = false), WordColors.CollapseIcon, vm::collapsePalette, width = 43.dp, height = ROW)
    }
}

@Composable
private fun PanelHeader(title: String, onBack: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ROW).background(WordColors.Strip).topEdge(WordColors.StripMore).bottomEdge(WordColors.StripMore),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(LucideIcons.ChevronLeft.glyph(), WordColors.BackIcon, onBack, width = 56.dp, height = ROW)
        Box(Modifier.width(1.dp).height(32.dp).background(WordColors.SwitcherDivider))
        Text(title, color = WordColors.Accent, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 16.dp))
        BarButton(Glyph.Triangle(up = false), WordColors.CollapseIcon, onClose, width = 43.dp, height = ROW)
    }
}

@Composable
internal fun Band() {
    Spacer(Modifier.fillMaxWidth().height(BAND).background(WordColors.Divider))
}

/** A row of the palette: an icon at 16 dp, a label at 50 dp, a chevron when it drills in. */
@Composable
internal fun PaletteRow(
    label: String,
    glyph: Glyph?,
    onClick: () -> Unit,
    chevron: Boolean = false,
    pressed: Boolean = false,
    onOpen: (() -> Unit)? = null,
    iconTint: Color = WordColors.OnStrip,
    height: Dp = ROW,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth().height(height + 1.dp).background(if (pressed) WordColors.Pressed else WordColors.Surface).bottomEdge(WordColors.Divider), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).fillMaxHeight().clickable(enabled = enabled, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { if (glyph != null) GlyphIcon(glyph, if (enabled) iconTint else WordColors.Disabled) }
            Spacer(Modifier.width(10.dp))
            Text(label, color = if (enabled) WordColors.OnSurface else WordColors.Disabled, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (chevron && onOpen == null) {
                GlyphIcon(LucideIcons.ChevronRight.glyph(), WordColors.OnStrip, size = 20.dp)
                Spacer(Modifier.width(15.dp))
            }
        }
        if (onOpen != null) {
            Box(Modifier.width(1.dp).height(25.dp).background(WordColors.Divider))
            Box(Modifier.width(50.dp).fillMaxHeight().clickable(enabled = enabled, onClick = onOpen), contentAlignment = Alignment.Center) {
                GlyphIcon(LucideIcons.ChevronRight.glyph(), if (enabled) WordColors.OnStrip else WordColors.Disabled, size = 20.dp)
            }
        }
    }
}

/**
 * A row of equal cells (the toggles, the indents, the alignments), each named by [labels] for
 * screen readers (as the reference names its: Bold, Increase Indent…), on or off as it shows.
 */
@Composable
internal fun CellRow(vararg cells: Triple<Glyph, Boolean, () -> Unit>, labels: List<String> = emptyList()) {
    Row(Modifier.fillMaxWidth().height(ROW + 1.dp).bottomEdge(WordColors.Divider)) {
        for ((i, cell) in cells.withIndex()) {
            val (glyph, on, click) = cell
            val label = labels.getOrNull(i)
            Box(
                Modifier.weight(1f).height(ROW).background(if (on) WordColors.Pressed else WordColors.Surface).clickable(onClick = click).semantics {
                    if (label != null) contentDescription = label
                    selected = on
                },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(glyph, WordColors.OnStrip, size = 26.dp)
            }
        }
    }
}

@Composable
internal fun Scrolling(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), content = content)
}

internal fun json(vararg pairs: Pair<String, Any>): String = JSONObject().apply { for ((k, v) in pairs) put(k, v) }.toString()

@Composable
private fun HomeTab(vm: AppViewModel, onFind: () -> Unit) {
    val f = vm.format
    Scrolling {
        Row(Modifier.fillMaxWidth().height(ROW + 1.dp).bottomEdge(WordColors.Divider), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight().clickable { vm.pushPanel("font") }, contentAlignment = Alignment.CenterStart) {
                Text(f?.font.orEmpty(), color = WordColors.OnSurface, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
            }
            Box(Modifier.width(95.dp).fillMaxHeight().clickable { vm.pushPanel("size") }, contentAlignment = Alignment.CenterStart) {
                Text(f?.size?.let(::sizeLabel).orEmpty(), color = WordColors.OnSurface, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        CellRow(
            Triple(Letters.Bold, f?.bold == true) { vm.command("format.bold") },
            Triple(Letters.Italic, f?.italic == true) { vm.command("format.italic") },
            Triple(Letters.Underline, f?.underline == true) { vm.command("format.underline") },
            Triple(Letters.Strike, f?.strikethrough == true) { vm.command("format.strikethrough") },
            labels = listOf("Bold", "Italic", "Underline", "Strikethrough"),
        )
        PaletteRow(
            "Highlight",
            Glyph.WithBar(LucideIcons.Highlighter.glyph(), hexColor(vm.lastHighlight.second)),
            onClick = { vm.applyHighlight(vm.lastHighlight.first, vm.lastHighlight.second) },
            onOpen = { vm.pushPanel("highlight") },
        )
        PaletteRow(
            "Font Color",
            Glyph.WithBar(Letters.FontColor, hexColor(vm.lastFontColor)),
            onClick = { vm.applyFontColor(vm.lastFontColor) },
            onOpen = { vm.pushPanel("fontColor") },
        )
        PaletteRow("Clear Formatting", LucideIcons.RemoveFormatting.glyph(), onClick = { vm.command("format.clear") })
        PaletteRow("More Formatting", LucideIcons.Subscript.glyph(), onClick = { vm.pushPanel("moreFormatting") }, chevron = true)
        Band()
        PaletteRow("Bullets", LucideIcons.List.glyph(), onClick = { vm.pushPanel("bullets") }, chevron = true, iconTint = WordColors.AccentLight)
        PaletteRow("Numbering", LucideIcons.ListOrdered.glyph(), onClick = { vm.pushPanel("numbering") }, chevron = true, iconTint = WordColors.AccentLight)
        CellRow(
            Triple(LucideIcons.ListIndentDecrease.glyph(), false) { vm.command("para.outdent") },
            Triple(LucideIcons.ListIndentIncrease.glyph(), false) { vm.command("para.indent") },
            Triple(Glyph.Letters("›¶"), f?.rtl == false) { vm.command("para.ltr") },
            Triple(Glyph.Letters("¶‹"), f?.rtl == true) { vm.command("para.rtl") },
            labels = listOf("Decrease Indent", "Increase Indent", "Left-to-Right", "Right-to-Left"),
        )
        CellRow(
            Triple(LucideIcons.AlignLeft.glyph(), f?.align == "left") { vm.command("para.alignLeft") },
            Triple(LucideIcons.AlignCenter.glyph(), f?.align == "center") { vm.command("para.alignCenter") },
            Triple(LucideIcons.AlignRight.glyph(), f?.align == "right") { vm.command("para.alignRight") },
            Triple(LucideIcons.AlignJustify.glyph(), f?.align == "justify" || f?.align == "distribute") { vm.command("para.justify") },
            labels = listOf("Align Left", "Center", "Align Right", "Justify"),
        )
        PaletteRow("Paragraph Formatting", null, onClick = { vm.pushPanel("paragraph") }, chevron = true)
        Band()
        PaletteRow("Styles", LucideIcons.Paintbrush.glyph(), onClick = { vm.pushPanel("styles") }, chevron = true)
        Band()
        PaletteRow("Find", LucideIcons.Search.glyph(), onClick = onFind)
    }
}

@Composable
private fun TabNotYet(tab: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("The $tab tab is on its way.", color = WordColors.OnSurface, fontSize = 15.sp)
    }
}

/** A drill-in page's group band: 26 dp, with a small label. */
@Composable
internal fun SectionBand(title: String) {
    Box(Modifier.fillMaxWidth().height(26.dp).background(WordColors.Section), contentAlignment = Alignment.CenterStart) {
        Text(title, color = WordColors.OnSurface, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp))
    }
}

/** A drill-in page's 40 dp list row; the current choice is filled. */
@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, trailing: String? = null, indent: Dp = 43.dp, size: Int = 18, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().height(LIST_ROW).background(if (selected) WordColors.Pressed else WordColors.Surface).bottomEdge(WordColors.Divider).clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (enabled) WordColors.OnSurface else WordColors.Disabled, fontSize = size.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = indent))
        if (trailing != null) Text(trailing, color = WordColors.OnSurface, fontSize = size.sp, modifier = Modifier.padding(end = 16.dp))
    }
}

@Composable
private fun FontPanel(vm: AppViewModel) {
    val current = vm.format?.font
    LazyColumn(Modifier.fillMaxSize()) {
        // the document's theme fonts first, as headings and body
        val theme = vm.themeFonts
        if (theme.size == 2) {
            item { SectionBand("Theme Fonts") }
            for ((family, role) in theme.zip(listOf(null, "(Body)"))) {
                item {
                    ChoiceRow(family, false, onClick = {
                        vm.command("format.font", json("name" to family))
                        vm.popPanel()
                    }, trailing = role, size = 15)
                }
            }
        }
        item { SectionBand("All Fonts") }
        items(vm.fonts) { family ->
            ChoiceRow(family, family == current, onClick = {
                vm.command("format.font", json("name" to family))
                vm.popPanel()
            }, size = 15)
        }
    }
}

internal fun sizeLabel(s: Float): String = if (s % 1f == 0f) s.toInt().toString() else s.toString()

@Composable
private fun SizePanel(vm: AppViewModel) {
    val current = vm.format?.size
    LazyColumn(Modifier.fillMaxSize()) {
        items(SIZES) { s ->
            ChoiceRow(sizeLabel(s), current == s, onClick = {
                vm.command("format.size", json("size" to s))
                vm.popPanel()
            }, indent = 16.dp)
        }
    }
}

/** A grid of colour cells (`cellW` × 42 dp, from 16 dp); the current colour has a darker edge. */
@Composable
internal fun SwatchGrid(swatches: List<Swatch>, perRow: Int, cellW: Dp, selected: String?, onPick: (Swatch) -> Unit) {
    Column(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp)) {
        for (row in swatches.chunked(perRow)) {
            Row {
                for (s in row) {
                    val on = s.hex.equals(selected, ignoreCase = true)
                    Box(
                        Modifier.size(cellW, 42.dp).clickable { onPick(s) }.semantics {
                            contentDescription = s.label
                            this.selected = on
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(cellW - 4.dp, 38.dp).background(hexColor(s.hex))
                                .border(if (on) 2.dp else 1.dp, if (on) WordColors.OnStrip else WordColors.Pressed),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightPanel(vm: AppViewModel) {
    val choices = vm.colors ?: return
    val current = vm.format?.highlight
    Scrolling {
        SwatchGrid(choices.highlights, perRow = 11, cellW = 45.dp, selected = current) { vm.applyHighlight(it.name, it.hex) }
        NoColorRow("No Fill", current == null) { vm.applyHighlight("none", "") }
    }
}

@Composable
internal fun NoColorRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ROW).background(if (selected) WordColors.Pressed else WordColors.Surface).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(24.dp).background(Color.White).border(1.dp, WordColors.Pressed))
        Spacer(Modifier.width(10.dp))
        Text(label, color = WordColors.OnSurface, fontSize = 16.sp)
    }
}

@Composable
private fun FontColorPanel(vm: AppViewModel) {
    val choices = vm.colors ?: return
    val current = vm.format?.color
    Scrolling {
        SectionBand("Theme Colors")
        SwatchGrid(choices.theme.flatten(), perRow = 10, cellW = 49.dp, selected = current) { vm.applyFontColor(it.hex) }
        SectionBand("Standard Colors")
        SwatchGrid(choices.standard, perRow = 10, cellW = 49.dp, selected = current) { vm.applyFontColor(it.hex) }
        NoColorRow("Automatic", current == "auto") { vm.applyFontColor("auto") }
        PaletteRow("More Colors", null, onClick = { vm.pushPanel("moreColors") }, chevron = true)
    }
}

/** More colours, free: twelve hues in five shades each, then greys. */
@Composable
private fun MoreColorsPanel(vm: AppViewModel) {
    val current = vm.format?.color
    val swatches = buildList {
        for (light in listOf(0.85f, 0.7f, 0.5f, 0.35f, 0.2f)) {
            for (h in 0 until 10) {
                val c = android.graphics.Color.HSVToColor(floatArrayOf(h * 36f, if (light > 0.5f) 1.6f - light * 1.2f else 1f, if (light > 0.5f) 1f else light * 2f))
                add("%06X".format(c and 0xFFFFFF))
            }
        }
        for (g in 0 until 10) add("%02X%02X%02X".format(255 - g * 28, 255 - g * 28, 255 - g * 28))
    }.map { Swatch(it, it, "#$it") }
    Scrolling {
        SwatchGrid(swatches, perRow = 10, cellW = 49.dp, selected = current) { vm.applyFontColor(it.hex) }
    }
}

@Composable
private fun MoreFormattingPanel(vm: AppViewModel) {
    val f = vm.format
    Scrolling {
        PaletteRow("Subscript", LucideIcons.Subscript.glyph(), onClick = { vm.command("format.subscript") }, pressed = f?.subscript == true)
        PaletteRow("Superscript", LucideIcons.Superscript.glyph(), onClick = { vm.command("format.superscript") }, pressed = f?.superscript == true)
        PaletteRow("Change Case", Letters.ChangeCase, onClick = { vm.pushPanel("changeCase") }, chevron = true)
    }
}

@Composable
private fun ChangeCasePanel(vm: AppViewModel) {
    Scrolling {
        for ((label, mode) in listOf("Sentence case." to "sentence", "lowercase" to "lower", "UPPERCASE" to "upper", "Capitalize Each Word" to "title", "tOGGLE cASE" to "toggle")) {
            ChoiceRow(label, false, onClick = { vm.command("format.changeCase", json("mode" to mode)) }, indent = 16.dp, size = 16)
        }
    }
}

/** A gallery tile: a preview of the choice, filled when it is the current one. */
@Composable
internal fun Tile(preview: String, selected: Boolean, w: Dp, h: Dp, onClick: () -> Unit, align: TextAlign = TextAlign.Center) {
    Box(
        Modifier.size(w, h).background(if (selected) WordColors.Pressed else WordColors.Surface).border(1.dp, WordColors.Pressed).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(preview, color = WordColors.OnStrip, fontSize = 18.sp, textAlign = align, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp))
    }
}

/** What a screen reader says for each bullet of the gallery. */
private val BULLET_NAMES = listOf("Solid Circle", "Hollow Circle", "Solid Square", "Four Diamonds", "Arrow", "Check Mark")

@Composable
private fun BulletsPanel(vm: AppViewModel) {
    val on = vm.format?.bullets == true
    // None, solid circle, hollow circle, solid square, four small diamonds, hollow arrow, check
    val kinds = listOf("•", "○", "■", "❖", "➢", "✓")
    // what each shows in its tile: a bullet as it sits in a list, at the size it prints
    val shown = mapOf("•" to "●", "■" to "▪")
    // the round ones small, as a bullet sits beside text
    val size = mapOf("•" to 9.sp, "○" to 10.sp)
    Scrolling {
        Row(Modifier.padding(start = 21.dp, top = 20.dp), horizontalArrangement = Arrangement.spacedBy(13.3.dp)) {
            ListTile(48.dp, 48.dp, !on, { vm.command("para.bullets", json("off" to true)) }, label = "None") { TileText("None", 11.sp) }
            for ((k, name) in kinds.zip(BULLET_NAMES)) ListTile(48.dp, 48.dp, false, { vm.command("para.bullets", json("kind" to k)) }, label = name) { TileText(shown[k] ?: k, size[k] ?: 15.sp) }
        }
    }
}

@Composable
private fun NumberingPanel(vm: AppViewModel) {
    val on = vm.format?.numbering == true
    // each format shown as a small list of three, and what it applies
    val tiles: List<Pair<List<String>?, () -> Unit>> = listOf(
        null to { vm.command("para.numbering", json("off" to true)) },
        listOf("1.", "2.", "3.") to { vm.command("para.numbering", json("kind" to "numbered")) },
        listOf("1)", "2)", "3)") to { vm.command("para.numbering", json("kind" to "numberedParen")) },
        listOf("I.", "II.", "III.") to { vm.command("para.defineNumber", json("format" to "upperRoman", "text" to "%1.")) },
        listOf("A.", "B.", "C.") to { vm.command("para.numbering", json("kind" to "upperLetter")) },
        listOf("a)", "b)", "c)") to { vm.command("para.defineNumber", json("format" to "lowerLetter", "text" to "%1)")) },
        listOf("a.", "b.", "c.") to { vm.command("para.numbering", json("kind" to "lowerLetter")) },
        listOf("i.", "ii.", "iii.") to { vm.command("para.numbering", json("kind" to "lowerRoman")) },
    )
    Scrolling {
        Column(Modifier.padding(start = 10.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            for (row in tiles.chunked(7)) {
                Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    for ((labels, click) in row) {
                        ListTile(57.dp, 56.dp, labels == null && !on, click, label = labels?.joinToString(" ") ?: "None") {
                            if (labels == null) TileText("None", 11.sp) else NumberPreview(labels)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParagraphPanel(vm: AppViewModel) {
    val f = vm.format
    Scrolling {
        PaletteRow("Special Indent", null, onClick = { vm.pushPanel("specialIndent") }, chevron = true)
        Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.BottomStart) {
            Text("Line Spacing:", color = WordColors.OnSurface, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp))
        }
        PaletteRow(f?.lineSpacing ?: "1.0", null, onClick = { vm.pushPanel("lineSpacing") }, chevron = true)
        val before = (f?.spaceBefore ?: 0f) > 0f
        val after = (f?.spaceAfter ?: 0f) > 0f
        PaletteRow(if (before) "Remove Space Before Paragraph" else "Add Space Before Paragraph", null, onClick = { vm.command("para.addSpaceBefore") })
        PaletteRow(if (after) "Remove Space After Paragraph" else "Add Space After Paragraph", null, onClick = { vm.command("para.removeSpaceAfter") })
    }
}

@Composable
private fun SpecialIndentPanel(vm: AppViewModel) {
    val first = vm.format?.firstLine ?: 0f
    Scrolling {
        ChoiceRow("None", first == 0f, onClick = { vm.command("para.indents", json("firstLine" to 0)) }, indent = 16.dp, size = 16)
        ChoiceRow("First Line", first > 0f, onClick = { vm.command("para.indents", json("firstLine" to 36)) }, indent = 16.dp, size = 16)
        ChoiceRow("Hanging", first < 0f, onClick = { vm.command("para.indents", json("firstLine" to -36)) }, indent = 16.dp, size = 16)
    }
}

@Composable
private fun LineSpacingPanel(vm: AppViewModel) {
    val current = vm.format?.lineSpacing
    Scrolling {
        for (v in listOf("1.0", "1.15", "1.5", "2.0", "2.5", "3.0")) {
            ChoiceRow(v, current == v, onClick = {
                vm.command("para.lineSpacing", json("value" to v.toDouble()))
                vm.popPanel()
            }, indent = 16.dp, size = 16)
        }
    }
}

@Composable
private fun StylesPanel(vm: AppViewModel) {
    val current = vm.format?.styleName
    // the document's own styles, then the recommended ones, each drawn in its style
    LaunchedEffect(vm.info?.documentId, vm.info?.revision) { vm.loadStyleLooks() }
    LazyColumn(Modifier.fillMaxSize()) {
        items(vm.styleLooks) { look ->
            StyleRow(look, look.name == current) { vm.command("para.style", json("style" to look.id)) }
        }
    }
}
