//! Charts WordCraft makes ([`ChartSpec`]) as chart parts (ECMA-376 Part 1 §21.2) and back.
//!
//! [`chart_xml`] writes a `c:chartSpace` with the data as literals in the part itself
//! (`c:strLit`, `c:numLit`): no embedded workbook (`c:externalData`) and no external references.
//! [`chart_items`] draws a chart through the same code that draws chart parts read from files.
//! [`spec_of`] turns a chart part back into the model only when writing that model again gives
//! the same markup, so a chart with anything the model leaves out stays as it was read.

use wordcraft_doc::chart::{ChartFamily, ChartPalette, ChartSeries, ChartSpec, ChartType, Grouping, LabelParts, LabelPos, LegendPos, ThemeShade};
use wordcraft_doc::graphic::GraphicItem;
use wordcraft_doc::props::Rgb;

use crate::xml::{self, El, MAX_DEPTH, Node, W};

const NS_C: &str = "http://schemas.openxmlformats.org/drawingml/2006/chart";
const NS_A: &str = "http://schemas.openxmlformats.org/drawingml/2006/main";
const NS_R: &str = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
/// Content type of a chart part.
pub(crate) const CHART_CT: &str = "application/vnd.openxmlformats-officedocument.drawingml.chart+xml";
/// `a:graphicData` URI of a chart.
pub(crate) const CHART_URI: &str = "http://schemas.openxmlformats.org/drawingml/2006/chart";
/// Axis ids (any pair of distinct numbers will do; Word keeps them).
const CAT_AX: &str = "500000001";
const VAL_AX: &str = "500000002";

/// The chart part for `spec`: a complete XML document. `spec` should be sanitized
/// ([`ChartSpec::sanitize`]); a value that isn't finite is written as a gap either way.
pub fn chart_xml(spec: &ChartSpec) -> String {
    let mut w = W::new();
    w.open("c:chartSpace", &[("xmlns:c", NS_C), ("xmlns:a", NS_A), ("xmlns:r", NS_R)]);
    w.empty("c:roundedCorners", &[("val", "0")]);
    w.open("c:chart", &[]);
    if let Some(t) = &spec.title {
        w.open("c:title", &[]);
        w.open("c:tx", &[]);
        w.open("c:rich", &[]);
        w.empty("a:bodyPr", &[]);
        w.empty("a:lstStyle", &[]);
        w.open("a:p", &[]);
        w.open("a:r", &[]);
        w.leaf("a:t", &[], t);
        w.close("a:r");
        w.close("a:p");
        w.close("c:rich");
        w.close("c:tx");
        w.empty("c:overlay", &[("val", if spec.title_overlay { "1" } else { "0" })]);
        w.close("c:title");
    }
    w.empty("c:autoTitleDeleted", &[("val", if spec.title.is_some() { "0" } else { "1" })]);
    w.open("c:plotArea", &[]);
    w.empty("c:layout", &[]);
    plot(&mut w, spec);
    w.close("c:plotArea");
    if let Some(pos) = spec.legend {
        w.open("c:legend", &[]);
        let p = match pos {
            LegendPos::Right => "r",
            LegendPos::Left => "l",
            LegendPos::Top => "t",
            LegendPos::Bottom => "b",
        };
        w.empty("c:legendPos", &[("val", p)]);
        w.empty("c:overlay", &[("val", "0")]);
        w.close("c:legend");
    }
    w.empty("c:plotVisOnly", &[("val", "1")]);
    w.empty("c:dispBlanksAs", &[("val", "gap")]);
    w.close("c:chart");
    // A white chart area without a border, in the document's own background colour (a shaded
    // style: a light grey one; a framed style: a grey border).
    let look = spec.style_look();
    w.open("c:spPr", &[]);
    shade_fill(&mut w, ThemeShade { slot: "bg1", lum_mod: if look.shaded { 95 } else { 100 }, lum_off: 0 });
    if look.framed {
        w.open("a:ln", &[("w", "9525")]);
        shade_fill(&mut w, ThemeShade { slot: "tx1", lum_mod: 25, lum_off: 75 });
        w.close("a:ln");
    } else {
        w.open("a:ln", &[]);
        w.empty("a:noFill", &[]);
        w.close("a:ln");
    }
    w.close("c:spPr");
    w.close("c:chartSpace");
    w.s
}

/// What `spec` draws as, `w` × `h` points, in the colours of `theme` (series take the accents in
/// turn). Empty when the chart has no series.
pub fn chart_items(spec: &ChartSpec, theme: &[Rgb], w: f32, h: f32) -> Vec<GraphicItem> {
    match xml::parse(chart_xml(spec).as_bytes()) {
        Ok(space) => crate::read::chart::chart_items(&space, theme, w, h),
        Err(_) => Vec::new(),
    }
}

/// The chart type element and the series in it, then the axes.
fn plot(w: &mut W, spec: &ChartSpec) {
    let k = spec.kind;
    let family = k.family();
    let el = match family {
        ChartFamily::Column | ChartFamily::Bar => "c:barChart",
        ChartFamily::Line => "c:lineChart",
        ChartFamily::Area => "c:areaChart",
        ChartFamily::Pie if k == ChartType::Doughnut => "c:doughnutChart",
        ChartFamily::Pie => "c:pieChart",
        ChartFamily::Scatter => "c:scatterChart",
        ChartFamily::Other if k.is_stock() => "c:stockChart",
        ChartFamily::Other => "c:radarChart",
    };
    let grouping = match k.grouping() {
        Grouping::Standard if k.is_bar() => "clustered",
        Grouping::Standard => "standard",
        Grouping::Stacked => "stacked",
        Grouping::Percent => "percentStacked",
    };
    w.open(el, &[]);
    match family {
        ChartFamily::Column | ChartFamily::Bar => {
            w.empty("c:barDir", &[("val", if k.horizontal() { "bar" } else { "col" })]);
            w.empty("c:grouping", &[("val", grouping)]);
        }
        ChartFamily::Line | ChartFamily::Area => w.empty("c:grouping", &[("val", grouping)]),
        ChartFamily::Scatter => w.empty("c:scatterStyle", &[("val", "lineMarker")]),
        ChartFamily::Other if k.is_radar() => w.empty("c:radarStyle", &[("val", if k == ChartType::RadarFilled { "filled" } else { "marker" })]),
        ChartFamily::Pie | ChartFamily::Other => {}
    }
    // a stock chart's colours don't vary: it has no such element
    if !k.is_stock() {
        w.empty("c:varyColors", &[("val", if k.is_round() { "1" } else { "0" })]);
    }
    // Pie and doughnut charts show the first series; the others are kept for other types.
    for (i, s) in spec.series.iter().enumerate() {
        series(w, spec, i, s);
    }
    if spec.data_labels {
        data_labels(w, spec);
    }
    match family {
        ChartFamily::Column | ChartFamily::Bar => {
            w.empty("c:gapWidth", &[("val", if spec.style_look().bold { "50" } else { "150" })]);
            if k.grouping() != Grouping::Standard {
                w.empty("c:overlap", &[("val", "100")]);
            }
        }
        ChartFamily::Line => w.empty("c:marker", &[("val", "1")]),
        ChartFamily::Pie => {
            w.empty("c:firstSliceAng", &[("val", "0")]);
            if k == ChartType::Doughnut {
                w.empty("c:holeSize", &[("val", "50")]);
            }
        }
        // a line from each day's high to its low, and with an open, a bar from it to the close
        ChartFamily::Other if k.is_stock() => {
            w.empty("c:hiLowLines", &[]);
            if k == ChartType::StockOhlc {
                w.open("c:upDownBars", &[]);
                w.empty("c:gapWidth", &[("val", "150")]);
                w.empty("c:upBars", &[]);
                w.empty("c:downBars", &[]);
                w.close("c:upDownBars");
            }
        }
        ChartFamily::Area | ChartFamily::Scatter | ChartFamily::Other => {}
    }
    if !k.is_round() {
        w.empty("c:axId", &[("val", CAT_AX)]);
        w.empty("c:axId", &[("val", VAL_AX)]);
    }
    w.close(el);
    match family {
        ChartFamily::Pie => {}
        ChartFamily::Scatter => {
            value_axis(w, CAT_AX, VAL_AX, "b", false, "midCat");
            value_axis(w, VAL_AX, CAT_AX, "l", !spec.style_look().bold, "midCat");
        }
        _ => {
            category_axis(w, if k.horizontal() { "l" } else { "b" });
            value_axis(w, VAL_AX, CAT_AX, if k.horizontal() { "b" } else { "l" }, !spec.style_look().bold, "between");
        }
    }
}

/// `c:dLblPos` for a label place (a callout sits where the type puts labels outside).
fn label_pos_val(spec: &ChartSpec, pos: LabelPos) -> Option<&'static str> {
    Some(match pos {
        LabelPos::Center => "ctr",
        LabelPos::InsideEnd => "inEnd",
        LabelPos::InsideBase => "inBase",
        LabelPos::OutsideEnd => "outEnd",
        LabelPos::BestFit => "bestFit",
        LabelPos::Left => "l",
        LabelPos::Right => "r",
        LabelPos::Above => "t",
        LabelPos::Below => "b",
        LabelPos::Callout => match spec.kind.family() {
            ChartFamily::Column | ChartFamily::Bar if spec.kind.grouping() == Grouping::Standard => "outEnd",
            ChartFamily::Column | ChartFamily::Bar => "ctr",
            ChartFamily::Pie => "bestFit",
            ChartFamily::Line | ChartFamily::Scatter => "r",
            ChartFamily::Area | ChartFamily::Other => return None,
        },
    })
}

/// The type's data labels (`c:dLbls`): a callout's box, the place, then what they show.
fn data_labels(w: &mut W, spec: &ChartSpec) {
    let callout = spec.label_pos == Some(LabelPos::Callout);
    let parts = if callout { LabelParts { value: true, category: true, percent: false } } else { spec.label_parts };
    w.open("c:dLbls", &[]);
    if callout {
        // a white box with a thin grey border
        w.open("c:spPr", &[]);
        w.open("a:solidFill", &[]);
        w.empty("a:schemeClr", &[("val", "bg1")]);
        w.close("a:solidFill");
        w.open("a:ln", &[("w", "9525")]);
        w.open("a:solidFill", &[]);
        w.open("a:schemeClr", &[("val", "tx1")]);
        w.empty("a:lumMod", &[("val", "25000")]);
        w.empty("a:lumOff", &[("val", "75000")]);
        w.close("a:schemeClr");
        w.close("a:solidFill");
        w.close("a:ln");
        w.close("c:spPr");
    }
    if let Some(pos) = spec.label_pos.and_then(|p| label_pos_val(spec, p)) {
        w.empty("c:dLblPos", &[("val", pos)]);
    }
    for (name, on) in [
        ("c:showLegendKey", false),
        ("c:showVal", parts.value),
        ("c:showCatName", parts.category),
        ("c:showSerName", false),
        ("c:showPercent", parts.percent),
        ("c:showBubbleSize", false),
    ] {
        w.empty(name, &[("val", if on { "1" } else { "0" })]);
    }
    w.close("c:dLbls");
}

/// A palette colour as a fill (`a:solidFill` with an `a:schemeClr`, its luminance changed).
fn shade_fill(w: &mut W, s: ThemeShade) {
    w.open("a:solidFill", &[]);
    if s.lum_mod == 100 && s.lum_off == 0 {
        w.empty("a:schemeClr", &[("val", s.slot)]);
    } else {
        w.open("a:schemeClr", &[("val", s.slot)]);
        w.empty("a:lumMod", &[("val", &(u32::from(s.lum_mod) * 1000).to_string())]);
        if s.lum_off > 0 {
            w.empty("a:lumOff", &[("val", &(u32::from(s.lum_off) * 1000).to_string())]);
        }
        w.close("a:schemeClr");
    }
    w.close("a:solidFill");
}

/// Colour `i` of the chart's palette, when it isn't the default one (whose colours readers take
/// from the theme by themselves).
fn palette_shade(spec: &ChartSpec, i: usize) -> Option<ThemeShade> {
    (!spec.palette.is_default()).then(|| spec.palette.shades().get(i % 6).copied()).flatten()
}

/// One `c:ser` (ECMA-376 element order: idx, order, tx, spPr, invertIfNegative, marker, dPt,
/// cat/xVal, val/yVal, smooth).
fn series(w: &mut W, spec: &ChartSpec, i: usize, s: &ChartSeries) {
    let k = spec.kind;
    let shade = palette_shade(spec, i);
    let idx = i.to_string();
    w.open("c:ser", &[]);
    w.empty("c:idx", &[("val", &idx)]);
    w.empty("c:order", &[("val", &idx)]);
    if !s.name.is_empty() {
        w.open("c:tx", &[]);
        w.leaf("c:v", &[], &s.name);
        w.close("c:tx");
    }
    let lined = matches!(k.family(), ChartFamily::Line | ChartFamily::Scatter | ChartFamily::Other) && k != ChartType::RadarFilled;
    if (k.family() == ChartFamily::Scatter && !k.joined_by_lines()) || k.is_stock() {
        // Markers only: the series has no line (a stock chart's prices are drawn by its high-low
        // lines and bars).
        w.open("c:spPr", &[]);
        w.open("a:ln", &[("w", "19050")]);
        w.empty("a:noFill", &[]);
        w.close("a:ln");
        w.close("c:spPr");
    } else if lined {
        if let Some(sh) = shade {
            w.open("c:spPr", &[]);
            w.open("a:ln", &[]);
            shade_fill(w, sh);
            w.close("a:ln");
            w.close("c:spPr");
        }
    } else {
        // a pie's colours go on its slices; a style's white outlines on the whole series
        let fill = shade.filter(|_| !k.is_round());
        let border = spec.style_look().white_borders;
        if fill.is_some() || border {
            w.open("c:spPr", &[]);
            if let Some(sh) = fill {
                shade_fill(w, sh);
            }
            if border {
                w.open("a:ln", &[("w", "19050")]);
                shade_fill(w, ThemeShade { slot: "bg1", lum_mod: 100, lum_off: 0 });
                w.close("a:ln");
            }
            w.close("c:spPr");
        }
    }
    if k.is_bar() {
        w.empty("c:invertIfNegative", &[("val", "0")]);
    }
    match k.family() {
        // the close's marker on a high-low-close chart; each series takes the palette's colour
        ChartFamily::Other if k.is_stock() => {
            let close = k == ChartType::StockHlc && i + 1 == spec.series.len();
            w.open("c:marker", &[]);
            w.empty("c:symbol", &[("val", if close { "dot" } else { "none" })]);
            if close {
                w.empty("c:size", &[("val", "5")]);
            }
            if let Some(sh) = shade {
                w.open("c:spPr", &[]);
                shade_fill(w, sh);
                w.close("c:spPr");
            }
            w.close("c:marker");
        }
        ChartFamily::Line | ChartFamily::Scatter | ChartFamily::Other
            if k.markers() && (k.family() != ChartFamily::Scatter || k.joined_by_lines()) =>
        {
            w.open("c:marker", &[]);
            w.empty("c:symbol", &[("val", "circle")]);
            w.empty("c:size", &[("val", "5")]);
            if let Some(sh) = shade {
                w.open("c:spPr", &[]);
                shade_fill(w, sh);
                w.close("c:spPr");
            }
            w.close("c:marker");
        }
        ChartFamily::Scatter if k.markers() => {
            if let Some(sh) = shade {
                w.open("c:marker", &[]);
                w.empty("c:symbol", &[("val", "circle")]);
                w.empty("c:size", &[("val", "5")]);
                w.open("c:spPr", &[]);
                shade_fill(w, sh);
                w.close("c:spPr");
                w.close("c:marker");
            }
        }
        ChartFamily::Line | ChartFamily::Scatter | ChartFamily::Other if k != ChartType::RadarFilled => {
            w.open("c:marker", &[]);
            w.empty("c:symbol", &[("val", "none")]);
            w.close("c:marker");
        }
        _ => {}
    }
    // a pie's slices take the palette's colours in turn
    if k.is_round() && !spec.palette.is_default() {
        for p in 0..s.values.len() {
            let Some(sh) = palette_shade(spec, p) else { break };
            w.open("c:dPt", &[]);
            w.empty("c:idx", &[("val", &p.to_string())]);
            w.open("c:spPr", &[]);
            shade_fill(w, sh);
            w.close("c:spPr");
            w.close("c:dPt");
        }
    }
    if k.family() == ChartFamily::Scatter {
        let xs: Option<Vec<Option<f64>>> = spec
            .categories
            .iter()
            .map(|c| if c.is_empty() { Some(None) } else { c.parse::<f64>().ok().filter(|v| v.is_finite()).map(Some) })
            .collect();
        w.open("c:xVal", &[]);
        match xs {
            Some(xs) => num_lit(w, &xs),
            None => str_lit(w, &spec.categories),
        }
        w.close("c:xVal");
        w.open("c:yVal", &[]);
        num_lit(w, &s.values);
        w.close("c:yVal");
    } else {
        w.open("c:cat", &[]);
        str_lit(w, &spec.categories);
        w.close("c:cat");
        w.open("c:val", &[]);
        num_lit(w, &s.values);
        w.close("c:val");
    }
    if matches!(k.family(), ChartFamily::Line | ChartFamily::Scatter) || k.is_stock() {
        w.empty("c:smooth", &[("val", "0")]);
    }
    w.close("c:ser");
}

fn str_lit(w: &mut W, items: &[String]) {
    w.open("c:strLit", &[]);
    w.empty("c:ptCount", &[("val", &items.len().to_string())]);
    for (i, s) in items.iter().enumerate().filter(|(_, s)| !s.is_empty()) {
        w.open("c:pt", &[("idx", &i.to_string())]);
        w.leaf("c:v", &[], s);
        w.close("c:pt");
    }
    w.close("c:strLit");
}

fn num_lit(w: &mut W, values: &[Option<f64>]) {
    w.open("c:numLit", &[]);
    w.leaf("c:formatCode", &[], "General");
    w.empty("c:ptCount", &[("val", &values.len().to_string())]);
    for (i, v) in values.iter().enumerate() {
        if let Some(v) = v.filter(|v| v.is_finite()) {
            w.open("c:pt", &[("idx", &i.to_string())]);
            w.leaf("c:v", &[], &num(v));
            w.close("c:pt");
        }
    }
    w.close("c:numLit");
}

/// A value as the shortest text that reads back as the same number.
fn num(v: f64) -> String {
    if v == 0.0 { "0".to_string() } else { format!("{v}") }
}

fn category_axis(w: &mut W, pos: &str) {
    w.open("c:catAx", &[]);
    w.empty("c:axId", &[("val", CAT_AX)]);
    w.open("c:scaling", &[]);
    w.empty("c:orientation", &[("val", "minMax")]);
    w.close("c:scaling");
    w.empty("c:delete", &[("val", "0")]);
    w.empty("c:axPos", &[("val", pos)]);
    w.empty("c:majorTickMark", &[("val", "none")]);
    w.empty("c:minorTickMark", &[("val", "none")]);
    w.empty("c:tickLblPos", &[("val", "nextTo")]);
    w.empty("c:crossAx", &[("val", VAL_AX)]);
    w.empty("c:crosses", &[("val", "autoZero")]);
    w.empty("c:auto", &[("val", "1")]);
    w.empty("c:lblAlgn", &[("val", "ctr")]);
    w.empty("c:lblOffset", &[("val", "100")]);
    w.empty("c:noMultiLvlLbl", &[("val", "0")]);
    w.close("c:catAx");
}

/// A value axis: with gridlines and no axis line (`grid`), else with its line and no gridlines.
fn value_axis(w: &mut W, id: &str, cross: &str, pos: &str, grid: bool, between: &str) {
    w.open("c:valAx", &[]);
    w.empty("c:axId", &[("val", id)]);
    w.open("c:scaling", &[]);
    w.empty("c:orientation", &[("val", "minMax")]);
    w.close("c:scaling");
    w.empty("c:delete", &[("val", "0")]);
    w.empty("c:axPos", &[("val", pos)]);
    if grid {
        w.empty("c:majorGridlines", &[]);
    }
    w.empty("c:majorTickMark", &[("val", "none")]);
    w.empty("c:minorTickMark", &[("val", "none")]);
    w.empty("c:tickLblPos", &[("val", "nextTo")]);
    if grid {
        w.open("c:spPr", &[]);
        w.open("a:ln", &[]);
        w.empty("a:noFill", &[]);
        w.close("a:ln");
        w.close("c:spPr");
    }
    w.empty("c:crossAx", &[("val", cross)]);
    w.empty("c:crosses", &[("val", "autoZero")]);
    w.empty("c:crossBetween", &[("val", between)]);
    w.close("c:valAx");
}

/// The chart a chart part holds, when the part is exactly what [`chart_xml`] writes for it (so
/// editing and saving it loses nothing). `None` for any other chart.
pub(crate) fn spec_of(space: &El) -> Option<ChartSpec> {
    let chart = space.child("c:chart")?;
    let plot = chart.child("c:plotArea")?;
    let ty = plot.els().find(|e| e.name.ends_with("Chart"))?;
    let val = |e: &El, n: &str| e.child(n).and_then(|c| c.attr("val")).map(str::to_string);
    let grouping = match val(ty, "c:grouping").as_deref() {
        Some("stacked") => Grouping::Stacked,
        Some("percentStacked") => Grouping::Percent,
        _ => Grouping::Standard,
    };
    let first = ty.child("c:ser");
    // the first series' marker: none, or a symbol
    let symbol = first.and_then(|s| s.child("c:marker")).and_then(|m| val(m, "c:symbol"));
    let kind = match ty.name.as_str() {
        "c:barChart" if val(ty, "c:barDir").as_deref() == Some("bar") => ChartType::of(ChartFamily::Bar, grouping, false)?,
        "c:barChart" => ChartType::of(ChartFamily::Column, grouping, false)?,
        "c:lineChart" => ChartType::of(ChartFamily::Line, grouping, symbol.as_deref().is_some_and(|v| v != "none"))?,
        "c:areaChart" => ChartType::of(ChartFamily::Area, grouping, false)?,
        "c:pieChart" => ChartType::Pie,
        "c:doughnutChart" => ChartType::Doughnut,
        "c:radarChart" if val(ty, "c:radarStyle").as_deref() == Some("filled") => ChartType::RadarFilled,
        "c:radarChart" if symbol.as_deref() == Some("none") => ChartType::Radar,
        "c:radarChart" => ChartType::RadarMarkers,
        "c:stockChart" if ty.child("c:upDownBars").is_some() => ChartType::StockOhlc,
        "c:stockChart" => ChartType::StockHlc,
        "c:scatterChart" => {
            let no_line = first.and_then(|s| s.child("c:spPr")).and_then(|p| p.child("a:ln")).is_some_and(|l| l.child("a:noFill").is_some());
            match (no_line, symbol.as_deref()) {
                (true, _) => ChartType::Scatter,
                (false, Some("none")) => ChartType::ScatterLines,
                (false, _) => ChartType::ScatterLinesMarkers,
            }
        }
        _ => return None,
    };
    let scatter = kind.family() == ChartFamily::Scatter;
    let sers: Vec<&El> = ty.children("c:ser").take(wordcraft_doc::chart::MAX_SERIES + 1).collect();
    // More than the model holds can't be the same chart: don't build it to find out.
    let count = |s: &&El| s.find("c:ptCount").and_then(|c| c.attr("val")).and_then(|v| v.parse::<usize>().ok()).unwrap_or(0);
    let longest = sers.iter().map(count).max().unwrap_or(0);
    if sers.len() > wordcraft_doc::chart::MAX_SERIES
        || longest > wordcraft_doc::chart::MAX_POINTS
        || sers.len().saturating_mul(longest) > wordcraft_doc::chart::MAX_CELLS
    {
        return None;
    }
    let categories = sers.first().and_then(|s| s.child(if scatter { "c:xVal" } else { "c:cat" })).map(literal).unwrap_or_default();
    let series = sers
        .iter()
        .map(|s| ChartSeries {
            name: s.child("c:tx").and_then(|t| t.child("c:v")).map(|v| v.text()).unwrap_or_default(),
            values: s
                .child(if scatter { "c:yVal" } else { "c:val" })
                .map(literal)
                .unwrap_or_default()
                .iter()
                .map(|v| v.parse::<f64>().ok().filter(|v| v.is_finite()))
                .collect(),
        })
        .collect();
    let title = chart.child("c:title").and_then(|t| t.find("a:t")).map(|t| t.text());
    let legend = chart.child("c:legend").map(|l| match val(l, "c:legendPos").as_deref() {
        Some("l") => LegendPos::Left,
        Some("t") => LegendPos::Top,
        Some("b") => LegendPos::Bottom,
        _ => LegendPos::Right,
    });
    let title_overlay = chart.child("c:title").and_then(|t| val(t, "c:overlay")).as_deref() == Some("1");
    let dl = ty.child("c:dLbls");
    let on = |n: &str| dl.and_then(|d| val(d, n)).as_deref() == Some("1");
    let data_labels = dl.is_some();
    let label_pos = if dl.is_some_and(|d| d.child("c:spPr").is_some()) {
        Some(LabelPos::Callout)
    } else {
        dl.and_then(|d| val(d, "c:dLblPos")).and_then(|v| {
            Some(match v.as_str() {
                "ctr" => LabelPos::Center,
                "inEnd" => LabelPos::InsideEnd,
                "inBase" => LabelPos::InsideBase,
                "outEnd" => LabelPos::OutsideEnd,
                "bestFit" => LabelPos::BestFit,
                "l" => LabelPos::Left,
                "r" => LabelPos::Right,
                "t" => LabelPos::Above,
                "b" => LabelPos::Below,
                _ => return None,
            })
        })
    };
    let label_parts = LabelParts { value: on("c:showVal"), category: on("c:showCatName"), percent: on("c:showPercent") };
    let palette = palette_of(ty, kind);
    let style = style_of(space, ty, kind);
    let spec = ChartSpec { kind, categories, series, title, title_overlay, legend, data_labels, label_pos, label_parts, palette, style }.sanitized();
    // Editable only when nothing would be lost: writing the model again gives the same part.
    let again = xml::parse(chart_xml(&spec).as_bytes()).ok()?;
    same(space, &again, 0).then_some(spec)
}

/// The palette whose colours the chart's series (a pie's slices) have, as [`series`] writes them;
/// the default one when they have none.
fn palette_of(ty: &El, kind: ChartType) -> ChartPalette {
    // the fill of each series (line and radar charts: the line's, markers-only scatter charts and
    // stock charts: the marker's)
    let fills: Vec<&El> = if kind.is_round() {
        ty.child("c:ser").into_iter().flat_map(|s| s.children("c:dPt")).filter_map(|d| d.child("c:spPr")?.child("a:solidFill")).take(6).collect()
    } else {
        ty.children("c:ser")
            .take(6)
            .filter_map(|s| {
                let sp = match kind.family() {
                    ChartFamily::Scatter if !kind.joined_by_lines() => s.child("c:marker")?.child("c:spPr")?,
                    ChartFamily::Other if kind.is_stock() => s.child("c:marker")?.child("c:spPr")?,
                    ChartFamily::Line | ChartFamily::Scatter => s.child("c:spPr")?.child("a:ln")?,
                    ChartFamily::Other if kind != ChartType::RadarFilled => s.child("c:spPr")?.child("a:ln")?,
                    _ => s.child("c:spPr")?,
                };
                sp.child("a:solidFill")
            })
            .collect()
    };
    let shade = |f: &El| -> Option<(String, u8, u8)> {
        let c = f.child("a:schemeClr")?;
        let pct =
            |n: &str, d: u8| c.child(n).and_then(|m| m.attr("val")).and_then(|v| v.parse::<u32>().ok()).map_or(d, |v| (v / 1000).min(255) as u8);
        Some((c.attr("val")?.to_string(), pct("a:lumMod", 100), pct("a:lumOff", 0)))
    };
    let got: Vec<Option<(String, u8, u8)>> = fills.iter().map(|f| shade(f)).collect();
    if got.is_empty() {
        return ChartPalette::default();
    }
    ChartPalette::ALL
        .into_iter()
        .find(|p| {
            got.iter().zip(p.shades()).all(|(g, s)| g.as_ref().is_some_and(|(slot, m, o)| slot == s.slot && *m == s.lum_mod && *o == s.lum_off))
        })
        .unwrap_or_default()
}

/// The chart style whose look the part has, as [`chart_xml`] writes it (the plain one, 0, when
/// none matches).
fn style_of(space: &El, ty: &El, kind: ChartType) -> u8 {
    let sp = space.child("c:spPr");
    let lum =
        |f: Option<&El>| f.and_then(|f| f.child("a:schemeClr")).and_then(|c| c.child("a:lumMod")).and_then(|m| m.attr("val")).map(str::to_string);
    let shaded = lum(sp.and_then(|p| p.child("a:solidFill"))).as_deref() == Some("95000");
    let framed = sp.and_then(|p| p.child("a:ln")).is_some_and(|l| l.child("a:solidFill").is_some());
    let white = !matches!(kind.family(), ChartFamily::Line | ChartFamily::Scatter | ChartFamily::Other)
        && ty.child("c:ser").and_then(|s| s.child("c:spPr")).and_then(|p| p.child("a:ln")).is_some_and(|l| l.attr("w") == Some("19050"));
    let gap50 = ty.child("c:gapWidth").and_then(|g| g.attr("val")) == Some("50");
    let no_grid = !kind.is_round() && space.find("c:valAx").is_some_and(|_| space.find("c:majorGridlines").is_none());
    let bold = if kind.is_bar() { gap50 } else { no_grid };
    let found = wordcraft_doc::chart::StyleLook { white_borders: white, shaded, bold, framed };
    (2..=wordcraft_doc::chart::CHART_STYLES).find(|&style| ChartSpec { kind, style, ..ChartSpec::default() }.style_look() == found).unwrap_or(0)
}

/// The palette `p`'s colours in `theme`, as a reader shows them.
pub fn palette_colors(p: ChartPalette, theme: &[Rgb]) -> Vec<Rgb> {
    p.shades()
        .iter()
        .filter_map(|s| {
            let mut w = W::new();
            w.open("a:fill", &[("xmlns:a", NS_A)]);
            shade_fill(&mut w, *s);
            w.close("a:fill");
            let el = xml::parse(w.s.as_bytes()).ok()?;
            crate::read::drawing_color::color(el.child("a:solidFill")?, theme)
        })
        .collect()
}

/// The points of a `c:strLit` / `c:numLit` (or a cache) as texts by index, `ptCount` long.
fn literal(e: &El) -> Vec<String> {
    let Some(lit) = e.els().find(|c| matches!(c.name.as_str(), "c:strLit" | "c:numLit")) else { return Vec::new() };
    let n = lit
        .child("c:ptCount")
        .and_then(|c| c.attr("val"))
        .and_then(|v| v.parse::<usize>().ok())
        .unwrap_or(0)
        .min(wordcraft_doc::chart::MAX_POINTS + 1);
    let mut out = vec![String::new(); n];
    for p in lit.children("c:pt") {
        if let Some(slot) = p.attr("idx").and_then(|i| i.parse::<usize>().ok()).and_then(|i| out.get_mut(i)) {
            *slot = p.child("c:v").map(|v| v.text()).unwrap_or_default();
        }
    }
    out
}

/// Whether two elements are the same markup: names, attributes (in any order), child elements in
/// order and text (whitespace-only text between elements ignored).
fn same(a: &El, b: &El, depth: usize) -> bool {
    if depth > MAX_DEPTH || a.name != b.name || a.attrs.len() != b.attrs.len() {
        return false;
    }
    if !a.attrs.iter().all(|(k, v)| b.attr(k) == Some(v.as_str()) && b.attrs.iter().any(|(bk, _)| bk == k)) {
        return false;
    }
    let text = |e: &El| -> String {
        e.kids
            .iter()
            .filter_map(|n| match n {
                Node::Text(t) if !t.trim().is_empty() => Some(t.as_str()),
                _ => None,
            })
            .collect()
    };
    if text(a) != text(b) {
        return false;
    }
    let (mut x, mut y) = (a.els(), b.els());
    loop {
        match (x.next(), y.next()) {
            (None, None) => return true,
            (Some(p), Some(q)) if same(p, q, depth + 1) => {}
            _ => return false,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use wordcraft_doc::THEME_COLORS;
    use wordcraft_doc::graphic::PathSeg as PathSegment;

    fn parse(spec: &ChartSpec) -> El {
        xml::parse(chart_xml(spec).as_bytes()).unwrap()
    }

    /// Children of `e` by name, in order.
    fn names(e: &El) -> Vec<&str> {
        e.els().map(|c| c.name.as_str()).collect()
    }

    #[test]
    fn every_type_reads_back_as_itself() {
        for kind in ChartType::ALL {
            let mut spec = ChartSpec::sample(kind);
            spec.title = Some("Sales & <costs>".into());
            spec.data_labels = true;
            spec.legend = Some(LegendPos::Right);
            spec.series[0].values[1] = None;
            spec.categories[2] = String::new();
            let spec = spec.sanitized();
            assert_eq!(spec_of(&parse(&spec)).as_ref(), Some(&spec), "{kind:?}");
            assert!(!chart_items(&spec, &THEME_COLORS, 300.0, 200.0).is_empty(), "{kind:?} draws");
        }
        let plain = ChartSpec { legend: None, ..ChartSpec::sample(ChartType::Line) };
        assert_eq!(spec_of(&parse(&plain)), Some(plain));
    }

    /// Titles over the chart, labels' places and contents and the palettes come back too, for
    /// every type.
    #[test]
    fn elements_and_colours_read_back() {
        for kind in ChartType::ALL {
            for (n, palette) in ChartPalette::ALL.into_iter().enumerate() {
                let mut spec = ChartSpec::sample(kind);
                spec.palette = palette;
                spec.title = Some("T".into());
                spec.title_overlay = n % 2 == 0;
                spec.data_labels = true;
                let places = LabelPos::choices(kind);
                spec.label_pos = places.get(n % places.len().max(1)).copied();
                spec.label_parts = LabelParts { value: n % 3 != 0, category: n % 3 != 1, percent: n % 2 == 1 };
                let spec = spec.sanitized();
                assert_eq!(spec_of(&parse(&spec)).as_ref(), Some(&spec), "{kind:?} {palette:?}");
            }
        }
    }

    /// Markers on lines, labels with categories and shares, callout boxes and a title over the
    /// plot are drawn.
    #[test]
    fn markers_labels_and_overlay_titles_draw() {
        use wordcraft_doc::para::ShapeKind;
        let draw = |spec: ChartSpec| chart_items(&spec.sanitized(), &THEME_COLORS, 300.0, 200.0);
        let ellipses = |items: &[GraphicItem]| items.iter().filter(|i| matches!(i, GraphicItem::Shape { kind: ShapeKind::Ellipse, .. })).count();
        assert_eq!(ellipses(&draw(ChartSpec::sample(ChartType::LineMarkers))), 12, "a marker at each of 3 x 4 points");
        assert_eq!(ellipses(&draw(ChartSpec::sample(ChartType::Line))), 0);
        assert_eq!(ellipses(&draw(ChartSpec::sample(ChartType::ScatterLinesMarkers))), 8);
        assert_eq!(ellipses(&draw(ChartSpec::sample(ChartType::ScatterLines))), 0);
        let texts = |items: &[GraphicItem]| {
            items.iter().filter_map(|i| if let GraphicItem::Text { text, .. } = i { Some(text.clone()) } else { None }).collect::<Vec<_>>()
        };
        let pie = ChartSpec {
            data_labels: true,
            label_parts: LabelParts { value: false, category: true, percent: true },
            ..ChartSpec::sample(ChartType::Pie)
        };
        assert!(texts(&draw(pie)).contains(&"North, 38%".to_string()));
        let boxes = |items: &[GraphicItem]| {
            items
                .iter()
                .filter(|i| matches!(i, GraphicItem::Shape { kind: ShapeKind::Rectangle, fill: Some(Rgb::WHITE), stroke: Some(_), .. }))
                .count()
        };
        let callout = ChartSpec { data_labels: true, label_pos: Some(LabelPos::Callout), ..ChartSpec::sample(ChartType::Column) };
        let items = draw(callout);
        assert_eq!(boxes(&items), 12, "a box per label");
        assert!(texts(&items).contains(&"Q1, 3.2".to_string()), "category and value");
        // over the plot, the title leaves the columns their full height
        let tallest = |items: &[GraphicItem]| {
            items
                .iter()
                .filter_map(|i| if let GraphicItem::Path { segs, .. } = i { Some(segs) } else { None })
                .map(|segs| {
                    let ys: Vec<f32> = segs
                        .iter()
                        .filter_map(|s| match s {
                            PathSegment::Move(_, y) | PathSegment::Line(_, y) => Some(*y),
                            _ => None,
                        })
                        .collect();
                    ys.iter().copied().fold(f32::MIN, f32::max) - ys.iter().copied().fold(f32::MAX, f32::min)
                })
                .fold(0.0f32, f32::max)
        };
        let titled = ChartSpec { title: Some("T".into()), ..ChartSpec::sample(ChartType::Column) };
        let over = ChartSpec { title_overlay: true, ..titled.clone() };
        assert!(tallest(&draw(over)) > tallest(&draw(titled)) + 5.0);
    }

    /// Each style reads back as itself on every type, with any palette.
    #[test]
    fn styles_read_back() {
        for kind in ChartType::ALL {
            for style in 0..=wordcraft_doc::chart::CHART_STYLES {
                for palette in [ChartPalette::Colorful(1), ChartPalette::Monochromatic(4)] {
                    let spec = ChartSpec { style, palette, ..ChartSpec::sample(kind) }.sanitized();
                    assert_eq!(spec_of(&parse(&spec)).as_ref(), Some(&spec), "{kind:?} style {style} {palette:?}");
                }
            }
        }
        // white outlines are drawn round the bars
        let outlined = chart_items(&ChartSpec { style: 2, ..ChartSpec::sample(ChartType::Column) }.sanitized(), &THEME_COLORS, 300.0, 200.0);
        assert!(outlined.iter().any(|i| matches!(i, GraphicItem::Path { fill: Some(_), stroke: Some(Rgb::WHITE), .. })));
    }

    #[test]
    fn palettes_resolve_in_the_theme() {
        let c = palette_colors(ChartPalette::Colorful(1), &THEME_COLORS);
        assert_eq!(c.len(), 6);
        assert_eq!(c[0], THEME_COLORS[4], "accent 1 as it is");
        let m = palette_colors(ChartPalette::Monochromatic(1), &THEME_COLORS);
        let lum = |c: Rgb| u32::from(c.0) + u32::from(c.1) + u32::from(c.2);
        assert!(m.windows(2).all(|w| lum(w[0]) < lum(w[1])), "dark to light: {m:?}");
    }

    #[test]
    fn a_chart_with_more_than_the_model_stays_read_only() {
        let spec = ChartSpec::sample(ChartType::Column);
        let x = chart_xml(&spec).replace("<c:roundedCorners val=\"0\"/>", "<c:roundedCorners val=\"0\"/><c:style val=\"2\"/>");
        assert_eq!(spec_of(&xml::parse(x.as_bytes()).unwrap()), None);
        let x = chart_xml(&spec).replace("<c:v>3.2</c:v>", "<c:v>3.20</c:v>");
        assert_eq!(spec_of(&xml::parse(x.as_bytes()).unwrap()), None);
    }

    #[test]
    fn elements_come_in_schema_order() {
        let mut spec = ChartSpec::sample(ChartType::ColumnStacked);
        spec.title = Some("T".into());
        spec.data_labels = true;
        let space = parse(&spec);
        assert_eq!(names(&space), ["c:roundedCorners", "c:chart", "c:spPr"]);
        let chart = space.child("c:chart").unwrap();
        assert_eq!(names(chart), ["c:title", "c:autoTitleDeleted", "c:plotArea", "c:legend", "c:plotVisOnly", "c:dispBlanksAs"]);
        let plot = chart.child("c:plotArea").unwrap();
        assert_eq!(names(plot), ["c:layout", "c:barChart", "c:catAx", "c:valAx"]);
        let bar = plot.child("c:barChart").unwrap();
        assert_eq!(
            names(bar),
            ["c:barDir", "c:grouping", "c:varyColors", "c:ser", "c:ser", "c:ser", "c:dLbls", "c:gapWidth", "c:overlap", "c:axId", "c:axId"]
        );
        assert_eq!(names(bar.child("c:ser").unwrap()), ["c:idx", "c:order", "c:tx", "c:invertIfNegative", "c:cat", "c:val"]);
        let val_ax = plot.child("c:valAx").unwrap();
        assert_eq!(
            names(val_ax),
            [
                "c:axId",
                "c:scaling",
                "c:delete",
                "c:axPos",
                "c:majorGridlines",
                "c:majorTickMark",
                "c:minorTickMark",
                "c:tickLblPos",
                "c:spPr",
                "c:crossAx",
                "c:crosses",
                "c:crossBetween"
            ]
        );
        let line = parse(&ChartSpec { data_labels: true, ..ChartSpec::sample(ChartType::Line) });
        let lc = line.child("c:chart").unwrap().child("c:plotArea").unwrap().child("c:lineChart").unwrap();
        assert_eq!(names(lc), ["c:grouping", "c:varyColors", "c:ser", "c:ser", "c:ser", "c:dLbls", "c:marker", "c:axId", "c:axId"]);
        assert_eq!(names(lc.child("c:ser").unwrap()), ["c:idx", "c:order", "c:tx", "c:marker", "c:cat", "c:val", "c:smooth"]);
        let sc = parse(&ChartSpec::sample(ChartType::Scatter));
        let sc = sc.child("c:chart").unwrap().child("c:plotArea").unwrap().child("c:scatterChart").unwrap();
        assert_eq!(names(sc.child("c:ser").unwrap()), ["c:idx", "c:order", "c:tx", "c:spPr", "c:xVal", "c:yVal", "c:smooth"]);
        let dn = parse(&ChartSpec::sample(ChartType::Doughnut));
        let dn = dn.child("c:chart").unwrap().child("c:plotArea").unwrap();
        assert_eq!(names(dn), ["c:layout", "c:doughnutChart"]);
        assert_eq!(names(dn.child("c:doughnutChart").unwrap()), ["c:varyColors", "c:ser", "c:firstSliceAng", "c:holeSize"]);
        let rd = parse(&ChartSpec { data_labels: true, ..ChartSpec::sample(ChartType::RadarMarkers) });
        let rd = rd.child("c:chart").unwrap().child("c:plotArea").unwrap();
        assert_eq!(names(rd), ["c:layout", "c:radarChart", "c:catAx", "c:valAx"]);
        let rc = rd.child("c:radarChart").unwrap();
        assert_eq!(names(rc), ["c:radarStyle", "c:varyColors", "c:ser", "c:ser", "c:ser", "c:dLbls", "c:axId", "c:axId"]);
        assert_eq!(names(rc.child("c:ser").unwrap()), ["c:idx", "c:order", "c:tx", "c:marker", "c:cat", "c:val"]);
        let st = parse(&ChartSpec { data_labels: true, ..ChartSpec::sample(ChartType::StockOhlc) });
        let st = st.child("c:chart").unwrap().child("c:plotArea").unwrap().child("c:stockChart").unwrap();
        assert_eq!(names(st), ["c:ser", "c:ser", "c:ser", "c:ser", "c:dLbls", "c:hiLowLines", "c:upDownBars", "c:axId", "c:axId"]);
        assert_eq!(names(st.child("c:ser").unwrap()), ["c:idx", "c:order", "c:tx", "c:spPr", "c:marker", "c:cat", "c:val", "c:smooth"]);
    }

    /// Radar charts draw each series as a closed line round the centre (filled: a shape) with
    /// the categories round the outside; stock charts a high-low line per day, the close's marker
    /// and, with an open, a bar to the close, light where the price rose.
    #[test]
    fn radar_and_stock_charts_draw() {
        use wordcraft_doc::para::ShapeKind;
        let draw = |spec: ChartSpec| chart_items(&spec.sanitized(), &THEME_COLORS, 300.0, 200.0);
        let ellipses = |items: &[GraphicItem]| items.iter().filter(|i| matches!(i, GraphicItem::Shape { kind: ShapeKind::Ellipse, .. })).count();
        assert_eq!(ellipses(&draw(ChartSpec::sample(ChartType::RadarMarkers))), 12, "a marker at each of 3 x 4 points");
        assert_eq!(ellipses(&draw(ChartSpec::sample(ChartType::Radar))), 0);
        let accent = THEME_COLORS[4];
        let filled_in = |items: &[GraphicItem]| items.iter().filter(|i| matches!(i, GraphicItem::Path { fill: Some(c), .. } if *c == accent)).count();
        assert_eq!(filled_in(&draw(ChartSpec::sample(ChartType::RadarFilled))), 1, "the first series as a shape");
        let radar = draw(ChartSpec::sample(ChartType::Radar));
        assert_eq!(filled_in(&radar), 0);
        let texts: Vec<&str> = radar.iter().filter_map(|i| if let GraphicItem::Text { text, .. } = i { Some(text.as_str()) } else { None }).collect();
        assert!(["Q1", "Q2", "Q3", "Q4"].iter().all(|q| texts.contains(q)), "{texts:?}");
        assert_eq!(ellipses(&draw(ChartSpec::sample(ChartType::StockHlc))), 4, "each day's close");
        let ohlc = draw(ChartSpec::sample(ChartType::StockOhlc));
        let bars = |c: Rgb| ohlc.iter().filter(|i| matches!(i, GraphicItem::Path { fill: Some(f), stroke: Some(_), .. } if *f == c)).count();
        // opens 48, 51, 50, 52 and closes 50, 49, 52, 55: up, down, up, up
        assert_eq!(bars(Rgb::WHITE), 3);
        assert_eq!(ellipses(&ohlc), 0);
    }
}
