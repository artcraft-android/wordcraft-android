//! Draw › Ink Editor: editing text with the pen. A stroke over the text is read as a gesture,
//! done to the text under it, and dropped (it never becomes ink): a line through words or a
//! scribble over them deletes them, a loop selects what it goes round, a vertical bar through a
//! word splits it, a ∪ under the gap between two words joins them, a ∧ under the text puts the
//! caret where it points, and a corner (down, then across) starts a new paragraph there. With the
//! highlighter, a stroke along text highlights it. A stroke that is none of these changes nothing.

use serde_json::{Value, json};
use wordcraft_doc::Pos;

use super::draw::{page_param, points_param};
use crate::{CmdError, CmdResult, CommandSpec, Selection, Session, p};

/// Most points read from one stroke.
const MAX_POINTS: usize = 5000;

/// The line height assumed where the stroke isn't over text (points).
const DEFAULT_LINE: f32 = 14.0;

pub fn specs() -> Vec<CommandSpec> {
    vec![CommandSpec::new("draw.inkEdit", "Ink Editor", "Draw › Ink Editor", ink_edit).params(
        r#"{"page": n, "points": [[x, y], …] (page points, at most 5000), "tool"?: "pen|highlighter", "color"?: highlight colour name (default yellow)}; returns {"gesture": "delete|select|split|join|insert|newParagraph|highlight|none"}"#,
    )]
}

/// What a stroke means.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Gesture {
    /// A line across words.
    Strike,
    /// Back and forth over words.
    Scribble,
    Loop,
    Bar,
    Cup,
    Caret,
    Corner,
    None,
}

/// The shape of a stroke, its size judged against `line`, the height of the text's lines there.
pub(crate) fn classify(pts: &[[f32; 2]], line: f32) -> Gesture {
    let line = if line.is_finite() { line.clamp(4.0, 200.0) } else { DEFAULT_LINE };
    let (Some(&first), Some(&last)) = (pts.first(), pts.last()) else { return Gesture::None };
    let dist = |a: [f32; 2], b: [f32; 2]| ((a[0] - b[0]).powi(2) + (a[1] - b[1]).powi(2)).sqrt();
    let (mut x0, mut y0, mut x1, mut y1) = (first[0], first[1], first[0], first[1]);
    let mut length = 0.0;
    let mut prev = first;
    // the lowest and highest points (page y grows downwards)
    let (mut low, mut high) = ((0usize, first), (0usize, first));
    for (i, &pt) in pts.iter().enumerate() {
        (x0, y0, x1, y1) = (x0.min(pt[0]), y0.min(pt[1]), x1.max(pt[0]), y1.max(pt[1]));
        length += dist(prev, pt);
        prev = pt;
        if pt[1] > low.1[1] {
            low = (i, pt);
        }
        if pt[1] < high.1[1] {
            high = (i, pt);
        }
    }
    let (w, h) = (x1 - x0, y1 - y0);
    let size = w.max(h);
    if size < line * 0.3 {
        return Gesture::None;
    }
    // back where it started after going round
    if dist(first, last) < size * 0.35 && length > (w + h) * 1.3 && w > line * 0.5 && h > line * 0.5 {
        return Gesture::Loop;
    }
    if x_turns(pts, line * 0.25) >= 3 && h < line * 2.5 && w > line * 0.8 {
        return Gesture::Scribble;
    }
    if w > h * 3.0 && w > line {
        return Gesture::Strike;
    }
    if h > w * 2.5 && h > line * 0.6 {
        return Gesture::Bar;
    }
    // ∪ and ∧: the ends about level, the middle well below or above them
    let n = pts.len().saturating_sub(1).max(1) as f32;
    let middle = |i: usize| (0.2..=0.8).contains(&(i as f32 / n));
    let level = (first[1] - last[1]).abs() < h * 0.5;
    let ends = (first[1] + last[1]) / 2.0;
    if level && w < line * 4.0 && h > line * 0.25 {
        if middle(low.0) && low.1[1] - ends > h * 0.6 {
            return Gesture::Cup;
        }
        if middle(high.0) && ends - high.1[1] > h * 0.6 {
            return Gesture::Caret;
        }
    }
    // down to a corner, then across
    let corner = low.1;
    let (dx1, dy1) = ((corner[0] - first[0]).abs(), corner[1] - first[1]);
    let (dx2, dy2) = ((last[0] - corner[0]).abs(), (last[1] - corner[1]).abs());
    if dy1 > line * 0.6 && dx1 < dy1 * 0.4 && dx2 > line * 0.6 && dy2 < dx2 * 0.4 {
        return Gesture::Corner;
    }
    Gesture::None
}

/// How often the stroke turns back left or right, ignoring wobbles under `jitter` points.
fn x_turns(pts: &[[f32; 2]], jitter: f32) -> usize {
    let mut turns = 0;
    let mut dir = 0i8;
    let mut anchor = match pts.first() {
        Some(p) => p[0],
        None => return 0,
    };
    for pt in pts {
        let d = pt[0] - anchor;
        if d.abs() < jitter {
            continue;
        }
        let now = if d > 0.0 { 1 } else { -1 };
        if dir != 0 && now != dir {
            turns += 1;
        }
        dir = now;
        anchor = pt[0];
    }
    turns
}

fn ink_edit(s: &mut Session, v: &Value) -> CmdResult {
    let (page, layout) = page_param(s, v)?;
    let pg = layout.pages.get(page).ok_or_else(|| CmdError::Params(format!("no page {page}")))?;
    let (pw, ph) = (pg.w.max(1.0), pg.h.clamp(1.0, 1e5));
    let pts = points_param(v, MAX_POINTS, pw, ph)?;
    let first = *pts.first().ok_or_else(|| CmdError::Params("`points` has no usable point".into()))?;
    let story = s.sel.focus.story;
    let hit = |x: f32, y: f32| layout.hit(page, x, y, story);
    let (mut x0, mut y0, mut x1, mut y1) = (first[0], first[1], first[0], first[1]);
    for [x, y] in &pts {
        (x0, y0, x1, y1) = (x0.min(*x), y0.min(*y), x1.max(*x), y1.max(*y));
    }
    let mid_y = pts.iter().map(|p| p[1]).sum::<f32>() / pts.len() as f32;
    // the text line the stroke is on: its height scales the shapes
    let under = hit((x0 + x1) / 2.0, mid_y);
    let caret = under.as_ref().and_then(|p| layout.caret_on(p, page));
    let line = caret.as_ref().map(|c| c.height).filter(|h| *h > 0.0).unwrap_or(DEFAULT_LINE);
    let done = |s: &mut Session, gesture: &str| -> CmdResult {
        s.relayout();
        Ok(json!({"gesture": gesture}))
    };

    if p::str(v, "tool") == Some("highlighter") {
        let (Some(a), Some(b)) = (hit(x0, mid_y), hit(x1, mid_y)) else { return Ok(json!({"gesture": "none"})) };
        let (a, b) = if b < a { (b, a) } else { (a, b) };
        if a == b {
            return Ok(json!({"gesture": "none"}));
        }
        s.sel = Selection { anchor: a, focus: b };
        let color = p::str(v, "color").unwrap_or("yellow");
        s.run("format.highlight", &json!({"color": color}))?;
        return done(s, "highlight");
    }

    match classify(&pts, line) {
        g @ (Gesture::Strike | Gesture::Scribble) => {
            // a line across must go through the letters, not under them
            if g == Gesture::Strike
                && let Some(c) = &caret
                && !(c.top + c.height * 0.2..=c.top + c.height * 0.85).contains(&mid_y)
            {
                return Ok(json!({"gesture": "none"}));
            }
            let Some((a, b)) = words(s, hit(x0, mid_y), hit(x1, mid_y)) else { return Ok(json!({"gesture": "none"})) };
            s.sel = Selection { anchor: a, focus: b };
            s.run("text.delete", &json!({}))?;
            done(s, "delete")
        }
        Gesture::Loop => {
            let Some((a, b)) = words(s, hit(x0, (y0 + y1) / 2.0).or_else(|| hit(x0, y0)), hit(x1, (y0 + y1) / 2.0).or_else(|| hit(x1, y1))) else {
                return Ok(json!({"gesture": "none"}));
            };
            s.sel = Selection { anchor: a, focus: b };
            Ok(json!({"gesture": "select"}))
        }
        Gesture::Bar => {
            let Some(at) = hit((x0 + x1) / 2.0, (y0 + y1) / 2.0) else { return Ok(json!({"gesture": "none"})) };
            s.sel = Selection::caret(at);
            s.run("text.insert", &json!({"text": " "}))?;
            done(s, "split")
        }
        Gesture::Cup => {
            // drawn under the gap: the text is just above the cup's top
            let lowest = pts.iter().copied().fold(first, |m, p| if p[1] > m[1] { p } else { m });
            let Some(at) = hit(lowest[0], y0 - line * 0.3) else { return Ok(json!({"gesture": "none"})) };
            let Some((a, b)) = gap(s, &at) else { return Ok(json!({"gesture": "none"})) };
            s.sel = Selection { anchor: a, focus: b };
            s.run("text.delete", &json!({}))?;
            done(s, "join")
        }
        Gesture::Caret => {
            let apex = pts.iter().copied().fold(first, |m, p| if p[1] < m[1] { p } else { m });
            let Some(at) = hit(apex[0], apex[1] - line * 0.3) else { return Ok(json!({"gesture": "none"})) };
            s.sel = Selection::caret(at);
            Ok(json!({"gesture": "insert"}))
        }
        Gesture::Corner => {
            let Some(at) = hit(first[0], first[1]) else { return Ok(json!({"gesture": "none"})) };
            s.sel = Selection::caret(at);
            s.run("text.newParagraph", &json!({}))?;
            done(s, "newParagraph")
        }
        Gesture::None => Ok(json!({"gesture": "none"})),
    }
}

/// The whole words from `a` to `b` (in either order), with the spaces after the last.
fn words(s: &Session, a: Option<Pos>, b: Option<Pos>) -> Option<(Pos, Pos)> {
    let (a, b) = (a?, b?);
    let (a, b) = if b < a { (b, a) } else { (a, b) };
    // a stroke that starts in the space before a word starts at that word
    let pa = s.doc.para_at(&a)?;
    let mut from = a.off.min(pa.text.len());
    while pa.text.get(from..).is_some_and(|t| t.starts_with(' ')) && (a.path != b.path || from < b.off) {
        from += 1;
    }
    let start = pa.word_at(from).0;
    let end = s.doc.para_at(&b).map(|p| p.word_at(b.off.saturating_sub(usize::from(b.off > 0 && b.off > a.off))).1)?;
    let (a, b) = (Pos { off: start, ..a }, Pos { off: end, ..b });
    (a < b).then_some((a, b))
}

/// The run of spaces at or beside `at` in its paragraph, if any.
fn gap(s: &Session, at: &Pos) -> Option<(Pos, Pos)> {
    let p = s.doc.para_at(at)?;
    let text = &p.text;
    let off = at.off.min(text.len());
    let mut a = off;
    while a > 0 && text[..a].ends_with(' ') {
        a -= 1;
    }
    let mut b = off;
    while text.get(b..).is_some_and(|t| t.starts_with(' ')) {
        b += 1;
    }
    (a < b).then(|| (Pos { off: a, ..at.clone() }, Pos { off: b, ..at.clone() }))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn line(from: [f32; 2], to: [f32; 2], n: usize) -> Vec<[f32; 2]> {
        (0..=n)
            .map(|i| {
                let t = i as f32 / n as f32;
                [from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t]
            })
            .collect()
    }

    #[test]
    fn shapes_are_read_against_the_line_height() {
        let l = 14.0;
        assert_eq!(classify(&line([10.0, 50.0], [90.0, 52.0], 20), l), Gesture::Strike);
        let zigzag: Vec<[f32; 2]> = (0..12).map(|i| [10.0 + (i % 2) as f32 * 40.0 + i as f32, 50.0 + (i % 3) as f32]).collect();
        assert_eq!(classify(&zigzag, l), Gesture::Scribble);
        let ring: Vec<[f32; 2]> = (0..=40)
            .map(|i| {
                let a = i as f32 / 40.0 * std::f32::consts::TAU;
                [50.0 + 30.0 * a.cos(), 50.0 + 12.0 * a.sin()]
            })
            .collect();
        assert_eq!(classify(&ring, l), Gesture::Loop);
        assert_eq!(classify(&line([50.0, 40.0], [51.0, 60.0], 10), l), Gesture::Bar);
        let cup = [line([40.0, 50.0], [50.0, 60.0], 5), line([50.0, 60.0], [60.0, 50.0], 5)].concat();
        assert_eq!(classify(&cup, l), Gesture::Cup);
        let hat = [line([40.0, 60.0], [50.0, 50.0], 5), line([50.0, 50.0], [60.0, 60.0], 5)].concat();
        assert_eq!(classify(&hat, l), Gesture::Caret);
        let corner = [line([50.0, 40.0], [50.0, 60.0], 5), line([50.0, 60.0], [20.0, 60.0], 5)].concat();
        assert_eq!(classify(&corner, l), Gesture::Corner);
        assert_eq!(classify(&[[10.0, 10.0], [11.0, 11.0]], l), Gesture::None, "a dot");
        assert_eq!(classify(&[], l), Gesture::None);
        assert_eq!(classify(&line([0.0, 0.0], [30.0, 30.0], 4), f32::NAN), Gesture::None, "a slant is nothing");
    }
}
