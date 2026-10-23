//! Preset shapes (DrawingML `a:prstGeom`, ECMA-376 Part 1 §20.1.10.56): the outline each named
//! preset draws in a `w` × `h` frame, so a shape keeps its preset (it saves back as itself) and
//! is drawn at any size. WordCraft's own geometry: each preset's look at its default adjust
//! values, with the adjust values most presets are dragged by (corner size, arrow shaft and
//! head) honoured.

use crate::para::ShapeKind;

/// A point in the shape's frame (points, y down).
pub type Pt = [f32; 2];

/// What a preset draws: closed outlines filled together (an inner outline wound the other way
/// is a hole) and stroked when `stroke_fill`; then faces filled in a lighter or darker shade of
/// the fill (a cube's top and side) and stroked like the outline; then open lines, stroked only.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Outline {
    pub fill: Vec<Vec<Pt>>,
    pub faces: Vec<(Shade, Vec<Pt>)>,
    pub lines: Vec<Vec<Pt>>,
    pub stroke_fill: bool,
}

/// How a face is shaded from the shape's fill.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Shade {
    Lighten,
    LightenLess,
    Darken,
    DarkenLess,
}

impl Shade {
    /// The face's colour for fill colour `rgb`.
    pub fn apply(self, rgb: [u8; 3]) -> [u8; 3] {
        let f = |c: u8| {
            let c = f32::from(c);
            let v = match self {
                Shade::Lighten => c + (255.0 - c) * 0.4,
                Shade::LightenLess => c + (255.0 - c) * 0.2,
                Shade::Darken => c * 0.6,
                Shade::DarkenLess => c * 0.8,
            };
            v.round().clamp(0.0, 255.0) as u8
        };
        [f(rgb[0]), f(rgb[1]), f(rgb[2])]
    }
}

impl Outline {
    fn filled(fill: Vec<Vec<Pt>>) -> Outline {
        Outline { fill, stroke_fill: true, ..Outline::default() }
    }
    fn one(pts: Vec<Pt>) -> Outline {
        Outline::filled(vec![pts])
    }
    /// Lines only (an arc, a bracket).
    fn stroked(lines: Vec<Vec<Pt>>) -> Outline {
        Outline { lines, stroke_fill: true, ..Outline::default() }
    }
    fn with_lines(mut self, lines: Vec<Vec<Pt>>) -> Outline {
        self.lines.extend(lines);
        self
    }
    fn with_faces(mut self, faces: Vec<(Shade, Vec<Pt>)>) -> Outline {
        self.faces.extend(faces);
        self
    }
}

/// The groups of the shape gallery, in order, and the presets in each (the preset names).
pub const GALLERY: &[(&str, &[&str])] = &[
    ("Lines", &["line", "straightConnector1", "bentConnector3", "curvedConnector3"]),
    (
        "Rectangles",
        &["rect", "roundRect", "snip1Rect", "snip2SameRect", "snip2DiagRect", "snipRoundRect", "round1Rect", "round2SameRect", "round2DiagRect"],
    ),
    (
        "Basic Shapes",
        &[
            "ellipse",
            "triangle",
            "rtTriangle",
            "parallelogram",
            "trapezoid",
            "diamond",
            "pentagon",
            "hexagon",
            "heptagon",
            "octagon",
            "decagon",
            "dodecagon",
            "pie",
            "chord",
            "teardrop",
            "frame",
            "halfFrame",
            "corner",
            "diagStripe",
            "plus",
            "plaque",
            "can",
            "cube",
            "bevel",
            "donut",
            "noSmoking",
            "blockArc",
            "foldedCorner",
            "smileyFace",
            "heart",
            "lightningBolt",
            "sun",
            "moon",
            "cloud",
            "arc",
            "bracketPair",
            "bracePair",
            "leftBracket",
            "rightBracket",
            "leftBrace",
            "rightBrace",
        ],
    ),
    (
        "Block Arrows",
        &[
            "rightArrow",
            "leftArrow",
            "upArrow",
            "downArrow",
            "leftRightArrow",
            "upDownArrow",
            "quadArrow",
            "leftRightUpArrow",
            "bentArrow",
            "uturnArrow",
            "leftUpArrow",
            "bentUpArrow",
            "curvedRightArrow",
            "curvedLeftArrow",
            "curvedUpArrow",
            "curvedDownArrow",
            "stripedRightArrow",
            "notchedRightArrow",
            "homePlate",
            "chevron",
            "rightArrowCallout",
            "downArrowCallout",
            "leftArrowCallout",
            "upArrowCallout",
            "leftRightArrowCallout",
            "quadArrowCallout",
            "circularArrow",
        ],
    ),
    ("Equation Shapes", &["mathPlus", "mathMinus", "mathMultiply", "mathDivide", "mathEqual", "mathNotEqual"]),
    (
        "Flowchart",
        &[
            "flowChartProcess",
            "flowChartAlternateProcess",
            "flowChartDecision",
            "flowChartInputOutput",
            "flowChartPredefinedProcess",
            "flowChartInternalStorage",
            "flowChartDocument",
            "flowChartMultidocument",
            "flowChartTerminator",
            "flowChartPreparation",
            "flowChartManualInput",
            "flowChartManualOperation",
            "flowChartConnector",
            "flowChartOffpageConnector",
            "flowChartPunchedCard",
            "flowChartPunchedTape",
            "flowChartSummingJunction",
            "flowChartOr",
            "flowChartCollate",
            "flowChartSort",
            "flowChartExtract",
            "flowChartMerge",
            "flowChartOnlineStorage",
            "flowChartDelay",
            "flowChartMagneticTape",
            "flowChartMagneticDisk",
            "flowChartMagneticDrum",
            "flowChartDisplay",
        ],
    ),
    (
        "Stars and Banners",
        &[
            "irregularSeal1",
            "irregularSeal2",
            "star4",
            "star5",
            "star6",
            "star7",
            "star8",
            "star10",
            "star12",
            "star16",
            "star24",
            "star32",
            "ribbon2",
            "ribbon",
            "ellipseRibbon2",
            "ellipseRibbon",
            "verticalScroll",
            "horizontalScroll",
            "wave",
            "doubleWave",
        ],
    ),
    (
        "Callouts",
        &[
            "wedgeRectCallout",
            "wedgeRoundRectCallout",
            "wedgeEllipseCallout",
            "cloudCallout",
            "borderCallout1",
            "accentCallout1",
            "callout1",
            "accentBorderCallout1",
            "borderCallout2",
            "accentCallout2",
            "callout2",
            "accentBorderCallout2",
            "borderCallout3",
            "accentCallout3",
            "callout3",
            "accentBorderCallout3",
        ],
    ),
];

/// The shape kind nearest preset `prst`: what a shape of that preset is to commands that know
/// only kinds (callouts are the box, round or cloud they point from, flowchart boxes rectangles).
pub fn nearest_kind(prst: &str) -> ShapeKind {
    match prst {
        "roundRect"
        | "round1Rect"
        | "round2SameRect"
        | "round2DiagRect"
        | "snipRoundRect"
        | "wedgeRoundRectCallout"
        | "flowChartAlternateProcess"
        | "flowChartTerminator"
        | "plaque" => ShapeKind::RoundedRectangle,
        "ellipse" | "wedgeEllipseCallout" | "cloudCallout" | "cloud" | "flowChartConnector" | "donut" | "flowChartOnlineStorage" => {
            ShapeKind::Ellipse
        }
        "flowChartOr" => ShapeKind::FlowchartOr,
        "triangle" | "flowChartExtract" | "flowChartMerge" => ShapeKind::Triangle,
        "rtTriangle" => ShapeKind::RightTriangle,
        "diamond" | "flowChartDecision" => ShapeKind::Diamond,
        "line" | "lineInv" => ShapeKind::Line,
        "straightConnector1" => ShapeKind::StraightConnector,
        "bentConnector2" | "bentConnector3" | "bentConnector4" | "bentConnector5" => ShapeKind::ElbowConnector,
        "curvedConnector2" | "curvedConnector3" | "curvedConnector4" | "curvedConnector5" => ShapeKind::CurvedConnector,
        "rightArrow" | "leftArrow" | "upArrow" | "downArrow" | "leftRightArrow" | "upDownArrow" | "notchedRightArrow" | "stripedRightArrow" => {
            ShapeKind::Arrow
        }
        "star4" | "star5" | "star6" | "star7" | "star8" | "star10" | "star12" | "star16" | "star24" | "star32" => ShapeKind::Star,
        "heart" => ShapeKind::Heart,
        _ => ShapeKind::Rectangle,
    }
}

/// A preset's usual shape, width over height: what it is first inserted at, and its gallery icon.
pub fn aspect(prst: &str) -> f32 {
    match prst {
        "leftBracket" | "rightBracket" => 0.25,
        "leftBrace" | "rightBrace" => 0.42,
        "flowChartSort" => 0.5,
        "flowChartCollate" => 0.55,
        "moon" | "upArrow" | "downArrow" => 0.57,
        "upDownArrow" => 0.46,
        "curvedRightArrow" | "curvedLeftArrow" => 0.6,
        "can" => 0.76,
        "trapezoid" => 0.78,
        "chord" => 0.86,
        "star6" => 0.87,
        "bentArrow" | "verticalScroll" => 0.91,
        "triangle" | "hexagon" | "bentUpArrow" => 1.16,
        "flowChartPunchedCard" | "flowChartPunchedTape" | "horizontalScroll" => 1.12,
        "line" | "straightConnector1" | "bentConnector3" | "curvedConnector3" => 1.0,
        "callout1"
        | "callout2"
        | "callout3"
        | "accentCallout1"
        | "accentCallout2"
        | "accentCallout3"
        | "borderCallout1"
        | "borderCallout2"
        | "borderCallout3"
        | "accentBorderCallout1"
        | "accentBorderCallout2"
        | "accentBorderCallout3" => 1.1,
        "flowChartMagneticDrum" => 1.32,
        "parallelogram" | "leftRightUpArrow" => 1.35,
        "flowChartMultidocument" => 1.4,
        "flowChartOnlineStorage" => 1.47,
        "flowChartProcess"
        | "flowChartAlternateProcess"
        | "flowChartDecision"
        | "flowChartInputOutput"
        | "flowChartPredefinedProcess"
        | "flowChartDocument"
        | "flowChartManualOperation"
        | "flowChartMagneticDisk"
        | "flowChartDisplay"
        | "wedgeRectCallout"
        | "wedgeRoundRectCallout"
        | "wedgeEllipseCallout"
        | "cloudCallout" => 1.5,
        "ellipseRibbon" | "ellipseRibbon2" => 1.62,
        "curvedUpArrow" | "curvedDownArrow" => 1.67,
        "flowChartPreparation" => 1.73,
        "rightArrow" | "leftArrow" | "stripedRightArrow" | "notchedRightArrow" => 1.75,
        "homePlate" | "flowChartManualInput" => 1.96,
        "ribbon" | "ribbon2" => 2.04,
        "leftRightArrowCallout" => 2.1,
        "leftRightArrow" => 2.19,
        "flowChartTerminator" => 3.0,
        _ => 1.0,
    }
}

/// Whether preset `prst` is drawn as lines (an arc, brackets, braces): it is inserted without a
/// fill, its lines in the shape's colour.
pub fn line_like(prst: &str) -> bool {
    matches!(prst, "arc" | "bracketPair" | "bracePair" | "leftBracket" | "rightBracket" | "leftBrace" | "rightBrace")
}

/// Whether `prst` is a preset this module draws.
pub fn known(prst: &str) -> bool {
    outline(prst, 100.0, 100.0, &[]).is_some()
}

/// The outline of preset `prst` in a `w` × `h` frame with adjust values `adj` (`a:gd` names and
/// their raw values), or `None` for a preset this module doesn't draw (and for lines and
/// connectors, which are drawn as lines).
pub fn outline(prst: &str, w: f32, h: f32, adj: &[(String, i64)]) -> Option<Outline> {
    if !(w.is_finite() && h.is_finite()) || w < 0.0 || h < 0.0 {
        return None;
    }
    let g = Geo { w, h, ss: w.min(h), hw: w / 2.0, hh: h / 2.0, adj };
    let o = match prst {
        "rect" | "flowChartProcess" => Outline::one(rect(0.0, 0.0, w, h)),
        "roundRect" => Outline::one(g.corners([g.round("adj", 0.16667); 4], [0.0; 4])),
        "flowChartAlternateProcess" => Outline::one(g.corners([g.ss / 6.0; 4], [0.0; 4])),
        "snip1Rect" => Outline::one(g.corners([0.0; 4], [0.0, g.round("adj", 0.16667), 0.0, 0.0])),
        "snip2SameRect" => {
            let (t, b) = (g.round("adj1", 0.16667), g.round("adj2", 0.0));
            Outline::one(g.corners([0.0; 4], [t, t, b, b]))
        }
        "snip2DiagRect" => {
            let (a1, a2) = (g.round("adj1", 0.0), g.round("adj2", 0.16667));
            Outline::one(g.corners([0.0; 4], [a1, a2, a1, a2]))
        }
        "snipRoundRect" => Outline::one(g.corners([g.round("adj1", 0.16667), 0.0, 0.0, 0.0], [0.0, g.round("adj2", 0.16667), 0.0, 0.0])),
        "round1Rect" => Outline::one(g.corners([0.0, g.round("adj", 0.16667), 0.0, 0.0], [0.0; 4])),
        "round2SameRect" => {
            let (t, b) = (g.round("adj1", 0.16667), g.round("adj2", 0.0));
            Outline::one(g.corners([t, t, b, b], [0.0; 4]))
        }
        "round2DiagRect" => {
            let (a1, a2) = (g.round("adj1", 0.16667), g.round("adj2", 0.0));
            Outline::one(g.corners([a1, a2, a1, a2], [0.0; 4]))
        }
        "ellipse" | "flowChartConnector" => Outline::one(ellipse(g.hw, g.hh, g.hw, g.hh)),
        "triangle" => Outline::one(vec![[w * g.frac("adj", 0.5), 0.0], [w, h], [0.0, h]]),
        "flowChartExtract" => Outline::one(vec![[g.hw, 0.0], [w, h], [0.0, h]]),
        "flowChartMerge" => Outline::one(vec![[0.0, 0.0], [w, 0.0], [g.hw, h]]),
        "rtTriangle" => Outline::one(vec![[0.0, 0.0], [w, h], [0.0, h]]),
        "parallelogram" => {
            let x = lim(g.ss * g.frac("adj", 0.25), w);
            Outline::one(vec![[x, 0.0], [w, 0.0], [w - x, h], [0.0, h]])
        }
        "flowChartInputOutput" => Outline::one(vec![[w / 5.0, 0.0], [w, 0.0], [w * 0.8, h], [0.0, h]]),
        "trapezoid" => {
            let x = lim(g.ss * g.frac("adj", 0.25), g.hw);
            Outline::one(vec![[x, 0.0], [w - x, 0.0], [w, h], [0.0, h]])
        }
        "flowChartManualOperation" => Outline::one(vec![[0.0, 0.0], [w, 0.0], [w * 0.8, h], [w * 0.2, h]]),
        "diamond" | "flowChartDecision" => Outline::one(vec![[g.hw, 0.0], [w, g.hh], [g.hw, h], [0.0, g.hh]]),
        "pentagon" => Outline::one(polygon(5, w, h, -90.0)),
        "hexagon" => {
            let x = lim(g.ss * g.frac("adj", 0.25), g.hw);
            Outline::one(vec![[0.0, g.hh], [x, 0.0], [w - x, 0.0], [w, g.hh], [w - x, h], [x, h]])
        }
        "flowChartPreparation" => Outline::one(vec![[0.0, g.hh], [w / 5.0, 0.0], [w * 0.8, 0.0], [w, g.hh], [w * 0.8, h], [w / 5.0, h]]),
        "heptagon" => Outline::one(polygon(7, w, h, -90.0)),
        "octagon" => {
            let c = lim(g.ss * g.frac("adj", 0.29289), g.ss / 2.0);
            Outline::one(vec![[c, 0.0], [w - c, 0.0], [w, c], [w, h - c], [w - c, h], [c, h], [0.0, h - c], [0.0, c]])
        }
        "decagon" => Outline::one(polygon(10, w, h, 0.0)),
        "dodecagon" => Outline::one(polygon(12, w, h, 15.0)),
        "pie" => {
            let (a1, a2) = (g.deg("adj1", 0.0), g.deg("adj2", 270.0));
            let mut pts = vec![[g.hw, g.hh]];
            pts.extend(arc(g.hw, g.hh, g.hw, g.hh, a1, sweep(a1, a2)));
            Outline::one(pts)
        }
        "chord" => {
            let (a1, a2) = (g.deg("adj1", 45.0), g.deg("adj2", 270.0));
            Outline::one(arc(g.hw, g.hh, g.hw, g.hh, a1, sweep(a1, a2)))
        }
        "teardrop" => {
            let k = g.frac("adj", 1.0).clamp(0.0, 2.0);
            let mut pts = arc(g.hw, g.hh, g.hw, g.hh, 0.0, 270.0);
            pts.push([g.hw + g.hw * k, g.hh - g.hh * k]);
            Outline::one(pts)
        }
        "frame" => {
            let t = lim(g.ss * g.frac("adj1", 0.125), g.ss / 2.0);
            Outline::filled(vec![rect(0.0, 0.0, w, h), rev(rect(t, t, w - t, h - t))])
        }
        "halfFrame" => {
            let (t1, t2) = (lim(g.ss * g.frac("adj1", 0.33333), h), lim(g.ss * g.frac("adj2", 0.33333), w));
            Outline::one(vec![[0.0, 0.0], [w, 0.0], [w - t1, t1], [t2, t1], [t2, h - t2], [0.0, h]])
        }
        "corner" => {
            let (ty, tx) = (lim(g.ss * g.frac("adj1", 0.5), h), lim(g.ss * g.frac("adj2", 0.5), w));
            Outline::one(vec![[0.0, 0.0], [tx, 0.0], [tx, h - ty], [w, h - ty], [w, h], [0.0, h]])
        }
        "diagStripe" => {
            let k = g.frac("adj", 0.5).clamp(0.0, 1.0);
            Outline::one(vec![[0.0, h * k], [w * k, 0.0], [w, 0.0], [0.0, h]])
        }
        "plus" => Outline::one(cross(w, h, lim(g.ss * g.frac("adj", 0.25), g.ss / 2.0))),
        "plaque" => {
            let r = lim(g.ss * g.frac("adj", 0.16667), g.ss / 2.0);
            let mut pts = arc(0.0, 0.0, r, r, 90.0, -90.0);
            pts.extend(arc(w, 0.0, r, r, 180.0, -90.0));
            pts.extend(arc(w, h, r, r, 270.0, -90.0));
            pts.extend(arc(0.0, h, r, r, 0.0, -90.0));
            Outline::one(pts)
        }
        "can" | "flowChartMagneticDisk" => {
            let ry = if prst == "can" { lim(g.ss * g.frac("adj", 0.25), h) / 2.0 } else { h / 6.0 };
            let mut pts = arc(g.hw, h - ry, g.hw, ry, 180.0, -180.0);
            pts.extend(arc(g.hw, ry, g.hw, ry, 0.0, -180.0));
            if prst == "can" {
                // the top, lit
                Outline::one(pts).with_faces(vec![(Shade::Lighten, ellipse(g.hw, ry, g.hw, ry))])
            } else {
                Outline::one(pts).with_lines(vec![arc(g.hw, ry, g.hw, ry, 180.0, -180.0)])
            }
        }
        "flowChartMagneticDrum" => {
            let rx = w / 6.0;
            let mut pts = arc(w - rx, g.hh, rx, g.hh, 270.0, 180.0);
            pts.extend(arc(rx, g.hh, rx, g.hh, 90.0, 180.0));
            Outline::one(pts).with_lines(vec![arc(w - rx, g.hh, rx, g.hh, 270.0, -180.0)])
        }
        "cube" => {
            let d = lim(g.ss * g.frac("adj", 0.25), g.ss);
            Outline::one(vec![[0.0, d], [d, 0.0], [w, 0.0], [w, h - d], [w - d, h], [0.0, h]]).with_faces(vec![
                (Shade::LightenLess, vec![[0.0, d], [d, 0.0], [w, 0.0], [w - d, d]]),
                (Shade::DarkenLess, vec![[w - d, d], [w, 0.0], [w, h - d], [w - d, h]]),
            ])
        }
        "bevel" => {
            let i = lim(g.ss * g.frac("adj", 0.125), g.ss / 2.0);
            Outline::one(rect(0.0, 0.0, w, h)).with_faces(vec![
                (Shade::LightenLess, vec![[0.0, 0.0], [w, 0.0], [w - i, i], [i, i]]),
                (Shade::Lighten, vec![[0.0, 0.0], [i, i], [i, h - i], [0.0, h]]),
                (Shade::Darken, vec![[w, 0.0], [w, h], [w - i, h - i], [w - i, i]]),
                (Shade::DarkenLess, vec![[0.0, h], [i, h - i], [w - i, h - i], [w, h]]),
            ])
        }
        "donut" => {
            let t = lim(g.ss * g.frac("adj", 0.25), g.ss / 2.0);
            Outline::filled(vec![ellipse(g.hw, g.hh, g.hw, g.hh), rev(ellipse(g.hw, g.hh, g.hw - t, g.hh - t))])
        }
        "noSmoking" => {
            let t = lim(g.ss * g.frac("adj", 0.1875), g.ss / 2.0);
            let (rx, ry) = (g.hw - t, g.hh - t);
            // the bar across the hole, from upper left to lower right, as thick as the ring
            let (c, s) = (std::f32::consts::FRAC_1_SQRT_2, std::f32::consts::FRAC_1_SQRT_2);
            let (bx, by) = (rx * c, ry * s);
            let (nx, ny) = (t / 2.0 * s, -t / 2.0 * c);
            let bar = vec![
                [g.hw - bx + nx, g.hh - by + ny],
                [g.hw + bx + nx, g.hh + by + ny],
                [g.hw + bx - nx, g.hh + by - ny],
                [g.hw - bx - nx, g.hh - by - ny],
            ];
            Outline::filled(vec![ellipse(g.hw, g.hh, g.hw, g.hh), rev(ellipse(g.hw, g.hh, rx, ry)), bar])
        }
        "blockArc" => {
            let (a1, a2) = (g.deg("adj1", 180.0), g.deg("adj2", 0.0));
            let t = lim(g.ss * g.frac("adj3", 0.25), g.ss / 2.0);
            let s = sweep(a1, a2);
            let mut pts = arc(g.hw, g.hh, g.hw, g.hh, a1, s);
            pts.extend(arc(g.hw, g.hh, g.hw - t, g.hh - t, a1 + s, -s));
            Outline::one(pts)
        }
        "foldedCorner" => {
            let f = lim(g.ss * g.frac("adj", 0.16667), g.ss);
            Outline::one(vec![[0.0, 0.0], [w, 0.0], [w, h - f], [w - f, h], [0.0, h]])
                .with_faces(vec![(Shade::DarkenLess, vec![[w - f, h], [w - f * 0.8, h - f * 0.8], [w, h - f]])])
        }
        "smileyFace" => {
            let (ex, ey, er) = (w * 0.34, h * 0.36, g.ss * 0.06);
            Outline::one(ellipse(g.hw, g.hh, g.hw, g.hh))
                .with_faces(vec![(Shade::DarkenLess, ellipse(ex, ey, er, er)), (Shade::DarkenLess, ellipse(w - ex, ey, er, er))])
                .with_lines(vec![arc(g.hw, h * 0.5, w * 0.27, h * 0.22, 20.0, 140.0)])
        }
        "heart" => Outline::one(heart(w, h)),
        "lightningBolt" => Outline::one(scaled(
            &[
                [0.39, 0.0],
                [0.0, 0.18],
                [0.35, 0.39],
                [0.23, 0.45],
                [0.56, 0.64],
                [0.46, 0.69],
                [1.0, 1.0],
                [0.68, 0.6],
                [0.77, 0.56],
                [0.51, 0.32],
                [0.59, 0.28],
            ],
            w,
            h,
        )),
        "sun" => {
            let r = g.ss * g.frac("adj", 0.25).clamp(0.12, 0.4);
            let mut fill = vec![ellipse(g.hw, g.hh, w * r / g.ss.max(1e-3), h * r / g.ss.max(1e-3))];
            for k in 0..8 {
                let t = (k as f32 * 45.0).to_radians();
                let (c, s) = (t.cos(), t.sin());
                let (tip, base, half) = (0.5, 0.5 * (r / g.ss.max(1e-3)) + 0.07, 0.07);
                fill.push(vec![
                    [g.hw + w * tip * c, g.hh + h * tip * s],
                    [g.hw + w * (base * c - half * s), g.hh + h * (base * s + half * c)],
                    [g.hw + w * (base * c + half * s), g.hh + h * (base * s - half * c)],
                ]);
            }
            Outline::filled(fill)
        }
        "moon" => {
            let k = g.frac("adj", 0.5).clamp(0.0, 0.875);
            let mut pts = arc(w, g.hh, w, g.hh, 270.0, -180.0);
            pts.extend(arc(w, g.hh, w * (1.0 - k), g.hh, 90.0, 180.0));
            Outline::one(pts)
        }
        "cloud" => Outline::one(cloud(g.hw, g.hh, g.hw, g.hh)),
        "arc" => {
            let (a1, a2) = (g.deg("adj1", 270.0), g.deg("adj2", 0.0));
            Outline::stroked(vec![arc(g.hw, g.hh, g.hw, g.hh, a1, sweep(a1, a2))])
        }
        "bracketPair" => {
            let r = lim(g.ss * g.frac("adj", 0.16667), g.ss / 2.0);
            let left = [arc(r, r, r, r, 270.0, -90.0), arc(r, h - r, r, r, 180.0, -90.0)].concat();
            let right = [arc(w - r, r, r, r, 270.0, 90.0), arc(w - r, h - r, r, r, 0.0, 90.0)].concat();
            Outline { fill: vec![g.corners([r; 4], [0.0; 4])], lines: vec![left, right], ..Outline::default() }
        }
        "bracePair" => {
            let r = lim(g.ss * g.frac("adj", 0.08333), (g.ss / 4.0).min(w / 4.0));
            let left = brace(r, r, h, g.hh);
            let right = left.iter().map(|p| [w - p[0], p[1]]).collect();
            let fill = vec![[r * 2.0, 0.0], [w - r * 2.0, 0.0], [w - r, r], [w - r, h - r], [w - r * 2.0, h], [r * 2.0, h], [r, h - r], [r, r]];
            Outline { fill: vec![fill], lines: vec![left, right], ..Outline::default() }
        }
        "leftBracket" | "rightBracket" => {
            let r = lim(g.ss * g.frac("adj", 0.08333), h / 2.0);
            let side = [arc(w, r, w, r, 270.0, -90.0), arc(w, h - r, w, r, 180.0, -90.0)].concat();
            let side = if prst == "rightBracket" { side.iter().map(|p| [w - p[0], p[1]]).collect() } else { side };
            Outline::stroked(vec![side])
        }
        "leftBrace" | "rightBrace" => {
            let r = lim(g.ss * g.frac("adj1", 0.08333), h / 4.0);
            let mid = h * g.frac("adj2", 0.5).clamp(0.0, 1.0);
            let b = brace(g.hw, r, h, mid.clamp(r * 2.0, (h - r * 2.0).max(r * 2.0)));
            let b = if prst == "rightBrace" { b.iter().map(|p| [w - p[0], p[1]]).collect() } else { b };
            Outline::stroked(vec![b])
        }
        "rightArrow" | "leftArrow" | "upArrow" | "downArrow" => {
            let vertical = prst == "upArrow" || prst == "downArrow";
            let (len, thick) = if vertical { (h, w) } else { (w, h) };
            let pts = arrow(len, thick, g.frac("adj1", 0.5), g.ss * g.frac("adj2", 0.5), false);
            Outline::one(
                pts.into_iter()
                    .map(|[u, v]| match prst {
                        "leftArrow" => [w - u, v],
                        "upArrow" => [v, h - u],
                        "downArrow" => [v, u],
                        _ => [u, v],
                    })
                    .collect(),
            )
        }
        "leftRightArrow" | "upDownArrow" => {
            let vertical = prst == "upDownArrow";
            let (len, thick) = if vertical { (h, w) } else { (w, h) };
            let pts = arrow(len, thick, g.frac("adj1", 0.5), g.ss * g.frac("adj2", 0.5), true);
            Outline::one(pts.into_iter().map(|[u, v]| if vertical { [v, u] } else { [u, v] }).collect())
        }
        "quadArrow" => Outline::one(quad_arrow(&g, true, true, true, true)),
        "leftRightUpArrow" => Outline::one(quad_arrow(&g, true, true, true, false)),
        "bentArrow" => {
            let t = g.ss * g.frac("adj1", 0.25);
            let head = g.ss * g.frac("adj2", 0.25);
            let hl = g.ss * g.frac("adj3", 0.25);
            let r = g.ss * g.frac("adj4", 0.4375);
            let top = head - t / 2.0;
            let mut pts = vec![[0.0, h], [0.0, top + r]];
            pts.extend(arc(r, top + r, r, r, 180.0, 90.0));
            pts.extend([[w - hl, top], [w - hl, 0.0], [w, head], [w - hl, head * 2.0], [w - hl, top + t]]);
            let ri = (r - t).max(0.0);
            pts.extend(arc(t + ri, top + t + ri, ri, ri, 270.0, -90.0));
            pts.push([t, h]);
            Outline::one(pts)
        }
        "uturnArrow" => {
            // up the left leg, over the top and down the right leg to a head pointing down
            let aw = lim(g.ss * g.frac("adj2", 0.25), w / 2.0);
            let t = lim(g.ss * g.frac("adj1", 0.25), aw * 2.0);
            let ah = lim(g.ss * g.frac("adj3", 0.25), h);
            let xc = w - aw;
            let xr = xc + t / 2.0;
            let tip = (h * g.frac("adj5", 0.75)).clamp(ah, h);
            let r = lim(g.ss * g.frac("adj4", 0.4375), (xr / 2.0).min(tip - ah));
            let ri = (r - t).max(0.0);
            let mut pts = vec![[0.0, h], [0.0, r]];
            pts.extend(arc(r, r, r, r, 180.0, 90.0));
            pts.extend(arc(xr - r, r, r, r, 270.0, 90.0));
            pts.extend([[xr, tip - ah], [w, tip - ah], [xc, tip], [w - aw * 2.0, tip - ah], [xr - t, tip - ah]]);
            pts.extend(arc(xr - r, r, ri, ri, 0.0, -90.0));
            pts.extend(arc(r, r, ri, ri, 270.0, -90.0));
            pts.push([t, h]);
            Outline::one(pts)
        }
        "leftUpArrow" => Outline::one(corner_arrow(&g)),
        "bentUpArrow" => {
            let t = g.ss * g.frac("adj1", 0.25);
            let head = g.ss * g.frac("adj2", 0.25);
            let hl = g.ss * g.frac("adj3", 0.25);
            let cx = w - head;
            Outline::one(vec![
                [0.0, h - t],
                [cx - t / 2.0, h - t],
                [cx - t / 2.0, hl],
                [cx - head, hl],
                [cx, 0.0],
                [w, hl],
                [cx + t / 2.0, hl],
                [cx + t / 2.0, h],
                [0.0, h],
            ])
        }
        "curvedRightArrow" | "curvedLeftArrow" | "curvedUpArrow" | "curvedDownArrow" => Outline::one(curved_arrow(prst, w, h)),
        "stripedRightArrow" => {
            let t = h * g.frac("adj1", 0.5) / 2.0;
            let hl = w - lim(g.ss * g.frac("adj2", 0.5), w);
            let s = w / 32.0;
            Outline::filled(vec![
                rect(0.0, g.hh - t, s, g.hh + t),
                rect(s * 2.0, g.hh - t, s * 4.0, g.hh + t),
                vec![[s * 5.0, g.hh - t], [hl, g.hh - t], [hl, 0.0], [w, g.hh], [hl, h], [hl, g.hh + t], [s * 5.0, g.hh + t]],
            ])
        }
        "notchedRightArrow" => {
            let t = h * g.frac("adj1", 0.5) / 2.0;
            let hl = w - lim(g.ss * g.frac("adj2", 0.5), w);
            let notch = t * (w - hl) / g.hh.max(1e-3);
            Outline::one(vec![[0.0, g.hh - t], [hl, g.hh - t], [hl, 0.0], [w, g.hh], [hl, h], [hl, g.hh + t], [0.0, g.hh + t], [notch.min(hl), g.hh]])
        }
        "homePlate" => {
            let x = w - lim(g.ss * g.frac("adj", 0.5), w);
            Outline::one(vec![[0.0, 0.0], [x, 0.0], [w, g.hh], [x, h], [0.0, h]])
        }
        "chevron" => {
            let x = lim(g.ss * g.frac("adj", 0.5), w);
            Outline::one(vec![[0.0, 0.0], [w - x, 0.0], [w, g.hh], [w - x, h], [0.0, h], [x, g.hh]])
        }
        "rightArrowCallout" | "leftArrowCallout" | "upArrowCallout" | "downArrowCallout" => Outline::one(arrow_callout(prst, w, h)),
        "leftRightArrowCallout" => {
            let (bw, sh, hh2, hl) = (w * 0.3, h * 0.125, h * 0.25, w * 0.125);
            let (x0, x1) = (g.hw - bw, g.hw + bw);
            Outline::one(vec![
                [x0, 0.0],
                [x1, 0.0],
                [x1, g.hh - sh],
                [w - hl, g.hh - sh],
                [w - hl, g.hh - hh2],
                [w, g.hh],
                [w - hl, g.hh + hh2],
                [w - hl, g.hh + sh],
                [x1, g.hh + sh],
                [x1, h],
                [x0, h],
                [x0, g.hh + sh],
                [hl, g.hh + sh],
                [hl, g.hh + hh2],
                [0.0, g.hh],
                [hl, g.hh - hh2],
                [hl, g.hh - sh],
                [x0, g.hh - sh],
            ])
        }
        "quadArrowCallout" => Outline::one(quad_callout(w, h)),
        "circularArrow" => Outline::one(circular_arrow(w, h)),
        // the equation shapes keep a margin: their marks span about three quarters of the frame
        "mathPlus" => {
            let (mx, my) = (w * 0.13255, h * 0.13255);
            let t = g.ss * g.frac("adj1", 0.2352).clamp(0.0, 0.7349);
            Outline::one(plus(mx, my, w - mx, h - my, t))
        }
        "mathMinus" => {
            let t = h * g.frac("adj1", 0.2352).clamp(0.0, 1.0) / 2.0;
            Outline::one(rect(w * 0.13255, g.hh - t, w * 0.86745, g.hh + t))
        }
        "mathMultiply" => {
            let t = g.ss * g.frac("adj1", 0.2352).clamp(0.0, 0.5) / 2.0;
            let a = g.ss * 0.37;
            // round the centre: each arm's two end corners, then the corner between it and the next
            let at = |deg: f32, r: f32| -> Pt { [g.hw + r * deg.to_radians().cos(), g.hh + r * deg.to_radians().sin()] };
            let mut pts = Vec::new();
            for k in 0..4 {
                let th = 45.0 + 90.0 * k as f32;
                let (d, n) = (at(th, a), [-(th.to_radians().sin()) * t, th.to_radians().cos() * t]);
                pts.push([d[0] - n[0], d[1] - n[1]]);
                pts.push([d[0] + n[0], d[1] + n[1]]);
                pts.push(at(th + 45.0, t * std::f32::consts::SQRT_2));
            }
            Outline::one(pts)
        }
        "mathDivide" => {
            let (t, gap, r) = (h * 0.095, h * 0.06, g.ss * 0.11);
            let (top, bot) = (g.hh - t - gap - r, g.hh + t + gap + r);
            Outline::filled(vec![rect(w * 0.13255, g.hh - t, w * 0.86745, g.hh + t), ellipse(g.hw, top, r, r), ellipse(g.hw, bot, r, r)])
        }
        "mathEqual" | "mathNotEqual" => {
            let t = h * g.frac("adj1", 0.2352).clamp(0.0, 0.5);
            let gap = h * g.frac("adj2", 0.1176).clamp(0.0, 0.5) / 2.0;
            let (x0, x1) = (w * 0.13255, w * 0.86745);
            let mut fill = vec![rect(x0, g.hh - gap - t, x1, g.hh - gap), rect(x0, g.hh + gap, x1, g.hh + gap + t)];
            if prst == "mathNotEqual" {
                // the stroke through both bars, top to bottom, leaning right
                let s = t * 0.5;
                fill.push(vec![[w * 0.62 - s, 0.0], [w * 0.62 + s, 0.0], [w * 0.38 + s, h], [w * 0.38 - s, h]]);
            }
            Outline::filled(fill)
        }
        "flowChartPredefinedProcess" => {
            let x = w / 8.0;
            Outline::one(rect(0.0, 0.0, w, h)).with_lines(vec![vec![[x, 0.0], [x, h]], vec![[w - x, 0.0], [w - x, h]]])
        }
        "flowChartInternalStorage" => {
            let (x, y) = (w / 8.0, h / 8.0);
            Outline::one(rect(0.0, 0.0, w, h)).with_lines(vec![vec![[x, 0.0], [x, h]], vec![[0.0, y], [w, y]]])
        }
        "flowChartDocument" => Outline::one(document(0.0, 0.0, w, h)),
        "flowChartMultidocument" => {
            // two sheets showing behind the front one, up and to the right
            let (dx, dy) = (w * 0.1, h * 0.1);
            let back2 = vec![[dx * 2.0, dy], [dx * 2.0, 0.0], [w, 0.0], [w, h * 0.68], [w - dx, h * 0.68], [w - dx, dy]];
            let back1 = vec![[dx, dy * 2.0], [dx, dy], [w - dx, dy], [w - dx, h * 0.76], [w - dx * 2.0, h * 0.76], [w - dx * 2.0, dy * 2.0]];
            Outline::filled(vec![back2, back1, document(0.0, dy * 2.0, w - dx * 2.0, h)])
        }
        "flowChartTerminator" => {
            let r = (w / 6.0).min(g.hw);
            let mut pts = arc(w - r, g.hh, r, g.hh, 270.0, 180.0);
            pts.extend(arc(r, g.hh, r, g.hh, 90.0, 180.0));
            Outline::one(pts)
        }
        "flowChartManualInput" => Outline::one(vec![[0.0, h / 5.0], [w, 0.0], [w, h], [0.0, h]]),
        "flowChartOffpageConnector" => Outline::one(vec![[0.0, 0.0], [w, 0.0], [w, h * 0.8], [g.hw, h], [0.0, h * 0.8]]),
        "flowChartPunchedCard" => Outline::one(vec![[w / 5.0, 0.0], [w, 0.0], [w, h], [0.0, h], [0.0, h / 5.0]]),
        "flowChartPunchedTape" => {
            let mut pts = wave_line(0.0, w, h * 0.1, h * 0.1, 1.0);
            pts.extend(wave_line(w, 0.0, h * 0.9, h * 0.1, 1.0));
            Outline::one(pts)
        }
        "flowChartSummingJunction" | "flowChartOr" => {
            let lines = if prst == "flowChartOr" {
                vec![vec![[g.hw, 0.0], [g.hw, h]], vec![[0.0, g.hh], [w, g.hh]]]
            } else {
                let (dx, dy) = (g.hw * std::f32::consts::FRAC_1_SQRT_2, g.hh * std::f32::consts::FRAC_1_SQRT_2);
                vec![vec![[g.hw - dx, g.hh - dy], [g.hw + dx, g.hh + dy]], vec![[g.hw + dx, g.hh - dy], [g.hw - dx, g.hh + dy]]]
            };
            Outline::one(ellipse(g.hw, g.hh, g.hw, g.hh)).with_lines(lines)
        }
        "flowChartCollate" => Outline::filled(vec![vec![[0.0, 0.0], [w, 0.0], [g.hw, g.hh]], vec![[g.hw, g.hh], [w, h], [0.0, h]]]),
        "flowChartSort" => Outline::one(vec![[g.hw, 0.0], [w, g.hh], [g.hw, h], [0.0, g.hh]]).with_lines(vec![vec![[0.0, g.hh], [w, g.hh]]]),
        "flowChartOnlineStorage" => {
            let rx = w / 6.0;
            let mut pts = arc(rx, g.hh, rx, g.hh, 270.0, -180.0);
            pts.extend(arc(w, g.hh, rx, g.hh, 90.0, 180.0));
            Outline::one(pts)
        }
        "flowChartDelay" => {
            let mut pts = vec![[0.0, 0.0]];
            pts.extend(arc(g.hw, g.hh, g.hw, g.hh, 270.0, 180.0));
            pts.push([0.0, h]);
            Outline::one(pts)
        }
        "flowChartMagneticTape" => {
            let mut pts = arc(g.hw, g.hh, g.hw, g.hh, 90.0, 315.0);
            pts.extend([[w, g.hh + g.hh * std::f32::consts::FRAC_1_SQRT_2], [w, h]]);
            Outline::one(pts)
        }
        "flowChartDisplay" => {
            let x = w / 6.0;
            let mut pts = vec![[0.0, g.hh], [x, 0.0]];
            pts.extend(arc(w - x, g.hh, x, g.hh, 270.0, 180.0));
            pts.push([x, h]);
            Outline::one(pts)
        }
        "irregularSeal1" => Outline::one(scaled(&SEAL1, w, h)),
        "irregularSeal2" => Outline::one(scaled(&SEAL2, w, h)),
        "star4" => Outline::one(star(4, w, h, g.frac("adj", 0.125) * 2.0)),
        "star5" => Outline::one(star(5, w, h, g.frac("adj", 0.19098) * 2.0)),
        "star6" => Outline::one(star(6, w, h, g.frac("adj", 0.28868) * 2.0)),
        "star7" => Outline::one(star(7, w, h, g.frac("adj", 0.34601) * 2.0)),
        "star8" => Outline::one(star(8, w, h, g.frac("adj", 0.375) * 2.0)),
        "star10" => Outline::one(star(10, w, h, g.frac("adj", 0.42533) * 2.0)),
        "star12" | "star16" | "star24" | "star32" => {
            let n = prst.trim_start_matches("star").parse().unwrap_or(12);
            Outline::one(star(n, w, h, g.frac("adj", 0.375) * 2.0))
        }
        "ribbon" | "ribbon2" | "ellipseRibbon" | "ellipseRibbon2" => {
            let curved = prst.starts_with("ellipse");
            ribbon(prst, w, h, g.frac("adj1", if curved { 0.25 } else { 0.16667 }), g.frac("adj2", 0.5).clamp(0.25, 0.75))
        }
        "verticalScroll" | "horizontalScroll" => scroll(prst == "verticalScroll", w, h, g.ss * g.frac("adj", 0.125)),
        "wave" | "doubleWave" => {
            let (amp, periods) = if prst == "wave" { (h * g.frac("adj1", 0.125), 1.0) } else { (h * g.frac("adj1", 0.0625), 2.0) };
            let mut pts = wave_line(0.0, w, amp, amp, periods);
            pts.extend(wave_line(w, 0.0, h - amp, amp, periods));
            Outline::one(pts)
        }
        "wedgeRectCallout" | "wedgeRoundRectCallout" => {
            let tip = [g.hw + w * g.frac("adj1", -0.20833), g.hh + h * g.frac("adj2", 0.625)];
            let r = if prst == "wedgeRoundRectCallout" { g.ss * 0.16667 } else { 0.0 };
            Outline::one(wedge_box(w, h, r, tip))
        }
        "wedgeEllipseCallout" => {
            let tip = [g.hw + w * g.frac("adj1", -0.20833), g.hh + h * g.frac("adj2", 0.625)];
            let at = (tip[1] - g.hh).atan2(tip[0] - g.hw).to_degrees();
            let mut pts = arc(g.hw, g.hh, g.hw, g.hh, at + 12.0, 336.0);
            pts.push(tip);
            Outline::one(pts)
        }
        "cloudCallout" => {
            let tip = [g.hw + w * g.frac("adj1", -0.20833), g.hh + h * g.frac("adj2", 0.625)];
            let mut fill = vec![cloud(g.hw, g.hh, g.hw, g.hh)];
            for (k, r) in [(0.45, 0.07), (0.7, 0.05), (0.92, 0.035)] {
                let c = [g.hw + (tip[0] - g.hw) * k, g.hh + (tip[1] - g.hh) * k];
                fill.push(ellipse(c[0], c[1], g.ss * r, g.ss * r));
            }
            Outline::filled(fill)
        }
        _ if line_callout(prst).is_some() => {
            let (bends, accent, border) = line_callout(prst)?;
            let defaults: [(f32, f32); 4] = match bends {
                1 => [(0.1875, -0.08333), (1.125, -0.38333), (0.0, 0.0), (0.0, 0.0)],
                2 => [(0.1875, -0.08333), (0.1875, -0.16667), (1.125, -0.46667), (0.0, 0.0)],
                _ => [(0.1875, -0.08333), (0.1875, -0.16667), (1.0, -0.16667), (1.12963, -0.08333)],
            };
            let mut leader = Vec::new();
            for (k, (dy, dx)) in defaults.iter().take(bends + 1).enumerate() {
                let y = h * g.frac(&format!("adj{}", 2 * k + 1), *dy);
                let x = w * g.frac(&format!("adj{}", 2 * k + 2), *dx);
                leader.push([x, y]);
            }
            let mut lines = vec![leader.clone()];
            if accent && let Some(first) = leader.first() {
                lines.push(vec![[first[0], 0.0], [first[0], h]]);
            }
            Outline { fill: vec![rect(0.0, 0.0, w, h)], lines, stroke_fill: border, ..Outline::default() }
        }
        _ => return None,
    };
    Some(o)
}

/// A line callout's bends (1–3), accent bar and border, from its preset name.
fn line_callout(prst: &str) -> Option<(usize, bool, bool)> {
    let n = prst.chars().last()?.to_digit(10)? as usize;
    if !(1..=3).contains(&n) {
        return None;
    }
    let base = prst.trim_end_matches(char::is_numeric);
    let (accent, border) = match base {
        "callout" => (false, false),
        "accentCallout" => (true, false),
        "borderCallout" => (false, true),
        "accentBorderCallout" => (true, true),
        _ => return None,
    };
    Some((n, accent, border))
}

/// The frame and the adjust values a preset reads.
struct Geo<'a> {
    w: f32,
    h: f32,
    ss: f32,
    hw: f32,
    hh: f32,
    adj: &'a [(String, i64)],
}

impl Geo<'_> {
    /// Adjust value `name` as a fraction (raw / 100000), `def` when the shape has none.
    fn frac(&self, name: &str, def: f32) -> f32 {
        self.adj
            .iter()
            .find(|(n, _)| n == name)
            .map(|(_, v)| (*v).clamp(-10_000_000, 10_000_000) as f32 / 100_000.0)
            .filter(|v| v.is_finite())
            .unwrap_or(def)
    }
    /// Adjust value `name` as degrees (raw / 60000), `def` when the shape has none.
    fn deg(&self, name: &str, def: f32) -> f32 {
        self.adj.iter().find(|(n, _)| n == name).map(|(_, v)| (*v).clamp(-21_600_000, 21_600_000) as f32 / 60_000.0).unwrap_or(def)
    }
    /// A corner size: adjust value `name` of the short side, at most half of it.
    fn round(&self, name: &str, def: f32) -> f32 {
        lim(self.ss * self.frac(name, def), self.ss / 2.0)
    }
    /// The frame with each corner (top left, top right, bottom right, bottom left) rounded by its
    /// radius in `round` or cut by its size in `snip`.
    fn corners(&self, round: [f32; 4], snip: [f32; 4]) -> Vec<Pt> {
        let (w, h) = (self.w, self.h);
        let mut pts = Vec::new();
        let centres = [[0.0, 0.0], [w, 0.0], [w, h], [0.0, h]];
        let start = [180.0f32, 270.0, 0.0, 90.0];
        for k in 0..4 {
            let (r, s) = (round[k], snip[k]);
            let [cx, cy] = centres[k];
            let (sx, sy) = (if cx > 0.0 { -1.0 } else { 1.0 }, if cy > 0.0 { -1.0 } else { 1.0 });
            if r > 0.0 {
                pts.extend(arc(cx + sx * r, cy + sy * r, r, r, start[k], 90.0));
            } else if s > 0.0 {
                let (a, b) = match k {
                    0 => ([0.0, s], [s, 0.0]),
                    1 => ([w - s, 0.0], [w, s]),
                    2 => ([w, h - s], [w - s, h]),
                    _ => ([s, h], [0.0, h - s]),
                };
                pts.push(a);
                pts.push(b);
            } else {
                pts.push([cx, cy]);
            }
        }
        pts
    }
}

/// `v` within `[0, hi]`, whatever `hi` is.
fn lim(v: f32, hi: f32) -> f32 {
    v.max(0.0).min(hi.max(0.0))
}

fn rect(x0: f32, y0: f32, x1: f32, y1: f32) -> Vec<Pt> {
    vec![[x0, y0], [x1, y0], [x1, y1], [x0, y1]]
}

fn rev(mut v: Vec<Pt>) -> Vec<Pt> {
    v.reverse();
    v
}

/// A closed outline as a line (its first point again at the end).
fn closed(mut v: Vec<Pt>) -> Vec<Pt> {
    if let Some(first) = v.first().copied() {
        v.push(first);
    }
    v
}

/// The sweep from `a1` to `a2` clockwise, in degrees (0–360).
fn sweep(a1: f32, a2: f32) -> f32 {
    let s = (a2 - a1).rem_euclid(360.0);
    if s == 0.0 { 360.0 } else { s }
}

/// Points along an ellipse arc (centre, radii), from `start` turning `sweep` degrees (clockwise
/// on the page when positive; 0° points right).
fn arc(cx: f32, cy: f32, rx: f32, ry: f32, start: f32, sweep: f32) -> Vec<Pt> {
    let n = ((sweep.abs() / 6.0).ceil() as usize).clamp(2, 120);
    (0..=n)
        .map(|k| {
            let t = (start + sweep * k as f32 / n as f32).to_radians();
            [cx + rx * t.cos(), cy + ry * t.sin()]
        })
        .collect()
}

fn ellipse(cx: f32, cy: f32, rx: f32, ry: f32) -> Vec<Pt> {
    let mut v = arc(cx, cy, rx, ry, 0.0, 360.0);
    v.pop();
    v
}

/// A regular `n`-gon stretched over the frame, its first corner at `rot` degrees.
fn polygon(n: usize, w: f32, h: f32, rot: f32) -> Vec<Pt> {
    let pts: Vec<Pt> = (0..n)
        .map(|k| {
            let t = (rot + 360.0 * k as f32 / n as f32).to_radians();
            [t.cos(), t.sin()]
        })
        .collect();
    fit(pts, w, h)
}

/// A star with `n` points, its inner corners at `inner` of the outer radius.
fn star(n: usize, w: f32, h: f32, inner: f32) -> Vec<Pt> {
    let inner = if inner.is_finite() { inner.clamp(0.05, 1.0) } else { 0.5 };
    let pts: Vec<Pt> = (0..n * 2)
        .map(|k| {
            let t = (-90.0 + 180.0 * k as f32 / n as f32).to_radians();
            let r = if k % 2 == 0 { 1.0 } else { inner };
            [r * t.cos(), r * t.sin()]
        })
        .collect();
    fit(pts, w, h)
}

/// Points scaled so their bounds fill the frame.
fn fit(pts: Vec<Pt>, w: f32, h: f32) -> Vec<Pt> {
    let (mut x0, mut y0, mut x1, mut y1) = (f32::MAX, f32::MAX, f32::MIN, f32::MIN);
    for [x, y] in &pts {
        (x0, y0, x1, y1) = (x0.min(*x), y0.min(*y), x1.max(*x), y1.max(*y));
    }
    let (sx, sy) = ((x1 - x0).max(1e-6), (y1 - y0).max(1e-6));
    pts.into_iter().map(|[x, y]| [(x - x0) / sx * w, (y - y0) / sy * h]).collect()
}

/// Unit-square points stretched over the frame.
fn scaled(pts: &[Pt], w: f32, h: f32) -> Vec<Pt> {
    pts.iter().map(|[x, y]| [x * w, y * h]).collect()
}

/// A plus sign filling the frame, its arms `t` thick.
fn cross(w: f32, h: f32, t: f32) -> Vec<Pt> {
    plus(0.0, 0.0, w, h, t)
}

/// A plus sign filling the box from (`l`, `t`) to (`r`, `b`), its arms `th` thick.
fn plus(l: f32, t: f32, r: f32, b: f32, th: f32) -> Vec<Pt> {
    let (cx, cy) = ((l + r) / 2.0, (t + b) / 2.0);
    let (x0, x1) = ((cx - th / 2.0).max(l), (cx + th / 2.0).min(r));
    let (y0, y1) = ((cy - th / 2.0).max(t), (cy + th / 2.0).min(b));
    vec![[x0, t], [x1, t], [x1, y0], [r, y0], [r, y1], [x1, y1], [x1, b], [x0, b], [x0, y1], [l, y1], [l, y0], [x0, y0]]
}

/// An arrow along `len` (pointing towards it), `thick` across: the shaft `shaft` of the thickness,
/// the head `head` long; with a head at each end when `both`.
fn arrow(len: f32, thick: f32, shaft: f32, head: f32, both: bool) -> Vec<Pt> {
    let half = thick * shaft.clamp(0.0, 1.0) / 2.0;
    let mid = thick / 2.0;
    let head = lim(head, if both { len / 2.0 } else { len });
    let tail = if both { head } else { 0.0 };
    let mut pts = vec![
        [tail, mid - half],
        [len - head, mid - half],
        [len - head, 0.0],
        [len, mid],
        [len - head, thick],
        [len - head, mid + half],
        [tail, mid + half],
    ];
    if both {
        pts.extend([[tail, thick], [0.0, mid], [tail, 0.0]]);
    }
    pts
}

/// Arrows from the centre to the sides that are asked for (left, right, up, down).
fn quad_arrow(g: &Geo, left: bool, right: bool, up: bool, down: bool) -> Vec<Pt> {
    let t = g.ss * g.frac("adj1", 0.225) / 2.0;
    let hw2 = g.ss * g.frac("adj2", 0.225);
    let hl = g.ss * g.frac("adj3", 0.225);
    let (cx, cy) = (g.hw, if down { g.hh } else { g.h - hw2 });
    let mut pts = Vec::new();
    // clockwise from the up arm
    if up {
        pts.extend([[cx - t, hl], [cx - hw2, hl], [cx, 0.0], [cx + hw2, hl], [cx + t, hl]]);
    } else {
        pts.extend([[cx - t, cy - t], [cx + t, cy - t]]);
    }
    pts.push([cx + t, cy - t]);
    if right {
        pts.extend([[g.w - hl, cy - t], [g.w - hl, cy - hw2], [g.w, cy], [g.w - hl, cy + hw2], [g.w - hl, cy + t]]);
    }
    pts.push([cx + t, cy + t]);
    if down {
        pts.extend([[cx + t, g.h - hl], [cx + hw2, g.h - hl], [cx, g.h], [cx - hw2, g.h - hl], [cx - t, g.h - hl]]);
    }
    pts.push([cx - t, cy + t]);
    if left {
        pts.extend([[hl, cy + t], [hl, cy + hw2], [0.0, cy], [hl, cy - hw2], [hl, cy - t]]);
    }
    pts.push([cx - t, cy - t]);
    pts.dedup();
    pts
}

/// Left-Up Arrow: arrows to the left and up from the lower right corner.
fn corner_arrow(g: &Geo) -> Vec<Pt> {
    let t = g.ss * g.frac("adj1", 0.25) / 2.0;
    let hw2 = g.ss * g.frac("adj2", 0.25);
    let hl = g.ss * g.frac("adj3", 0.25);
    let (cx, cy) = (g.w - hw2, g.h - hw2);
    vec![
        [cx - t, hl],
        [cx - hw2, hl],
        [cx, 0.0],
        [g.w, hl],
        [cx + t, hl],
        [cx + t, cy + t],
        [hl, cy + t],
        [hl, g.h],
        [0.0, cy],
        [hl, cy - hw2],
        [hl, cy - t],
        [cx - t, cy - t],
    ]
}

/// A curved arrow (Curved Right/Left/Up/Down): a band bending round towards its head.
fn curved_arrow(prst: &str, w: f32, h: f32) -> Vec<Pt> {
    // drawn as Curved Right (the band down the left, the head at the bottom pointing right) in a
    // frame `bw` across and `bh` down, then turned
    let vertical = prst == "curvedUpArrow" || prst == "curvedDownArrow";
    let (bw, bh) = if vertical { (h, w) } else { (w, h) };
    let ss = bw.min(bh).max(1e-3);
    let t = ss * 0.25;
    let aw = ss * 0.5;
    let ah = (ss * 0.25).min(bw * 0.8);
    // the band's centre line: part of an ellipse centred on the right edge, from the top round the
    // left to where the head's base stands
    let rx = (bw - t / 2.0).max(1e-3);
    let end = (180.0 - (ah / rx).clamp(0.0, 1.0).acos().to_degrees()).clamp(90.0, 180.0);
    let ry = ((bh - aw / 2.0 - t / 2.0) / (1.0 + end.to_radians().sin())).max(1e-3);
    let cy = t / 2.0 + ry;
    let at = |a: f32| -> (Pt, Pt) {
        let r = a.to_radians();
        let p = [bw + rx * r.cos(), cy + ry * r.sin()];
        let nrm = [r.cos() / rx, r.sin() / ry];
        let len = (nrm[0] * nrm[0] + nrm[1] * nrm[1]).sqrt().max(1e-6);
        (p, [nrm[0] / len, nrm[1] / len])
    };
    let n = 32;
    let angles: Vec<f32> = (0..=n).map(|k| 270.0 - (270.0 - end) * k as f32 / n as f32).collect();
    let side = |a: f32, k: f32| -> Pt {
        let (p, nrm) = at(a);
        [p[0] + nrm[0] * k, p[1] + nrm[1] * k]
    };
    let mut pts: Vec<Pt> = angles.iter().map(|a| side(*a, t / 2.0)).collect();
    // the head, along the way the band runs at its end
    let (e, nrm) = at(end);
    let dir = {
        let r = end.to_radians();
        let d = [rx * r.sin(), -ry * r.cos()];
        let len = (d[0] * d[0] + d[1] * d[1]).sqrt().max(1e-6);
        [d[0] / len, d[1] / len]
    };
    pts.push([e[0] + nrm[0] * aw / 2.0, e[1] + nrm[1] * aw / 2.0]);
    pts.push([e[0] + dir[0] * ah, e[1] + dir[1] * ah]);
    pts.push([e[0] - nrm[0] * aw / 2.0, e[1] - nrm[1] * aw / 2.0]);
    pts.extend(angles.iter().rev().map(|a| side(*a, -t / 2.0)));
    fit(pts, bw, bh)
        .into_iter()
        .map(|[x, y]| match prst {
            "curvedLeftArrow" => [w - x, y],
            "curvedUpArrow" => [y, h - x],
            "curvedDownArrow" => [y, x],
            _ => [x, y],
        })
        .collect()
}

/// An arrow callout (Right/Left/Up/Down): a box with an arrow out of one side.
fn arrow_callout(prst: &str, w: f32, h: f32) -> Vec<Pt> {
    let vertical = prst == "upArrowCallout" || prst == "downArrowCallout";
    let (len, thick) = if vertical { (h, w) } else { (w, h) };
    let bx = len * 0.6;
    let hl = len * 0.2;
    let (sh, hh2) = (thick * 0.125, thick * 0.25);
    let mid = thick / 2.0;
    let pts = vec![
        [0.0, 0.0],
        [bx, 0.0],
        [bx, mid - sh],
        [len - hl, mid - sh],
        [len - hl, mid - hh2],
        [len, mid],
        [len - hl, mid + hh2],
        [len - hl, mid + sh],
        [bx, mid + sh],
        [bx, thick],
        [0.0, thick],
    ];
    pts.into_iter()
        .map(|[u, v]| match prst {
            "leftArrowCallout" => [w - u, v],
            "upArrowCallout" => [v, h - u],
            "downArrowCallout" => [v, u],
            _ => [u, v],
        })
        .collect()
}

/// A box in the middle with arrows out of all four sides.
fn quad_callout(w: f32, h: f32) -> Vec<Pt> {
    let (hw, hh) = (w / 2.0, h / 2.0);
    let (bx, by) = (w * 0.25, h * 0.25);
    let (sx, sy, ax, ay, lx, ly) = (w * 0.06, h * 0.06, w * 0.12, h * 0.12, w * 0.12, h * 0.12);
    vec![
        [hw - sx, by],
        [hw - sx, ly],
        [hw - ax, ly],
        [hw, 0.0],
        [hw + ax, ly],
        [hw + sx, ly],
        [hw + sx, by],
        [w - bx, by],
        [w - bx, hh - sy],
        [w - lx, hh - sy],
        [w - lx, hh - ay],
        [w, hh],
        [w - lx, hh + ay],
        [w - lx, hh + sy],
        [w - bx, hh + sy],
        [w - bx, h - by],
        [hw + sx, h - by],
        [hw + sx, h - ly],
        [hw + ax, h - ly],
        [hw, h],
        [hw - ax, h - ly],
        [hw - sx, h - ly],
        [hw - sx, h - by],
        [bx, h - by],
        [bx, hh + sy],
        [lx, hh + sy],
        [lx, hh + ay],
        [0.0, hh],
        [lx, hh - ay],
        [lx, hh - sy],
        [bx, hh - sy],
        [bx, by],
    ]
}

/// Circular Arrow: a band round the top of the frame from the left, clockwise, ending in a head
/// pointing down on the right.
fn circular_arrow(w: f32, h: f32) -> Vec<Pt> {
    let (cx, cy) = (w / 2.0, h / 2.0);
    let t = w.min(h) * 0.125;
    let (rx, ry) = ((cx - t).max(0.0), (cy - t).max(0.0));
    let at = |deg: f32, k: f32| -> Pt {
        let r = deg.to_radians();
        [cx + (rx + k) * r.cos(), cy + (ry + k) * r.sin()]
    };
    let (start, end) = (180.0f32, 320.0f32);
    let n = 28;
    let mut pts: Vec<Pt> = (0..=n).map(|k| at(start + (end - start) * k as f32 / n as f32, t / 2.0)).collect();
    pts.extend([at(end, t), at(end + 22.0, 0.0), at(end, -t)]);
    pts.extend((0..=n).rev().map(|k| at(start + (end - start) * k as f32 / n as f32, -t / 2.0)));
    pts
}

/// A document's outline: straight top and sides, a wavy bottom edge.
fn document(x0: f32, y0: f32, x1: f32, y1: f32) -> Vec<Pt> {
    let (w, h) = (x1 - x0, y1 - y0);
    let base = y0 + h * 0.83;
    let mut pts = vec![[x0, y0], [x1, y0], [x1, base]];
    let n = 24;
    for k in 0..=n {
        let t = k as f32 / n as f32;
        let x = x1 - w * t;
        let y = base + h * 0.1 * (t * std::f32::consts::TAU).sin();
        pts.push([x, y]);
    }
    pts
}

/// A wave from `x0` to `x1` around height `y` with amplitude `amp`, `periods` waves long.
fn wave_line(x0: f32, x1: f32, y: f32, amp: f32, periods: f32) -> Vec<Pt> {
    let n = (24.0 * periods) as usize;
    (0..=n)
        .map(|k| {
            let t = k as f32 / n as f32;
            [x0 + (x1 - x0) * t, y + amp * (t * periods * std::f32::consts::TAU).sin()]
        })
        .collect()
}

/// A heart filling the frame.
fn heart(w: f32, h: f32) -> Vec<Pt> {
    let n = 72;
    let pts: Vec<Pt> = (0..n)
        .map(|k| {
            let t = k as f32 / n as f32 * std::f32::consts::TAU;
            let x = 16.0 * t.sin().powi(3);
            let y = -(13.0 * t.cos() - 5.0 * (2.0 * t).cos() - 2.0 * (3.0 * t).cos() - (4.0 * t).cos());
            [x, y]
        })
        .collect();
    fit(pts, w, h)
}

/// A cloud: bumps round an ellipse.
fn cloud(cx: f32, cy: f32, rx: f32, ry: f32) -> Vec<Pt> {
    let n = 11;
    let mut pts = Vec::new();
    for k in 0..n {
        let a0 = 360.0 * k as f32 / n as f32;
        let a1 = 360.0 * (k + 1) as f32 / n as f32;
        let p0 = [cx + rx * 0.8 * a0.to_radians().cos(), cy + ry * 0.78 * a0.to_radians().sin()];
        let p1 = [cx + rx * 0.8 * a1.to_radians().cos(), cy + ry * 0.78 * a1.to_radians().sin()];
        // a bump bulging out between the two points
        let mid = [(p0[0] + p1[0]) / 2.0, (p0[1] + p1[1]) / 2.0];
        let out = ((a0 + a1) / 2.0).to_radians();
        let ctrl = [mid[0] + rx * 0.32 * out.cos(), mid[1] + ry * 0.32 * out.sin()];
        for s in 0..8 {
            let t = s as f32 / 8.0;
            let u = 1.0 - t;
            pts.push([u * u * p0[0] + 2.0 * u * t * ctrl[0] + t * t * p1[0], u * u * p0[1] + 2.0 * u * t * ctrl[1] + t * t * p1[1]]);
        }
    }
    fit(pts, cx * 2.0 - (cx - rx) * 2.0, cy * 2.0 - (cy - ry) * 2.0).into_iter().map(|[x, y]| [x + cx - rx, y + cy - ry]).collect()
}

/// A curly brace `h` tall pointing left, `depth` across (its point at x = 0, its ends at
/// `depth`), its curves `r` tall and its point at height `mid`.
fn brace(depth: f32, r: f32, h: f32, mid: f32) -> Vec<Pt> {
    let half = depth / 2.0;
    let mut pts = arc(depth, r, half, r, 270.0, -90.0);
    pts.extend(arc(0.0, mid - r, half, r, 0.0, 90.0));
    pts.extend(arc(0.0, mid + r, half, r, 270.0, 90.0));
    pts.extend(arc(depth, h - r, half, r, 180.0, -90.0));
    pts
}

/// A ribbon banner: the middle panel in front, an end each side behind it with a notch, and the
/// folds between them; the panel raised (Tilted Up) or lowered, straight or curved. `fold` is the
/// folds' height and `panel` the panel's width, as fractions.
fn ribbon(prst: &str, w: f32, h: f32, fold: f32, panel: f32) -> Outline {
    let up = prst.ends_with('2');
    let curved = prst.starts_with("ellipse");
    // drawn raised (the ends hanging below the panel); a curved one sags towards its ends
    let dip = if curved { h * 0.12 } else { 0.0 };
    let hb = h - dip;
    let bend = |x: f32| dip * (1.0 - (x / w.max(1e-3) * std::f32::consts::PI).sin());
    let f = lim(hb * fold, hb / 2.0);
    let x1 = lim(w * (1.0 - panel) / 2.0, w / 2.0);
    let x2 = w - x1;
    let fw = (w / 8.0).min(x1);
    let pt = |x: f32, y: f32| -> Pt { if up { [x, y + bend(x)] } else { [x, h - y - bend(x)] } };
    let line = |a: f32, b: f32, y: f32| -> Vec<Pt> { (0..=8).map(|k| a + (b - a) * k as f32 / 8.0).map(|x| pt(x, y)).collect() };
    let mut front = line(x1, x2, 0.0);
    front.extend(line(x2, x1, hb - f));
    let mut left = line(0.0, x1, f);
    left.extend(line(x1, 0.0, hb));
    left.push(pt(fw, (f + hb) / 2.0));
    let mut right = line(x2, w, f);
    right.push(pt(w - fw, (f + hb) / 2.0));
    right.extend(line(w, x2, hb));
    let mut fold_l = line(x1, x1 + fw, hb - f);
    fold_l.extend(line(x1 + fw, x1, hb));
    let mut fold_r = line(x2 - fw, x2, hb - f);
    fold_r.extend(line(x2, x2 - fw, hb));
    // turning it over reverses the outlines; wind them back so they fill together
    let wind = |v: Vec<Pt>| if up { v } else { rev(v) };
    Outline::filled(vec![wind(left), wind(right), wind(front)]).with_faces(vec![(Shade::DarkenLess, wind(fold_l)), (Shade::DarkenLess, wind(fold_r))])
}

/// A scroll: a sheet rolled at the top (its roll running off to the right) with a curl at the
/// bottom left; a horizontal scroll is the same turned over, rolled down its left side.
fn scroll(vertical: bool, w: f32, h: f32, ch: f32) -> Outline {
    let (bw, bh) = if vertical { (w, h) } else { (h, w) };
    let ch = lim(ch, bw.min(bh) / 3.0);
    let r = ch / 2.0;
    let sheet = rect(ch, r, bw - ch, bh);
    let mut roll = vec![[ch, 0.0]];
    roll.extend(arc(bw - r, r, r, r, 270.0, 180.0));
    roll.push([ch, ch]);
    let curl = ellipse(ch, bh - r, r, r);
    let lines = vec![vec![[ch, ch], [bw - ch, ch]], closed(ellipse(bw - r, r, r / 2.0, r / 2.0))];
    let faces = vec![(Shade::DarkenLess, rect(ch, 0.0, ch + r, ch)), (Shade::DarkenLess, ellipse(ch, bh - r, r / 2.0, r / 2.0))];
    let turn = |p: Vec<Pt>| -> Vec<Pt> { if vertical { p } else { rev(p.into_iter().map(|[x, y]| [y, x]).collect()) } };
    Outline {
        fill: vec![turn(sheet), turn(roll), turn(curl)],
        faces: faces.into_iter().map(|(s, p)| (s, turn(p))).collect(),
        lines: lines.into_iter().map(turn).collect(),
        stroke_fill: true,
    }
}

/// A box with a wedge pointing out to `tip`, its corners rounded by `r`.
fn wedge_box(w: f32, h: f32, r: f32, tip: Pt) -> Vec<Pt> {
    let g = Geo { w, h, ss: w.min(h), hw: w / 2.0, hh: h / 2.0, adj: &[] };
    let outline = g.corners([r; 4], [0.0; 4]);
    // the side the tip is past: the wedge's base sits on it
    let (dx, dy) = (tip[0] - w / 2.0, tip[1] - h / 2.0);
    let bottom = dy.abs() * w >= dx.abs() * h && dy > 0.0;
    let top = dy.abs() * w >= dx.abs() * h && dy <= 0.0;
    let (b0, b1) = if top || bottom { ([w * 0.15, 0.0], [w * 0.35, 0.0]) } else { ([0.0, h * 0.15], [0.0, h * 0.35]) };
    let base = |p: Pt| -> Pt {
        if bottom {
            [if tip[0] < w / 2.0 { p[0] } else { w - p[0] }, h]
        } else if top {
            [if tip[0] < w / 2.0 { p[0] } else { w - p[0] }, 0.0]
        } else if dx > 0.0 {
            [w, if tip[1] < h / 2.0 { p[1] } else { h - p[1] }]
        } else {
            [0.0, if tip[1] < h / 2.0 { p[1] } else { h - p[1] }]
        }
    };
    let (p0, p1) = (base(b0), base(b1));
    // put the wedge into the outline where its base lies
    let mut out = Vec::with_capacity(outline.len() + 3);
    let on_edge =
        |a: Pt, b: Pt, p: Pt| (a[0] - p[0]).abs() < 1e-3 && (b[0] - p[0]).abs() < 1e-3 || (a[1] - p[1]).abs() < 1e-3 && (b[1] - p[1]).abs() < 1e-3;
    let mut done = false;
    for i in 0..outline.len() {
        let a = outline[i];
        let b = outline[(i + 1) % outline.len()];
        out.push(a);
        if !done && on_edge(a, b, p0) {
            let ab = [b[0] - a[0], b[1] - a[1]];
            let t = |p: Pt| ((p[0] - a[0]) * ab[0] + (p[1] - a[1]) * ab[1]) / (ab[0] * ab[0] + ab[1] * ab[1]).max(1e-6);
            let (first, second) = if t(p0) <= t(p1) { (p0, p1) } else { (p1, p0) };
            if (0.0..=1.0).contains(&t(first)) && (0.0..=1.0).contains(&t(second)) {
                out.extend([first, tip, second]);
                done = true;
            }
        }
    }
    if !done {
        out.push(tip);
    }
    out
}

/// Explosion: 8 Points, in the unit square.
const SEAL1: [Pt; 24] = [
    [0.46, 0.0],
    [0.55, 0.22],
    [0.75, 0.04],
    [0.69, 0.28],
    [0.98, 0.21],
    [0.79, 0.4],
    [1.0, 0.54],
    [0.76, 0.6],
    [0.86, 0.84],
    [0.63, 0.72],
    [0.61, 1.0],
    [0.48, 0.77],
    [0.37, 0.95],
    [0.33, 0.73],
    [0.12, 0.88],
    [0.2, 0.66],
    [0.0, 0.62],
    [0.18, 0.5],
    [0.02, 0.33],
    [0.25, 0.33],
    [0.17, 0.11],
    [0.36, 0.22],
    [0.39, 0.04],
    [0.43, 0.15],
];

/// Explosion: 14 Points, in the unit square.
const SEAL2: [Pt; 28] = [
    [0.52, 0.04],
    [0.58, 0.2],
    [0.72, 0.0],
    [0.72, 0.21],
    [0.92, 0.12],
    [0.82, 0.3],
    [1.0, 0.35],
    [0.84, 0.46],
    [0.97, 0.6],
    [0.8, 0.62],
    [0.86, 0.83],
    [0.68, 0.72],
    [0.66, 0.92],
    [0.53, 0.78],
    [0.43, 1.0],
    [0.38, 0.79],
    [0.22, 0.91],
    [0.25, 0.71],
    [0.0, 0.72],
    [0.16, 0.57],
    [0.02, 0.44],
    [0.2, 0.39],
    [0.06, 0.2],
    [0.27, 0.25],
    [0.24, 0.05],
    [0.38, 0.2],
    [0.42, 0.06],
    [0.47, 0.17],
];

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn every_gallery_preset_has_an_outline_inside_reason() {
        for (group, presets) in GALLERY {
            for p in *presets {
                if *group == "Lines" {
                    continue;
                }
                let o = outline(p, 120.0, 80.0, &[]).unwrap_or_else(|| panic!("{p} has no outline"));
                assert!(!(o.fill.is_empty() && o.lines.is_empty()), "{p} draws nothing");
                for pt in o.fill.iter().chain(&o.lines).flatten() {
                    assert!(pt[0].is_finite() && pt[1].is_finite(), "{p}: {pt:?}");
                    // callouts point outside their frame; nothing reaches far beyond it
                    assert!((-120.0..=240.0).contains(&pt[0]) && (-80.0..=160.0).contains(&pt[1]), "{p}: {pt:?}");
                }
            }
        }
    }

    #[test]
    fn adjust_values_change_the_outline_and_junk_is_safe() {
        let plain = outline("roundRect", 100.0, 100.0, &[]).unwrap();
        let big = outline("roundRect", 100.0, 100.0, &[("adj".into(), 50_000)]).unwrap();
        assert_ne!(plain, big);
        for v in [i64::MIN, -1, 0, i64::MAX] {
            for p in ["roundRect", "rightArrow", "pie", "star5", "wedgeRectCallout", "borderCallout2", "donut"] {
                let o = outline(p, 50.0, 30.0, &[("adj".into(), v), ("adj1".into(), v), ("adj2".into(), v), ("adj3".into(), v)]).unwrap();
                assert!(o.fill.iter().chain(&o.lines).flatten().all(|q| q[0].is_finite() && q[1].is_finite()), "{p} {v}");
            }
        }
        assert!(outline("rect", f32::NAN, 10.0, &[]).is_none());
        assert!(outline("rect", -5.0, 10.0, &[]).is_none());
        assert!(outline("noSuchPreset", 10.0, 10.0, &[]).is_none());
        assert!(known("hexagon") && !known("noSuchPreset"));
    }

    #[test]
    fn a_ring_is_a_hole_wound_the_other_way() {
        let area = |p: &[Pt]| p.iter().zip(p.iter().cycle().skip(1)).map(|(a, b)| a[0] * b[1] - b[0] * a[1]).sum::<f32>() / 2.0;
        let o = outline("donut", 100.0, 100.0, &[]).unwrap();
        assert_eq!(o.fill.len(), 2);
        assert!(area(&o.fill[0]).signum() != area(&o.fill[1]).signum());
    }
}
