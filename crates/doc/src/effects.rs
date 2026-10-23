//! Shape effects (DrawingML `a:effectLst`, ECMA-376 Part 1 §20.1.8): an outer shadow, a glow, a
//! reflection and soft edges. Lengths are points, angles degrees, transparencies percent (0 =
//! opaque).
//!
//! Renderers approximate the blurs with [`bands`]: a few copies of the shape's silhouette, grown
//! or shrunk by steps and overlaid with partial opacity, so the screen and PDF output (which has
//! no blur filter) match.

use serde::{Deserialize, Serialize};

use crate::props::Rgb;

/// Largest blur radius, points.
pub const MAX_BLUR: f32 = 100.0;
/// Largest shadow distance, points.
pub const MAX_DISTANCE: f32 = 200.0;
/// Largest glow size, points.
pub const MAX_GLOW: f32 = 150.0;
/// Largest soft-edge radius, points.
pub const MAX_SOFT_EDGE: f32 = 100.0;

/// The effects on a shape. Empty (the default) draws the shape plainly.
#[derive(Clone, Copy, Debug, PartialEq, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ShapeEffects {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub shadow: Option<Shadow>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub glow: Option<Glow>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub reflection: Option<Reflection>,
    /// A shadow inside the shape, as if it were a hole.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub inner_shadow: Option<InnerShadow>,
    /// Soft-edge radius, points: the shape fades out over this distance inside its outline.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub soft_edge: Option<f32>,
}

/// An outer shadow: a blurred copy of the shape's silhouette cast `distance` points away in
/// direction `angle` (degrees clockwise from the positive x axis, y pointing down).
///
/// `rot_with_shape` (DrawingML `rotWithShape`, true when absent) says whether that direction is
/// the shape's own, turning with it when it is rotated or flipped, or the page's. Word's shadow
/// presets write `rotWithShape="0"`: the light stays put as the shape turns.
#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Shadow {
    pub color: Rgb,
    pub transparency: f32,
    pub blur: f32,
    pub distance: f32,
    pub angle: f32,
    pub rot_with_shape: bool,
    /// A perspective shadow: the silhouette flattened to `scale_y` percent of its height from
    /// its bottom edge (negative: turned down below it) and leaning `skew_x` degrees (positive:
    /// its top to the right) before it is cast (DrawingML `sy`, `kx`).
    #[serde(skip_serializing_if = "is_hundred")]
    pub scale_y: f32,
    #[serde(skip_serializing_if = "is_zero_f")]
    pub skew_x: f32,
}

fn is_hundred(v: &f32) -> bool {
    *v == 100.0
}
fn is_zero_f(v: &f32) -> bool {
    *v == 0.0
}

impl Default for Shadow {
    /// "Offset: Bottom Right".
    fn default() -> Self {
        Shadow { color: Rgb::BLACK, transparency: 60.0, blur: 4.0, distance: 3.0, angle: 45.0, rot_with_shape: false, scale_y: 100.0, skew_x: 0.0 }
    }
}

/// An inner shadow: the shape's outside, blurred `blur` points and moved `distance` points in
/// direction `angle` (degrees clockwise from the positive x axis), showing inside it.
#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct InnerShadow {
    pub color: Rgb,
    pub transparency: f32,
    pub blur: f32,
    pub distance: f32,
    pub angle: f32,
}

impl Default for InnerShadow {
    /// "Inside: Top Left".
    fn default() -> Self {
        InnerShadow { color: Rgb::BLACK, transparency: 50.0, blur: 5.0, distance: 4.0, angle: 45.0 }
    }
}

/// The inner-shadow presets: id, label.
pub const INNER_SHADOW_PRESETS: [(&str, &str); 9] = [
    ("insideTopLeft", "Inside: Top Left"),
    ("insideTop", "Inside: Top"),
    ("insideTopRight", "Inside: Top Right"),
    ("insideLeft", "Inside: Left"),
    ("insideCenter", "Inside: Center"),
    ("insideRight", "Inside: Right"),
    ("insideBottomLeft", "Inside: Bottom Left"),
    ("insideBottom", "Inside: Bottom"),
    ("insideBottomRight", "Inside: Bottom Right"),
];

impl InnerShadow {
    /// A preset by id (see [`INNER_SHADOW_PRESETS`]): the shadow lies along the named edges.
    pub fn preset(id: &str) -> Option<InnerShadow> {
        let d = InnerShadow::default();
        // the outside moves away from the named edges, so its shadow falls on them
        let angle = match id {
            "insideTopLeft" => 45.0,
            "insideTop" => 90.0,
            "insideTopRight" => 135.0,
            "insideRight" => 180.0,
            "insideBottomRight" => 225.0,
            "insideBottom" => 270.0,
            "insideBottomLeft" => 315.0,
            "insideLeft" => 0.0,
            "insideCenter" => return Some(InnerShadow { distance: 0.0, blur: 9.0, ..d }),
            _ => return None,
        };
        Some(InnerShadow { angle, ..d })
    }
    /// Finite, in range.
    pub fn sanitized(self) -> InnerShadow {
        let angle = clamp(self.angle, -1e6, 1e6, 45.0).rem_euclid(360.0);
        InnerShadow {
            color: self.color,
            transparency: clamp(self.transparency, 0.0, 100.0, 50.0),
            blur: clamp(self.blur, 0.0, MAX_BLUR, 0.0),
            distance: clamp(self.distance, 0.0, MAX_DISTANCE, 0.0),
            angle: if angle < 360.0 { angle } else { 0.0 },
        }
    }
    /// The offset of the outside, points (x right, y down).
    pub fn offset(&self) -> (f32, f32) {
        let s = self.sanitized();
        let a = s.angle.to_radians();
        (s.distance * a.cos(), s.distance * a.sin())
    }
    /// Opacity 0..1.
    pub fn opacity(&self) -> f32 {
        1.0 - self.sanitized().transparency / 100.0
    }
}

/// A glow: a soft band of colour `size` points wide around the shape's outline.
#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Glow {
    pub color: Rgb,
    pub size: f32,
    pub transparency: f32,
}

impl Default for Glow {
    fn default() -> Self {
        Glow { color: Rgb(0x15, 0x60, 0x82), size: 5.0, transparency: 60.0 }
    }
}

/// A reflection: the shape mirrored below its bottom edge, `distance` points away, fading from
/// `start` percent opaque at the edge to `end` percent `size` percent of the shape's height down
/// (DrawingML `a:reflection`: `stA`, `endA`, `endPos`, `dist`, `blurRad`).
#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Reflection {
    pub start: f32,
    pub end: f32,
    pub size: f32,
    pub distance: f32,
    pub blur: f32,
}

impl Default for Reflection {
    /// "Tight Reflection: Touching".
    fn default() -> Self {
        Reflection { start: 52.0, end: 0.3, size: 35.0, distance: 0.0, blur: 0.5 }
    }
}

/// The reflection presets, three sizes (tight, half, full) at three distances (touching, 4 pt
/// and 8 pt): id, label.
pub const REFLECTION_PRESETS: [(&str, &str); 9] = [
    ("tightTouching", "Tight Reflection: Touching"),
    ("halfTouching", "Half Reflection: Touching"),
    ("fullTouching", "Full Reflection: Touching"),
    ("tight4pt", "Tight Reflection: 4 pt offset"),
    ("half4pt", "Half Reflection: 4 pt offset"),
    ("full4pt", "Full Reflection: 4 pt offset"),
    ("tight8pt", "Tight Reflection: 8 pt offset"),
    ("half8pt", "Half Reflection: 8 pt offset"),
    ("full8pt", "Full Reflection: 8 pt offset"),
];

impl Reflection {
    /// A preset by id (see [`REFLECTION_PRESETS`]).
    pub fn preset(id: &str) -> Option<Reflection> {
        let (size, rest) = if let Some(r) = id.strip_prefix("tight") {
            (35.0, r)
        } else if let Some(r) = id.strip_prefix("half") {
            (55.0, r)
        } else {
            (90.0, id.strip_prefix("full")?)
        };
        let distance = match rest {
            "Touching" => 0.0,
            "4pt" => 4.0,
            "8pt" => 8.0,
            _ => return None,
        };
        let start = if size > 60.0 { 50.0 } else { 52.0 };
        Some(Reflection { start, size, distance, ..Reflection::default() })
    }
    /// Finite, in range.
    pub fn sanitized(self) -> Reflection {
        Reflection {
            start: clamp(self.start, 0.0, 100.0, 52.0),
            end: clamp(self.end, 0.0, 100.0, 0.0),
            size: clamp(self.size, 0.0, 100.0, 35.0),
            distance: clamp(self.distance, 0.0, MAX_DISTANCE, 0.0),
            blur: clamp(self.blur, 0.0, MAX_BLUR, 0.0),
        }
    }
    /// How far below the shape the reflection reaches, points, for a shape `h` points tall.
    pub fn reach(&self, h: f32) -> f32 {
        let r = self.sanitized();
        r.distance + r.size / 100.0 * clamp(h, 0.0, 1e5, 0.0)
    }
}

fn clamp(v: f32, lo: f32, hi: f32, dflt: f32) -> f32 {
    if v.is_finite() { v.clamp(lo, hi) } else { dflt }
}

/// The perspective-shadow presets: id, label.
pub const PERSPECTIVE_SHADOW_PRESETS: [(&str, &str); 5] = [
    ("perspectiveUpperLeft", "Perspective: Upper Left"),
    ("perspectiveUpperRight", "Perspective: Upper Right"),
    ("perspectiveBelow", "Perspective: Below"),
    ("perspectiveLowerLeft", "Perspective: Lower Left"),
    ("perspectiveLowerRight", "Perspective: Lower Right"),
];

/// The outer-shadow presets: id, label.
pub const SHADOW_PRESETS: [(&str, &str); 9] = [
    ("offsetBottomRight", "Offset: Bottom Right"),
    ("offsetBottom", "Offset: Bottom"),
    ("offsetBottomLeft", "Offset: Bottom Left"),
    ("offsetRight", "Offset: Right"),
    ("offsetCenter", "Offset: Center"),
    ("offsetLeft", "Offset: Left"),
    ("offsetTopRight", "Offset: Top Right"),
    ("offsetTop", "Offset: Top"),
    ("offsetTopLeft", "Offset: Top Left"),
];

impl Shadow {
    /// A preset by id (see [`SHADOW_PRESETS`]).
    pub fn preset(id: &str) -> Option<Shadow> {
        let d = Shadow::default();
        let angle = match id {
            "offsetBottomRight" => 45.0,
            "offsetBottom" => 90.0,
            "offsetBottomLeft" => 135.0,
            "offsetLeft" => 180.0,
            "offsetTopLeft" => 225.0,
            "offsetTop" => 270.0,
            "offsetTopRight" => 315.0,
            "offsetRight" => 0.0,
            "offsetCenter" => return Some(Shadow { distance: 0.0, blur: 5.0, ..d }),
            // flattened behind the shape, leaning back left or right; under it; or turned down
            // in front of it, leaning
            "perspectiveUpperLeft" => return Some(Shadow { distance: 0.0, blur: 6.0, scale_y: 23.0, skew_x: -45.0, transparency: 70.0, ..d }),
            "perspectiveUpperRight" => return Some(Shadow { distance: 0.0, blur: 6.0, scale_y: 23.0, skew_x: 45.0, transparency: 70.0, ..d }),
            "perspectiveBelow" => {
                return Some(Shadow { distance: 6.0, angle: 90.0, blur: 8.0, scale_y: -15.0, skew_x: 0.0, transparency: 75.0, ..d });
            }
            "perspectiveLowerLeft" => return Some(Shadow { distance: 0.0, blur: 6.0, scale_y: -23.0, skew_x: 45.0, transparency: 70.0, ..d }),
            "perspectiveLowerRight" => return Some(Shadow { distance: 0.0, blur: 6.0, scale_y: -23.0, skew_x: -45.0, transparency: 70.0, ..d }),
            _ => return None,
        };
        Some(Shadow { angle, ..d })
    }
    /// Finite, in range.
    pub fn sanitized(self) -> Shadow {
        let angle = clamp(self.angle, -1e6, 1e6, 45.0).rem_euclid(360.0);
        Shadow {
            color: self.color,
            transparency: clamp(self.transparency, 0.0, 100.0, 60.0),
            blur: clamp(self.blur, 0.0, MAX_BLUR, 0.0),
            distance: clamp(self.distance, 0.0, MAX_DISTANCE, 0.0),
            angle: if angle.is_finite() && angle < 360.0 { angle } else { 0.0 },
            rot_with_shape: self.rot_with_shape,
            scale_y: clamp(self.scale_y, -100.0, 100.0, 100.0),
            skew_x: clamp(self.skew_x, -80.0, 80.0, 0.0),
        }
    }
    /// Flattened or leaning (a perspective shadow).
    pub fn is_perspective(&self) -> bool {
        let s = self.sanitized();
        s.scale_y != 100.0 || s.skew_x != 0.0
    }
    /// Where point (`x`, `y`) of a shape whose bottom edge is at `bottom` lands in its perspective
    /// shadow (before the shadow's offset).
    pub fn project(&self, x: f32, y: f32, bottom: f32) -> (f32, f32) {
        let s = self.sanitized();
        let y2 = bottom + (y - bottom) * s.scale_y / 100.0;
        (x + (bottom - y2) * s.skew_x.to_radians().tan(), y2)
    }
    /// The shadow's offset from the shape, points (x right, y down).
    pub fn offset(&self) -> (f32, f32) {
        let s = self.sanitized();
        let a = s.angle.to_radians();
        (s.distance * a.cos(), s.distance * a.sin())
    }
    /// Opacity 0..1.
    pub fn opacity(&self) -> f32 {
        1.0 - self.sanitized().transparency / 100.0
    }
}

impl Glow {
    /// Finite, in range.
    pub fn sanitized(self) -> Glow {
        Glow { color: self.color, size: clamp(self.size, 0.0, MAX_GLOW, 0.0), transparency: clamp(self.transparency, 0.0, 100.0, 60.0) }
    }
    /// Opacity 0..1.
    pub fn opacity(&self) -> f32 {
        1.0 - self.sanitized().transparency / 100.0
    }
}

impl ShapeEffects {
    pub fn is_empty(&self) -> bool {
        self.shadow.is_none() && self.glow.is_none() && self.reflection.is_none() && self.inner_shadow.is_none() && self.soft_edge.is_none()
    }
    /// Finite, in range; zero-size glows, reflections and soft edges dropped.
    pub fn sanitized(self) -> ShapeEffects {
        ShapeEffects {
            shadow: self.shadow.map(Shadow::sanitized),
            glow: self.glow.map(Glow::sanitized).filter(|g| g.size > 0.0),
            reflection: self.reflection.map(Reflection::sanitized).filter(|r| r.size > 0.0),
            inner_shadow: self.inner_shadow.map(InnerShadow::sanitized),
            soft_edge: self.soft_edge.map(|r| clamp(r, 0.0, MAX_SOFT_EDGE, 0.0)).filter(|r| *r > 0.0),
        }
    }
    /// How far the effects reach beyond the shape's frame on each side (left, top, right,
    /// bottom), points — what `wp:effectExtent` records.
    pub fn extent(&self) -> [f32; 4] {
        let e = self.sanitized();
        let mut out = [0.0f32; 4];
        if let Some(g) = e.glow {
            out = [g.size; 4];
        }
        if let Some(s) = e.shadow {
            let (dx, dy) = s.offset();
            let r = s.blur / 2.0;
            let reach = [r - dx, r - dy, r + dx, r + dy];
            for (o, v) in out.iter_mut().zip(reach) {
                *o = o.max(v.max(0.0));
            }
        }
        out.map(|v| (v * 100.0).round() / 100.0)
    }
    /// [`ShapeEffects::extent`] for a shape `h` points tall: a reflection reaches below it too.
    pub fn extent_for(&self, h: f32) -> [f32; 4] {
        let mut out = self.extent();
        if let Some(r) = self.sanitized().reflection {
            out[3] = out[3].max((r.reach(h) * 100.0).round() / 100.0);
        }
        out
    }
}

/// Bands approximating a blurred edge: `(grow, alpha)` pairs. Each is a copy of the silhouette
/// grown outward by `grow` points (negative shrinks it), filled with opacity `alpha`; overlaid in
/// any order, in one colour, they ramp linearly from `opacity` at `from` to nothing at `to`.
pub fn bands(from: f32, to: f32, opacity: f32) -> Vec<(f32, f32)> {
    let (from, to) = (clamp(from, -1000.0, 1000.0, 0.0), clamp(to, -1000.0, 1000.0, 0.0));
    let opacity = clamp(opacity, 0.0, 1.0, 0.0);
    if opacity <= 0.0 {
        return Vec::new();
    }
    let span = (to - from).max(0.0);
    if span < 0.25 {
        return vec![((from + to) / 2.0, opacity)];
    }
    let n = ((span / 1.5).ceil() as usize).clamp(3, 12);
    let nf = n as f32;
    (1..=n)
        .map(|m| {
            // The m-th band from the outside: after it, coverage is opacity·m/n.
            let grow = to - span * (m as f32 - 0.5) / nf;
            let before = 1.0 - opacity * (m as f32 - 1.0) / nf;
            let after = 1.0 - opacity * m as f32 / nf;
            let alpha = if before > 0.0 { 1.0 - after / before } else { 1.0 };
            (grow, alpha.clamp(0.0, 1.0))
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bands_ramp_to_the_requested_opacity() {
        for (from, to, o) in [(-2.0, 2.0, 0.4), (-5.0, 0.0, 1.0), (0.0, 8.0, 0.6)] {
            let b = bands(from, to, o);
            assert!(b.iter().all(|(g, a)| *g > from && *g < to && (0.0..=1.0).contains(a)));
            let covered = 1.0 - b.iter().map(|(_, a)| 1.0 - a).product::<f32>();
            assert!((covered - o).abs() < 1e-4, "{covered} vs {o}");
        }
        assert_eq!(bands(0.0, 0.0, 0.5), vec![(0.0, 0.5)]);
        assert!(bands(f32::NAN, f32::INFINITY, f32::NAN).is_empty());
    }

    #[test]
    fn hostile_numbers_are_clamped() {
        let s = Shadow { transparency: f32::NAN, blur: 1e9, distance: -4.0, angle: -90.0, scale_y: f32::NAN, ..Shadow::default() }.sanitized();
        assert_eq!(s.scale_y, 100.0);
        assert_eq!((s.transparency, s.blur, s.distance, s.angle), (60.0, MAX_BLUR, 0.0, 270.0));
        let e = ShapeEffects { glow: Some(Glow { size: 1e9, ..Glow::default() }), soft_edge: Some(-1.0), ..Default::default() }.sanitized();
        assert_eq!(e.glow.map(|g| g.size), Some(MAX_GLOW));
        assert_eq!(e.soft_edge, None);
        let e = ShapeEffects { glow: Some(Glow { size: f32::INFINITY, ..Glow::default() }), ..Default::default() }.sanitized();
        assert!(e.is_empty(), "a non-finite size is no glow");
        let (dx, dy) = Shadow::preset("offsetBottomRight").unwrap().offset();
        assert!(dx > 2.0 && dy > 2.0);
        let r = Reflection { start: f32::NAN, size: 400.0, distance: -3.0, ..Reflection::default() }.sanitized();
        assert_eq!((r.start, r.size, r.distance), (52.0, 100.0, 0.0));
    }

    #[test]
    fn inner_and_perspective_presets() {
        for (id, _) in INNER_SHADOW_PRESETS {
            assert!(InnerShadow::preset(id).is_some(), "{id}");
        }
        for (id, _) in PERSPECTIVE_SHADOW_PRESETS {
            let s = Shadow::preset(id).unwrap();
            assert!(s.is_perspective(), "{id}");
        }
        assert!(!Shadow::preset("offsetTop").unwrap().is_perspective());
        // the top-left inner shadow: the outside moves down and right
        let (dx, dy) = InnerShadow::preset("insideTopLeft").unwrap().offset();
        assert!(dx > 0.0 && dy > 0.0);
        // flattened from the bottom edge, leaning right
        let s = Shadow { scale_y: 50.0, skew_x: 45.0, ..Shadow::default() };
        let (x, y) = s.project(0.0, 0.0, 100.0);
        assert_eq!(y, 50.0);
        assert!((x - 50.0).abs() < 0.01);
        assert_eq!(s.project(10.0, 100.0, 100.0), (10.0, 100.0), "the bottom edge stays");
    }

    #[test]
    fn reflection_presets_grow_and_reach_below() {
        for (id, _) in REFLECTION_PRESETS {
            assert!(Reflection::preset(id).is_some(), "{id}");
        }
        assert_eq!(Reflection::preset("half"), None);
        let (tight, full8) = (Reflection::preset("tightTouching").unwrap(), Reflection::preset("full8pt").unwrap());
        assert!(tight.size < full8.size && full8.distance == 8.0);
        let e = ShapeEffects { reflection: Some(full8), ..Default::default() };
        assert_eq!(e.extent(), [0.0; 4], "the extent of the shadow and glow alone");
        assert_eq!(e.extent_for(100.0)[3], 98.0);
    }
}
