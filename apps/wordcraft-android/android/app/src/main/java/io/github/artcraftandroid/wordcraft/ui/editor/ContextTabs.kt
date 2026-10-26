package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.artcraftandroid.wordcraft.core.FormatState
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.LucideIcons
import io.github.artcraftandroid.wordcraft.ui.WordColors
import java.util.Locale
import org.json.JSONObject

/** The contextual tab for what is selected or where the caret is, if any. */
fun contextTab(f: FormatState?): String? = when {
    f == null -> null
    f.`object` == "picture" && f.objectIcon -> "Graphics"
    f.`object` == "picture" -> "Picture"
    f.`object` == "chart" -> "Chart"
    f.`object` == "smartArt" -> "SmartArt"
    f.`object` == "shape" || f.`object` == "graphic" -> "Shape"
    f.inTable -> "Table"
    f.story == "header" || f.story == "footer" -> "Header & Footer"
    else -> null
}

/** The titles of the contextual tabs' pages. */
val CONTEXT_PANEL_TITLES = mapOf(
    "tableDelete" to "Delete",
    "tableInsert" to "Insert Rows and Columns",
    "styleOptions" to "Style Options",
    "tableStyles" to "Table Styles",
    "shading" to "Shading",
    "cellAlign" to "Alignment",
    "textRotation" to "Text Rotation",
    "autofit" to "AutoFit",
    "distribute" to "Distribute",
    "pictureStyles" to "Styles",
    "pictureEffects" to "Picture Effects",
    "wrapText" to "Wrap Text",
    "arrange" to "Arrange",
    "shapeFill" to "Fill",
    "graphicFill" to "Fill",
    "graphicOutline" to "Outline",
    "graphicEffects" to "Graphics Effects",
    "graphicStyles" to "Styles",
    "shapeStyles" to "Shape Styles",
    "chartType" to "Type",
    "chartLayout" to "Layout",
    "chartColor" to "Color",
    "chartStyle" to "Style",
    "chartTitle" to "Chart Title",
    "chartLabels" to "Data Labels",
    "pictureShadow" to "Shadow",
    "pictureReflection" to "Reflection",
    "cropPicture" to "Crop Picture",
    "picturePosition" to "Picture Position",
    "cropPosition" to "Crop Position",
    "chartElements" to "Elements",
    "chartLegend" to "Legend",
    "smartArtAdd" to "Add Shape",
    "smartArtLayout" to "Layout",
    "smartArtColors" to "Colors",
    "objectSize" to "Size",
    "altText" to "Alt Text",
    "shapeOutline" to "Outline",
    "shapeText" to "Text",
    "hfHeader" to "Header",
    "hfFooter" to "Footer",
    "docInfo" to "Document Info",
    "hfOptions" to "Options",
)

@Composable
fun ContextRows(vm: AppViewModel, tab: String): Boolean {
    when (tab) {
        "Table" -> TableTab(vm)
        "Picture" -> PictureTab(vm)
        "Graphics" -> GraphicsTab(vm)
        "Shape" -> ShapeTab(vm)
        "Chart" -> ChartTab(vm)
        "SmartArt" -> SmartArtTab(vm)
        "Header & Footer" -> HeaderFooterTab(vm)
        else -> return false
    }
    return true
}

@Composable
fun ContextPanel(vm: AppViewModel, panel: String): Boolean {
    if (ChartPanel(vm, panel)) return true
    when (panel) {
        "tableDelete" -> Choices(vm, "Delete Columns" to "table.deleteColumn", "Delete Rows" to "table.deleteRow", "Delete Table" to "table.deleteTable")
        "tableInsert" -> Choices(vm, "Insert Row Above" to "table.insertRowAbove", "Insert Row Below" to "table.insertRowBelow", "Insert Column Left" to "table.insertColumnLeft", "Insert Column Right" to "table.insertColumnRight")
        "styleOptions" -> StyleOptionsPanel(vm)
        "tableStyles" -> TableStylesPanel(vm)
        "shading" -> ColorGridPanel(vm) { hex -> vm.command("table.shading", if (hex == null) """{"color":null}""" else json("color" to hex)) }
        "cellAlign" -> CellAlignPanel(vm)
        "textRotation" -> Choices(vm, "Horizontal" to "table.textDirection|horizontal", "Rotate all text 90°" to "table.textDirection|down", "Rotate all text 270°" to "table.textDirection|up")
        "autofit" -> Choices(vm, "AutoFit Contents" to "table.autofit|contents", "AutoFit Window" to "table.autofit|window", "Fixed Column Width" to "table.autofit|fixed")
        "distribute" -> Choices(vm, "Distribute Rows" to "table.distributeRows", "Distribute Columns" to "table.distributeColumns")
        "pictureStyles" -> PictureStyleGallery(vm)
        "pictureEffects", "graphicEffects" -> Scrolling {
            PaletteRow("Shadow", null, onClick = { vm.pushPanel("pictureShadow") }, chevron = true)
            PaletteRow("Reflection", null, onClick = { vm.pushPanel("pictureReflection") }, chevron = true)
        }
        "pictureShadow" -> ShadowGallery(vm)
        "pictureReflection" -> ReflectionGallery(vm)
        "wrapText" -> Choices(
            vm,
            "In Line with Text" to "arrange.wrap|inline", "Square" to "arrange.wrap|square", "Tight" to "arrange.wrap|tight", "Through" to "arrange.wrap|through",
            "Top and Bottom" to "arrange.wrap|topAndBottom", "Behind Text" to "arrange.wrap|behindText", "In Front of Text" to "arrange.wrap|inFrontOfText",
        )
        "arrange" -> ArrangePanel(vm)
        "cropPicture" -> Scrolling {
            PaletteRow("Picture Position", null, onClick = { vm.pushPanel("picturePosition") }, chevron = true)
            PaletteRow("Crop Position", null, onClick = { vm.pushPanel("cropPosition") }, chevron = true)
        }
        "picturePosition" -> CropFields(vm, picture = true)
        "cropPosition" -> CropFields(vm, picture = false)
        "shapeFill" -> ColorGridPanel(vm) { hex -> vm.command("shape.fill", if (hex == null) """{"color":null}""" else json("color" to hex)) }
        "shapeOutline" -> ColorGridPanel(vm) { hex -> vm.command("shape.outline", if (hex == null) """{"color":null}""" else json("color" to hex)) }
        "graphicFill" -> ColorGridPanel(vm) { hex -> vm.command("graphic.fill", if (hex == null) """{"color":null}""" else json("color" to hex)) }
        "graphicOutline" -> ColorGridPanel(vm) { hex -> vm.command("graphic.outline", if (hex == null) """{"color":null}""" else json("color" to hex)) }
        "shapeText" -> ShapeTextPanel(vm)
        "shapeStyles" -> ShapeStylesPanel(vm)
        "graphicStyles" -> GraphicStylesPanel(vm)
        "smartArtAdd" -> Choices(vm, "Add Shape After" to "smartArt.addShape", "Add Shape Below" to "smartArt.addShape|below")
        "smartArtLayout" -> Choices(
            vm,
            "Basic List" to "smartArt.layout|basicList", "Process" to "smartArt.layout|process", "Cycle" to "smartArt.layout|cycle", "Hierarchy" to "smartArt.layout|hierarchy",
            "Pyramid" to "smartArt.layout|pyramid", "Radial" to "smartArt.layout|radial", "Matrix" to "smartArt.layout|matrix", "Venn" to "smartArt.layout|venn",
        )
        "smartArtColors" -> Choices(
            vm,
            "Colorful" to "smartArt.colors|colorful", "Accent 1" to "smartArt.colors|accent1", "Accent 2" to "smartArt.colors|accent2", "Accent 3" to "smartArt.colors|accent3",
            "Accent 4" to "smartArt.colors|accent4", "Accent 5" to "smartArt.colors|accent5", "Accent 6" to "smartArt.colors|accent6",
        )
        "objectSize" -> ObjectSizeFields(vm)
        "altText" -> AltTextPanel(vm)
        "hfHeader" -> Choices(vm, "Edit Header" to "insert.editHeader", "Remove Header" to "insert.removeHeader")
        "hfFooter" -> Choices(vm, "Edit Footer" to "insert.editFooter", "Remove Footer" to "insert.removeFooter")
        "docInfo" -> Choices(vm, "Title" to "insert.docProperty|Title", "Author" to "insert.docProperty|Author", "Subject" to "insert.docProperty|Subject", "Keywords" to "insert.docProperty|Keywords")
        "hfOptions" -> Choices(vm, "Different First Page" to "layout.differentFirstPage", "Different Odd & Even Pages" to "layout.differentOddEven")
        else -> return false
    }
    return true
}

/**
 * A list of rows, each running an engine command: `id`, or `id|value`, where the value goes in
 * the parameter each command takes for it.
 */
@Composable
private fun Choices(vm: AppViewModel, vararg rows: Pair<String, String>) {
    Scrolling {
        for ((label, spec) in rows) {
            ChoiceRow(label, false, onClick = { runSpec(vm, spec) }, indent = 16.dp, size = 16)
        }
    }
}

private fun runSpec(vm: AppViewModel, spec: String) {
    val (id, value) = spec.split('|').let { it[0] to it.getOrNull(1) }
    if (value == null) {
        vm.command(id)
        return
    }
    if (id == "smartArt.addShape") {
        vm.command(id, json("below" to (value == "below")))
        return
    }
    val key = when (id) {
        "arrange.wrap" -> "wrap"
        "chart.type" -> "type"
        "shape.effects" -> "shadow"
        "chart.legend" -> "pos"
        "smartArt.layout" -> "layout"
        "smartArt.colors" -> "variant"
        "arrange.rotate" -> "direction"
        "picture.style" -> "style"
        "table.autofit" -> "mode"
        "insert.docProperty" -> "name"
        else -> "value"
    }
    vm.command(id, json(key to value))
}

/** A chart or diagram doesn't turn; pictures, shapes and drawings do. */
private fun turns(vm: AppViewModel) = vm.format?.`object` in setOf("picture", "shape", "graphic")

/**
 * Arrange: the stacking order, in front of or behind the text, and the quarter turns. An object
 * in the line can't be stacked (only turned), so those rows are greyed for it.
 */
@Composable
private fun ArrangePanel(vm: AppViewModel) {
    val floating = vm.format?.objectInline == false
    Scrolling {
        for ((label, spec) in listOf(
            "Bring Forward" to "arrange.bringForward", "Bring to Front" to "arrange.bringToFront", "Bring in Front of Text" to "arrange.wrap|inFrontOfText",
            "Send Backward" to "arrange.sendBackward", "Send to Back" to "arrange.sendToBack", "Send Behind Text" to "arrange.wrap|behindText",
        )) ChoiceRow(label, false, onClick = { runSpec(vm, spec) }, indent = 16.dp, size = 16, enabled = floating)
        if (turns(vm)) {
            ChoiceRow("Rotate Right 90°", false, onClick = { runSpec(vm, "arrange.rotate|right") }, indent = 16.dp, size = 16)
            ChoiceRow("Rotate Left 90°", false, onClick = { runSpec(vm, "arrange.rotate|left") }, indent = 16.dp, size = 16)
        }
    }
}

/** The Arrange row: greyed when nothing in it applies (a chart or diagram in the line). */
@Composable
private fun ArrangeRow(vm: AppViewModel) {
    PaletteRow("Arrange", LucideIcons.Shapes.glyph(), onClick = { vm.pushPanel("arrange") }, chevron = true, enabled = vm.format?.objectInline == false || turns(vm))
}

/** The Font Color grid for fills, outlines and shading, then No Color. */
@Composable
private fun ColorGridPanel(vm: AppViewModel, apply: (String?) -> Unit) {
    val choices = vm.colors ?: return
    Scrolling {
        SectionBand("Theme Colors")
        SwatchGrid(choices.theme.flatten(), perRow = 10, cellW = 49.dp, selected = null) { apply(it.hex) }
        SectionBand("Standard Colors")
        SwatchGrid(choices.standard, perRow = 10, cellW = 49.dp, selected = null) { apply(it.hex) }
        NoColorRow("No Color", false) { apply(null) }
    }
}

@Composable
private fun TableTab(vm: AppViewModel) {
    Scrolling {
        PaletteRow("Delete", LucideIcons.X.glyph(), onClick = { vm.pushPanel("tableDelete") }, chevron = true)
        PaletteRow("Insert Rows and Columns", LucideIcons.Table.glyph(), onClick = { vm.pushPanel("tableInsert") }, chevron = true)
        PaletteRow("Merge Cells", null, onClick = { vm.command("table.merge") })
        PaletteRow("Split Cells...", null, onClick = { vm.command("table.split", json("columns" to 2)) })
        Band()
        PaletteRow("Style Options", null, onClick = { vm.pushPanel("styleOptions") }, chevron = true)
        Band()
        PaletteRow("Table Styles", LucideIcons.Paintbrush.glyph(), onClick = { vm.pushPanel("tableStyles") }, chevron = true)
        Band()
        PaletteRow("Shading", LucideIcons.Palette.glyph(), onClick = { vm.pushPanel("shading") }, onOpen = { vm.pushPanel("shading") })
        PaletteRow("Alignment", LucideIcons.AlignCenter.glyph(), onClick = { vm.pushPanel("cellAlign") }, chevron = true)
        PaletteRow("Text Rotation", LucideIcons.RotateCcwSquare.glyph(), onClick = { vm.pushPanel("textRotation") }, chevron = true)
        PaletteRow("AutoFit", LucideIcons.ArrowLeftRight.glyph(), onClick = { vm.pushPanel("autofit") }, chevron = true)
        PaletteRow("Distribute", LucideIcons.Columns2.glyph(), onClick = { vm.pushPanel("distribute") }, chevron = true)
    }
}

@Composable
private fun StyleOptionsPanel(vm: AppViewModel) {
    Scrolling {
        for ((label, key) in listOf(
            "Header Row" to "headerRow", "Total Row" to "totalRow", "Banded Rows" to "bandedRows",
            "First Column" to "firstColumn", "Last Column" to "lastColumn", "Banded Columns" to "bandedColumns",
        )) {
            var on by remember { mutableStateOf(key == "headerRow" || key == "bandedRows" || key == "firstColumn") }
            Row(Modifier.fillMaxWidth().height(ROW + 1.dp).bottomEdge(WordColors.Divider), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(on, onCheckedChange = {
                    on = it
                    vm.command("table.look", json(key to it))
                }, colors = CheckboxDefaults.colors(checkedColor = WordColors.Accent), modifier = Modifier.padding(start = 4.dp))
                Text(label, color = WordColors.OnSurface, fontSize = 16.sp)
            }
        }
        PaletteRow("Repeat Header Rows", null, onClick = { vm.command("table.repeatHeader") })
    }
}

@Composable
private fun TableStylesPanel(vm: AppViewModel) {
    var styles by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.tableStyles { styles = it } }
    Scrolling {
        for ((id, name) in styles) ChoiceRow(name, false, onClick = { vm.command("table.style", json("style" to id)) }, indent = 16.dp, size = 16)
    }
}

@Composable
private fun CellAlignPanel(vm: AppViewModel) {
    val f = vm.format
    Scrolling {
        // the text's alignment, then the cells' vertical alignment (the selected cells)
        CellRow(
            Triple(LucideIcons.AlignLeft.glyph(), f?.align == "left") { vm.command("para.alignLeft") },
            Triple(LucideIcons.AlignCenter.glyph(), f?.align == "center") { vm.command("para.alignCenter") },
            Triple(LucideIcons.AlignRight.glyph(), f?.align == "right") { vm.command("para.alignRight") },
            labels = listOf("Align Left", "Center", "Align Right"),
        )
        CellRow(
            *listOf("top", "middle", "bottom").map { v ->
                Triple(Glyph.CellLines(v) as Glyph, f?.cellValign == v) { vm.command("table.cellAlign", json("value" to v)) }
            }.toTypedArray(),
            labels = listOf("Align Top", "Align Middle", "Align Bottom"),
        )
    }
}

@Composable
private fun PictureTab(vm: AppViewModel) {
    Scrolling {
        PaletteRow("Styles", LucideIcons.Paintbrush.glyph(), onClick = { vm.pushPanel("pictureStyles") }, chevron = true)
        PaletteRow("Picture Effects", LucideIcons.Image.glyph(), onClick = { vm.pushPanel("pictureEffects") }, chevron = true)
        Band()
        PaletteRow("Wrap Text", LucideIcons.TextCursorInput.glyph(), onClick = { vm.pushPanel("wrapText") }, chevron = true)
        ArrangeRow(vm)
        Band()
        // the left part turns the crop handles on and off, the right part opens its page
        PaletteRow("Crop Picture", LucideIcons.Crop.glyph(), onClick = { vm.cropping = !vm.cropping }, pressed = vm.cropping, onOpen = { vm.pushPanel("cropPicture") })
        Band()
        PaletteRow("Size", LucideIcons.RectangleVertical.glyph(), onClick = { vm.pushPanel("objectSize") }, chevron = true)
        PaletteRow("Alt Text", LucideIcons.MessageSquare.glyph(), onClick = { vm.pushPanel("altText") }, chevron = true)
        Band()
        PaletteRow("Reset", LucideIcons.RotateCcwSquare.glyph(), onClick = { vm.command("picture.reset") })
    }
}

/**
 * The Graphics tab, for an icon: its effects, fill and outline (its SVG repainted), then how it
 * sits among the text, cropping, its size and alt text.
 */
@Composable
private fun GraphicsTab(vm: AppViewModel) {
    Scrolling {
        // the left part applies the latest style again, the right part opens the styles
        PaletteRow("Styles", LucideIcons.Paintbrush.glyph(), onClick = { vm.recentGraphicStyles.firstOrNull()?.let(vm::applyGraphicStyle) }, onOpen = { vm.pushPanel("graphicStyles") })
        PaletteRow("Graphics Effects", LucideIcons.Image.glyph(), onClick = { vm.pushPanel("graphicEffects") }, chevron = true)
        Band()
        PaletteRow("Fill", LucideIcons.Palette.glyph(), onClick = { vm.pushPanel("graphicFill") }, onOpen = { vm.pushPanel("graphicFill") })
        PaletteRow("Outline", LucideIcons.SquareDashed.glyph(), onClick = { vm.pushPanel("graphicOutline") }, onOpen = { vm.pushPanel("graphicOutline") })
        Band()
        PaletteRow("Wrap Text", LucideIcons.TextCursorInput.glyph(), onClick = { vm.pushPanel("wrapText") }, chevron = true)
        ArrangeRow(vm)
        Band()
        PaletteRow("Crop Graphic", LucideIcons.Crop.glyph(), onClick = { vm.cropping = !vm.cropping }, pressed = vm.cropping, onOpen = { vm.pushPanel("cropPicture") })
        Band()
        PaletteRow("Size", LucideIcons.RectangleVertical.glyph(), onClick = { vm.pushPanel("objectSize") }, chevron = true)
        PaletteRow("Alt Text", LucideIcons.MessageSquare.glyph(), onClick = { vm.pushPanel("altText") }, chevron = true)
    }
}

@Composable
private fun ShapeTab(vm: AppViewModel) {
    Scrolling {
        // another shape, from the gallery
        PaletteRow("Insert", LucideIcons.Shapes.glyph(), onClick = { vm.pushPanel("shapes") }, chevron = true)
        Band()
        // the left part applies the latest style again, the right part opens the styles
        PaletteRow("Shape Styles", LucideIcons.Paintbrush.glyph(), onClick = { vm.recentShapeStyles.firstOrNull()?.let(vm::applyShapeStyle) }, onOpen = { vm.pushPanel("shapeStyles") })
        Band()
        PaletteRow("Fill", LucideIcons.Palette.glyph(), onClick = { vm.pushPanel("shapeFill") }, onOpen = { vm.pushPanel("shapeFill") })
        PaletteRow("Outline", LucideIcons.SquareDashed.glyph(), onClick = { vm.pushPanel("shapeOutline") }, onOpen = { vm.pushPanel("shapeOutline") })
        Band()
        PaletteRow("Text", LucideIcons.Type.glyph(), onClick = { vm.pushPanel("shapeText") }, chevron = true)
        Band()
        PaletteRow("Wrap Text", LucideIcons.TextCursorInput.glyph(), onClick = { vm.pushPanel("wrapText") }, chevron = true)
        ArrangeRow(vm)
        Band()
        PaletteRow("Size", LucideIcons.RectangleVertical.glyph(), onClick = { vm.pushPanel("objectSize") }, chevron = true)
        PaletteRow("Alt Text", LucideIcons.MessageSquare.glyph(), onClick = { vm.pushPanel("altText") }, chevron = true)
    }
}

/** The Chart tab: the chart's type, its layout and elements, its colours, then how it sits among the text, its size and alt text. */
@Composable
private fun ChartTab(vm: AppViewModel) {
    Scrolling {
        PaletteRow("Type", LucideIcons.ChartColumn.glyph(), onClick = { vm.pushPanel("chartType") }, chevron = true)
        Band()
        PaletteRow("Layout", LucideIcons.LayoutTemplate.glyph(), onClick = { vm.pushPanel("chartLayout") }, chevron = true)
        PaletteRow("Elements", LucideIcons.ListPlus.glyph(), onClick = { vm.pushPanel("chartElements") }, chevron = true)
        Band()
        PaletteRow("Color", LucideIcons.SwatchBook.glyph(), onClick = { vm.pushPanel("chartColor") }, chevron = true)
        PaletteRow("Style", LucideIcons.WandSparkles.glyph(), onClick = { vm.pushPanel("chartStyle") }, chevron = true)
        Band()
        PaletteRow("Wrap Text", LucideIcons.TextCursorInput.glyph(), onClick = { vm.pushPanel("wrapText") }, chevron = true)
        ArrangeRow(vm)
        Band()
        PaletteRow("Size", LucideIcons.RectangleVertical.glyph(), onClick = { vm.pushPanel("objectSize") }, chevron = true)
        PaletteRow("Alt Text", LucideIcons.MessageSquare.glyph(), onClick = { vm.pushPanel("altText") }, chevron = true)
    }
}

/** The SmartArt tab: its items, its layout and colours, then how it sits among the text, its size and alt text. */
@Composable
private fun SmartArtTab(vm: AppViewModel) {
    Scrolling {
        PaletteRow("Add Shape", LucideIcons.Plus.glyph(), onClick = { vm.pushPanel("smartArtAdd") }, chevron = true)
        PaletteRow("Add Bullet", LucideIcons.List.glyph(), onClick = { vm.command("smartArt.addShape", json("below" to true)) })
        PaletteRow("Promote", LucideIcons.ListIndentDecrease.glyph(), onClick = { vm.command("smartArt.promote") })
        PaletteRow("Demote", LucideIcons.ListIndentIncrease.glyph(), onClick = { vm.command("smartArt.demote") })
        Band()
        PaletteRow("Layout", LucideIcons.ListTree.glyph(), onClick = { vm.pushPanel("smartArtLayout") }, chevron = true)
        PaletteRow("Colors", LucideIcons.Palette.glyph(), onClick = { vm.pushPanel("smartArtColors") }, chevron = true)
        Band()
        PaletteRow("Wrap Text", LucideIcons.TextCursorInput.glyph(), onClick = { vm.pushPanel("wrapText") }, chevron = true)
        ArrangeRow(vm)
        Band()
        PaletteRow("Size", LucideIcons.RectangleVertical.glyph(), onClick = { vm.pushPanel("objectSize") }, chevron = true)
        PaletteRow("Alt Text", LucideIcons.MessageSquare.glyph(), onClick = { vm.pushPanel("altText") }, chevron = true)
    }
}

/**
 * Shape Styles' theme rows: each colour's outline on white, its fill (darker outline), its fill
 * outlined in white, then its fill with dark text, with light text, and with a shadow.
 */
private val STYLE_ROWS = listOf("coloredOutline", "coloredFill", "lightOutline", "subtleEffect", "moderateEffect", "intenseEffect")

/** The Presets rows: see-through with coloured text, see-through with a coloured outline, half see-through, solid, a gradient. */
private val PRESET_ROWS = listOf("transparent", "transparentOutline", "semitransparent", "solid", "gradient")

/** The colours across: the dark text colour, then the six accents. */
private val STYLE_COLORS = listOf("dark1", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6")

/** How a style tile looks: its fill (null: none), how opaque, whether a gradient, its outline (null: none), its text colour, a shadow. */
private data class TileLook(val fill: Color?, val alpha: Float, val gradient: Boolean, val edge: Color?, val text: Color, val shadow: Boolean)

@Composable
private fun ShapeStylesPanel(vm: AppViewModel) {
    // the theme's colours as the colour menus have them: light 1, dark 1, light 2, dark 2, the accents
    val theme = vm.colors?.theme?.firstOrNull() ?: return
    fun color(i: Int) = Color(android.graphics.Color.parseColor("#" + (theme.getOrNull(i)?.hex ?: "000000")))
    val light = color(0)
    val dark = color(1)
    fun look(key: String): TileLook {
        val c = when (val name = key.substringAfter(':')) {
            "dark1" -> dark
            else -> color(3 + (name.removePrefix("accent").toIntOrNull() ?: 1))
        }
        val darker = Color(c.red / 2, c.green / 2, c.blue / 2)
        return when (key.substringBefore(':')) {
            "coloredOutline" -> TileLook(light, 1f, false, c, dark, false)
            "lightOutline" -> TileLook(c, 1f, false, light, light, false)
            "subtleEffect" -> TileLook(c, 1f, false, null, dark, false)
            "moderateEffect", "solid" -> TileLook(c, 1f, false, null, light, false)
            "intenseEffect" -> TileLook(c, 1f, false, null, light, true)
            "transparent" -> TileLook(null, 0f, false, null, c, false)
            "transparentOutline" -> TileLook(null, 0f, false, c, c, false)
            "semitransparent" -> TileLook(c, 0.5f, false, null, light, false)
            "gradient" -> TileLook(c, 1f, true, null, light, false)
            else -> TileLook(c, 1f, false, darker, light, false)
        }
    }
    Scrolling {
        SectionBand("Recent Styles")
        StyleRow(vm.recentShapeStyles, ::look) { vm.applyShapeStyle(it) }
        SectionBand("Theme Styles")
        for (row in STYLE_ROWS) StyleRow(STYLE_COLORS.map { "$row:$it" }, ::look) { vm.applyShapeStyle(it) }
        SectionBand("Presets")
        for (row in PRESET_ROWS) StyleRow(STYLE_COLORS.map { "$row:$it" }, ::look) { vm.applyShapeStyle(it) }
    }
}

/**
 * Graphics Styles for an icon: Recent Styles, then the presets, a row of seven (dark 1 and the six
 * accents) for each look: a light fill with an outline in the colour, a fill in the colour with no
 * outline, the same with a dark outline, and no fill with an outline in the colour.
 */
@Composable
private fun GraphicStylesPanel(vm: AppViewModel) {
    val theme = vm.colors?.theme?.firstOrNull() ?: return
    fun color(i: Int) = Color(android.graphics.Color.parseColor("#" + (theme.getOrNull(i)?.hex ?: "000000")))
    val light = color(0)
    val dark = color(1)
    fun look(key: String): TileLook {
        val c = when (val name = key.substringAfter(':')) {
            "dark1" -> dark
            else -> color(3 + (name.removePrefix("accent").toIntOrNull() ?: 1))
        }
        return when (key.substringBefore(':')) {
            "lightFill" -> TileLook(light, 1f, false, c, dark, false)
            "coloredFill" -> TileLook(c, 1f, false, null, light, false)
            "darkOutline" -> TileLook(c, 1f, false, dark, light, false)
            else -> TileLook(null, 0f, false, c, c, false)
        }
    }
    Scrolling {
        SectionBand("Recent Styles")
        StyleRow(vm.recentGraphicStyles, ::look, text = false) { vm.applyGraphicStyle(it) }
        SectionBand("Presets")
        for (row in listOf("lightFill", "coloredFill", "darkOutline", "transparent")) {
            StyleRow(STYLE_COLORS.map { "$row:$it" }, ::look, text = false) { vm.applyGraphicStyle(it) }
        }
    }
}

/** A row of style tiles: rounded squares in the style's fill and outline (a checkerboard under what is see-through), "Abc" in its text colour unless [text] is off. */
@Composable
private fun StyleRow(keys: List<String>, look: (String) -> TileLook, text: Boolean = true, onPick: (String) -> Unit) {
    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
        for (key in keys) {
            val l = look(key)
            Box(Modifier.size(70.dp, 56.dp).clickable { onPick(key) }, contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.size(46.dp)) {
                    val r = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx())
                    if (l.shadow) drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft = androidx.compose.ui.geometry.Offset(0f, 2.dp.toPx()), cornerRadius = r)
                    if (l.fill == null || l.alpha < 1f) {
                        // a checkerboard: what shows through
                        val n = 6
                        val cell = size.width / n
                        for (i in 0 until n) for (j in 0 until n) {
                            drawRect(if ((i + j) % 2 == 0) Color(0xFF8A8A8A) else Color(0xFF5C5C5C), androidx.compose.ui.geometry.Offset(i * cell, j * cell), androidx.compose.ui.geometry.Size(cell, cell))
                        }
                    }
                    val f = l.fill
                    if (f != null && l.gradient) {
                        val top = androidx.compose.ui.graphics.lerp(f, Color.White, 0.35f)
                        val bottom = androidx.compose.ui.graphics.lerp(f, Color.Black, 0.3f)
                        drawRoundRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, f, bottom)), cornerRadius = r)
                    } else if (f != null) {
                        drawRoundRect(f.copy(alpha = l.alpha), cornerRadius = r)
                    }
                    l.edge?.let { drawRoundRect(it, cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx())) }
                }
                if (text) Text("Abc", color = l.text, fontSize = 19.sp)
            }
        }
    }
}

/** Alt Text: a description of the selected object for screen readers, or marked decorative. */
@Composable
private fun AltTextPanel(vm: AppViewModel) {
    var text by remember { mutableStateOf("") }
    var decorative by remember { mutableStateOf(false) }
    // what the object had, so leaving the pane saves only a change
    var saved by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    fun save() {
        val now = (if (decorative) "" else text) to decorative
        if (saved == null || now == saved) return
        vm.command("object.altText", JSONObject().put("text", now.first).put("decorative", now.second).toString())
        saved = now
    }
    // the field takes the keyboard from the document's input
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        vm.objectAlt { alt ->
            text = alt?.text.orEmpty()
            decorative = alt?.decorative == true
            saved = text to decorative
        }
        runCatching { focus.requestFocus() }
    }
    DisposableEffect(Unit) {
        onDispose {
            vm.paletteTyping = false
            save()
        }
    }
    Scrolling {
        Caption("Describe this object for someone who can't see it.")
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { text = it },
            enabled = !decorative,
            textStyle = androidx.compose.ui.text.TextStyle(color = WordColors.OnSurface, fontSize = 16.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(WordColors.Accent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(136.dp).border(1.dp, WordColors.CheckEdge).background(WordColors.FindField).padding(10.dp)
                .focusRequester(focus).onFocusChanged { vm.paletteTyping = it.isFocused },
        )
        // saved on leaving the pane
        CheckboxRow("Mark as decorative", decorative) { decorative = it }
    }
}

@Composable
private fun ShapeTextPanel(vm: AppViewModel) {
    Scrolling {
        CellRow(
            Triple(LucideIcons.AlignLeft.glyph(), false) { vm.command("para.alignLeft") },
            Triple(LucideIcons.AlignCenter.glyph(), false) { vm.command("para.alignCenter") },
            Triple(LucideIcons.AlignRight.glyph(), false) { vm.command("para.alignRight") },
            labels = listOf("Align Left", "Center", "Align Right"),
        )
        CellRow(
            Triple(Glyph.Letters("⤒"), false) { vm.command("shape.alignText", json("anchor" to "t")) },
            Triple(Glyph.Letters("↕"), false) { vm.command("shape.alignText", json("anchor" to "ctr")) },
            Triple(Glyph.Letters("⤓"), false) { vm.command("shape.alignText", json("anchor" to "b")) },
            labels = listOf("Top", "Middle", "Bottom"),
        )
        PaletteRow("Text Rotation", LucideIcons.RotateCcwSquare.glyph(), onClick = { vm.command("shape.textDirection", json("dir" to "vert")) })
    }
}

@Composable
private fun HeaderFooterTab(vm: AppViewModel) {
    Scrolling {
        PaletteRow("Close Header & Footer", LucideIcons.X.glyph(), onClick = {
            vm.command("insert.closeHeader")
            vm.closePalette()
        })
        PaletteRow("Header", LucideIcons.PanelTop.glyph(), onClick = { vm.pushPanel("hfHeader") }, chevron = true)
        PaletteRow("Footer", LucideIcons.PanelTop.glyph(), onClick = { vm.pushPanel("hfFooter") }, chevron = true)
        PaletteRow("Page Number", LucideIcons.Hash.glyph(), onClick = { vm.pushPanel("pageNumber") }, chevron = true)
        PaletteRow("Pictures", LucideIcons.Image.glyph(), onClick = { vm.pushPanel("pictures") }, chevron = true)
        PaletteRow("Link", LucideIcons.Link.glyph(), onClick = { vm.linkPane = true }, chevron = true)
        PaletteRow("Document Info", LucideIcons.FileText.glyph(), onClick = { vm.pushPanel("docInfo") }, chevron = true)
        PaletteRow("Options", LucideIcons.Settings.glyph(), onClick = { vm.pushPanel("hfOptions") }, chevron = true)
    }
}

/** The floating toolbar's buttons for a table cell, a picture or a shape (before the text buttons). */
fun contextPillItems(vm: AppViewModel, tab: String?): List<PillItem> = when (tab) {
    "Table" -> listOf(
        PillItem(Glyph.Letters("⊞›")) { vm.command("table.insertColumnRight") },
        PillItem(Glyph.Letters("⊞⌄")) { vm.command("table.insertRowBelow") },
        PillItem(LucideIcons.X.glyph()) { vm.openPalette("tableDelete") },
    )
    "Picture" -> listOf(
        PillItem(LucideIcons.Crop.glyph(), on = vm.cropping) { vm.cropping = !vm.cropping },
        PillItem(LucideIcons.Paintbrush.glyph()) { vm.openPalette("pictureStyles") },
        PillItem(Glyph.Letters("◩")) { vm.openPalette("pictureShadow") },
        PillItem(LucideIcons.FlipVertical2.glyph()) { vm.openPalette("pictureReflection") },
        PillItem(LucideIcons.TextCursorInput.glyph()) { vm.openPalette("wrapText") },
        PillItem(LucideIcons.Shapes.glyph()) { vm.openPalette("arrange") },
        PillItem(LucideIcons.Undo2.glyph()) { vm.undo() },
    )
    "Graphics" -> listOf(
        PillItem(LucideIcons.Paintbrush.glyph()) { vm.openPalette("graphicStyles") },
        PillItem(Glyph.Letters("◩")) { vm.openPalette("pictureShadow") },
        PillItem(LucideIcons.FlipVertical2.glyph()) { vm.openPalette("pictureReflection") },
        PillItem(LucideIcons.Palette.glyph()) { vm.openPalette("graphicFill") },
        PillItem(LucideIcons.SquareDashed.glyph()) { vm.openPalette("graphicOutline") },
        PillItem(LucideIcons.Crop.glyph(), on = vm.cropping) { vm.cropping = !vm.cropping },
        PillItem(LucideIcons.TextCursorInput.glyph()) { vm.openPalette("wrapText") },
        PillItem(LucideIcons.Shapes.glyph()) { vm.openPalette("arrange") },
        PillItem(LucideIcons.Undo2.glyph()) { vm.undo() },
    )
    "Chart" -> listOf(
        PillItem(LucideIcons.ChartColumn.glyph()) { vm.openPalette("chartType") },
        PillItem(LucideIcons.LayoutTemplate.glyph()) { vm.openPalette("chartLayout") },
        PillItem(LucideIcons.ListPlus.glyph()) { vm.openPalette("chartElements") },
        PillItem(LucideIcons.SwatchBook.glyph()) { vm.openPalette("chartColor") },
        PillItem(LucideIcons.WandSparkles.glyph()) { vm.openPalette("chartStyle") },
        PillItem(LucideIcons.Undo2.glyph()) { vm.undo() },
    )
    "Shape" -> listOf(
        PillItem(LucideIcons.Paintbrush.glyph()) { vm.openPalette("shapeStyles") },
        PillItem(LucideIcons.Palette.glyph()) { vm.openPalette("shapeFill") },
        PillItem(LucideIcons.SquareDashed.glyph()) { vm.openPalette("shapeOutline") },
        PillItem(LucideIcons.TextCursorInput.glyph()) { vm.openPalette("wrapText") },
        PillItem(LucideIcons.AlignCenter.glyph()) { vm.openPalette("shapeText") },
        PillItem(LucideIcons.Shapes.glyph()) { vm.openPalette("arrange") },
        PillItem(LucideIcons.Undo2.glyph()) { vm.undo() },
    )
    else -> emptyList()
}
