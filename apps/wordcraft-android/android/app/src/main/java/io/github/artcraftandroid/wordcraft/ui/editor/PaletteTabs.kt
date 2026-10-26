package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.LucideIcons
import io.github.artcraftandroid.wordcraft.ui.ViewMode
import io.github.artcraftandroid.wordcraft.ui.WordColors
import java.util.Locale
import kotlin.math.abs
import org.json.JSONObject

/** The titles of these tabs' pages. */
val TAB_PANEL_TITLES = mapOf(
    "table" to "Table",
    "pictures" to "Pictures",
    "shapes" to "Shapes",
    "pageNumber" to "Page Number",
    "numberFormat" to "Number Format",
    "margins" to "Margins",
    "customMargins" to "Custom Margins",
    "orientation" to "Orientation",
    "paperSize" to "Size",
    "columns" to "Columns",
    "breaks" to "Breaks",
    "proofing" to "Proofing and Language",
    "language" to "Language",
    "link" to "Link",
    "wordCount" to "Word Count",
    "deleteComment" to "Delete",
    "displayForReview" to "Display for Review",
    "accept" to "Accept",
    "reject" to "Reject",
    "inkColor" to "Color",
    "thickness" to "Thickness",
)

/** The page a tab row opened, or null when [panel] isn't one of these tabs'. */
@Composable
fun TabPanel(vm: AppViewModel, panel: String): Boolean {
    when (panel) {
        "table" -> TablePanel(vm)
        "pictures" -> PicturesPanel(vm)
        "shapes" -> ShapeGalleryPanel(vm)
        "pageNumber" -> PageNumberPanel(vm)
        "numberFormat" -> NumberFormatPanel(vm)
        "margins" -> MarginsPanel(vm)
        "customMargins" -> CustomMarginsPanel(vm)
        "orientation" -> OrientationPanel(vm)
        "paperSize" -> PaperSizePanel(vm)
        "columns" -> ColumnsPanel(vm)
        "breaks" -> BreaksPanel(vm)
        "proofing" -> ProofingPanel(vm)
        "language" -> LanguagePanel(vm)
        "link" -> LinkPanel(vm)
        "wordCount" -> WordCountPanel(vm)
        "deleteComment" -> SimpleChoices(listOf("Delete" to { vm.command("review.deleteComment") }, "Delete All Comments in Document" to { vm.command("review.deleteComment", json("all" to true)) }))
        "displayForReview" -> DisplayForReviewPanel(vm)
        "accept" -> SimpleChoices(listOf("Accept and Move to Next" to { vm.command("review.accept"); vm.command("review.nextChange") }, "Accept All Changes" to { vm.command("review.acceptAll") }))
        "reject" -> SimpleChoices(listOf("Reject and Move to Next" to { vm.command("review.reject"); vm.command("review.nextChange") }, "Reject All Changes" to { vm.command("review.rejectAll") }))
        "inkColor" -> InkColorPanel(vm)
        "thickness" -> ThicknessPanel(vm)
        else -> return false
    }
    return true
}

/** The tab's own rows, or false when [tab] isn't one of these. */
@Composable
fun TabRows(vm: AppViewModel, tab: String): Boolean {
    when (tab) {
        "Insert" -> InsertTab(vm)
        "Draw" -> DrawTab(vm)
        "Layout" -> LayoutTab(vm)
        "Review" -> ReviewTab(vm)
        "View" -> ViewTab(vm)
        else -> return false
    }
    return true
}

@Composable
private fun SimpleChoices(items: List<Pair<String, () -> Unit>>) {
    Scrolling { for ((label, click) in items) PaletteRow(label, null, onClick = click) }
}

/** A caption over a combo row ("Number Format:", "Display for Review"). */
@Composable
internal fun Caption(text: String) {
    Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.BottomStart) {
        Text(text, color = WordColors.OnSurface, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp))
    }
}

/** A checkbox row: the box at 16 dp, the label after it. */
@Composable
internal fun CheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(ROW + 1.dp).bottomEdge(WordColors.Divider).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(4.dp))
        Checkbox(checked, onCheckedChange = onChange, colors = CheckboxDefaults.colors(checkedColor = WordColors.Accent))
        Text(label, color = WordColors.OnSurface, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Insert.

@Composable
private fun InsertTab(vm: AppViewModel) {
    Scrolling {
        PaletteRow("Blank Page", LucideIcons.FilePlus2.glyph(), onClick = { vm.command("insert.blankPage") })
        PaletteRow("Scan Document", LucideIcons.ScanText.glyph(), onClick = { vm.pictureRequests.trySend("camera") })
        PaletteRow("Table", LucideIcons.Table.glyph(), onClick = { vm.pushPanel("table") }, chevron = true)
        PaletteRow("Pictures", LucideIcons.Image.glyph(), onClick = { vm.pushPanel("pictures") }, chevron = true)
        PaletteRow("Shapes", LucideIcons.Shapes.glyph(), onClick = { vm.pushPanel("shapes") }, chevron = true)
        PaletteRow("Text Box", LucideIcons.TextCursorInput.glyph(), onClick = { vm.command("insert.textBox") })
        PaletteRow("Link", LucideIcons.Link.glyph(), onClick = { vm.pushPanel("link") }, chevron = true)
        PaletteRow("Comment", LucideIcons.MessageSquarePlus.glyph(), onClick = { vm.commentsOpen = true })
        PaletteRow("Header & Footer", LucideIcons.PanelTop.glyph(), onClick = {
            vm.command("insert.editHeader")
            vm.closePalette()
        })
        PaletteRow("Page Number", LucideIcons.Hash.glyph(), onClick = { vm.pushPanel("pageNumber") }, chevron = true)
        PaletteRow("Equation", LucideIcons.Sigma.glyph(), onClick = { vm.command("insert.equation") })
        PaletteRow("Footnote", LucideIcons.Superscript.glyph(), onClick = { vm.command("references.footnote") })
        PaletteRow("Endnote", LucideIcons.Subscript.glyph(), onClick = { vm.command("references.endnote") })
    }
}

/** A row with a value between minus and plus buttons (the table's rows and columns). */
@Composable
private fun StepperRow(label: String, glyph: Glyph, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(ROW + 1.dp).bottomEdge(WordColors.Divider), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(16.dp))
        GlyphIcon(glyph, WordColors.OnStrip)
        Spacer(Modifier.width(16.dp))
        Text(label, color = WordColors.OnSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        BarButton(Glyph.Letters("−"), WordColors.AccentLight, { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first, width = 48.dp, height = ROW, iconSize = 30.dp, label = "Decrease $label")
        Text("$value", color = WordColors.OnSurface, fontSize = 16.sp, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        BarButton(Glyph.Letters("+"), WordColors.AccentLight, { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last, width = 48.dp, height = ROW, iconSize = 30.dp, label = "Increase $label")
        Spacer(Modifier.width(8.dp))
    }
}

/** A filled button across the page, its label in white. */
@Composable
private fun WideButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(36.dp).clip(RoundedCornerShape(4.dp)).background(WordColors.Accent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 16.sp)
    }
}

/** Link: Insert Link… (its pane), then the addresses linked lately, each a tap from linking again. */
@Composable
private fun LinkPanel(vm: AppViewModel) {
    Scrolling {
        PaletteRow("Insert Link...", LucideIcons.Link.glyph(), onClick = { vm.linkPane = true })
        if (vm.recentLinks.isEmpty()) {
            Text("No items available.", color = WordColors.Disabled, fontSize = 15.sp, modifier = Modifier.padding(16.dp))
        }
        for (address in vm.recentLinks) {
            PaletteRow(address, LucideIcons.Link.glyph(), onClick = { vm.insertLink("", address) })
        }
    }
}

@Composable
private fun TablePanel(vm: AppViewModel) {
    var rows by remember { mutableIntStateOf(3) }
    var cols by remember { mutableIntStateOf(3) }
    // the button sits at the foot of the page
    Column(Modifier.fillMaxSize()) {
        StepperRow("Rows", LucideIcons.Table.glyph(), rows, 1..20) { rows = it }
        StepperRow("Columns", LucideIcons.Columns2.glyph(), cols, 1..20) { cols = it }
        Spacer(Modifier.weight(1f))
        WideButton("Insert Table") { vm.insertTable(rows, cols) }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun PicturesPanel(vm: AppViewModel) {
    Scrolling {
        PaletteRow("Photos", LucideIcons.Images.glyph(), onClick = { vm.pictureRequests.trySend("photos") })
        PaletteRow("Camera", LucideIcons.Camera.glyph(), onClick = { vm.pictureRequests.trySend("camera") })
    }
}

@Composable
private fun PageNumberPanel(vm: AppViewModel) {
    var withCount by remember { mutableStateOf(false) }
    // the section's number format, shown on its row
    var format by remember { mutableStateOf("decimal") }
    LaunchedEffect(vm.info?.revision) {
        vm.query("layout.section") { out -> format = out?.optString("pageNumFormat")?.ifBlank { null } ?: "decimal" }
    }
    val positions = listOf("top" to "left", "top" to "center", "top" to "right", "bottom" to "left", "bottom" to "center", "bottom" to "right")
    Scrolling {
        for (row in positions.chunked(3)) {
            Row {
                for ((pos, align) in row) {
                    Box(
                        Modifier.weight(1f).height(64.dp).border(1.dp, WordColors.Divider).clickable {
                            val p = JSONObject().put("position", pos).put("align", align)
                            if (withCount) p.put("format", "x of y")
                            vm.command("insert.pageNumber", p.toString())
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        PageGlyph(pos, align)
                    }
                }
            }
        }
        Caption("Number Format:")
        PaletteRow(PAGE_NUMBER_FORMATS.firstOrNull { it.second == format }?.first ?: PAGE_NUMBER_FORMATS.first().first, null, onClick = { vm.pushPanel("numberFormat") }, chevron = true)
        CheckboxRow("Include Page Count", withCount) { withCount = it }
        PaletteRow("Remove Page Numbers", null, onClick = { vm.command("insert.removePageNumbers") })
    }
}

/** The page number formats offered, as their numbers read, and the format each sets. */
private val PAGE_NUMBER_FORMATS = listOf(
    "1, 2, 3, …" to "decimal",
    "- 1 -, - 2 -, - 3 -, …" to "numberInDash",
    "a, b, c, …" to "lowerLetter",
    "A, B, C, …" to "upperLetter",
    "i, ii, iii, …" to "lowerRoman",
    "I, II, III, …" to "upperRoman",
)

/** A small page with a blue # where the page number goes. */
@Composable
private fun PageGlyph(pos: String, align: String) {
    Box(Modifier.size(32.dp, 42.dp).border(1.dp, WordColors.OnStrip).padding(3.dp)) {
        val a = when (align) {
            "left" -> if (pos == "top") Alignment.TopStart else Alignment.BottomStart
            "right" -> if (pos == "top") Alignment.TopEnd else Alignment.BottomEnd
            else -> if (pos == "top") Alignment.TopCenter else Alignment.BottomCenter
        }
        Text("#", color = WordColors.AccentLight, fontSize = 11.sp, modifier = Modifier.align(a))
    }
}

@Composable
private fun NumberFormatPanel(vm: AppViewModel) {
    Scrolling {
        for ((label, f) in PAGE_NUMBER_FORMATS) ChoiceRow(label, false, onClick = {
            vm.command("layout.pageNumberFormat", json("format" to f))
            vm.popPanel()
        }, indent = 16.dp, size = 16)
    }
}

// Draw.

/** The four pens in a row: WordCraft's own black, red, blue and green. */
private val PEN_PRESETS = listOf("000000", "E03131", "1C7ED6", "37B24D")
private val PEN_NAMES = listOf("Black", "Red", "Blue", "Green")

@Composable
private fun DrawTab(vm: AppViewModel) {
    val pen = vm.inkOn && vm.inkTool == "pen"
    // ink goes on the pages: in Mobile View the tab is there but off
    val on = vm.viewMode != ViewMode.Mobile
    Scrolling {
        PaletteRow("Draw with Touch", LucideIcons.Pencil.glyph(), onClick = { vm.setInk(!vm.inkOn) }, pressed = vm.inkOn, enabled = on)
        PaletteRow("Stroke Eraser", LucideIcons.Eraser.glyph(), onClick = { vm.pickTool("eraser") }, pressed = vm.inkOn && vm.inkTool == "eraser", enabled = on)
        PaletteRow("Pen", LucideIcons.PenTool.glyph(), onClick = { vm.pickTool("pen") }, pressed = pen, enabled = on)
        PaletteRow("Highlighter", LucideIcons.Highlighter.glyph(), onClick = { vm.pickTool("highlighter") }, pressed = vm.inkOn && vm.inkTool == "highlighter", enabled = on)
        if (!on) {
            Band()
            CellRow(*PEN_PRESETS.map { Triple(Glyph.Dot(WordColors.Disabled) as Glyph, false) {} }.toTypedArray(), labels = PEN_NAMES)
            PaletteRow("Color", LucideIcons.Palette.glyph(), onClick = {}, chevron = true, enabled = false)
            Band()
            PaletteRow("Thickness", LucideIcons.Minus.glyph(), onClick = {}, chevron = true, enabled = false)
            Band()
            PaletteRow("Ink Editor", LucideIcons.PenLine.glyph(), onClick = {}, enabled = false)
            return@Scrolling
        }
        Band()
        CellRow(
            *PEN_PRESETS.map { hex ->
                Triple(Glyph.Dot(hexColor(hex)) as Glyph, pen && vm.penColor == hex) {
                    vm.pickTool("pen")
                    vm.setInkColor(hex)
                }
            }.toTypedArray(),
            labels = PEN_NAMES,
        )
        PaletteRow("Color", LucideIcons.Palette.glyph(), onClick = { vm.pushPanel("inkColor") }, chevron = true)
        Band()
        PaletteRow("Thickness", LucideIcons.Minus.glyph(), onClick = { vm.pushPanel("thickness") }, chevron = true)
        Band()
        // the pen edits the text: a line through words deletes them, a loop selects, and so on
        PaletteRow("Ink Editor", LucideIcons.PenLine.glyph(), onClick = { vm.useInkEditor(!vm.inkEditor) }, pressed = vm.inkEditor)
    }
}

/** Sixteen ink colours, our own: purples and pinks, grey, warm colours, greens, blues. */
private val INK_COLORS = listOf(
    "5F3DC4", "9C36B5", "D6336C", "F783AC", "A61E4D", "868E96", "E03131", "F76707",
    "FCC419", "94D82D", "37B24D", "0CA678", "15AABF", "74C0FC", "1C7ED6", "1864AB",
)

@Composable
private fun InkColorPanel(vm: AppViewModel) {
    val current = if (vm.inkTool == "highlighter") vm.highlighterColor else vm.penColor
    Scrolling {
        SwatchGrid(INK_COLORS.map { io.github.artcraftandroid.wordcraft.core.Swatch(it, it, "#$it") }, perRow = 8, cellW = 61.dp, selected = current) { vm.setInkColor(it.hex) }
    }
}

@Composable
private fun ThicknessPanel(vm: AppViewModel) {
    val current = if (vm.inkTool == "highlighter") vm.highlighterWidth else vm.penWidth
    val color = hexColor(if (vm.inkTool == "highlighter") vm.highlighterColor else vm.penColor)
    Scrolling {
        for (mm in listOf(0.25f, 0.35f, 0.5f, 0.7f, 1f, 1.5f, 2f, 3.5f, 5f)) {
            val pt = mm * 72f / 25.4f
            Row(
                Modifier.fillMaxWidth().height(52.dp).background(if (abs(current - pt) < 0.01f) WordColors.Pressed else WordColors.Surface).bottomEdge(WordColors.Divider).clickable {
                    vm.setInkWidth(pt)
                    vm.popPanel()
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${sizeLabel(mm)} mm", color = WordColors.OnSurface, fontSize = 16.sp, modifier = Modifier.width(96.dp).padding(start = 16.dp))
                Box(Modifier.weight(1f).padding(end = 24.dp).height((pt * 1.2f).coerceAtLeast(1f).dp).background(color))
            }
        }
    }
}

// Layout.

@Composable
private fun LayoutTab(vm: AppViewModel) {
    Scrolling {
        PaletteRow("Margins", LucideIcons.SquareDashed.glyph(), onClick = { vm.pushPanel("margins") }, chevron = true)
        PaletteRow("Orientation", LucideIcons.RotateCcwSquare.glyph(), onClick = { vm.pushPanel("orientation") }, chevron = true)
        PaletteRow("Size", LucideIcons.File.glyph(), onClick = { vm.pushPanel("paperSize") }, chevron = true)
        PaletteRow("Columns", LucideIcons.Columns2.glyph(), onClick = { vm.pushPanel("columns") }, chevron = true)
        PaletteRow("Breaks", LucideIcons.SeparatorHorizontal.glyph(), onClick = { vm.pushPanel("breaks") }, chevron = true)
    }
}

/** Inches where the phone's region measures in inches, else centimetres. */
private val usesInches: Boolean get() = Locale.getDefault().country in setOf("US", "LR", "MM")

private fun length(pt: Float): String = if (usesInches) "%.2f".format(pt / 72f).trimEnd('0').trimEnd('.') + "\"" else "%.2f".format(pt / 72f * 2.54f).trimEnd('0').trimEnd('.') + " cm"

/** A 73 dp row with a page glyph, a name, and two lines of values. */
/** Margin presets: name, engine preset, top/bottom/left/right in points. */
private val MARGINS = listOf(
    Triple("Normal", "normal", listOf(72f, 72f, 72f, 72f)),
    Triple("Narrow", "narrow", listOf(36f, 36f, 36f, 36f)),
    Triple("Moderate", "moderate", listOf(72f, 72f, 54f, 54f)),
    Triple("Wide", "wide", listOf(72f, 72f, 144f, 144f)),
    Triple("Mirrored", "mirrored", listOf(72f, 72f, 90f, 72f)),
)

@Composable
private fun MarginsPanel(vm: AppViewModel) {
    val p = vm.pageSetup
    fun near(a: Float, b: Float) = abs(a - b) < 0.6f
    Scrolling {
        for ((name, preset, m) in MARGINS) {
            val on = p != null && near(p.marginTop, m[0]) && near(p.marginBottom, m[1]) && near(p.marginLeft, m[2]) && near(p.marginRight, m[3])
            MarginRow(name, m, preset == "mirrored", on, ::length) { vm.command("layout.margins", json("preset" to preset)) }
        }
        PaletteRow("Custom Margins...", null, onClick = { vm.pushPanel("customMargins") }, chevron = true)
    }
}

@Composable
private fun CustomMarginsPanel(vm: AppViewModel) {
    val p = vm.pageSetup ?: return
    val unit = if (usesInches) 72f else 72f / 2.54f
    val fields = listOf("Top" to p.marginTop, "Left" to p.marginLeft, "Right" to p.marginRight, "Bottom" to p.marginBottom)
    Scrolling {
        for ((label, value) in fields) {
            var text by remember(value) { mutableStateOf("%.2f".format(value / unit).trimEnd('0').trimEnd('.')) }
            Caption(label)
            Row(Modifier.fillMaxWidth().height(ROW).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = WordColors.OnSurface, fontSize = 16.sp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                        text.toFloatOrNull()?.let { v ->
                            val pt = v * unit
                            // top and bottom must leave room on the page, as must left and right
                            val ok = when (label) {
                                "Top" -> pt + p.marginBottom < p.height
                                "Bottom" -> pt + p.marginTop < p.height
                                "Left" -> pt + p.marginRight < p.width
                                else -> pt + p.marginLeft < p.width
                            }
                            if (ok) vm.command("layout.margins", json(label.lowercase() to pt)) else text = "%.2f".format(value / unit).trimEnd('0').trimEnd('.')
                        }
                    }),
                    modifier = Modifier.weight(1f).border(1.dp, WordColors.Pressed).padding(8.dp),
                )
                Text(if (usesInches) "in" else "cm", color = WordColors.OnSurface, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun OrientationPanel(vm: AppViewModel) {
    val landscape = vm.pageSetup?.landscape == true
    Scrolling {
        ChoiceRow("Portrait", !landscape, onClick = { vm.command("layout.orientation", json("value" to "portrait")) }, indent = 16.dp, size = 16)
        ChoiceRow("Landscape", landscape, onClick = { vm.command("layout.orientation", json("value" to "landscape")) }, indent = 16.dp, size = 16)
    }
}

/** Paper sizes: the label shown, the engine's name, width and height in points (portrait). */
private val PAPERS = listOf(
    Triple("Letter", "Letter", 612f to 792f),
    Triple("Legal", "Legal", 612f to 1008f),
    Triple("A3", "A3", 841.89f to 1190.55f),
    Triple("A4", "A4", 595.28f to 841.89f),
    Triple("A5", "A5", 419.53f to 595.28f),
    Triple("JIS B4", "B4 (JIS)", 728.5f to 1031.8f),
    Triple("JIS B5", "B5 (JIS)", 515.9f to 728.5f),
)

@Composable
private fun PaperSizePanel(vm: AppViewModel) {
    val p = vm.pageSetup
    Scrolling {
        for ((label, name, size) in PAPERS) {
            val (w, h) = size
            val on = p != null && ((abs(p.width - w) < 0.6f && abs(p.height - h) < 0.6f) || (abs(p.width - h) < 0.6f && abs(p.height - w) < 0.6f))
            PaperRow(label, w, h, on, "${length(w)} x ${length(h)}") { vm.command("layout.size", json("name" to name)) }
        }
    }
}

@Composable
private fun ColumnsPanel(vm: AppViewModel) {
    Scrolling {
        for ((label, n) in listOf("One" to 1, "Two" to 2, "Three" to 3)) {
            ChoiceRow(label, false, onClick = { vm.command("layout.columns", json("count" to n)) }, indent = 16.dp, size = 16)
        }
        ChoiceRow("Left", false, onClick = { vm.command("layout.columns", json("preset" to "left")) }, indent = 16.dp, size = 16)
        ChoiceRow("Right", false, onClick = { vm.command("layout.columns", json("preset" to "right")) }, indent = 16.dp, size = 16)
    }
}

@Composable
private fun BreaksPanel(vm: AppViewModel) {
    val kinds = listOf(
        "Page" to "page", "Column" to "column", "Text Wrapping" to "textWrapping",
        "Next Page" to "nextPage", "Continuous" to "continuous", "Even Page" to "evenPage", "Odd Page" to "oddPage",
    )
    Scrolling {
        SectionBand("Page Breaks")
        for ((label, k) in kinds.take(3)) ChoiceRow(label, false, onClick = { vm.command("layout.break", json("kind" to k)) }, indent = 16.dp, size = 16)
        SectionBand("Section Breaks")
        for ((label, k) in kinds.drop(3)) ChoiceRow(label, false, onClick = { vm.command("layout.break", json("kind" to k)) }, indent = 16.dp, size = 16)
    }
}

// Review.

@Composable
private fun ReviewTab(vm: AppViewModel) {
    val r = vm.review
    Scrolling {
        PaletteRow("Check Document", LucideIcons.SpellCheck.glyph(), onClick = { vm.openCheck() })
        PaletteRow("Proofing and Language", null, onClick = { vm.pushPanel("proofing") }, chevron = true)
        Band()
        PaletteRow("Read Aloud", LucideIcons.AudioLines.glyph(), onClick = {
            vm.closePalette()
            vm.startReadAloud()
        })
        Band()
        PaletteRow("Word Count", LucideIcons.FileDigit.glyph(), onClick = { vm.pushPanel("wordCount") }, chevron = true)
        Band()
        PaletteRow("New Comment", LucideIcons.MessageSquarePlus.glyph(), onClick = { vm.commentsOpen = true })
        // Delete and Resolve act on the comment at the caret
        val here = r?.commentHere == true
        PaletteRow("Delete", LucideIcons.MessageSquareX.glyph(), onClick = { vm.command("review.deleteComment") }, onOpen = { vm.pushPanel("deleteComment") }, enabled = here)
        PaletteRow("Resolve", LucideIcons.CheckCheck.glyph(), onClick = { vm.command("review.resolveComment") }, enabled = here)
        CellRow(
            Triple(Glyph.Letters("‹"), false) { vm.command("review.previousComment") },
            Triple(Glyph.Letters("›"), false) { vm.command("review.nextComment") },
            labels = listOf("Previous Comment", "Next Comment"),
        )
        Band()
        PaletteRow("Track Changes", LucideIcons.FilePenLine.glyph(), onClick = { vm.command("review.trackChanges") }, pressed = r?.trackChanges == true)
        Caption("Display for Review")
        val markup = MARKUP_VIEWS.firstOrNull { it.first == r?.markup }?.second ?: "All Markup"
        PaletteRow(markup, null, onClick = { vm.pushPanel("displayForReview") }, chevron = true, description = "Display for Review combo box, $markup selected")
        PaletteRow("Accept", LucideIcons.Check.glyph(), onClick = { vm.command("review.accept") }, onOpen = { vm.pushPanel("accept") })
        PaletteRow("Reject", LucideIcons.X.glyph(), onClick = { vm.command("review.reject") }, onOpen = { vm.pushPanel("reject") })
        CellRow(
            Triple(Glyph.Letters("‹"), false) { vm.command("review.previousChange") },
            Triple(Glyph.Letters("›"), false) { vm.command("review.nextChange") },
            labels = listOf("Previous Change", "Next Change"),
        )
    }
}

@Composable
private fun ProofingPanel(vm: AppViewModel) {
    var hideAll by remember { mutableStateOf(false) }
    val language = caretLanguage(vm)
    Scrolling {
        Caption("Language")
        PaletteRow(language, null, onClick = { vm.pushPanel("language") }, chevron = true, bare = true, description = "Language combo box, $language selected")
        CheckboxRow("Hide All Proofing Marks", hideAll) {
            hideAll = it
            vm.command("review.proofing", json("value" to !it))
        }
        CheckboxRow("Hide Proofing Marks in Selected Text", false) { if (it) vm.command("review.language", json("noProof" to true)) }
    }
}

/** The name of the language the selection is marked as (its BCP 47 tag from the engine). */
@Composable
private fun caretLanguage(vm: AppViewModel): String {
    var tag by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.query("format.state") { tag = it?.optString("lang")?.ifEmpty { null } } }
    return tag?.let { languageName(it) } ?: ""
}

/**
 * The languages text can be marked as: the phone's own locales that name a region, called what
 * the phone calls them (a language spoken in only one of them by its name alone), alphabetically.
 */
private val MARK_LANGUAGES: List<Pair<String, String>> by lazy {
    val shown = Locale.getDefault()
    // two-letter languages only: the phone also knows many minority languages by three-letter codes
    val regional = Locale.getAvailableLocales().filter { it.language.length == 2 && it.country.length == 2 && it.variant.isEmpty() && it.script.isEmpty() }
    val regions = regional.groupingBy { it.language }.eachCount()
    regional
        .map { it.toLanguageTag() to if (regions[it.language] == 1) it.getDisplayLanguage(shown) else it.getDisplayName(shown) }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase(shown) }
}

private fun languageName(tag: String): String =
    MARK_LANGUAGES.firstOrNull { it.first.equals(tag, ignoreCase = true) }?.second ?: Locale.forLanguageTag(tag).displayName.ifEmpty { tag }

/**
 * Language: "Mark text as:" on a section band over the selection's language (chosen), a band
 * between gaps, then every language; a tap marks the selection and goes back.
 */
@Composable
private fun LanguagePanel(vm: AppViewModel) {
    val current = caretLanguage(vm)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().height(26.dp).background(WordColors.Section), contentAlignment = Alignment.CenterStart) {
                Text("Mark text as:", color = WordColors.OnSurface, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp))
            }
        }
        item { LanguageRow(current, chosen = true) {} }
        item {
            Column {
                Spacer(Modifier.height(13.dp))
                Spacer(Modifier.fillMaxWidth().height(BAND).background(WordColors.Section))
                Spacer(Modifier.height(12.dp))
            }
        }
        items(MARK_LANGUAGES, key = { it.first }) { (tag, name) ->
            LanguageRow(name, chosen = false) {
                vm.command("review.language", json("lang" to tag))
                vm.popPanel()
            }
        }
    }
}

@Composable
private fun LanguageRow(name: String, chosen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).background(if (chosen) WordColors.ListChosen else WordColors.Surface).bottomEdge(WordColors.Divider)
            .clickable(onClick = onClick).semantics { contentDescription = "$name list item"; selected = chosen },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, color = WordColors.OnLists, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 24.dp, end = 16.dp))
    }
}

/** Where Word Count's four rows sit below the header: the first lower than the pitch, as the reference has it. */
private val COUNT_ROW_TOPS = listOf(11.dp, 38.dp, 70.dp, 102.dp)

@Composable
private fun WordCountPanel(vm: AppViewModel) {
    var counts by remember { mutableStateOf<JSONObject?>(null) }
    val withBoxes = vm.countWithBoxes
    LaunchedEffect(withBoxes) { vm.query("review.wordCount", json("includeTextBoxes" to withBoxes)) { counts = it } }
    Scrolling {
        val c = counts
        val rows = listOf("Pages" to "pages", "Words" to "words", "Characters (no spaces)" to "characters", "Characters (with spaces)" to "charactersWithSpaces")
        Box(Modifier.fillMaxWidth().height(141.dp)) {
            for ((i, row) in rows.withIndex()) {
                Row(Modifier.offset(y = COUNT_ROW_TOPS[i]).fillMaxWidth().height(32.dp).padding(start = 20.dp, end = 22.5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(row.first, color = WordColors.OnLists, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(c?.optLong(row.second)?.let { "%,d".format(it) } ?: "", color = WordColors.OnLists, fontSize = 15.sp)
                }
            }
        }
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(WordColors.CountsRule))
        Spacer(Modifier.height(13.dp))
        CountsCheck("Include textboxes, footnotes and endnotes", withBoxes) { vm.chooseCountWithBoxes(it) }
        Spacer(Modifier.height(31.dp))
        CountsCheck("Show Word Count", vm.showWordCount) { vm.chooseShowWordCount(it) }
        Spacer(Modifier.height(16.dp))
    }
}

/** Word Count's check boxes: a plain square (filled grey in dark) with a heavy tick in the text colour, no row lines. */
@Composable
private fun CountsCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(1.5.dp)
    Row(
        Modifier.padding(start = 16.dp).height(27.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(3.dp))
        val tick = WordColors.OnLists
        Box(Modifier.offset(y = 1.5.dp).size(21.dp).background(WordColors.CheckOff, shape).border(1.dp, WordColors.CountsBoxEdge, shape)) {
            if (checked) {
                Canvas(Modifier.fillMaxSize()) {
                    val path = Path().apply {
                        moveTo(size.width * 0.19f, size.height * 0.51f)
                        lineTo(size.width * 0.41f, size.height * 0.76f)
                        lineTo(size.width * 0.80f, size.height * 0.25f)
                    }
                    drawPath(path, tick, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = WordColors.OnLists, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Display for Review's views: the engine's name for each, and its label. */
private val MARKUP_VIEWS = listOf("simpleMarkup" to "Simple Markup", "allMarkup" to "All Markup", "noMarkup" to "No Markup", "original" to "Original")

@Composable
private fun DisplayForReviewPanel(vm: AppViewModel) {
    val current = vm.review?.markup ?: "allMarkup"
    Scrolling {
        for ((id, label) in MARKUP_VIEWS) {
            ChoiceRow(label, id == current, onClick = { vm.command("review.markup", json("value" to id)) }, indent = 16.dp, size = 16)
        }
    }
}

// View.

@Composable
private fun ViewTab(vm: AppViewModel) {
    val mobile = vm.viewMode == ViewMode.Mobile
    Scrolling {
        PaletteRow("Mobile View", LucideIcons.Smartphone.glyph(), onClick = { if (!mobile) vm.toggleMobileView() }, pressed = mobile)
        PaletteRow("Print View", LucideIcons.FileText.glyph(), onClick = { if (mobile) vm.toggleMobileView() }, pressed = !mobile)
        Band()
        PaletteRow("Headings", LucideIcons.ListTree.glyph(), onClick = {
            vm.closePalette()
            vm.headingsOpen = true
        })
        Band()
        // the page zooms belong to the pages; Mobile View has only Zoom In and Out
        if (!mobile) PaletteRow("100%", LucideIcons.Percent.glyph(), onClick = { vm.zoom("100") })
        PaletteRow("Zoom In", LucideIcons.ZoomIn.glyph(), onClick = { vm.zoom("in") })
        PaletteRow("Zoom Out", LucideIcons.ZoomOut.glyph(), onClick = { vm.zoom("out") })
        if (!mobile) {
            PaletteRow("One Page", LucideIcons.File.glyph(), onClick = { vm.zoom("onePage") })
            PaletteRow("Page Width", LucideIcons.ArrowLeftRight.glyph(), onClick = { vm.zoom("pageWidth") })
        }
    }
}

/** The Headings pane: the document's headings as a tree; tapping one goes there. */
@Composable
fun HeadingsSheet(vm: AppViewModel, modifier: Modifier = Modifier) {
    var headings by remember { mutableStateOf<List<Triple<Int, Int, String>>>(emptyList()) }
    // headings with headings under them that show those (the top level's open at first)
    var open by remember { mutableStateOf(emptySet<Int>()) }
    var chosen by remember { mutableStateOf(-1) }
    LaunchedEffect(vm.autoHeadings) {
        vm.query("navigation.headings", JSONObject().put("detect", vm.autoHeadings).toString()) { out ->
            val arr = out?.optJSONArray("headings")
            headings = if (arr == null) emptyList() else (0 until arr.length()).map { i ->
                val h = arr.getJSONObject(i)
                Triple(h.optInt("block"), h.optInt("level", 1), h.optString("text"))
            }
            val top = headings.minOfOrNull { it.second } ?: 1
            open = headings.indices.filter { headings[it].second == top }.toSet()
        }
    }
    // a heading has headings under it when the next one is deeper
    val parent = { i: Int -> (headings.getOrNull(i + 1)?.second ?: 0) > (headings.getOrNull(i)?.second ?: 0) }
    // shown when every heading above it that holds it is open
    val shown = remember(headings, open) {
        val out = mutableListOf<Int>()
        val holders = ArrayDeque<Int>()
        for (i in headings.indices) {
            while (holders.isNotEmpty() && headings[holders.last()].second >= headings[i].second) holders.removeLast()
            if (holders.all { it in open }) out.add(i)
            holders.addLast(i)
        }
        out
    }
    Column(modifier.background(WordColors.Surface)) {
        Row(Modifier.fillMaxWidth().height(50.dp).topEdge(WordColors.StripMore).bottomEdge(WordColors.Divider), verticalAlignment = Alignment.CenterVertically) {
            Text("Headings", color = WordColors.OnSurface, fontSize = 20.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
            BarButton(LucideIcons.X.glyph(), WordColors.OnStrip, { vm.headingsOpen = false }, width = 56.dp, height = 50.dp)
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f)) {
            val top = headings.minOfOrNull { it.second } ?: 1
            items(shown.size) { k ->
                val i = shown[k]
                val (block, level, text) = headings[i]
                Row(
                    Modifier.fillMaxWidth().height(42.dp).background(if (i == chosen) WordColors.Pressed else WordColors.Surface).clickable {
                        chosen = i
                        vm.command("caret.set", JSONObject().put("pos", JSONObject().put("block", block).put("off", 0)).toString())
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text, color = WordColors.OnSurface, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = (16 + 15 * (level - top).coerceAtLeast(0)).dp, end = 8.dp))
                    if (parent(i)) {
                        val isOpen = i in open
                        BarButton((if (isOpen) LucideIcons.ChevronUp else LucideIcons.ChevronDown).glyph(), WordColors.OnSurface, { open = if (isOpen) open - i else open + i }, width = 56.dp, height = 42.dp)
                    }
                }
            }
            if (headings.isEmpty()) item {
                Text("No headings yet. Use the Heading styles to add some.", color = WordColors.OnSurface, fontSize = 15.sp, modifier = Modifier.padding(16.dp))
            }
        }
        // lines that look like headings, in a document without heading styles
        Row(
            Modifier.fillMaxWidth().height(50.dp).topEdge(WordColors.Divider).clickable { vm.chooseAutoHeadings(!vm.autoHeadings) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Auto-Detected Headings", color = WordColors.OnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 40.dp))
            SmallSwitch(vm.autoHeadings, Modifier.padding(end = 20.dp))
        }
    }
}

/** A full-screen pane with a title, a field per value, and Insert and Cancel. */
@Composable
fun FormPane(title: String, action: String, fields: List<String>, onDone: (List<String>) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier, initial: List<String> = emptyList()) {
    val values = remember { fields.indices.map { mutableStateOf(initial.getOrElse(it) { "" }) } }
    // the first field takes the keyboard from the document's input
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(modifier.background(WordColors.Divider)) {
        Row(Modifier.fillMaxWidth().height(48.dp).background(WordColors.Bar).bottomEdge(WordColors.Strip), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = WordColors.OnSurface, fontSize = 20.sp, modifier = Modifier.weight(1f).padding(start = 16.dp))
            val ready = values.last().value.isNotBlank()
            Text(action, color = if (ready) WordColors.Accent else WordColors.Disabled, fontSize = 16.sp, modifier = Modifier.clickable(enabled = ready) { onDone(values.map { it.value }) }.padding(12.dp))
            Text("Cancel", color = WordColors.Accent, fontSize = 16.sp, modifier = Modifier.clickable(onClick = onCancel).padding(12.dp))
        }
        for ((i, label) in fields.withIndex()) {
            Text(label, color = WordColors.OnSurface, fontSize = 14.sp, modifier = Modifier.padding(start = 23.dp, top = 16.dp, bottom = 4.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = values[i].value,
                onValueChange = { values[i].value = it },
                textStyle = androidx.compose.ui.text.TextStyle(color = WordColors.OnSurface, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(WordColors.Accent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 23.dp).background(WordColors.FindField).border(1.dp, WordColors.CheckEdge).padding(8.dp)
                    .let { if (i == 0) it.focusRequester(focus) else it },
            )
        }
    }
}

