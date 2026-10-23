//! SVG pictures (icons) read into the same model as metafiles, so they are drawn from their own
//! outlines and stay sharp at any size. The subset icons use: `g`, `path`, `rect`, `circle`,
//! `ellipse`, `line`, `polyline` and `polygon`, painted by `fill`, `stroke`, `stroke-width`,
//! `fill-rule` and the opacities (set as attributes, in a `style` attribute, or by class or element
//! name from a `style` element) and moved by `transform`s. A gradient paints as its first stop.
//! Text, clipping, masks, filters, patterns and `use` are left out. The picture is the view box,
//! stretched to whatever frame it is drawn in.

use std::collections::HashMap;

use kurbo::{Affine, BezPath, PathEl, Point};
use quick_xml::events::Event;
use quick_xml::reader::Reader;

use crate::{Error, Item, MAX_ITEMS, Picture, Seg};

/// Largest SVG read, bytes.
const MAX_SVG_BYTES: usize = 8 << 20;
/// Most elements and nesting one SVG may have.
const MAX_ELEMENTS: usize = 50_000;
const MAX_DEPTH: usize = 64;
/// Path segments kept from one file.
const MAX_SEGS: usize = 1_000_000;
/// Coordinates beyond this are hostile (an arc that large would split into endless pieces).
const MAX_COORD: f64 = 1.0e6;

type Rgb = [u8; 3];
const BLACK: Rgb = [0, 0, 0];

/// True when the bytes look like an SVG file: an `<svg` element near the start.
pub(crate) fn is_svg(bytes: &[u8]) -> bool {
    let head = bytes.get(..bytes.len().min(2048)).unwrap_or(bytes);
    head.windows(4).any(|w| w == b"<svg")
}

/// Reads an SVG file. [`Error::BadHeader`] when it isn't XML with an `svg` root and a usable view
/// box (or width and height), [`Error::NoRecords`] when it draws nothing WordCraft reads.
pub(crate) fn parse(bytes: &[u8]) -> Result<Picture, Error> {
    let root = tree(bytes).ok_or(Error::BadHeader)?;
    let mut sheet = Sheet::default();
    sheet.read(&root, 0);
    let vb = root.attr("viewBox").map(numbers).filter(|v| v.len() == 4);
    let size = |k: &str| root.attr(k).and_then(|v| length(v, 0.0)).filter(|n| *n > 0.0);
    let view = match (vb, size("width"), size("height")) {
        (Some(v), ..) => [v[0], v[1], v[2], v[3]],
        (None, Some(w), Some(h)) => [0.0, 0.0, w, h],
        _ => return Err(Error::BadHeader),
    };
    if view.iter().any(|c| !c.is_finite() || c.abs() > MAX_COORD) || view[2] <= 0.0 || view[3] <= 0.0 {
        return Err(Error::BadHeader);
    }
    let mut walk = Walk { sheet: &sheet, view: [view[2], view[3]], items: Vec::new(), segs: 0 };
    // the view box's corner is the picture's origin
    walk.node(&root, &Paint::default(), Affine::translate((-view[0], -view[1])), 0);
    if walk.items.is_empty() {
        return Err(Error::NoRecords);
    }
    Ok(Picture { width: view[2] as f32, height: view[3] as f32, items: walk.items })
}

/// An element of the file.
#[derive(Debug, Default)]
struct Node {
    name: String,
    attrs: Vec<(String, String)>,
    kids: Vec<Node>,
    text: String,
}

impl Node {
    fn attr(&self, k: &str) -> Option<&str> {
        self.attrs.iter().find(|(n, _)| n == k).map(|(_, v)| v.as_str())
    }
}

/// The file's element tree, or `None` when it isn't XML, has no `svg` root or is over the limits.
fn tree(bytes: &[u8]) -> Option<Node> {
    if bytes.len() > MAX_SVG_BYTES {
        return None;
    }
    let mut r = Reader::from_reader(bytes.strip_prefix(&[0xEF, 0xBB, 0xBF][..]).unwrap_or(bytes));
    r.config_mut().check_end_names = false;
    let mut stack: Vec<Node> = Vec::new();
    let mut root: Option<Node> = None;
    let mut count = 0usize;
    let mut buf = Vec::new();
    loop {
        buf.clear();
        let ev = r.read_event_into(&mut buf).ok()?;
        match ev {
            Event::Start(ref e) | Event::Empty(ref e) => {
                count += 1;
                if count > MAX_ELEMENTS {
                    return None;
                }
                let name = String::from_utf8_lossy(e.local_name().as_ref()).into_owned();
                let mut node = Node { name, ..Node::default() };
                for a in e.attributes().with_checks(false).flatten() {
                    let key = String::from_utf8_lossy(a.key.local_name().as_ref()).into_owned();
                    let val = match a.normalized_value(quick_xml::XmlVersion::Explicit1_0) {
                        Ok(v) => v.into_owned(),
                        Err(_) => String::from_utf8_lossy(&a.value).into_owned(),
                    };
                    node.attrs.push((key, val));
                }
                if matches!(ev, Event::Start(_)) {
                    if stack.len() >= MAX_DEPTH {
                        return None;
                    }
                    stack.push(node);
                } else {
                    attach(&mut stack, &mut root, node);
                }
            }
            Event::End(_) => {
                if let Some(n) = stack.pop() {
                    attach(&mut stack, &mut root, n);
                }
            }
            Event::Text(t) => {
                if let (Some(top), Ok(s)) = (stack.last_mut(), t.decode()) {
                    top.text.push_str(&s);
                }
            }
            Event::CData(t) => {
                if let Some(top) = stack.last_mut() {
                    top.text.push_str(&String::from_utf8_lossy(&t));
                }
            }
            Event::Eof => break,
            _ => {}
        }
        if root.is_some() && stack.is_empty() {
            break;
        }
    }
    while let Some(n) = stack.pop() {
        attach(&mut stack, &mut root, n);
    }
    root.filter(|n| n.name == "svg")
}

fn attach(stack: &mut [Node], root: &mut Option<Node>, n: Node) {
    match stack.last_mut() {
        Some(top) => top.kids.push(n),
        None => *root = Some(n),
    }
}

/// What a shape is painted with, as the elements around it pass it down.
#[derive(Clone, Debug)]
struct Paint {
    fill: Option<Rgb>,
    stroke: Option<Rgb>,
    stroke_width: f64,
    fill_opacity: f32,
    stroke_opacity: f32,
    /// The product of the `opacity` of the element and of those around it.
    opacity: f32,
    even_odd: bool,
    /// `color`, what `currentColor` means.
    color: Rgb,
}

impl Default for Paint {
    fn default() -> Paint {
        Paint {
            fill: Some(BLACK),
            stroke: None,
            stroke_width: 1.0,
            fill_opacity: 1.0,
            stroke_opacity: 1.0,
            opacity: 1.0,
            even_odd: false,
            color: BLACK,
        }
    }
}

/// The file's style rules (declarations by class and by element name) and the first stop colour
/// of each gradient, by id.
#[derive(Default)]
struct Sheet {
    classes: HashMap<String, Vec<(String, String)>>,
    elements: HashMap<String, Vec<(String, String)>>,
    gradients: HashMap<String, Rgb>,
}

/// `fill: #fff; stroke: none` as (property, value) pairs.
fn declarations(s: &str) -> Vec<(String, String)> {
    s.split(';')
        .filter_map(|d| {
            let (k, v) = d.split_once(':')?;
            let v = v.trim().trim_end_matches("!important").trim();
            Some((k.trim().to_ascii_lowercase(), v.to_string()))
        })
        .collect()
}

impl Sheet {
    fn read(&mut self, n: &Node, depth: usize) {
        if depth > MAX_DEPTH {
            return;
        }
        match n.name.as_str() {
            "style" => {
                // comments out, then `selectors { declarations }` pairs
                let mut css = n.text.clone();
                while let Some(a) = css.find("/*") {
                    let b = css.get(a..).and_then(|t| t.find("*/")).map_or(css.len(), |e| a + e + 2);
                    css.replace_range(a..b, "");
                }
                for block in css.split('}') {
                    let Some((sel, body)) = block.split_once('{') else { continue };
                    let decls = declarations(body);
                    for s in sel.split(',').map(str::trim) {
                        let (map, key) = match s.strip_prefix('.') {
                            Some(c) => (&mut self.classes, c),
                            None => (&mut self.elements, s),
                        };
                        if !key.is_empty() && key.chars().all(|c| c.is_alphanumeric() || c == '_' || c == '-') {
                            map.entry(key.to_string()).or_default().extend(decls.iter().cloned());
                        }
                    }
                }
            }
            "linearGradient" | "radialGradient" => {
                let first = n.kids.iter().filter(|k| k.name == "stop").find_map(|s| {
                    let style = s.attr("style").map(declarations).unwrap_or_default();
                    let v = style.iter().find(|(k, _)| k == "stop-color").map(|(_, v)| v.as_str()).or(s.attr("stop-color"))?;
                    color(v, BLACK).flatten()
                });
                if let (Some(id), Some(c)) = (n.attr("id"), first) {
                    self.gradients.insert(id.to_string(), c);
                }
            }
            _ => {}
        }
        for k in &n.kids {
            self.read(k, depth + 1);
        }
    }
}

/// A colour value: `Some(None)` for `none`, `None` when it can't be read.
fn color(v: &str, current: Rgb) -> Option<Option<Rgb>> {
    let lower = v.trim().to_ascii_lowercase();
    match lower.as_str() {
        "none" | "transparent" => return Some(None),
        "currentcolor" => return Some(Some(current)),
        _ => {}
    }
    if let Some(hex) = lower.strip_prefix('#') {
        let digits: Vec<u8> = hex.chars().map(|c| c.to_digit(16).and_then(|d| u8::try_from(d).ok())).collect::<Option<_>>()?;
        return match digits.as_slice() {
            [r, g, b] => Some(Some([r * 17, g * 17, b * 17])),
            [r1, r2, g1, g2, b1, b2] => Some(Some([r1 * 16 + r2, g1 * 16 + g2, b1 * 16 + b2])),
            _ => None,
        };
    }
    if let Some(args) = lower.strip_prefix("rgb(").or_else(|| lower.strip_prefix("rgba(")).and_then(|a| a.strip_suffix(')')) {
        let parts: Vec<u8> = args
            .split([',', ' ', '/'])
            .filter(|p| !p.is_empty())
            .take(3)
            .map(|p| match p.strip_suffix('%') {
                Some(pc) => pc.parse::<f32>().ok().filter(|f| f.is_finite()).map(|f| (f.clamp(0.0, 100.0) * 2.55).round() as u8),
                None => p.parse::<f32>().ok().filter(|f| f.is_finite()).map(|f| f.clamp(0.0, 255.0).round() as u8),
            })
            .collect::<Option<_>>()?;
        return match parts.as_slice() {
            [r, g, b] => Some(Some([*r, *g, *b])),
            _ => None,
        };
    }
    let named = match lower.as_str() {
        "black" => [0, 0, 0],
        "white" => [255, 255, 255],
        "red" => [255, 0, 0],
        "lime" => [0, 255, 0],
        "green" => [0, 128, 0],
        "blue" => [0, 0, 255],
        "yellow" => [255, 255, 0],
        "cyan" | "aqua" => [0, 255, 255],
        "magenta" | "fuchsia" => [255, 0, 255],
        "gray" | "grey" => [128, 128, 128],
        "silver" => [192, 192, 192],
        "maroon" => [128, 0, 0],
        "olive" => [128, 128, 0],
        "navy" => [0, 0, 128],
        "purple" => [128, 0, 128],
        "teal" => [0, 128, 128],
        "orange" => [255, 165, 0],
        _ => return None,
    };
    Some(Some(named))
}

/// The numbers in `s` (separated by spaces, commas, signs or a second decimal point), units
/// dropped.
fn numbers(s: &str) -> Vec<f64> {
    fn flush(cur: &mut String, out: &mut Vec<f64>) {
        if let Ok(n) = cur.parse::<f64>() {
            out.push(n);
        }
        cur.clear();
    }
    let mut out = Vec::new();
    let mut cur = String::new();
    let mut prev = ' ';
    for c in s.chars() {
        // a sign (not an exponent's) or a second point starts the next number
        let starts_new = ((c == '-' || c == '+') && !matches!(prev, 'e' | 'E')) || (c == '.' && cur.contains('.') && !cur.contains(['e', 'E']));
        if starts_new {
            flush(&mut cur, &mut out);
        }
        if c.is_ascii_digit() || matches!(c, '.' | '-' | '+') || (matches!(c, 'e' | 'E') && !cur.is_empty()) {
            cur.push(c);
        } else {
            flush(&mut cur, &mut out);
        }
        prev = c;
    }
    flush(&mut cur, &mut out);
    out
}

/// A length (`12`, `12px`, or `50%` of `whole`), refused when not finite or hostile.
fn length(v: &str, whole: f64) -> Option<f64> {
    let v = v.trim();
    let n = match v.strip_suffix('%') {
        Some(p) => p.trim().parse::<f64>().ok()? / 100.0 * whole,
        None => *numbers(v).first()?,
    };
    (n.is_finite() && n.abs() <= MAX_COORD).then_some(n)
}

/// A `transform` list, or `None` when it can't be read or is hostile.
fn transform(v: &str) -> Option<Affine> {
    let mut m = Affine::IDENTITY;
    for part in v.split(')') {
        let Some((name, args)) = part.split_once('(') else { continue };
        let a = numbers(args);
        let get = |i: usize| a.get(i).copied();
        let t = match name.trim().trim_start_matches(',').trim() {
            "matrix" => match a.as_slice() {
                [a0, b, c, d, e, f] => Affine::new([*a0, *b, *c, *d, *e, *f]),
                _ => return None,
            },
            "translate" => Affine::translate((get(0)?, get(1).unwrap_or(0.0))),
            "scale" => {
                let sx = get(0)?;
                Affine::scale_non_uniform(sx, get(1).unwrap_or(sx))
            }
            "rotate" => {
                let r = Affine::rotate(get(0)?.to_radians());
                match (get(1), get(2)) {
                    (Some(cx), Some(cy)) => Affine::translate((cx, cy)) * r * Affine::translate((-cx, -cy)),
                    _ => r,
                }
            }
            "skewX" => Affine::skew(get(0)?.to_radians().tan(), 0.0),
            "skewY" => Affine::skew(0.0, get(0)?.to_radians().tan()),
            _ => return None,
        };
        m *= t;
    }
    m.as_coeffs().iter().all(|c| c.is_finite() && c.abs() <= MAX_COORD).then_some(m)
}

/// The presentation properties an element can set as attributes.
const PROPS: [&str; 10] =
    ["fill", "stroke", "stroke-width", "fill-opacity", "stroke-opacity", "opacity", "fill-rule", "color", "display", "visibility"];

/// Element `n`'s own declarations, each overriding those before: its element rule, its classes',
/// its presentation attributes, then its `style` attribute.
fn own_style(n: &Node, sheet: &Sheet) -> Vec<(String, String)> {
    let mut out: Vec<(String, String)> = sheet.elements.get(&n.name).cloned().unwrap_or_default();
    if let Some(classes) = n.attr("class") {
        for c in classes.split_whitespace() {
            if let Some(d) = sheet.classes.get(c) {
                out.extend(d.iter().cloned());
            }
        }
    }
    for p in PROPS {
        if let Some(v) = n.attr(p) {
            out.push((p.to_string(), v.to_string()));
        }
    }
    if let Some(s) = n.attr("style") {
        out.extend(declarations(s));
    }
    out
}

/// A paint value: a colour, `none`, or a gradient (`url(#id)`) as its first stop.
fn paint_value(v: &str, current: Rgb, sheet: &Sheet) -> Option<Option<Rgb>> {
    if let Some(id) = v.trim().strip_prefix("url(").and_then(|r| r.split(')').next()) {
        let id = id.trim().trim_matches(['\'', '"']).trim_start_matches('#');
        return Some(sheet.gradients.get(id).copied());
    }
    color(v, current)
}

fn opacity(v: &str) -> Option<f32> {
    let v = v.trim();
    let f = match v.strip_suffix('%') {
        Some(p) => p.parse::<f32>().ok()? / 100.0,
        None => v.parse::<f32>().ok()?,
    };
    f.is_finite().then(|| f.clamp(0.0, 1.0))
}

/// `paint` with element `n`'s own style applied; `None` when the element isn't shown.
fn styled(n: &Node, paint: &Paint, sheet: &Sheet) -> Option<Paint> {
    let mut p = paint.clone();
    let decls = own_style(n, sheet);
    // `color` first: `currentColor` in the others means this element's colour
    if let Some(c) = decls.iter().rev().find(|(k, _)| k == "color").and_then(|(_, v)| color(v, p.color).flatten()) {
        p.color = c;
    }
    for (k, v) in &decls {
        match k.as_str() {
            "fill" => p.fill = paint_value(v, p.color, sheet).unwrap_or(p.fill),
            "stroke" => p.stroke = paint_value(v, p.color, sheet).unwrap_or(p.stroke),
            "stroke-width" => p.stroke_width = length(v, 1.0).filter(|w| *w >= 0.0).unwrap_or(p.stroke_width),
            "fill-opacity" => p.fill_opacity = opacity(v).unwrap_or(p.fill_opacity),
            "stroke-opacity" => p.stroke_opacity = opacity(v).unwrap_or(p.stroke_opacity),
            "opacity" => p.opacity *= opacity(v).unwrap_or(1.0),
            "fill-rule" => p.even_odd = v.trim() == "evenodd",
            "display" if v.trim() == "none" => return None,
            "visibility" if matches!(v.trim(), "hidden" | "collapse") => return None,
            _ => {}
        }
    }
    Some(p)
}

/// The outline of a basic shape or path element in its own user units; `view` is the view box's
/// size, what percentages are of.
fn outline(n: &Node, view: [f64; 2]) -> Option<BezPath> {
    let [vw, vh] = view;
    let num = |k: &str, whole: f64| n.attr(k).and_then(|v| length(v, whole)).unwrap_or(0.0);
    match n.name.as_str() {
        "path" => {
            let d = n.attr("d")?;
            if numbers(d).iter().any(|x| x.abs() > MAX_COORD) {
                return None;
            }
            BezPath::from_svg(d).ok()
        }
        "rect" => {
            let (x, y, w, h) = (num("x", vw), num("y", vh), num("width", vw), num("height", vh));
            if w <= 0.0 || h <= 0.0 {
                return None;
            }
            let (rx, ry) = match (n.attr("rx").and_then(|v| length(v, vw)), n.attr("ry").and_then(|v| length(v, vh))) {
                (Some(rx), Some(ry)) => (rx, ry),
                (Some(r), None) | (None, Some(r)) => (r, r),
                (None, None) => (0.0, 0.0),
            };
            // one radius for both axes: the smaller, within half each side
            let r = rx.clamp(0.0, w / 2.0).min(ry.clamp(0.0, h / 2.0));
            Some(kurbo::Shape::to_path(&kurbo::RoundedRect::new(x, y, x + w, y + h, r), 0.1))
        }
        "circle" => {
            let r = num("r", vw.hypot(vh) / std::f64::consts::SQRT_2);
            (r > 0.0).then(|| kurbo::Shape::to_path(&kurbo::Circle::new((num("cx", vw), num("cy", vh)), r), 0.1))
        }
        "ellipse" => {
            let (rx, ry) = (num("rx", vw), num("ry", vh));
            (rx > 0.0 && ry > 0.0).then(|| kurbo::Shape::to_path(&kurbo::Ellipse::new((num("cx", vw), num("cy", vh)), (rx, ry), 0.0), 0.1))
        }
        "line" => {
            let mut p = BezPath::new();
            p.move_to((num("x1", vw), num("y1", vh)));
            p.line_to((num("x2", vw), num("y2", vh)));
            Some(p)
        }
        "polyline" | "polygon" => {
            let pts = numbers(n.attr("points")?);
            if pts.iter().any(|x| x.abs() > MAX_COORD) {
                return None;
            }
            let mut p = BezPath::new();
            for (i, [x, y]) in pts.as_chunks::<2>().0.iter().enumerate() {
                if i == 0 {
                    p.move_to((*x, *y));
                } else {
                    p.line_to((*x, *y));
                }
            }
            if n.name == "polygon" {
                p.close_path();
            }
            (p.elements().len() > 1).then_some(p)
        }
        _ => None,
    }
}

/// A path's elements as segments moved by `m`; `None` when a point isn't finite.
fn segs(p: &BezPath, m: Affine) -> Option<Vec<Seg>> {
    let pt = |q: Point| {
        let q = m * q;
        let (x, y) = (q.x as f32, q.y as f32);
        (x.is_finite() && y.is_finite()).then_some((x, y))
    };
    let mut out = Vec::with_capacity(p.elements().len());
    let mut last = Point::ORIGIN;
    for el in p.elements() {
        out.push(match *el {
            PathEl::MoveTo(a) => {
                last = a;
                let (x, y) = pt(a)?;
                Seg::Move(x, y)
            }
            PathEl::LineTo(a) => {
                last = a;
                let (x, y) = pt(a)?;
                Seg::Line(x, y)
            }
            PathEl::QuadTo(c, a) => {
                // the same curve as a cubic
                let ((x1, y1), (x2, y2), (x, y)) = (pt(last + (c - last) * (2.0 / 3.0))?, pt(a + (c - a) * (2.0 / 3.0))?, pt(a)?);
                last = a;
                Seg::Cubic(x1, y1, x2, y2, x, y)
            }
            PathEl::CurveTo(c1, c2, a) => {
                let ((x1, y1), (x2, y2), (x, y)) = (pt(c1)?, pt(c2)?, pt(a)?);
                last = a;
                Seg::Cubic(x1, y1, x2, y2, x, y)
            }
            PathEl::ClosePath => Seg::Close,
        });
    }
    Some(out)
}

/// Elements whose content is never drawn where it stands.
const NOT_DRAWN: [&str; 14] = [
    "defs",
    "clipPath",
    "mask",
    "symbol",
    "pattern",
    "marker",
    "style",
    "linearGradient",
    "radialGradient",
    "metadata",
    "title",
    "desc",
    "text",
    "foreignObject",
];

struct Walk<'a> {
    sheet: &'a Sheet,
    view: [f64; 2],
    items: Vec<Item>,
    segs: usize,
}

impl Walk<'_> {
    fn node(&mut self, n: &Node, paint: &Paint, m: Affine, depth: usize) {
        if depth > MAX_DEPTH || self.items.len() >= MAX_ITEMS || NOT_DRAWN.contains(&n.name.as_str()) {
            return;
        }
        let Some(p) = styled(n, paint, self.sheet) else { return };
        let m = match n.attr("transform") {
            Some(t) => match transform(t) {
                Some(t) => m * t,
                None => return,
            },
            None => m,
        };
        if let Some(segs) = outline(n, self.view).and_then(|o| segs(&o, m)) {
            self.segs = self.segs.saturating_add(segs.len());
            // a stroke as wide as the transform makes it (the mean of its two scales)
            let width = (p.stroke_width * m.determinant().abs().sqrt()) as f32;
            let rgba = |c: Rgb, alpha: f32| [c[0], c[1], c[2], (alpha.clamp(0.0, 1.0) * 255.0).round() as u8];
            // an open polyline is filled as if closed; a line never is
            let fill = p.fill.filter(|_| n.name != "line").map(|c| rgba(c, p.opacity * p.fill_opacity));
            let stroke = p.stroke.filter(|_| width > 0.0 && width.is_finite()).map(|c| (rgba(c, p.opacity * p.stroke_opacity), width));
            if self.segs <= MAX_SEGS && segs.len() > 1 && (fill.is_some() || stroke.is_some()) {
                self.items.push(Item::Path { segs, fill, stroke, even_odd: p.even_odd });
            }
        }
        for k in &n.kids {
            self.node(k, &p, m, depth + 1);
        }
    }
}

/// New paint for the drawn shapes of an SVG picture (an icon's Fill, Outline and colour). `None`
/// leaves that paint alone; `Some(None)` takes it away.
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct Recolor {
    /// The fill of every shape that has one (a fill isn't added to a shape drawn as a line).
    pub fill: Option<Option<[u8; 3]>>,
    /// The outline colour of every shape, or only of those already outlined (`outlined_only`).
    pub outline: Option<Option<[u8; 3]>>,
    /// The outline width, in the picture's units, of every shape that ends up outlined.
    pub width: Option<f32>,
    /// `outline` recolours existing outlines and adds none: one colour for line art.
    pub outlined_only: bool,
}

/// The SVG in `bytes` with `r` applied. The new paint goes in each drawn shape's `style`
/// attribute, which wins over the file's style rules and presentation attributes; everything else
/// is written back as it was. `None` when the file isn't an SVG [`parse`] reads, or nothing in it
/// changes.
pub fn recolor(bytes: &[u8], r: Recolor) -> Option<Vec<u8>> {
    use quick_xml::writer::Writer;

    let root = tree(bytes)?;
    let mut sheet = Sheet::default();
    sheet.read(&root, 0);
    let hex = |c: [u8; 3]| format!("#{:02X}{:02X}{:02X}", c[0], c[1], c[2]);
    let width = r.width.filter(|w| w.is_finite()).map(|w| w.clamp(0.0, MAX_COORD as f32) as f64);
    let mut reader = Reader::from_reader(bytes.strip_prefix(&[0xEF, 0xBB, 0xBF][..]).unwrap_or(bytes));
    reader.config_mut().check_end_names = false;
    let mut out = Writer::new(Vec::with_capacity(bytes.len() + 256));
    // the paint and transform each open element passes down (`None` inside something not drawn)
    let mut stack: Vec<Option<(Paint, Affine)>> = Vec::new();
    let mut buf = Vec::new();
    let mut changed = false;
    loop {
        buf.clear();
        let ev = reader.read_event_into(&mut buf).ok()?;
        match ev {
            Event::Start(ref e) | Event::Empty(ref e) => {
                let name = String::from_utf8_lossy(e.local_name().as_ref()).into_owned();
                let mut node = Node { name, ..Node::default() };
                for a in e.attributes().with_checks(false).flatten() {
                    let key = String::from_utf8_lossy(a.key.local_name().as_ref()).into_owned();
                    let val = a.normalized_value(quick_xml::XmlVersion::Explicit1_0).map(|v| v.into_owned()).unwrap_or_default();
                    node.attrs.push((key, val));
                }
                let parent = match stack.last() {
                    Some(p) => p.clone(),
                    None => Some((Paint::default(), Affine::IDENTITY)),
                };
                // as the reader walks it: what isn't drawn passes nothing down
                let here = parent.filter(|_| !NOT_DRAWN.contains(&node.name.as_str())).and_then(|(p, m)| {
                    let p = styled(&node, &p, &sheet)?;
                    let m = match node.attr("transform") {
                        Some(t) => m * transform(t)?,
                        None => m,
                    };
                    Some((p, m))
                });
                let shape = matches!(node.name.as_str(), "path" | "rect" | "circle" | "ellipse" | "line" | "polyline" | "polygon");
                let mut extra = Vec::new();
                if let (true, Some((p, m))) = (shape, &here) {
                    if let Some(f) = r.fill
                        && p.fill.is_some()
                        && node.name != "line"
                    {
                        extra.push(format!("fill:{}", f.map_or("none".to_string(), hex)));
                    }
                    let outlined = match r.outline {
                        Some(Some(c)) if !r.outlined_only || p.stroke.is_some() => {
                            extra.push(format!("stroke:{}", hex(c)));
                            true
                        }
                        Some(None) => {
                            extra.push("stroke:none".to_string());
                            false
                        }
                        _ => p.stroke.is_some(),
                    };
                    if let Some(w) = width
                        && outlined
                    {
                        // the width in the shape's own units, as the transforms above it scale them
                        let k = m.determinant().abs().sqrt();
                        let local = if k > 1e-9 { w / k } else { w };
                        extra.push(format!("stroke-width:{}", (local * 1000.0).round() / 1000.0));
                    }
                }
                let written = if extra.is_empty() {
                    e.to_owned()
                } else {
                    changed = true;
                    let mut el = e.to_owned();
                    el.clear_attributes();
                    let mut style = String::new();
                    for a in e.attributes().with_checks(false).flatten() {
                        if a.key.local_name().as_ref() == b"style" {
                            style = a.normalized_value(quick_xml::XmlVersion::Explicit1_0).map(|v| v.into_owned()).unwrap_or_default();
                        } else {
                            el.push_attribute(a);
                        }
                    }
                    // the declarations set again are dropped, so repeated changes don't pile up
                    let set: Vec<&str> = extra.iter().filter_map(|d| d.split(':').next()).collect();
                    let mut kept: Vec<String> =
                        declarations(&style).into_iter().filter(|(k, _)| !set.contains(&k.as_str())).map(|(k, v)| format!("{k}:{v}")).collect();
                    kept.extend(extra);
                    el.push_attribute(("style", kept.join(";").as_str()));
                    el
                };
                let is_start = matches!(ev, Event::Start(_));
                out.write_event(if is_start { Event::Start(written) } else { Event::Empty(written) }).ok()?;
                if is_start {
                    if stack.len() >= MAX_DEPTH {
                        return None;
                    }
                    stack.push(here);
                }
            }
            Event::End(e) => {
                stack.pop();
                out.write_event(Event::End(e)).ok()?;
            }
            Event::Eof => break,
            other => out.write_event(other).ok()?,
        }
    }
    changed.then(|| out.into_inner())
}
