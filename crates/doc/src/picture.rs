//! A picture's frame (Picture Styles, Picture Border): the outline drawn around it and the shape
//! it is cut to (DrawingML `pic:spPr`'s `a:ln` and `a:prstGeom`, ECMA-376 Part 1 §20.1.2.2.24,
//! §20.1.9.18). The picture's pixels are never changed: the frame is drawn over them.

use serde::{Deserialize, Serialize};

use crate::props::Rgb;

/// Widest outline, points.
pub const MAX_LINE_WIDTH: f32 = 100.0;
/// Narrowest outline drawn, points.
pub const MIN_LINE_WIDTH: f32 = 0.25;

/// The lines an outline is drawn as (DrawingML `a:ln/@cmpd`): one, or two or three side by side
/// sharing its width.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Compound {
    #[default]
    Single,
    Double,
    ThickThin,
    ThinThick,
    Triple,
}

impl Compound {
    pub const ALL: [Compound; 5] = [Compound::Single, Compound::Double, Compound::ThickThin, Compound::ThinThick, Compound::Triple];

    /// Its `a:ln/@cmpd` value.
    pub fn id(self) -> &'static str {
        match self {
            Compound::Single => "sng",
            Compound::Double => "dbl",
            Compound::ThickThin => "thickThin",
            Compound::ThinThick => "thinThick",
            Compound::Triple => "tri",
        }
    }

    pub fn from_id(id: &str) -> Option<Compound> {
        Compound::ALL.into_iter().find(|c| c.id() == id)
    }

    /// The lines across the outline's width, from its outside in: where each starts and ends,
    /// as fractions of the width.
    pub fn lines(self) -> &'static [(f32, f32)] {
        match self {
            Compound::Single => &[(0.0, 1.0)],
            Compound::Double => &[(0.0, 0.34), (0.66, 1.0)],
            Compound::ThickThin => &[(0.0, 0.55), (0.75, 1.0)],
            Compound::ThinThick => &[(0.0, 0.25), (0.45, 1.0)],
            Compound::Triple => &[(0.0, 0.2), (0.35, 0.65), (0.8, 1.0)],
        }
    }
}

/// An outline: its colour, width (points) and lines. It is centred on the picture's edge, half
/// inside it and half out.
#[derive(Clone, Copy, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FrameLine {
    pub color: Rgb,
    pub width: f32,
    #[serde(default, skip_serializing_if = "is_single")]
    pub compound: Compound,
}

fn is_single(c: &Compound) -> bool {
    *c == Compound::Single
}

impl FrameLine {
    /// Finite and in range.
    pub fn sanitized(self) -> FrameLine {
        let width = if self.width.is_finite() { self.width.clamp(MIN_LINE_WIDTH, MAX_LINE_WIDTH) } else { 1.0 };
        FrameLine { width, ..self }
    }
}

/// A picture's frame. The default (no outline, the picture's rectangle) draws it plainly.
#[derive(Clone, Debug, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PictureFrame {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub line: Option<FrameLine>,
    /// The preset shape the picture is cut to (`a:prstGeom/@prst`, see [`crate::preset`]);
    /// `None`: its rectangle.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub shape: Option<String>,
}

impl PictureFrame {
    pub fn is_none(&self) -> bool {
        self.line.is_none() && self.shape.is_none()
    }

    /// The outline in range, and the shape only when it is a preset WordCraft draws other than
    /// the rectangle.
    pub fn sanitized(&self) -> PictureFrame {
        PictureFrame { line: self.line.map(FrameLine::sanitized), shape: self.shape.clone().filter(|p| is_picture_shape(p)) }
    }

    /// How far the outline reaches outside the picture's frame, points: half its width.
    pub fn reach(&self) -> f32 {
        self.line.map_or(0.0, |l| l.sanitized().width / 2.0)
    }
}

/// Whether a picture can be cut to preset `prst`: one WordCraft draws, other than the rectangle
/// (which is no cut) and the presets drawn only as lines.
pub fn is_picture_shape(prst: &str) -> bool {
    prst != "rect" && crate::preset::outline(prst, 100.0, 100.0, &[]).is_some_and(|o| !o.fill.is_empty())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn frames_sanitize() {
        let f = PictureFrame { line: Some(FrameLine { color: Rgb::BLACK, width: f32::NAN, compound: Compound::Double }), shape: Some("rect".into()) };
        let s = f.sanitized();
        assert_eq!(s.line.map(|l| l.width), Some(1.0));
        assert_eq!(s.shape, None, "a rectangle is no cut");
        assert_eq!(PictureFrame { shape: Some("nonsense".into()), ..Default::default() }.sanitized().shape, None);
        assert_eq!(PictureFrame { shape: Some("ellipse".into()), ..Default::default() }.sanitized().shape.as_deref(), Some("ellipse"));
        let wide = PictureFrame { line: Some(FrameLine { color: Rgb::WHITE, width: 1e9, compound: Compound::Single }), shape: None };
        assert_eq!(wide.reach(), MAX_LINE_WIDTH / 2.0);
    }

    #[test]
    fn compound_lines_fit_the_width() {
        for c in Compound::ALL {
            assert_eq!(Compound::from_id(c.id()), Some(c));
            let lines = c.lines();
            assert_eq!(lines.first().map(|l| l.0), Some(0.0));
            assert_eq!(lines.last().map(|l| l.1), Some(1.0));
            assert!(lines.windows(2).all(|w| w[0].1 < w[1].0), "{c:?}: gaps between the lines");
        }
    }
}
