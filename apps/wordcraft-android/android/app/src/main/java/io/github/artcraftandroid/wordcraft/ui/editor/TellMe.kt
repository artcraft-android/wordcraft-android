package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.LucideIcons
import io.github.artcraftandroid.wordcraft.ui.ViewMode
import io.github.artcraftandroid.wordcraft.ui.WordColors

/**
 * A command Tell Me finds: its name as the palette shows it, the tab it is on, other words people
 * use for it, whether it opens a page (a chevron), and what picking it does. [run] gets the
 * palette's Find too.
 */
internal class TellMeItem(
    val label: String,
    val tab: String,
    val glyph: Glyph?,
    val words: String = "",
    val opens: Boolean = false,
    val run: (AppViewModel, () -> Unit) -> Unit,
)

private fun page(label: String, tab: String, panel: String, glyph: Glyph?, words: String = "") =
    TellMeItem(label, tab, glyph, words, opens = true) { vm, _ -> vm.showPanel(tab, panel) }

private fun cmd(label: String, tab: String, id: String, glyph: Glyph?, words: String = "") =
    TellMeItem(label, tab, glyph, words) { vm, _ -> vm.command(id) }

/** Picks that close the palette first, then act on the document's screen. */
private fun away(label: String, tab: String, glyph: Glyph?, words: String = "", act: (AppViewModel) -> Unit) =
    TellMeItem(label, tab, glyph, words) { vm, _ ->
        vm.closePalette()
        act(vm)
    }

/** The palette's commands Tell Me searches, tab by tab. */
internal val TELL_ME_ITEMS: List<TellMeItem> = listOf(
    page("Font", "Home", "font", LucideIcons.Type.glyph(), "typeface family"),
    page("Font Size", "Home", "size", LucideIcons.ALargeSmall.glyph(), "bigger smaller larger text size"),
    cmd("Bold", "Home", "format.bold", Letters.Bold, "strong heavy"),
    cmd("Italic", "Home", "format.italic", Letters.Italic, "slanted oblique"),
    cmd("Underline", "Home", "format.underline", Letters.Underline),
    cmd("Strikethrough", "Home", "format.strikethrough", Letters.Strike, "cross out"),
    page("Highlight", "Home", "highlight", LucideIcons.Highlighter.glyph(), "marker colour color background"),
    page("Font Color", "Home", "fontColor", Letters.FontColor, "text colour color"),
    cmd("Clear Formatting", "Home", "format.clear", LucideIcons.RemoveFormatting.glyph(), "remove reset plain"),
    cmd("Subscript", "Home", "format.subscript", LucideIcons.Subscript.glyph(), "lower small"),
    cmd("Superscript", "Home", "format.superscript", LucideIcons.Superscript.glyph(), "raise power exponent"),
    page("Change Case", "Home", "changeCase", Letters.ChangeCase, "uppercase lowercase capitals capitalize"),
    page("Bullets", "Home", "bullets", LucideIcons.List.glyph(), "list points"),
    page("Numbering", "Home", "numbering", LucideIcons.ListOrdered.glyph(), "numbered list"),
    cmd("Decrease Indent", "Home", "para.outdent", LucideIcons.ListIndentDecrease.glyph(), "outdent"),
    cmd("Increase Indent", "Home", "para.indent", LucideIcons.ListIndentIncrease.glyph(), "indent"),
    cmd("Align Left", "Home", "para.alignLeft", LucideIcons.AlignLeft.glyph(), "alignment"),
    cmd("Center", "Home", "para.alignCenter", LucideIcons.AlignCenter.glyph(), "centre alignment"),
    cmd("Align Right", "Home", "para.alignRight", LucideIcons.AlignRight.glyph(), "alignment"),
    cmd("Justify", "Home", "para.justify", LucideIcons.AlignJustify.glyph(), "alignment both"),
    cmd("Left-to-Right Text Direction", "Home", "para.ltr", Glyph.Letters("›¶")),
    cmd("Right-to-Left Text Direction", "Home", "para.rtl", Glyph.Letters("¶‹"), "arabic hebrew"),
    page("Paragraph Formatting", "Home", "paragraph", null, "spacing indent"),
    page("Line Spacing", "Home", "lineSpacing", null, "space between lines double single"),
    page("Special Indent", "Home", "specialIndent", null, "first line hanging"),
    page("Styles", "Home", "styles", LucideIcons.Paintbrush.glyph(), "heading title normal quote"),
    TellMeItem("Find", "Home", LucideIcons.Search.glyph(), "search look for replace") { _, find -> find() },
    cmd("Blank Page", "Insert", "insert.blankPage", LucideIcons.FilePlus2.glyph(), "new page"),
    TellMeItem("Scan Document", "Insert", LucideIcons.ScanText.glyph(), "camera photo") { vm, _ -> vm.pictureRequests.trySend("camera") },
    page("Table", "Insert", "table", LucideIcons.Table.glyph(), "grid rows columns"),
    page("Pictures", "Insert", "pictures", LucideIcons.Image.glyph(), "photo image camera"),
    page("Shapes", "Insert", "shapes", LucideIcons.Shapes.glyph(), "rectangle circle arrow line"),
    cmd("Text Box", "Insert", "insert.textBox", LucideIcons.TextCursorInput.glyph()),
    TellMeItem("Link", "Insert", LucideIcons.Link.glyph(), "hyperlink web address url", opens = true) { vm, _ -> vm.linkPane = true },
    TellMeItem("Comment", "Insert", LucideIcons.MessageSquarePlus.glyph(), "note remark") { vm, _ -> vm.openComments(write = true) },
    away("Header & Footer", "Insert", LucideIcons.PanelTop.glyph(), "top bottom of page") { it.command("insert.editHeader") },
    page("Page Number", "Insert", "pageNumber", LucideIcons.Hash.glyph(), "numbers count"),
    cmd("Equation", "Insert", "insert.equation", LucideIcons.Sigma.glyph(), "math formula"),
    cmd("Footnote", "Insert", "references.footnote", LucideIcons.Superscript.glyph(), "note reference"),
    cmd("Endnote", "Insert", "references.endnote", LucideIcons.Subscript.glyph(), "note reference"),
    TellMeItem("Draw with Touch", "Draw", LucideIcons.Pencil.glyph(), "ink pen handwriting sketch") { vm, _ ->
        vm.showTab("Draw")
        if (vm.viewMode != ViewMode.Mobile && !vm.inkOn) vm.setInk(true)
    },
    page("Ink Color", "Draw", "inkColor", LucideIcons.Palette.glyph(), "pen colour color"),
    page("Ink Thickness", "Draw", "thickness", LucideIcons.Minus.glyph(), "pen width"),
    page("Margins", "Layout", "margins", LucideIcons.SquareDashed.glyph(), "page edges"),
    page("Custom Margins", "Layout", "customMargins", LucideIcons.SquareDashed.glyph(), "page edges"),
    page("Orientation", "Layout", "orientation", LucideIcons.RotateCcwSquare.glyph(), "landscape portrait"),
    page("Size", "Layout", "paperSize", LucideIcons.File.glyph(), "paper page letter a4"),
    page("Columns", "Layout", "columns", LucideIcons.Columns2.glyph(), "newspaper"),
    page("Breaks", "Layout", "breaks", LucideIcons.SeparatorHorizontal.glyph(), "page break section"),
    away("Check Document", "Review", LucideIcons.SpellCheck.glyph(), "spelling spell check proofing grammar") { it.openCheck() },
    page("Proofing and Language", "Review", "proofing", null, "spelling language"),
    away("Read Aloud", "Review", LucideIcons.AudioLines.glyph(), "speak listen voice") { it.startReadAloud() },
    page("Word Count", "Review", "wordCount", LucideIcons.FileDigit.glyph(), "characters pages statistics"),
    TellMeItem("New Comment", "Review", LucideIcons.MessageSquarePlus.glyph(), "note remark") { vm, _ -> vm.openComments(write = true) },
    cmd("Delete Comment", "Review", "review.deleteComment", LucideIcons.MessageSquareX.glyph(), "remove"),
    cmd("Resolve Comment", "Review", "review.resolveComment", LucideIcons.CheckCheck.glyph(), "done"),
    cmd("Next Comment", "Review", "review.nextComment", null),
    cmd("Previous Comment", "Review", "review.previousComment", null),
    cmd("Track Changes", "Review", "review.trackChanges", LucideIcons.FilePenLine.glyph(), "revisions edits"),
    page("Display for Review", "Review", "displayForReview", null, "markup simple original changes"),
    page("Accept", "Review", "accept", LucideIcons.Check.glyph(), "changes revisions"),
    page("Reject", "Review", "reject", LucideIcons.X.glyph(), "changes revisions"),
    cmd("Next Change", "Review", "review.nextChange", null, "revision"),
    cmd("Previous Change", "Review", "review.previousChange", null, "revision"),
    TellMeItem("Mobile View", "View", LucideIcons.Smartphone.glyph(), "reflow phone fit") { vm, _ -> if (vm.viewMode != ViewMode.Mobile) vm.toggleMobileView() },
    TellMeItem("Print View", "View", LucideIcons.FileText.glyph(), "pages layout") { vm, _ -> if (vm.viewMode == ViewMode.Mobile) vm.toggleMobileView() },
    away("Headings", "View", LucideIcons.ListTree.glyph(), "navigation outline contents") { it.headingsOpen = true },
    TellMeItem("Zoom In", "View", LucideIcons.ZoomIn.glyph(), "bigger magnify") { vm, _ -> vm.zoom("in") },
    TellMeItem("Zoom Out", "View", LucideIcons.ZoomOut.glyph(), "smaller") { vm, _ -> vm.zoom("out") },
    TellMeItem("One Page", "View", LucideIcons.File.glyph(), "zoom whole") { vm, _ -> vm.zoom("onePage") },
    TellMeItem("Page Width", "View", LucideIcons.ArrowLeftRight.glyph(), "zoom fit") { vm, _ -> vm.zoom("pageWidth") },
)

/** Shown before anything is typed, after the recent picks. */
private val TELL_ME_SUGGESTED = listOf("Styles", "Table", "Pictures", "Track Changes", "Word Count", "Page Number", "Margins", "Find")

/**
 * The items matching [query]: names starting with it first, then names containing it, then
 * those whose other words start with each word typed.
 */
internal fun tellMeMatches(query: String, items: List<TellMeItem> = TELL_ME_ITEMS): List<TellMeItem> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val words = q.split(' ').filter { it.isNotEmpty() }
    fun rank(item: TellMeItem): Int? {
        val name = item.label.lowercase()
        val all = (name + " " + item.words.lowercase()).split(' ', '-', '&').filter { it.isNotEmpty() }
        return when {
            name.startsWith(q) -> 0
            name.contains(q) -> 1
            words.all { w -> all.any { it.startsWith(w) } } -> 2
            else -> null
        }
    }
    return items.mapNotNull { item -> rank(item)?.let { item to it } }.sortedBy { it.second }.map { it.first }
}

/** Tell Me's header: back, the search field, clear, and collapse. */
@Composable
internal fun TellMeHeader(vm: AppViewModel, query: String, onQuery: (String) -> Unit, onSearch: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // the field types with the palette up (the keyboard would otherwise put it away), from the start
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    DisposableEffect(Unit) { onDispose { vm.paletteTyping = false } }
    Row(
        Modifier.fillMaxWidth().height(ROW).background(WordColors.Strip).topEdge(WordColors.StripMore).bottomEdge(WordColors.StripMore),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(LucideIcons.ChevronLeft.glyph(), WordColors.BackIcon, { vm.tellMeOpen = false }, width = 56.dp, height = ROW)
        Box(Modifier.width(1.dp).height(32.dp).background(WordColors.SwitcherDivider))
        Box(Modifier.weight(1f).padding(start = 16.dp, end = 4.dp), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search for a command", color = WordColors.HeaderIconDisabled, fontSize = 16.sp, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = WordColors.OnSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(WordColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { vm.paletteTyping = it.isFocused },
            )
        }
        if (query.isNotEmpty()) BarButton(LucideIcons.X.glyph(), WordColors.HeaderIcon, { onQuery("") }, width = 48.dp, height = ROW)
        BarButton(Glyph.Triangle(up = false), WordColors.CollapseIcon, vm::collapsePalette, width = 43.dp, height = ROW)
    }
}

/** Tell Me in the palette: the search header over the matches, or the recent and suggested picks. */
@Composable
internal fun TellMeSheet(vm: AppViewModel, onFind: () -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query) { tellMeMatches(query) }
    val pick = { item: TellMeItem ->
        vm.noteTellMe(item.label)
        vm.tellMeOpen = false
        item.run(vm, onFind)
    }
    Column(modifier.fillMaxSize().background(WordColors.Surface)) {
        TellMeHeader(vm, query, { query = it }, { matches.firstOrNull()?.let(pick) })
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (query.isBlank()) {
                val byName = TELL_ME_ITEMS.associateBy { it.label }
                val recent = vm.tellMeRecent.mapNotNull { byName[it] }
                if (recent.isNotEmpty()) {
                    item { Caption("Recently Used") }
                    items(recent.size) { i -> TellMeRow(recent[i], pick) }
                }
                val suggested = TELL_ME_SUGGESTED.mapNotNull { byName[it] }.filter { it !in recent }
                item { Caption("Suggested") }
                items(suggested.size) { i -> TellMeRow(suggested[i], pick) }
            } else if (matches.isEmpty()) {
                item { Text("Nothing matches “${query.trim()}”.", color = WordColors.OnSurface, fontSize = 15.sp, modifier = Modifier.padding(16.dp)) }
            } else {
                items(matches.size) { i -> TellMeRow(matches[i], pick) }
            }
        }
    }
}

@Composable
private fun TellMeRow(item: TellMeItem, pick: (TellMeItem) -> Unit) {
    PaletteRow(item.label, item.glyph, onClick = { pick(item) }, chevron = item.opens)
}
