//! Charts WordCraft makes and edits (Insert › Chart): the chart type, its data (categories and
//! series), title, legend, data labels and colours.
//!
//! A chart object ([`crate::graphic::Graphic`] with `chart` set) keeps this model next to the
//! items it is drawn with; the DOCX writer turns it into a chart part (`c:chartSpace`) with the
//! data cached in the part itself, and the reader turns a chart part back into it when the part
//! holds nothing this model leaves out. Charts from other programs stay as they were read.
//!
//! Every value from outside (commands, files) goes through [`ChartSpec::sanitize`]: series,
//! points and text lengths are capped and values that aren't finite numbers become gaps.

use serde::{Deserialize, Serialize};

/// Most series in a chart.
pub const MAX_SERIES: usize = 255;
/// Most categories (points per series) in a chart.
pub const MAX_POINTS: usize = 4000;
/// Most cells (series × categories) in a chart: what the chart drawing reads at most.
pub const MAX_CELLS: usize = 100_000;
/// Longest title, series name or category label, characters.
pub const MAX_TEXT: usize = 255;

/// What a chart shows its data as.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum ChartType {
    /// Clustered columns.
    #[default]
    Column,
    /// Stacked columns.
    ColumnStacked,
    /// Columns stacked to 100%: each category's share.
    ColumnStacked100,
    /// Clustered horizontal bars.
    Bar,
    /// Stacked horizontal bars.
    BarStacked,
    BarStacked100,
    Line,
    /// Each line drawn at the running total of the lines before it.
    LineStacked,
    LineStacked100,
    /// Lines with a marker at each point.
    LineMarkers,
    LineMarkersStacked,
    LineMarkersStacked100,
    Area,
    AreaStacked,
    AreaStacked100,
    Pie,
    Doughnut,
    /// XY scatter with markers: the categories are the x values.
    Scatter,
    /// XY scatter, its points joined by straight lines, with markers.
    ScatterLinesMarkers,
    /// XY scatter, its points joined by straight lines.
    ScatterLines,
    /// A spoke per category, each series a closed line round the centre.
    Radar,
    /// Radar with a marker at each point.
    RadarMarkers,
    /// Radar with each series a filled shape.
    RadarFilled,
    /// Stock: a line from each category's highest value to its lowest, with a marker at the close
    /// (the series are the high, low and closing prices).
    StockHlc,
    /// Stock with a bar from each category's open to its close, light when the price rose and dark
    /// when it fell (the series are the opening, high, low and closing prices).
    StockOhlc,
}

/// The families the Type menu lists, each with its types.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum ChartFamily {
    Column,
    Line,
    Pie,
    Bar,
    Area,
    Scatter,
    /// Radar and stock charts.
    Other,
}

impl ChartFamily {
    /// In the Type menu's order.
    pub const ALL: [ChartFamily; 7] =
        [ChartFamily::Column, ChartFamily::Line, ChartFamily::Pie, ChartFamily::Bar, ChartFamily::Area, ChartFamily::Scatter, ChartFamily::Other];

    pub fn id(self) -> &'static str {
        match self {
            ChartFamily::Column => "column",
            ChartFamily::Line => "line",
            ChartFamily::Pie => "pie",
            ChartFamily::Bar => "bar",
            ChartFamily::Area => "area",
            ChartFamily::Scatter => "scatter",
            ChartFamily::Other => "other",
        }
    }

    pub fn from_id(id: &str) -> Option<ChartFamily> {
        ChartFamily::ALL.into_iter().find(|f| f.id().eq_ignore_ascii_case(id.trim()))
    }

    pub fn label(self) -> &'static str {
        match self {
            ChartFamily::Column => "Column",
            ChartFamily::Line => "Line",
            ChartFamily::Pie => "Pie",
            ChartFamily::Bar => "Bar",
            ChartFamily::Area => "Area",
            ChartFamily::Scatter => "X Y (Scatter)",
            ChartFamily::Other => "Other",
        }
    }

    /// Its types, in its gallery's order.
    pub fn types(self) -> Vec<ChartType> {
        ChartType::ALL.into_iter().filter(|t| t.family() == self).collect()
    }
}

/// How a chart's series sit together: side by side (or each on its own), stacked, or stacked to
/// 100%.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Grouping {
    Standard,
    Stacked,
    Percent,
}

impl ChartType {
    pub const ALL: [ChartType; 25] = [
        ChartType::Column,
        ChartType::ColumnStacked,
        ChartType::ColumnStacked100,
        ChartType::Bar,
        ChartType::BarStacked,
        ChartType::BarStacked100,
        ChartType::Line,
        ChartType::LineStacked,
        ChartType::LineStacked100,
        ChartType::LineMarkers,
        ChartType::LineMarkersStacked,
        ChartType::LineMarkersStacked100,
        ChartType::Area,
        ChartType::AreaStacked,
        ChartType::AreaStacked100,
        ChartType::Pie,
        ChartType::Doughnut,
        ChartType::Scatter,
        ChartType::ScatterLinesMarkers,
        ChartType::ScatterLines,
        ChartType::Radar,
        ChartType::RadarMarkers,
        ChartType::RadarFilled,
        ChartType::StockHlc,
        ChartType::StockOhlc,
    ];

    /// The id commands use (`column`, `barStacked`…).
    pub fn id(self) -> &'static str {
        match self {
            ChartType::Column => "column",
            ChartType::ColumnStacked => "columnStacked",
            ChartType::ColumnStacked100 => "columnStacked100",
            ChartType::Bar => "bar",
            ChartType::BarStacked => "barStacked",
            ChartType::BarStacked100 => "barStacked100",
            ChartType::Line => "line",
            ChartType::LineStacked => "lineStacked",
            ChartType::LineStacked100 => "lineStacked100",
            ChartType::LineMarkers => "lineMarkers",
            ChartType::LineMarkersStacked => "lineMarkersStacked",
            ChartType::LineMarkersStacked100 => "lineMarkersStacked100",
            ChartType::Area => "area",
            ChartType::AreaStacked => "areaStacked",
            ChartType::AreaStacked100 => "areaStacked100",
            ChartType::Pie => "pie",
            ChartType::Doughnut => "doughnut",
            ChartType::Scatter => "scatter",
            ChartType::ScatterLinesMarkers => "scatterLinesMarkers",
            ChartType::ScatterLines => "scatterLines",
            ChartType::Radar => "radar",
            ChartType::RadarMarkers => "radarMarkers",
            ChartType::RadarFilled => "radarFilled",
            ChartType::StockHlc => "stockHlc",
            ChartType::StockOhlc => "stockOhlc",
        }
    }

    pub fn family(self) -> ChartFamily {
        match self {
            ChartType::Column | ChartType::ColumnStacked | ChartType::ColumnStacked100 => ChartFamily::Column,
            ChartType::Bar | ChartType::BarStacked | ChartType::BarStacked100 => ChartFamily::Bar,
            ChartType::Line
            | ChartType::LineStacked
            | ChartType::LineStacked100
            | ChartType::LineMarkers
            | ChartType::LineMarkersStacked
            | ChartType::LineMarkersStacked100 => ChartFamily::Line,
            ChartType::Area | ChartType::AreaStacked | ChartType::AreaStacked100 => ChartFamily::Area,
            ChartType::Pie | ChartType::Doughnut => ChartFamily::Pie,
            ChartType::Scatter | ChartType::ScatterLinesMarkers | ChartType::ScatterLines => ChartFamily::Scatter,
            ChartType::Radar | ChartType::RadarMarkers | ChartType::RadarFilled | ChartType::StockHlc | ChartType::StockOhlc => ChartFamily::Other,
        }
    }

    pub fn grouping(self) -> Grouping {
        match self {
            ChartType::ColumnStacked | ChartType::BarStacked | ChartType::LineStacked | ChartType::LineMarkersStacked | ChartType::AreaStacked => {
                Grouping::Stacked
            }
            ChartType::ColumnStacked100
            | ChartType::BarStacked100
            | ChartType::LineStacked100
            | ChartType::LineMarkersStacked100
            | ChartType::AreaStacked100 => Grouping::Percent,
            _ => Grouping::Standard,
        }
    }

    /// The type of `family` grouped `grouping`, with markers or not (lines), if there is one.
    pub fn of(family: ChartFamily, grouping: Grouping, markers: bool) -> Option<ChartType> {
        ChartType::ALL
            .into_iter()
            .find(|t| t.family() == family && t.grouping() == grouping && t.markers() == markers && !t.joined_by_lines())
            .or_else(|| ChartType::ALL.into_iter().find(|t| t.family() == family && t.grouping() == grouping && t.markers() == markers))
    }

    /// A marker at each point (lines and radars with markers, scatter charts other than lines
    /// only).
    pub fn markers(self) -> bool {
        matches!(
            self,
            ChartType::LineMarkers
                | ChartType::LineMarkersStacked
                | ChartType::LineMarkersStacked100
                | ChartType::Scatter
                | ChartType::ScatterLinesMarkers
                | ChartType::RadarMarkers
        )
    }

    /// A scatter chart whose points are joined by lines.
    pub fn joined_by_lines(self) -> bool {
        matches!(self, ChartType::ScatterLinesMarkers | ChartType::ScatterLines)
    }

    /// Bars run across (Bar), not up (Column).
    pub fn horizontal(self) -> bool {
        self.family() == ChartFamily::Bar
    }

    /// Bars or columns.
    pub fn is_bar(self) -> bool {
        matches!(self.family(), ChartFamily::Column | ChartFamily::Bar)
    }

    /// The type an id names (any ASCII case).
    pub fn from_id(id: &str) -> Option<ChartType> {
        ChartType::ALL.into_iter().find(|t| t.id().eq_ignore_ascii_case(id.trim()))
    }

    /// The name shown for it.
    pub fn label(self) -> &'static str {
        match self {
            ChartType::Column => "Clustered Column",
            ChartType::ColumnStacked => "Stacked Column",
            ChartType::ColumnStacked100 => "100% Stacked Column",
            ChartType::Bar => "Clustered Bar",
            ChartType::BarStacked => "Stacked Bar",
            ChartType::BarStacked100 => "100% Stacked Bar",
            ChartType::Line => "Line",
            ChartType::LineStacked => "Stacked Line",
            ChartType::LineStacked100 => "100% Stacked Line",
            ChartType::LineMarkers => "Line with Markers",
            ChartType::LineMarkersStacked => "Stacked Line with Markers",
            ChartType::LineMarkersStacked100 => "100% Stacked Line with Markers",
            ChartType::Area => "Area",
            ChartType::AreaStacked => "Stacked Area",
            ChartType::AreaStacked100 => "100% Stacked Area",
            ChartType::Pie => "Pie",
            ChartType::Doughnut => "Doughnut",
            ChartType::Scatter => "Scatter",
            ChartType::ScatterLinesMarkers => "Scatter with Straight Lines and Markers",
            ChartType::ScatterLines => "Scatter with Straight Lines",
            ChartType::Radar => "Radar",
            ChartType::RadarMarkers => "Radar with Markers",
            ChartType::RadarFilled => "Filled Radar",
            ChartType::StockHlc => "High-Low-Close",
            ChartType::StockOhlc => "Open-High-Low-Close",
        }
    }

    /// Pie and doughnut charts show one series, a slice per category.
    pub fn is_round(self) -> bool {
        matches!(self, ChartType::Pie | ChartType::Doughnut)
    }

    pub fn is_radar(self) -> bool {
        matches!(self, ChartType::Radar | ChartType::RadarMarkers | ChartType::RadarFilled)
    }

    pub fn is_stock(self) -> bool {
        matches!(self, ChartType::StockHlc | ChartType::StockOhlc)
    }

    /// The series a stock chart takes, in order (its prices); `None` for the other types, which
    /// take any.
    pub fn series_needed(self) -> Option<&'static [&'static str]> {
        match self {
            ChartType::StockHlc => Some(&["High", "Low", "Close"]),
            ChartType::StockOhlc => Some(&["Open", "High", "Low", "Close"]),
            _ => None,
        }
    }
}

/// Where a chart's legend sits.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum LegendPos {
    Right,
    Left,
    Top,
    #[default]
    Bottom,
}

impl LegendPos {
    pub const ALL: [LegendPos; 4] = [LegendPos::Right, LegendPos::Top, LegendPos::Left, LegendPos::Bottom];

    pub fn id(self) -> &'static str {
        match self {
            LegendPos::Right => "right",
            LegendPos::Left => "left",
            LegendPos::Top => "top",
            LegendPos::Bottom => "bottom",
        }
    }

    pub fn from_id(id: &str) -> Option<LegendPos> {
        LegendPos::ALL.into_iter().find(|p| p.id().eq_ignore_ascii_case(id.trim()))
    }
}

/// Where a chart's data labels sit (DrawingML `c:dLblPos`), or a callout: a box beside the point
/// with its category and value.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum LabelPos {
    Center,
    InsideEnd,
    InsideBase,
    OutsideEnd,
    BestFit,
    Left,
    Right,
    Above,
    Below,
    Callout,
}

impl LabelPos {
    pub const ALL: [LabelPos; 10] = [
        LabelPos::Center,
        LabelPos::InsideEnd,
        LabelPos::InsideBase,
        LabelPos::OutsideEnd,
        LabelPos::BestFit,
        LabelPos::Left,
        LabelPos::Right,
        LabelPos::Above,
        LabelPos::Below,
        LabelPos::Callout,
    ];

    pub fn id(self) -> &'static str {
        match self {
            LabelPos::Center => "center",
            LabelPos::InsideEnd => "insideEnd",
            LabelPos::InsideBase => "insideBase",
            LabelPos::OutsideEnd => "outsideEnd",
            LabelPos::BestFit => "bestFit",
            LabelPos::Left => "left",
            LabelPos::Right => "right",
            LabelPos::Above => "above",
            LabelPos::Below => "below",
            LabelPos::Callout => "callout",
        }
    }

    pub fn from_id(id: &str) -> Option<LabelPos> {
        LabelPos::ALL.into_iter().find(|p| p.id().eq_ignore_ascii_case(id.trim()))
    }

    pub fn label(self) -> &'static str {
        match self {
            LabelPos::Center => "Center",
            LabelPos::InsideEnd => "Inside End",
            LabelPos::InsideBase => "Inside Base",
            LabelPos::OutsideEnd => "Outside End",
            LabelPos::BestFit => "Best Fit",
            LabelPos::Left => "Left",
            LabelPos::Right => "Right",
            LabelPos::Above => "Above",
            LabelPos::Below => "Below",
            LabelPos::Callout => "Data Callout",
        }
    }

    /// The places a chart of type `kind` can put its labels (ECMA-376 §21.2.2.48 says which
    /// positions each chart type takes; outside the end only for unstacked bars). Empty: the
    /// labels go where the type puts them (area, doughnut and radar charts).
    pub fn choices(kind: ChartType) -> &'static [LabelPos] {
        use LabelPos::*;
        match kind.family() {
            ChartFamily::Column | ChartFamily::Bar if kind.grouping() == Grouping::Standard => &[Center, InsideEnd, InsideBase, OutsideEnd, Callout],
            ChartFamily::Column | ChartFamily::Bar => &[Center, InsideEnd, InsideBase, Callout],
            ChartFamily::Line | ChartFamily::Scatter => &[Center, Left, Right, Above, Below, Callout],
            ChartFamily::Pie if kind == ChartType::Pie => &[Center, InsideEnd, OutsideEnd, BestFit, Callout],
            ChartFamily::Other if kind.is_stock() => &[Center, Left, Right, Above, Below],
            ChartFamily::Pie | ChartFamily::Area | ChartFamily::Other => &[],
        }
    }
}

/// What a data label shows: the value, the category's name, the point's share of its series.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct LabelParts {
    pub value: bool,
    pub category: bool,
    pub percent: bool,
}

impl Default for LabelParts {
    fn default() -> Self {
        LabelParts { value: true, category: false, percent: false }
    }
}

impl LabelParts {
    pub fn is_default(&self) -> bool {
        *self == LabelParts::default()
    }
}

/// A chart's colours: the theme's accents in one of four mixes (Colorful), or the shades of one
/// colour (Monochromatic 1 to 13: each accent dark to light, greys, each accent light to dark).
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum ChartPalette {
    Colorful(u8),
    Monochromatic(u8),
}

impl Default for ChartPalette {
    fn default() -> Self {
        ChartPalette::Colorful(1)
    }
}

/// A theme colour as a chart palette uses it: the slot (`accent1`… `accent6`, `tx1`, `bg1`) and
/// its luminance scaled (`lum_mod`) then raised (`lum_off`), percent (DrawingML `a:lumMod`,
/// `a:lumOff`).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct ThemeShade {
    pub slot: &'static str,
    pub lum_mod: u8,
    pub lum_off: u8,
}

impl ChartPalette {
    pub const ALL: [ChartPalette; 17] = [
        ChartPalette::Colorful(1),
        ChartPalette::Colorful(2),
        ChartPalette::Colorful(3),
        ChartPalette::Colorful(4),
        ChartPalette::Monochromatic(1),
        ChartPalette::Monochromatic(2),
        ChartPalette::Monochromatic(3),
        ChartPalette::Monochromatic(4),
        ChartPalette::Monochromatic(5),
        ChartPalette::Monochromatic(6),
        ChartPalette::Monochromatic(7),
        ChartPalette::Monochromatic(8),
        ChartPalette::Monochromatic(9),
        ChartPalette::Monochromatic(10),
        ChartPalette::Monochromatic(11),
        ChartPalette::Monochromatic(12),
        ChartPalette::Monochromatic(13),
    ];

    /// `colorful1`… `colorful4`, `monochromatic1`… `monochromatic13`.
    pub fn id(self) -> String {
        match self {
            ChartPalette::Colorful(n) => format!("colorful{n}"),
            ChartPalette::Monochromatic(n) => format!("monochromatic{n}"),
        }
    }

    pub fn from_id(id: &str) -> Option<ChartPalette> {
        ChartPalette::ALL.into_iter().find(|p| p.id().eq_ignore_ascii_case(id.trim()))
    }

    pub fn is_default(&self) -> bool {
        *self == ChartPalette::default()
    }

    /// Its six colours, used in turn by the series (or a pie's slices).
    pub fn shades(self) -> [ThemeShade; 6] {
        const ACCENTS: [&str; 6] = ["accent1", "accent2", "accent3", "accent4", "accent5", "accent6"];
        let plain = |slot: &'static str| ThemeShade { slot, lum_mod: 100, lum_off: 0 };
        // darker by 40%: the second three of a two-tone mix
        let deep = |slot: &'static str| ThemeShade { slot, lum_mod: 60, lum_off: 0 };
        let mix = |a: usize, b: usize, c: usize| {
            let s = |i: usize| ACCENTS.get(i).copied().unwrap_or("accent1");
            [plain(s(a)), plain(s(b)), plain(s(c)), deep(s(a)), deep(s(b)), deep(s(c))]
        };
        // dark to light: half, three quarters, as it is, then 40%, 60% and 80% of the way to white
        let ramp = |slot: &'static str| {
            [
                ThemeShade { slot, lum_mod: 50, lum_off: 0 },
                ThemeShade { slot, lum_mod: 75, lum_off: 0 },
                plain(slot),
                ThemeShade { slot, lum_mod: 60, lum_off: 40 },
                ThemeShade { slot, lum_mod: 40, lum_off: 60 },
                ThemeShade { slot, lum_mod: 20, lum_off: 80 },
            ]
        };
        match self {
            ChartPalette::Colorful(2) => mix(0, 2, 4),
            ChartPalette::Colorful(3) => mix(1, 3, 5),
            ChartPalette::Colorful(4) => mix(5, 4, 3),
            ChartPalette::Colorful(_) => ACCENTS.map(plain),
            ChartPalette::Monochromatic(7) => [
                ThemeShade { slot: "tx1", lum_mod: 65, lum_off: 35 },
                ThemeShade { slot: "bg1", lum_mod: 70, lum_off: 0 },
                ThemeShade { slot: "bg1", lum_mod: 55, lum_off: 0 },
                ThemeShade { slot: "tx1", lum_mod: 85, lum_off: 15 },
                ThemeShade { slot: "bg1", lum_mod: 85, lum_off: 0 },
                ThemeShade { slot: "bg1", lum_mod: 65, lum_off: 0 },
            ],
            ChartPalette::Monochromatic(n) => {
                let i = usize::from(n.clamp(1, 13));
                let mut r = ramp(ACCENTS.get(if i < 7 { i - 1 } else { i - 8 }).copied().unwrap_or("accent1"));
                if i > 7 {
                    r.reverse();
                }
                r
            }
        }
    }
}

/// One series: its name and a value per category (`None` = no value, a gap).
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChartSeries {
    pub name: String,
    #[serde(default)]
    pub values: Vec<Option<f64>>,
}

/// A chart WordCraft can edit.
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChartSpec {
    #[serde(rename = "type")]
    pub kind: ChartType,
    /// Category labels (scatter charts: the x values).
    #[serde(default)]
    pub categories: Vec<String>,
    #[serde(default)]
    pub series: Vec<ChartSeries>,
    #[serde(default)]
    pub title: Option<String>,
    /// The title lies over the chart (Centered Overlay) instead of above it.
    #[serde(default, skip_serializing_if = "std::ops::Not::not")]
    pub title_overlay: bool,
    /// `None`: no legend.
    #[serde(default)]
    pub legend: Option<LegendPos>,
    /// Show a label at each point.
    #[serde(default)]
    pub data_labels: bool,
    /// Where the labels sit (`None`: where the type puts them).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub label_pos: Option<LabelPos>,
    /// What the labels show.
    #[serde(default, skip_serializing_if = "LabelParts::is_default")]
    pub label_parts: LabelParts,
    #[serde(default, skip_serializing_if = "ChartPalette::is_default")]
    pub palette: ChartPalette,
    /// Its look (Chart Styles, 1 to [`CHART_STYLES`]; see [`ChartSpec::style_look`]); 0 or 1 is
    /// the plain one.
    #[serde(default, skip_serializing_if = "is_plain_style")]
    pub style: u8,
}

/// How many chart styles there are.
pub const CHART_STYLES: u8 = 6;

fn is_plain_style(s: &u8) -> bool {
    *s <= 1
}

/// What a chart style changes: white outlines between bars and slices, a light grey chart area,
/// wide bars without gridlines, a grey border round the chart.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct StyleLook {
    pub white_borders: bool,
    pub shaded: bool,
    pub bold: bool,
    pub framed: bool,
}

impl ChartSpec {
    /// A new chart of type `kind` with WordCraft's sample data: four quarters and three series
    /// (one for pie and doughnut charts, x values for scatter charts, four days' prices for stock
    /// charts), legend at the bottom.
    pub fn sample(kind: ChartType) -> ChartSpec {
        let (categories, series): (Vec<&str>, Vec<(&str, [f64; 4])>) = match kind.family() {
            ChartFamily::Pie => (vec!["North", "East", "South", "West"], vec![("Share", [38.0, 24.0, 21.0, 17.0])]),
            ChartFamily::Scatter => (vec!["1", "2", "3", "4"], vec![("Series 1", [2.1, 3.4, 3.0, 4.6]), ("Series 2", [1.2, 1.9, 2.8, 3.1])]),
            _ if kind.is_stock() => {
                let mut prices = vec![("High", [52.0, 54.0, 53.0, 56.0]), ("Low", [47.0, 49.0, 48.0, 51.0]), ("Close", [50.0, 49.0, 52.0, 55.0])];
                if kind == ChartType::StockOhlc {
                    prices.insert(0, ("Open", [48.0, 51.0, 50.0, 52.0]));
                }
                (vec!["Mon", "Tue", "Wed", "Thu"], prices)
            }
            _ => (
                vec!["Q1", "Q2", "Q3", "Q4"],
                vec![("Series 1", [3.2, 4.1, 3.6, 5.0]), ("Series 2", [2.0, 2.7, 3.9, 3.3]), ("Series 3", [1.5, 2.2, 2.4, 3.8])],
            ),
        };
        ChartSpec {
            kind,
            categories: categories.into_iter().map(str::to_string).collect(),
            series: series.into_iter().map(|(n, v)| ChartSeries { name: n.to_string(), values: v.into_iter().map(Some).collect() }).collect(),
            legend: Some(LegendPos::Bottom),
            ..ChartSpec::default()
        }
    }

    /// What its style changes (see [`StyleLook`]): 2 white outlines, 3 a shaded chart area with
    /// white outlines, 4 wide bars without gridlines, 5 a border round the chart, 6 shaded with
    /// wide bars.
    /// Lines (radar and stock charts' too) have no outlines to whiten.
    pub fn style_look(&self) -> StyleLook {
        let mut look = match self.style {
            2 => StyleLook { white_borders: true, ..StyleLook::default() },
            3 => StyleLook { white_borders: true, shaded: true, ..StyleLook::default() },
            4 => StyleLook { bold: true, ..StyleLook::default() },
            5 => StyleLook { framed: true, ..StyleLook::default() },
            6 => StyleLook { shaded: true, bold: true, ..StyleLook::default() },
            _ => StyleLook::default(),
        };
        look.white_borders &= !matches!(self.kind.family(), ChartFamily::Line | ChartFamily::Scatter | ChartFamily::Other);
        // nor do pies have bars to widen or gridlines to drop
        look.bold &= !self.kind.is_round();
        look
    }

    /// Number of categories: every series has as many values.
    pub fn points(&self) -> usize {
        self.categories.len()
    }

    /// Make the chart safe to store and draw: text cut to [`MAX_TEXT`] characters with control
    /// characters turned into spaces and the ends trimmed (an empty title is none); at most
    /// [`MAX_POINTS`] categories, [`MAX_SERIES`] series and [`MAX_CELLS`] cells; values that
    /// aren't finite become gaps; every series as long as the categories (padded with gaps,
    /// categories padded with empty labels to the longest series).
    pub fn sanitize(&mut self) {
        self.series.truncate(MAX_SERIES);
        let longest = self.series.iter().map(|s| s.values.len()).max().unwrap_or(0);
        let n = self.categories.len().max(longest).min(MAX_POINTS);
        let keep = MAX_CELLS.checked_div(n).unwrap_or(MAX_SERIES).clamp(1, MAX_SERIES);
        self.series.truncate(keep);
        self.categories.truncate(n);
        for c in &mut self.categories {
            *c = clean_text(c);
        }
        self.categories.resize(n, String::new());
        for s in &mut self.series {
            s.name = clean_text(&s.name);
            s.values.truncate(n);
            for v in &mut s.values {
                *v = v.filter(|x| x.is_finite());
            }
            s.values.resize(n, None);
        }
        self.title = self.title.as_deref().map(clean_text).filter(|t| !t.is_empty());
        self.title_overlay &= self.title.is_some();
        // a place the type doesn't take is the type's own
        self.label_pos = self.label_pos.filter(|p| LabelPos::choices(self.kind).contains(p));
        // a callout shows the category and the value
        if self.label_pos == Some(LabelPos::Callout) {
            self.label_parts = LabelParts { value: true, category: true, percent: false };
        }
        if !(self.label_parts.value || self.label_parts.category || self.label_parts.percent) {
            self.label_parts = LabelParts::default();
        }
        // a share only means something on a pie
        if self.kind.family() != ChartFamily::Pie {
            self.label_parts.percent = false;
            if !(self.label_parts.value || self.label_parts.category) {
                self.label_parts.value = true;
            }
        }
        // a style that changes nothing on this type is the plain one
        if self.style > CHART_STYLES || self.style_look() == StyleLook::default() {
            self.style = 0;
        }
        self.palette = match self.palette {
            ChartPalette::Colorful(n) if (1..=4).contains(&n) => self.palette,
            ChartPalette::Monochromatic(n) if (1..=13).contains(&n) => self.palette,
            _ => ChartPalette::default(),
        };
    }

    /// The chart after `sanitize`.
    pub fn sanitized(mut self) -> ChartSpec {
        self.sanitize();
        self
    }
}

/// `s` as chart text: control characters as spaces, ends trimmed, at most [`MAX_TEXT`] characters.
pub fn clean_text(s: &str) -> String {
    let s: String = s.chars().take(MAX_TEXT * 4).map(|c| if c.is_control() { ' ' } else { c }).collect();
    s.trim().chars().take(MAX_TEXT).collect::<String>().trim_end().to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sanitize_caps_sizes_and_drops_non_finite_values() {
        let mut c = ChartSpec {
            categories: vec!["a".into(); MAX_POINTS + 10],
            series: (0..MAX_SERIES + 5)
                .map(|i| ChartSeries { name: format!("{}\n{}", "x".repeat(400), i), values: vec![Some(f64::NAN), Some(f64::INFINITY), Some(1.0)] })
                .collect(),
            title: Some("  ".into()),
            ..Default::default()
        };
        c.sanitize();
        assert_eq!(c.points(), MAX_POINTS);
        assert!(c.series.len() * c.points() <= MAX_CELLS);
        assert!(!c.series.is_empty());
        let s = &c.series[0];
        assert_eq!(s.values.len(), MAX_POINTS);
        assert_eq!(&s.values[..3], &[None, None, Some(1.0)]);
        assert_eq!(s.name.chars().count(), MAX_TEXT);
        assert!(!s.name.contains('\n'));
        assert_eq!(c.title, None);
    }

    #[test]
    fn short_categories_are_padded_to_the_longest_series() {
        let c = ChartSpec {
            categories: vec!["a".into()],
            series: vec![ChartSeries { name: "s".into(), values: vec![Some(1.0), Some(2.0)] }],
            ..Default::default()
        }
        .sanitized();
        assert_eq!(c.categories, vec!["a".to_string(), String::new()]);
        assert_eq!(ChartType::from_id("BARSTACKED"), Some(ChartType::BarStacked));
        assert_eq!(LegendPos::from_id("top"), Some(LegendPos::Top));
    }

    #[test]
    fn every_type_has_a_family_and_the_families_cover_them() {
        let all: usize = ChartFamily::ALL.iter().map(|f| f.types().len()).sum();
        assert_eq!(all, ChartType::ALL.len());
        for t in ChartType::ALL {
            assert_eq!(ChartType::from_id(t.id()), Some(t));
        }
        assert_eq!(ChartType::of(ChartFamily::Bar, Grouping::Percent, false), Some(ChartType::BarStacked100));
        assert_eq!(ChartType::of(ChartFamily::Line, Grouping::Stacked, true), Some(ChartType::LineMarkersStacked));
        assert_eq!(ChartType::of(ChartFamily::Scatter, Grouping::Standard, true), Some(ChartType::Scatter));
        assert_eq!(ChartFamily::Column.types(), [ChartType::Column, ChartType::ColumnStacked, ChartType::ColumnStacked100]);
    }

    #[test]
    fn labels_take_only_the_places_their_type_has() {
        let mut c = ChartSpec { label_pos: Some(LabelPos::OutsideEnd), ..ChartSpec::sample(ChartType::ColumnStacked) }.sanitized();
        assert_eq!(c.label_pos, None, "a stacked column has no outside end");
        c.kind = ChartType::Pie;
        c.label_pos = Some(LabelPos::BestFit);
        c.label_parts = LabelParts { value: false, category: true, percent: true };
        let c = c.sanitized();
        assert_eq!((c.label_pos, c.label_parts.percent), (Some(LabelPos::BestFit), true));
        let line =
            ChartSpec { label_parts: LabelParts { value: false, category: false, percent: true }, ..ChartSpec::sample(ChartType::Line) }.sanitized();
        assert!(line.label_parts.value && !line.label_parts.percent, "no shares on a line chart");
        let untitled = ChartSpec { title_overlay: true, ..ChartSpec::sample(ChartType::Line) }.sanitized();
        assert!(!untitled.title_overlay);
    }

    #[test]
    fn palettes_have_six_colours_and_ids() {
        for p in ChartPalette::ALL {
            assert_eq!(ChartPalette::from_id(&p.id()), Some(p));
            assert_eq!(p.shades().len(), 6);
        }
        assert_eq!(ChartPalette::Colorful(1).shades()[3].slot, "accent4");
        let m1 = ChartPalette::Monochromatic(1).shades();
        let m8 = ChartPalette::Monochromatic(8).shades();
        assert!(m1.iter().all(|s| s.slot == "accent1") && m1[0] == m8[5], "light to dark is dark to light turned round");
        let odd = ChartSpec { palette: ChartPalette::Monochromatic(40), ..ChartSpec::default() }.sanitized();
        assert!(odd.palette.is_default());
    }
}
