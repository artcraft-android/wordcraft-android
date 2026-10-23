package io.github.artcraftandroid.wordcraft.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.artcraftandroid.wordcraft.core.ChartPaletteView
import io.github.artcraftandroid.wordcraft.core.ChartState
import io.github.artcraftandroid.wordcraft.ui.AppViewModel
import io.github.artcraftandroid.wordcraft.ui.LucideIcons

/** The chart families the Type page lists (the engine's ids and names): the first six, those it can't make (greyed), then Other. */
private val CHART_FAMILIES = listOf("column" to "Column", "line" to "Line", "pie" to "Pie", "bar" to "Bar", "area" to "Area", "scatter" to "X Y (Scatter)", "other" to "Other")
private val GREYED_CHART_KINDS = listOf(
    "maps" to "Maps",
    "treemap" to "Treemap",
    "sunburst" to "Sunburst",
    "histogram" to "Histogram",
    "boxWhisker" to "Box and Whisker",
    "waterfall" to "Waterfall",
    "funnel" to "Funnel",
)

/** The title of a Chart page that has one of its own: a family's types. */
fun chartPanelTitle(panel: String): String? =
    panel.removePrefix("chartType:").takeIf { it != panel }?.let { id -> CHART_FAMILIES.firstOrNull { it.first == id }?.second }

/** The Chart tab's pages; false for any other page. */
@Composable
fun ChartPanel(vm: AppViewModel, panel: String): Boolean {
    when {
        panel == "chartType" -> Scrolling {
            val (first, other) = CHART_FAMILIES.partition { it.first != "other" }
            for ((id, name) in first) PaletteRow(name, Glyph.Chart(id), onClick = { vm.pushPanel("chartType:$id") }, chevron = true)
            for ((id, name) in GREYED_CHART_KINDS) PaletteRow(name, Glyph.Chart(id), onClick = {}, chevron = true, enabled = false)
            for ((id, name) in other) PaletteRow(name, Glyph.Chart(id), onClick = { vm.pushPanel("chartType:$id") }, chevron = true)
        }
        panel.startsWith("chartType:") -> ChartGallery(vm, "type:" + panel.removePrefix("chartType:")) { vm.command("chart.type", json("type" to it)) }
        panel == "chartLayout" -> ChartGallery(vm, "layout") { id -> id.toIntOrNull()?.let { vm.command("chart.quickLayout", json("layout" to it)) } }
        panel == "chartStyle" -> ChartGallery(vm, "style") { id -> id.toIntOrNull()?.let { vm.command("chart.style", json("style" to it)) } }
        panel == "chartElements" -> Scrolling {
            PaletteRow("Chart Title", LucideIcons.PanelTop.glyph(), onClick = { vm.pushPanel("chartTitle") }, chevron = true)
            PaletteRow("Data Labels", LucideIcons.Type.glyph(), onClick = { vm.pushPanel("chartLabels") }, chevron = true)
            PaletteRow("Legend", LucideIcons.List.glyph(), onClick = { vm.pushPanel("chartLegend") }, chevron = true)
        }
        panel == "chartTitle" -> WithChart(vm) { st ->
            Scrolling {
                for ((id, name) in listOf("none" to "None", "above" to "Above Chart", "overlay" to "Centered Overlay")) {
                    ChoiceRow(name, st.title == id, onClick = { vm.command("chart.title", json("pos" to id)) }, indent = 16.dp, size = 16)
                }
            }
        }
        panel == "chartLabels" -> WithChart(vm) { st ->
            Scrolling {
                ChoiceRow("None", st.labels == "none", onClick = { vm.command("chart.dataLabels", json("pos" to "none")) }, indent = 16.dp, size = 16)
                // a type that puts its labels in one place only: just on
                if (st.labelPlaces.isEmpty()) {
                    ChoiceRow("Show", st.labels == "default", onClick = { vm.command("chart.dataLabels", json("pos" to "default")) }, indent = 16.dp, size = 16)
                }
                for (p in st.labelPlaces) {
                    ChoiceRow(p.name, st.labels == p.id, onClick = { vm.command("chart.dataLabels", json("pos" to p.id)) }, indent = 16.dp, size = 16)
                }
            }
        }
        panel == "chartLegend" -> WithChart(vm) { st ->
            Scrolling {
                for ((id, name) in listOf("none" to "None", "right" to "Right", "top" to "Top", "left" to "Left", "bottom" to "Bottom")) {
                    ChoiceRow(name, st.legend == id, onClick = { vm.command("chart.legend", json("pos" to id)) }, indent = 16.dp, size = 16)
                }
            }
        }
        panel == "chartColor" -> ChartColors(vm)
        else -> return false
    }
    return true
}

/** [content] with the selected chart's state, read again after each change. */
@Composable
private fun WithChart(vm: AppViewModel, content: @Composable (ChartState) -> Unit) {
    var st by remember { mutableStateOf<ChartState?>(null) }
    LaunchedEffect(vm.info?.revision) { vm.chartState { st = it } }
    st?.let { content(it) }
}

/**
 * A gallery of the selected chart as each choice would make it, two pictures to a row on white,
 * drawn by the engine; picking one applies its id.
 */
@Composable
private fun ChartGallery(vm: AppViewModel, which: String, onPick: (String) -> Unit) {
    val widthPx = with(LocalDensity.current) { ((LocalConfiguration.current.screenWidthDp.dp - 48.dp) / 2).roundToPx() }
    var pictures by remember(which) { mutableStateOf<List<Pair<String, ImageBitmap>>>(emptyList()) }
    LaunchedEffect(which, vm.info?.revision) {
        vm.chartGallery(which, widthPx) { list -> pictures = list.map { (id, bmp) -> id to bmp.asImageBitmap() } }
    }
    Scrolling {
        for (row in pictures.chunked(2)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                for ((i, pic) in row.withIndex()) {
                    if (i > 0) Spacer(Modifier.padding(horizontal = 6.dp))
                    Box(Modifier.weight(1f).aspectRatio(300f / 180f).background(Color.White).clickable { onPick(pic.first) }) {
                        Image(pic.second, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (row.size == 1) {
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Color: the colourful palettes, then the monochromatic ones, each a row of its six colours. */
@Composable
private fun ChartColors(vm: AppViewModel) {
    var palettes by remember { mutableStateOf<List<ChartPaletteView>>(emptyList()) }
    LaunchedEffect(vm.info?.revision) { vm.chartPalettes { palettes = it } }
    Scrolling {
        SectionBand("Colorful")
        for (p in palettes.filter { it.id.startsWith("colorful") }) PaletteSwatches(p) { vm.command("chart.colors", json("palette" to p.id)) }
        SectionBand("Monochromatic")
        for (p in palettes.filter { it.id.startsWith("monochromatic") }) PaletteSwatches(p) { vm.command("chart.colors", json("palette" to p.id)) }
    }
}

@Composable
private fun PaletteSwatches(p: ChartPaletteView, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 2.dp, vertical = 2.dp).clickable(onClick = onPick)) {
        for (hex in p.colors) {
            Box(Modifier.weight(1f).height(36.dp).background(Color(android.graphics.Color.parseColor("#$hex"))))
        }
    }
}
