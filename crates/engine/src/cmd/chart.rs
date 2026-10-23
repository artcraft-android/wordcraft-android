//! Insert › Chart and the Chart Design tab: insert a chart, change its type, data, title, legend,
//! data labels, quick layout and colours.
//!
//! A chart is an inline [`InlineObject::Graphic`] whose graphic keeps its model
//! ([`ChartSpec`]); it is drawn by the same code that draws charts read from files, and saved as
//! a chart part with its data in it (see `wordcraft_docx::chart_xml`). Charts made by other
//! programs are shown as they are; the chart commands only edit charts with a model.

use std::sync::Arc;

use serde_json::{Value, json};
use wordcraft_doc::chart::{
    CHART_STYLES, ChartFamily, ChartPalette, ChartSeries, ChartSpec, ChartType, LabelParts, LabelPos, LegendPos, MAX_POINTS, MAX_SERIES,
};
use wordcraft_doc::graphic::{Graphic, GraphicKind};
use wordcraft_doc::para::{Float, InlineObject};
use wordcraft_doc::props::Rgb;

use super::objects::selected;
use super::{delete_selection, sel_result};
use crate::{CmdError, CmdResult, CommandSpec, Selection, Session, p};

/// A new chart's size: 15 × 7.5 cm, points.
pub const DEFAULT_W: f32 = 15.0 / 2.54 * 72.0;
pub const DEFAULT_H: f32 = 7.5 / 2.54 * 72.0;

const TYPES: &str = "column|columnStacked|columnStacked100|bar|barStacked|barStacked100|line|lineStacked|lineStacked100|lineMarkers|lineMarkersStacked|lineMarkersStacked100|area|areaStacked|areaStacked100|pie|doughnut|scatter|scatterLinesMarkers|scatterLines|radar|radarMarkers|radarFilled|stockHlc|stockOhlc";
const DATA: &str = r#"{"categories": [string], "series": [{"name": string, "values": [number|null]}]}"#;

pub fn specs() -> Vec<CommandSpec> {
    vec![
        CommandSpec::new("insert.chart", "Chart", "Insert › Illustrations", insert).params(
            r#"{"type"?: "column|columnStacked|columnStacked100|bar|barStacked|barStacked100|line|lineStacked|lineStacked100|lineMarkers|lineMarkersStacked|lineMarkersStacked100|area|areaStacked|areaStacked100|pie|doughnut|scatter|scatterLinesMarkers|scatterLines|radar|radarMarkers|radarFilled|stockHlc|stockOhlc", "data"?: {"categories": [string], "series": [{"name": string, "values": [number|null]}]}, "title"?: string, "legend"?: "right|left|top|bottom|none", "dataLabels"?: bool, "width"?: pt, "height"?: pt} (no params: the chart type picker)"#,
        ),
        CommandSpec::new("chart.type", "Change Chart Type", "Chart Design › Type", |s, v| {
            let kind = chart_type(v)?.ok_or_else(|| CmdError::Params(format!("`type` is required: {TYPES}")))?;
            edit(s, |c| {
                // a stock chart's series are its prices, in their order
                if let Some(names) = kind.series_needed()
                    && c.series.len() != names.len()
                {
                    return Err(CmdError::Failed(format!(
                        "a {} chart takes {} series, in this order: {}",
                        kind.label(),
                        names.len(),
                        names.join(", ")
                    )));
                }
                c.kind = kind;
                Ok(())
            })
        })
        .params(r#"{"type": "column|columnStacked|columnStacked100|bar|barStacked|barStacked100|line|lineStacked|lineStacked100|lineMarkers|lineMarkersStacked|lineMarkersStacked100|area|areaStacked|areaStacked100|pie|doughnut|scatter|scatterLinesMarkers|scatterLines|radar|radarMarkers|radarFilled|stockHlc|stockOhlc"}"#)
        .when(has_chart),
        CommandSpec::new("chart.editData", "Edit Data", "Chart Design › Data", |s, v| {
            let d = v.get("data").unwrap_or(v);
            let (cats, series) = (d.get("categories"), d.get("series"));
            if cats.is_none() && series.is_none() {
                return Err(CmdError::Params(format!("`data` is required: {DATA}")));
            }
            let cats = cats.map(categories).transpose()?;
            let series = series.map(series_list).transpose()?;
            edit(s, move |c| {
                if let Some(cats) = &cats {
                    c.categories = cats.clone();
                }
                if let Some(series) = &series {
                    c.series = series.clone();
                }
                Ok(())
            })
        })
        .params(r#"{"data": {"categories"?: [string], "series"?: [{"name": string, "values": [number|null]}]}}"#)
        .when(has_chart),
        CommandSpec::new("chart.title", "Chart Title", "Chart Design › Chart Layouts › Add Chart Element", |s, v| {
            let text = match v.get("text") {
                Some(Value::String(t)) => Some(Some(t.clone())),
                Some(Value::Null) => Some(None),
                None => None,
                Some(_) => return Err(CmdError::Params("`text`: a string, or null for no title".into())),
            };
            let pos = match p::str(v, "pos") {
                None => None,
                Some(x) if x.eq_ignore_ascii_case("none") => Some(None),
                Some(x) if x.eq_ignore_ascii_case("above") => Some(Some(false)),
                Some(x) if x.eq_ignore_ascii_case("overlay") => Some(Some(true)),
                Some(x) => return Err(CmdError::Params(format!("`pos`: none, above or overlay, not `{x}`"))),
            };
            edit(s, move |c| {
                c.title = match (&text, pos) {
                    (Some(t), _) => t.clone(),
                    (None, Some(None)) => None,
                    (None, Some(Some(_))) => c.title.clone().or_else(|| Some("Chart Title".into())),
                    // No text given: switch the title on (placeholder) or off.
                    (None, None) if c.title.is_some() => None,
                    (None, None) => Some("Chart Title".into()),
                };
                if let Some(Some(over)) = pos {
                    c.title_overlay = over;
                }
                Ok(())
            })
        })
        .params(r#"{"text"?: string|null, "pos"?: "none|above|overlay"} (no text and no pos: switch the title on or off; above the chart, or over it, centred)"#)
        .when(has_chart),
        CommandSpec::new("chart.legend", "Legend", "Chart Design › Chart Layouts › Add Chart Element", |s, v| {
            let pos = match p::str(v, "pos").or_else(|| p::str(v, "position")) {
                Some(id) if id.eq_ignore_ascii_case("none") => Some(None),
                Some(id) => Some(Some(LegendPos::from_id(id).ok_or_else(|| CmdError::Params("`pos`: right|left|top|bottom|none".into()))?)),
                None => None,
            };
            edit(s, move |c| {
                c.legend = match pos {
                    Some(p) => p,
                    None if c.legend.is_some() => None,
                    None => Some(LegendPos::Bottom),
                };
                Ok(())
            })
        })
        .params(r#"{"pos"?: "right|left|top|bottom|none"} (no pos: switch the legend on or off)"#)
        .when(has_chart),
        CommandSpec::new("chart.dataLabels", "Data Labels", "Chart Design › Chart Layouts › Add Chart Element", |s, v| {
            let on = p::bool(v, "on");
            // where they go: a place (on), none (off), or the type's own (default)
            let pos = match p::str(v, "pos") {
                None => None,
                Some(x) if x.eq_ignore_ascii_case("none") => Some(None),
                Some(x) if x.eq_ignore_ascii_case("default") => Some(Some(None)),
                Some(x) => Some(Some(Some(LabelPos::from_id(x).ok_or_else(|| CmdError::Params(format!("`pos`: none, default or a place, not `{x}`")))?))),
            };
            let parts = ["showValue", "showCategory", "showPercent"].map(|k| p::bool(v, k));
            edit(s, move |c| {
                if let Some(Some(place)) = pos
                    && let Some(at) = place
                    && !LabelPos::choices(c.kind).contains(&at)
                {
                    return Err(CmdError::Params(format!("a {} chart can't put its labels at `{}`", c.kind.label(), at.id())));
                }
                c.data_labels = match (on, pos) {
                    (Some(on), _) => on,
                    (None, Some(place)) => place.is_some(),
                    // nothing said: on or off as the parts say, else switch them
                    (None, None) if parts.iter().any(Option::is_some) => true,
                    (None, None) => !c.data_labels,
                };
                if let Some(Some(place)) = pos {
                    c.label_pos = place;
                }
                let [value, category, percent] = parts;
                c.label_parts = LabelParts {
                    value: value.unwrap_or(c.label_parts.value),
                    category: category.unwrap_or(c.label_parts.category),
                    percent: percent.unwrap_or(c.label_parts.percent),
                };
                Ok(())
            })
        })
        .params(r#"{"on"?: bool, "pos"?: "none|default|center|insideEnd|insideBase|outsideEnd|bestFit|left|right|above|below|callout", "showValue"?: bool, "showCategory"?: bool, "showPercent"?: bool} (no value: switch the data labels on or off; the places a type takes: bars and columns center, insideEnd, insideBase, outsideEnd (not stacked); lines and scatter center, left, right, above, below; pies center, insideEnd, outsideEnd, bestFit; all of these callout)"#)
        .when(has_chart),
        CommandSpec::new("chart.quickLayout", "Quick Layout", "Chart Design › Chart Layouts", |s, v| {
            let n = p::u64(v, "layout").ok_or_else(|| CmdError::Params("`layout`: 1 to 7".into()))?;
            let n = u8::try_from(n).ok().filter(|n| (1..=QUICK_LAYOUTS).contains(n)).ok_or_else(|| CmdError::Params("`layout`: 1 to 7".into()))?;
            edit(s, move |c| {
                quick_layout(c, n);
                Ok(())
            })
        })
        .params(r#"{"layout": 1-7} (sets the title, legend and data labels together: pies 1 title and labels with the category and share, 2 legend at the top and shares, 3 legend at the bottom, 4 labels with the category and value, 5 title and categories, 6 legend at the right and shares, 7 legend at the right; others 1 title and legend at the right, 2 legend at the top and values, 3 legend at the bottom, 4 legend at the bottom and values without a title, 5 title and values, 6 legend at the right and values, 7 legend at the right without a title)"#)
        .when(has_chart),
        CommandSpec::new("chart.style", "Chart Styles", "Chart Design › Chart Styles", |s, v| {
            let n = p::u64(v, "style").and_then(|n| u8::try_from(n).ok()).filter(|n| (1..=CHART_STYLES).contains(n));
            let n = n.ok_or_else(|| CmdError::Params(format!("`style`: 1 to {CHART_STYLES}")))?;
            edit(s, move |c| {
                c.style = n;
                Ok(())
            })
        })
        .params(r#"{"style": 1-6} (1 plain, 2 white outlines between bars and slices, 3 a shaded chart area with white outlines, 4 wide bars without gridlines, 5 a grey border round the chart, 6 shaded with wide bars)"#)
        .when(has_chart),
        CommandSpec::new("chart.colors", "Change Colors", "Chart Design › Chart Styles", |s, v| {
            let id = p::req_str(v, "palette")?;
            let palette = ChartPalette::from_id(id).ok_or_else(|| CmdError::Params(format!("`palette`: colorful1-4 or monochromatic1-13, not `{id}`")))?;
            edit(s, move |c| {
                c.palette = palette;
                Ok(())
            })
        })
        .params(r#"{"palette": "colorful1|colorful2|colorful3|colorful4|monochromatic1|…|monochromatic13"} (the theme's accents in turn (colorful1), two-tone mixes of three accents (colorful2-4), or one colour's shades: each accent dark to light (monochromatic1-6), greys (7), each accent light to dark (8-13))"#)
        .when(has_chart),
    ]
}

/// How many quick layouts there are.
pub const QUICK_LAYOUTS: u8 = 7;

/// Quick Layout `n` (1 to [`QUICK_LAYOUTS`]) on `c`: its title, legend and data labels set
/// together (see `chart.quickLayout`).
pub fn quick_layout(c: &mut ChartSpec, n: u8) {
    use LegendPos::{Bottom, Right, Top};
    let value = LabelParts::default();
    let share = LabelParts { value: false, category: false, percent: true };
    // title, legend, labels and their place
    let (title, legend, labels, place): (bool, Option<LegendPos>, Option<LabelParts>, Option<LabelPos>) = if c.kind.family() == ChartFamily::Pie {
        match n {
            1 => (true, None, Some(LabelParts { value: false, category: true, percent: true }), Some(LabelPos::BestFit)),
            2 => (true, Some(Top), Some(share), None),
            3 => (false, Some(Bottom), None, None),
            4 => (false, None, Some(LabelParts { value: true, category: true, percent: false }), Some(LabelPos::Center)),
            5 => (true, None, Some(LabelParts { value: false, category: true, percent: false }), None),
            6 => (true, Some(Right), Some(share), None),
            _ => (false, Some(Right), None, None),
        }
    } else {
        match n {
            1 => (true, Some(Right), None, None),
            2 => (true, Some(Top), Some(value), None),
            3 => (true, Some(Bottom), None, None),
            4 => (false, Some(Bottom), Some(value), None),
            5 => (true, None, Some(value), None),
            6 => (true, Some(Right), Some(value), None),
            _ => (false, Some(Right), None, None),
        }
    };
    c.title = if title { c.title.clone().or_else(|| Some("Chart Title".into())) } else { None };
    c.title_overlay = false;
    c.legend = legend;
    c.data_labels = labels.is_some();
    c.label_parts = labels.unwrap_or_default();
    c.label_pos = place;
}

/// The charts a gallery shows for `c`, each with its id: `type:<family>` the family's types
/// (by type id), `layout` the quick layouts (1 to 7), `style` the chart styles (1 to 6).
pub fn gallery(c: &ChartSpec, which: &str) -> Vec<(String, ChartSpec)> {
    if let Some(family) = which.strip_prefix("type:").and_then(ChartFamily::from_id) {
        return family
            .types()
            .into_iter()
            .map(|t| {
                let mut v = c.clone();
                v.kind = t;
                (t.id().to_string(), v.sanitized())
            })
            .collect();
    }
    if which == "style" {
        // the styles that look different on this type
        let mut seen = Vec::new();
        return (1..=CHART_STYLES)
            .filter_map(|n| {
                let mut v = c.clone();
                v.style = n;
                let look = v.style_look();
                if seen.contains(&look) {
                    return None;
                }
                seen.push(look);
                Some((n.to_string(), v.sanitized()))
            })
            .collect();
    }
    if which == "layout" {
        return (1..=QUICK_LAYOUTS)
            .map(|n| {
                let mut v = c.clone();
                quick_layout(&mut v, n);
                (n.to_string(), v.sanitized())
            })
            .collect();
    }
    Vec::new()
}

/// The palette's colours in the theme `theme`, as the chart shows them.
pub fn palette_colors(p: ChartPalette, theme: &[Rgb]) -> Vec<Rgb> {
    wordcraft_docx::palette_colors(p, theme)
}

/// The selected chart's model, when it has one.
pub fn selected_chart(s: &Session) -> Option<Arc<ChartSpec>> {
    match selected(s) {
        Some((_, InlineObject::Graphic { graphic, .. })) => graphic.chart.clone(),
        _ => None,
    }
}

fn has_chart(s: &Session) -> Option<&'static str> {
    match selected(s) {
        Some((_, InlineObject::Graphic { graphic, .. })) if graphic.chart.is_some() => None,
        Some((_, InlineObject::Graphic { graphic, .. })) if graphic.kind == GraphicKind::Chart => {
            Some("this chart was made in another program: WordCraft shows it but can't edit it")
        }
        _ => Some("select a chart first"),
    }
}

/// The graphic for `spec` drawn `w` × `h` points in the theme's colours.
pub fn chart_graphic(spec: ChartSpec, theme: &[Rgb], w: f32, h: f32) -> Graphic {
    let spec = spec.sanitized();
    let items = wordcraft_docx::chart_items(&spec, theme, w, h);
    Graphic { kind: GraphicKind::Chart, items, w, h, source: None, chart: Some(Arc::new(spec)), smart_art: None }
}

/// `type` of the params, if given.
fn chart_type(v: &Value) -> Result<Option<ChartType>, CmdError> {
    match p::str(v, "type") {
        Some(id) => ChartType::from_id(id).map(Some).ok_or_else(|| CmdError::Params(format!("unknown chart type `{id}`: {TYPES}"))),
        None if v.get("type").is_some_and(|t| !t.is_null()) => Err(CmdError::Params(format!("`type`: {TYPES}"))),
        None => Ok(None),
    }
}

fn insert(s: &mut Session, v: &Value) -> CmdResult {
    let kind = chart_type(v)?;
    // No type and no data: Insert › Chart's picker, as the ribbon button does.
    if kind.is_none() && v.get("data").is_none() {
        s.ui_requests.push(json!({"open": "insertChart"}));
        return sel_result(s);
    }
    let kind = kind.unwrap_or_default();
    let mut spec = ChartSpec::sample(kind);
    if let Some(d) = v.get("data") {
        spec.categories = d.get("categories").map(categories).transpose()?.unwrap_or_default();
        spec.series = d.get("series").map(series_list).transpose()?.unwrap_or_default();
        if spec.series.is_empty() {
            return Err(CmdError::Params(format!("`data.series` needs at least one series: {DATA}")));
        }
    }
    spec.title = p::str(v, "title").map(str::to_string);
    match p::str(v, "legend") {
        Some(id) if id.eq_ignore_ascii_case("none") => spec.legend = None,
        Some(id) => spec.legend = Some(LegendPos::from_id(id).ok_or_else(|| CmdError::Params("`legend`: right|left|top|bottom|none".into()))?),
        None => {}
    }
    spec.data_labels = p::bool(v, "dataLabels").unwrap_or(false);
    // Fitted to the text width, as Word sizes a new chart.
    let max_w = s.doc.sections().first().map(|(_, sp)| sp.text_width()).unwrap_or(468.0).max(36.0);
    let w = p::f32(v, "width").unwrap_or(DEFAULT_W).clamp(36.0, 2000.0).min(max_w);
    let h = p::f32(v, "height").unwrap_or(DEFAULT_H).clamp(36.0, 2000.0);
    let graphic = chart_graphic(spec, &s.doc.settings.theme_colors, w, h);
    let out = json!({"chart": graphic.chart.as_deref(), "width": w, "height": h});
    let props = s.typing_props();
    let at = delete_selection(s)?;
    let obj = InlineObject::Graphic { w, h, alt: String::new(), float: Float::default(), graphic: Arc::new(graphic) };
    let end = s.doc.insert_object(&at, obj, &props)?;
    s.sel = Selection { anchor: at, focus: end };
    Ok(out)
}

/// Change the selected chart's model with `f`, then draw it again at its size.
fn edit(s: &mut Session, f: impl Fn(&mut ChartSpec) -> Result<(), CmdError>) -> CmdResult {
    let (pos, obj) = selected(s).ok_or_else(|| CmdError::Disabled("select a chart first".into()))?;
    let InlineObject::Graphic { w, h, graphic, .. } = obj else { return Err(CmdError::Disabled("select a chart first".into())) };
    let mut spec = graphic.chart.as_deref().cloned().ok_or_else(|| CmdError::Disabled("this chart can't be edited".into()))?;
    f(&mut spec)?;
    let next = Arc::new(chart_graphic(spec, &s.doc.settings.theme_colors, w, h));
    let out = json!({"chart": next.chart.as_deref()});
    let para = s.doc.para_mut(pos.story, &pos.path)?;
    if let Some(InlineObject::Graphic { graphic, .. }) = para.object_at_mut(pos.off) {
        *graphic = next;
    }
    para.touch();
    Ok(out)
}

/// Draw the chart of `o` again at its current size (after a resize), if it has a model.
pub fn redraw(o: &mut InlineObject, theme: &[Rgb]) {
    if let InlineObject::Graphic { w, h, graphic, .. } = o
        && let Some(spec) = graphic.chart.as_deref()
    {
        *graphic = Arc::new(chart_graphic(spec.clone(), theme, *w, *h));
    }
}

/// Category labels from JSON: strings, or numbers written as text.
fn categories(v: &Value) -> Result<Vec<String>, CmdError> {
    let a = v.as_array().ok_or_else(|| CmdError::Params("`categories`: an array of strings".into()))?;
    Ok(a.iter().take(MAX_POINTS).map(cell_text).collect())
}

fn cell_text(v: &Value) -> String {
    match v {
        Value::String(t) => wordcraft_doc::chart::clean_text(t),
        Value::Null => String::new(),
        other => wordcraft_doc::chart::clean_text(&other.to_string()),
    }
}

/// Series from JSON: `[{"name", "values": [number|null|numeric string]}]`.
fn series_list(v: &Value) -> Result<Vec<ChartSeries>, CmdError> {
    let a = v.as_array().ok_or_else(|| CmdError::Params(format!("`series`: {DATA}")))?;
    a.iter()
        .take(MAX_SERIES)
        .map(|s| {
            let name = s.get("name").map(cell_text).unwrap_or_default();
            let values = match s.get("values") {
                Some(Value::Array(vals)) => vals.iter().take(MAX_POINTS).map(number).collect(),
                None | Some(Value::Null) => Vec::new(),
                Some(_) => return Err(CmdError::Params("`values`: an array of numbers (null for none)".into())),
            };
            Ok(ChartSeries { name, values })
        })
        .collect()
}

/// A value: a number, or text that reads as one; anything else (or not finite) is no value.
fn number(v: &Value) -> Option<f64> {
    match v {
        Value::Number(n) => n.as_f64(),
        Value::String(t) => t.trim().parse::<f64>().ok(),
        _ => None,
    }
    .filter(|x| x.is_finite())
}
