//! Host tests of the bridge: what the Android app relies on, without a phone.

use super::*;
use wordcraft_engine::doc::StoryRef;

fn body_text(m: &MobileSession) -> String {
    m.lock().doc.plain_text(StoryRef::Body).trim_end_matches('\n').to_string()
}

#[test]
fn a_new_session_has_one_blank_page() {
    let m = MobileSession::default();
    let info = m.document_info();
    assert_eq!(info.pages.len(), 1);
    assert!(info.pages[0].width > 500.0 && info.pages[0].height > 700.0, "{:?}", info.pages[0]);
    assert!(!info.dirty && !info.can_undo);
}

#[test]
fn the_sample_opens_and_its_pages_draw() {
    let m = MobileSession::default();
    let info = m.new_document("sample".into()).unwrap();
    assert!(!info.pages.is_empty());
    let page = info.pages[0];
    let img = m.render_page(0, 1.0).unwrap();
    assert_eq!(img.rgba.len(), img.width as usize * img.height as usize * 4);
    assert!((img.width as f32 - page.width).abs() <= 2.0, "{} vs {}", img.width, page.width);
    // opaque paper (anti-aliasing may round a little below 255), and something drawn on it
    let (pixels, _) = img.rgba.as_chunks::<4>();
    let min_alpha = pixels.iter().map(|p| p[3]).min().unwrap_or(0);
    assert!(min_alpha >= 250, "min alpha {min_alpha}");
    assert!(pixels.iter().any(|p| p[0] < 128));
}

#[test]
fn a_tile_has_the_size_asked_for() {
    let m = MobileSession::default();
    m.new_document("sample".into()).unwrap();
    let img = m.render_area(0, 72.0, 72.0, 100.0, 50.0, 2.0).unwrap();
    assert_eq!((img.width, img.height), (200, 100));
}

#[test]
fn bad_pages_and_scales_are_errors_or_clamped_never_panics() {
    let m = MobileSession::default();
    assert!(matches!(m.render_page(7, 1.0), Err(MobileError::NoPage(8))));
    for scale in [f32::NAN, f32::INFINITY, -3.0, 0.0, 1e9] {
        let img = m.render_page(0, scale).unwrap();
        assert!(img.width >= 1 && img.width <= 16_000);
    }
    assert!(m.render_area(0, f32::NAN, -1e9, f32::INFINITY, -5.0, 1.0).is_ok());
}

#[test]
fn typing_inserts_text_and_moves_the_caret() {
    let m = MobileSession::default();
    m.type_text("Hello world".into()).unwrap();
    assert_eq!(body_text(&m), "Hello world");
    let view = m.selection_view();
    assert!(view.collapsed && view.rects.is_empty());
    let caret = view.focus.unwrap();
    assert_eq!(caret.page, 0);
    assert!(caret.x > 72.0 && caret.height > 5.0, "{caret:?}");
    let info = m.document_info();
    assert!(info.dirty && info.can_undo);
}

#[test]
fn a_new_line_starts_a_paragraph_and_autocorrect_runs_on_committed_text() {
    let m = MobileSession::default();
    // a keyboard commits whole words: sentence capitals and smart quotes still apply
    m.type_text("one\r\ntwo \"three\"".into()).unwrap();
    assert_eq!(body_text(&m), "One\nTwo \u{201c}three\u{201d}");
}

#[test]
fn deleting_around_the_caret_and_undo() {
    let m = MobileSession::default();
    m.type_text("abcdef".into()).unwrap();
    m.execute("caret.left".into(), String::new()).unwrap();
    m.delete_around(2, 1).unwrap();
    assert_eq!(body_text(&m), "abc");
    m.execute("edit.undo".into(), String::new()).unwrap();
    assert_ne!(body_text(&m), "abc");
}

#[test]
fn select_all_shows_rectangles_and_both_ends() {
    let m = MobileSession::default();
    m.type_text("A line of text\nand another".into()).unwrap();
    m.execute("select.all".into(), String::new()).unwrap();
    let view = m.selection_view();
    assert!(!view.collapsed);
    assert!(view.rects.len() >= 2, "{:?}", view.rects);
    assert!(view.anchor.is_some() && view.focus.is_some());
}

#[test]
fn a_tap_moves_the_caret_and_a_double_tap_selects_the_word() {
    let m = MobileSession::default();
    m.type_text("alpha beta gamma".into()).unwrap();
    let start = m.selection_view().focus.unwrap();
    // a point in the middle of the line: on "beta"
    let mid_x = 72.0 + (start.x - 72.0) / 2.0;
    let mid_y = start.top + start.height / 2.0;
    m.tap(0, 72.0, mid_y, false).unwrap();
    assert!(m.selection_view().focus.unwrap().x < start.x);
    m.select_at(0, mid_x, mid_y, "word".into()).unwrap();
    let around = m.text_around(100, 100);
    assert_eq!(around.selected.trim(), "beta");
    assert_eq!(around.before, "Alpha ");
}

#[test]
fn dragging_either_handle_moves_only_that_end() {
    let m = MobileSession::default();
    m.type_text("alpha beta gamma delta".into()).unwrap();
    let end = m.selection_view().focus.unwrap();
    let y = end.top + end.height / 2.0;
    let x_of = |frac: f32| 72.0 + (end.x - 72.0) * frac;
    m.select_at(0, x_of(0.4), y, "word".into()).unwrap();
    assert_eq!(m.text_around(0, 0).selected.trim(), "beta");
    // the focus handle to the end of the line: "beta gamma delta"
    m.set_selection_end("focus".into(), 0, end.x, y).unwrap();
    assert!(m.text_around(0, 0).selected.trim().ends_with("delta"));
    // the anchor handle to the start: the whole line
    m.set_selection_end("anchor".into(), 0, 72.0, y).unwrap();
    assert_eq!(m.text_around(0, 0).selected, "Alpha beta gamma delta");
}

#[test]
fn text_around_counts_utf16_units_and_never_splits_a_character() {
    let m = MobileSession::default();
    m.type_text("a\u{1F600}b\u{e9}c".into()).unwrap();
    m.execute("caret.left".into(), String::new()).unwrap();
    m.execute("caret.left".into(), String::new()).unwrap();
    let t = m.text_around(2, 5);
    // the emoji is two UTF-16 units: "b" plus the emoji would be three
    assert_eq!(t.before, "b");
    assert_eq!(t.after, "\u{e9}c");
    assert_eq!(last_utf16("a\u{1F600}", 2), "\u{1F600}");
    assert_eq!(first_utf16("\u{1F600}a", 1), "");
}

#[test]
fn saving_as_docx_round_trips_and_marks_the_document_saved() {
    let m = MobileSession::default();
    m.type_text("Saved on a phone".into()).unwrap();
    let bytes = m.save_bytes("note.docx".into()).unwrap();
    assert!(bytes.starts_with(b"PK"));
    assert!(!m.document_info().dirty);
    let doc = io::open_bytes("note.docx", &bytes).unwrap();
    assert!(doc.plain_text(StoryRef::Body).contains("Saved on a phone"));
    // a PDF is an export: the document still has unsaved changes after one
    m.type_text(" again".into()).unwrap();
    let pdf = m.save_bytes("note.pdf".into()).unwrap();
    assert!(pdf.starts_with(b"%PDF"));
    assert!(m.document_info().dirty);
}

#[test]
fn read_aloud_gives_sentences_and_where_each_is() {
    let m = MobileSession::default();
    m.type_text("First sentence here. Second one follows! A third?".into()).unwrap();
    let all = m.read_aloud_sentences(true);
    assert_eq!(all.len(), 3, "{all:?}");
    assert!(all[0].starts_with("First") && all[2].contains("third"));
    let rects = m.read_aloud_rects(1);
    assert!(!rects.is_empty());
    assert!(m.read_aloud_rects(99).is_empty());
    // from the caret: the caret is at the end, so nothing is left to read
    assert!(m.read_aloud_sentences(false).len() <= 1);
}

#[test]
fn exporting_a_copy_leaves_the_document_unsaved() {
    let m = MobileSession::default();
    m.type_text("Shared, not saved".into()).unwrap();
    let pdf = m.export_bytes("copy.pdf".into()).unwrap();
    assert!(pdf.starts_with(b"%PDF"));
    let docx = m.export_bytes("copy.docx".into()).unwrap();
    assert!(docx.starts_with(b"PK"));
    assert!(m.document_info().dirty);
}

#[test]
fn opening_a_saved_file_and_a_missing_one() {
    let dir = std::env::temp_dir().join(format!("wordcraft-android-test-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let path = dir.join("letter.docx");
    let m = MobileSession::default();
    m.type_text("Dear reader".into()).unwrap();
    std::fs::write(&path, m.save_bytes("letter.docx".into()).unwrap()).unwrap();

    let other = MobileSession::default();
    let info = other.open(path.to_string_lossy().into_owned(), None).unwrap();
    assert_eq!(info.pages.len(), 1);
    assert_eq!(body_text(&other), "Dear reader");
    assert!(matches!(other.open(dir.join("nope.docx").to_string_lossy().into_owned(), None), Err(MobileError::Open(_))));
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn hostile_calls_are_errors_not_panics() {
    let m = MobileSession::default();
    assert!(m.execute("no.such.command".into(), String::new()).is_err());
    assert!(m.execute("text.insert".into(), "{not json".into()).is_err());
    assert!(m.execute("text.insert".into(), "[1,2]".into()).is_err());
    assert!(m.tap(99, f32::NAN, f32::INFINITY, true).is_err() || m.selection_view().focus.is_some());
    assert!(m.select_at(0, -1e30, 1e30, "paragraph".into()).is_ok() || m.selection_view().focus.is_some());
    // an unknown template falls back to a blank document
    assert_eq!(m.new_document("no-such-template".into()).map(|i| i.pages.len()).ok(), Some(1));
    assert!(m.disabled_reason("no.such".into()).is_some());
    assert!(m.delete_around(u32::MAX, u32::MAX).is_ok());
    let _ = m.text_around(u32::MAX, u32::MAX);
}

#[test]
fn the_format_state_follows_the_selection() {
    let m = MobileSession::default();
    let plain = m.format_state();
    assert!(!plain.bold && !plain.bullets && plain.align == "left");
    assert!(plain.size.is_some_and(|s| s > 5.0));
    m.execute("format.bold".into(), String::new()).unwrap();
    m.type_text("Strong".into()).unwrap();
    m.execute("para.alignCenter".into(), String::new()).unwrap();
    m.execute("para.bullets".into(), String::new()).unwrap();
    let s = m.format_state();
    assert!(s.bold && s.bullets && !s.numbering, "{s:?}");
    assert_eq!(s.align, "center");
    m.execute("para.lineSpacing".into(), r#"{"value":1.5}"#.into()).unwrap();
    m.execute("para.indents".into(), r#"{"firstLine":-36}"#.into()).unwrap();
    let s = m.format_state();
    assert_eq!(s.line_spacing, "1.5");
    assert_eq!(s.first_line, -36.0);
    m.execute("para.lineSpacing".into(), r#"{"exactly":12}"#.into()).unwrap();
    assert_eq!(m.format_state().line_spacing, "Exactly 12 pt");
    assert_eq!(spacing_label(wordcraft_engine::doc::props::LineSpacing::Multiple(1.08)), "1.08");
    assert_eq!(spacing_label(wordcraft_engine::doc::props::LineSpacing::Multiple(1.0)), "1.0");
    // asking doesn't break Repeat or the typing group
    m.type_text("er".into()).unwrap();
    m.execute("edit.undo".into(), String::new()).unwrap();
    assert_eq!(body_text(&m), "Strong");
}

#[test]
fn mobile_view_reflows_to_the_width_and_print_brings_the_pages_back() {
    let m = MobileSession::default();
    let pages = m.new_document("sample".into()).unwrap().pages.len();
    let mobile = m.set_view("mobile".into(), 300.0).unwrap();
    assert_eq!(m.view_mode(), "mobile");
    assert_eq!(mobile.pages.len(), 1, "one long page");
    assert!((mobile.pages[0].width - 300.0).abs() < 1.0, "{:?}", mobile.pages[0]);
    assert!(mobile.pages[0].height > 792.0);
    // a narrower phone reflows again
    let narrower = m.set_view("mobile".into(), 250.0).unwrap();
    assert!(narrower.pages[0].height > mobile.pages[0].height);
    // a tile of the long page draws
    let tile = m.render_area(0, 0.0, 400.0, 250.0, 100.0, 2.0).unwrap();
    assert_eq!((tile.width, tile.height), (500, 200));
    let print = m.set_view("print".into(), 0.0).unwrap();
    assert_eq!(m.view_mode(), "print");
    assert_eq!(print.pages.len(), pages);
    assert!(m.set_view("mobile".into(), f32::NAN).is_ok());
}

#[test]
fn colour_choices_are_ones_the_commands_take() {
    let m = MobileSession::default();
    let c = m.color_choices();
    assert_eq!(c.standard.len(), 10);
    assert_eq!(c.highlights.len(), 15);
    assert!(c.theme.iter().all(|row| row.len() == 10), "{:?}", c.theme);
    m.type_text("Coloured".into()).unwrap();
    m.execute("select.all".into(), String::new()).unwrap();
    for h in &c.highlights {
        m.execute("format.highlight".into(), json!({ "color": h.name }).to_string()).unwrap();
        assert_eq!(m.format_state().highlight.as_deref(), Some(h.hex.as_str()), "{}", h.name);
    }
    let red = &c.standard[1];
    m.execute("format.color".into(), json!({ "color": red.name }).to_string()).unwrap();
    assert_eq!(m.format_state().color.as_deref(), Some("FF0000"));
}

#[test]
fn page_setup_and_review_state_follow_the_commands() {
    let m = MobileSession::default();
    let p = m.page_setup();
    assert!(p.width > 500.0 && p.height > 700.0 && !p.landscape, "{p:?}");
    m.execute("layout.margins".into(), r#"{"preset":"narrow"}"#.into()).unwrap();
    assert_eq!(m.page_setup().margin_left, 36.0);
    m.execute("layout.orientation".into(), r#"{"value":"landscape"}"#.into()).unwrap();
    let l = m.page_setup();
    assert!(l.landscape && l.width > l.height, "{l:?}");
    let r = m.review_state();
    assert!(!r.track_changes && r.comments == 0);
    m.execute("review.trackChanges".into(), r#"{"value":true}"#.into()).unwrap();
    m.type_text("tracked".into()).unwrap();
    let r = m.review_state();
    assert!(r.track_changes && r.revisions > 0, "{r:?}");
}

#[test]
fn tables_objects_and_headers_show_in_the_state() {
    let m = MobileSession::default();
    assert!(!m.format_state().in_table && m.format_state().story == "body");
    m.execute("insert.table".into(), r#"{"rows":2,"cols":2}"#.into()).unwrap();
    assert!(m.format_state().in_table);
    m.execute("caret.docEnd".into(), String::new()).unwrap();
    m.execute("insert.shape".into(), r#"{"kind":"rectangle"}"#.into()).unwrap();
    let s = m.format_state();
    assert_eq!(s.object.as_deref(), Some("shape"), "{s:?}");
    let frame = m.selection_view().object_frame.expect("a frame");
    assert!(frame.width > 10.0 && frame.height > 10.0);
    // tapping the shape's middle selects it again
    m.execute("caret.docStart".into(), String::new()).unwrap();
    assert!(m.format_state().object.is_none());
    m.tap(frame.page, frame.x + frame.width / 2.0, frame.y + frame.height / 2.0, false).unwrap();
    assert_eq!(m.format_state().object.as_deref(), Some("shape"));
    m.execute("insert.editHeader".into(), String::new()).unwrap();
    assert_eq!(m.format_state().story, "header");
}

#[test]
fn a_tap_selects_a_text_box_and_edit_text_goes_inside() {
    let m = MobileSession::default();
    m.execute("insert.textBox".into(), r#"{"text":"Inside","width":200,"height":80}"#.into()).unwrap();
    // inserting leaves the caret in the box's text: back to the body
    m.execute("caret.set".into(), r#"{"pos":{"block":0,"off":0}}"#.into()).unwrap();
    assert_eq!(m.format_state().story, "body");
    // where the box is on the page, from the layout
    let frame = {
        let mut s = m.lock();
        let layout = s.layout();
        let o = layout.find_object(0, |o| o.text_box.is_some()).expect("a text box on the page");
        PageRect { page: page_index(o.page), x: o.rect.x, y: o.rect.y, width: o.rect.w, height: o.rect.h }
    };
    assert!(m.format_state().object.is_none());
    m.tap(frame.page, frame.x + frame.width / 2.0, frame.y + frame.height / 2.0, false).unwrap();
    assert!(m.format_state().object.is_some(), "the first tap selects the box");
    assert!(m.edit_object_text());
    assert_eq!(m.format_state().story, "textBox");
    // a tap inside its text now stays in the text
    m.tap(frame.page, frame.x + frame.width / 2.0, frame.y + frame.height / 2.0, false).unwrap();
    assert_eq!(m.format_state().story, "textBox");
}

#[test]
fn drawings_are_found_and_footnotes_are_not_drawings() {
    let m = MobileSession::default();
    m.type_text("Plain text".into()).unwrap();
    m.execute("references.footnote".into(), r#"{"text":"a note"}"#.into()).unwrap();
    assert!(!m.has_drawings());
    m.execute("caret.set".into(), r#"{"pos":{"block":0,"off":0}}"#.into()).unwrap();
    m.execute("insert.shape".into(), r#"{"kind":"ellipse"}"#.into()).unwrap();
    // in front of the text it stays where it is in Mobile View; wrapped, it moves
    m.execute("arrange.wrap".into(), r#"{"wrap":"inFrontOfText"}"#.into()).unwrap();
    assert!(!m.has_drawings());
    m.execute("arrange.wrap".into(), r#"{"wrap":"square"}"#.into()).unwrap();
    assert!(m.has_drawings());
}

#[test]
fn keyboard_shortcuts_go_through_the_key_map() {
    let m = MobileSession::default();
    m.type_text("Shortcut".into()).unwrap();
    assert!(m.run_shortcut("Mod+A".into()).unwrap());
    assert!(m.run_shortcut("Mod+B".into()).unwrap());
    assert!(m.format_state().bold);
    assert!(m.run_shortcut("Mod+Z".into()).unwrap());
    assert!(!m.format_state().bold);
    assert!(!m.run_shortcut("Mod+Alt+Shift+F13".into()).unwrap());
    assert!(!m.run_shortcut(String::new()).unwrap());
}

#[test]
fn dark_pages_are_near_black_with_light_text() {
    let m = MobileSession::default();
    m.type_text("Night".into()).unwrap();
    let paper = |img: &PageImage| img.rgba.get(..4).map(<[u8]>::to_vec);
    assert_eq!(paper(&m.render_page(0, 1.0).unwrap()), Some(vec![255, 255, 255, 255]));
    m.set_dark(true);
    let img = m.render_page(0, 1.0).unwrap();
    assert_eq!(paper(&img), Some(vec![DARK_PAPER, DARK_PAPER, DARK_PAPER, 255]));
    let (pixels, _) = img.rgba.as_chunks::<4>();
    assert!(pixels.iter().any(|p| p[0] > 200), "the text turns light");
    m.set_dark(false);
    assert_eq!(paper(&m.render_page(0, 1.0).unwrap()), Some(vec![255, 255, 255, 255]));
}

#[test]
fn a_picture_s_shadow_stays_dark_on_a_dark_page() {
    let m = MobileSession::default();
    let png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";
    m.execute("insert.picture".into(), json!({ "data": png, "width": 100 }).to_string()).unwrap();
    m.set_dark(true);
    let plain = m.render_page(0, 1.0).unwrap();
    m.execute("shape.effects".into(), json!({ "shadow": "offsetBottomRight" }).to_string()).unwrap();
    let shadowed = m.render_page(0, 1.0).unwrap();
    let (a, _) = plain.rgba.as_chunks::<4>();
    let (b, _) = shadowed.rgba.as_chunks::<4>();
    let sum = |p: &[u8; 4]| p[0] as i32 + p[1] as i32 + p[2] as i32;
    // as the reference keeps a picture's shadow black on its dark page: it only darkens (and
    // little, the page being near black already)
    assert!(a.iter().zip(b).all(|(x, y)| sum(y) <= sum(x) + 6), "a shadow lightened the page");
    assert!(a.iter().zip(b).any(|(x, y)| sum(y) + 9 < sum(x)), "no shadow drawn");
}

#[test]
fn find_view_marks_every_match_and_the_selected_one() {
    let m = MobileSession::default();
    m.type_text("one cat, two cats, red cat".into()).unwrap();
    m.execute("caret.docStart".into(), "{}".into()).unwrap();
    m.execute("edit.find".into(), r#"{"text": "cat", "wholeWord": true}"#.into()).unwrap();
    let v = m.find_view();
    assert_eq!((v.count, v.current), (2, Some(0)));
    assert_eq!((v.matches.len(), v.current_rects.len()), (1, 1));
    assert!(v.current_rects[0].x < v.matches[0].x, "the first is selected");
    m.execute("edit.findNext".into(), "{}".into()).unwrap();
    assert_eq!(m.find_view().current, Some(1));
    // the caret elsewhere: no match is the selected one
    m.execute("caret.docStart".into(), "{}".into()).unwrap();
    let v = m.find_view();
    assert_eq!((v.current, v.matches.len()), (None, 2));
    m.clear_find();
    assert_eq!(m.find_view().count, 0);

    // typing in Find searches again from the selected match: it stays selected as it grows
    m.execute("caret.docStart".into(), "{}".into()).unwrap();
    for (typed, at) in [("c", 0), ("ca", 0), ("cat", 0), ("cats", 0)] {
        let v = m.find_again(format!(r#"{{"text": "{typed}", "wholeWord": false}}"#)).unwrap();
        assert_eq!(v.current, Some(at), "{typed}");
    }
    assert_eq!(m.find_view().count, 1, "one cats");
    assert!(m.find_again("not json".into()).is_err());
}

#[test]
fn theme_fonts_are_the_headings_then_the_body_font() {
    let m = MobileSession::default();
    let fonts = m.theme_fonts();
    assert_eq!(fonts.len(), 2);
    assert!(fonts.iter().all(|f| !f.is_empty()), "{fonts:?}");
    let doc = m.lock();
    assert_eq!((fonts[0].as_str(), fonts[1].as_str()), (doc.doc.settings.major_font.as_str(), doc.doc.settings.minor_font.as_str()));
}

#[test]
fn theme_colour_shades_follow_the_colour_menus() {
    use wordcraft_engine::doc::props::Rgb;
    // the colour menus' grid for the default theme, row by row: each shade within a step
    const GRID: [&str; 6] = [
        "FFFFFF 000000 E8E8E8 0E2841 156082 E97132 196B24 0F9ED5 A02B93 4EA72E",
        "E5E5E5 E5E5E5 D0D0D0 DBE9F7 C1E4F5 FAE2D6 C1F0C8 CAEDFB F1CEEE D9F2D0",
        "BFBFBF BFBFBF AEAEAE A6C9EB 83CAEB F6C6AC 84E291 95DCF7 E49EDD B3E5A1",
        "7F7F7F 7F7F7F 747474 4D94D8 45B0E1 F1A984 47D45A 60CBF3 D76DCC 8ED873",
        "3F3F3F 3F3F3F 3A3A3A 215E99 0F4861 BF4F14 12501B 0B769F 78206E 3A7D22",
        "191919 191919 171717 153D64 0A3041 7F340D 0C3512 074F6A 501549 265316",
    ];
    let rows: Vec<Vec<Rgb>> = GRID.iter().map(|r| r.split(' ').map(|h| Rgb::parse(h).unwrap()).collect()).collect();
    for col in 0..10 {
        let got = shades(rows[0][col]);
        for (row, want) in rows.iter().enumerate() {
            let (g, w) = (got[row], want[col]);
            let close = g.0.abs_diff(w.0) <= 1 && g.1.abs_diff(w.1) <= 1 && g.2.abs_diff(w.2) <= 1;
            assert!(close, "column {col} row {row}: {} for {}", g.hex(), w.hex());
        }
    }
    let grid = MobileSession::default().color_choices().theme;
    assert_eq!(grid.len(), 6);
    assert!(grid.iter().all(|row| row.len() == 10));
}

#[test]
fn the_link_at_the_caret_can_be_read_selected_and_removed() {
    let m = MobileSession::default();
    m.type_text("See ".into()).unwrap();
    m.execute("insert.link".into(), r#"{"url": "https://example.org", "text": "the site"}"#.into()).unwrap();
    m.type_text(" now".into()).unwrap();
    assert_eq!(m.link_here(), None, "the caret after the link's text and a space");
    m.execute("caret.set".into(), r#"{"pos": {"block": 0, "off": 6}}"#.into()).unwrap();
    assert_eq!(m.link_here(), Some(LinkHere { url: "https://example.org".into(), text: "the site".into() }));
    assert!(m.select_link());
    assert_eq!(m.text_around(0, 0).selected, "the site");
    m.execute("insert.removeLink".into(), "{}".into()).unwrap();
    assert_eq!(m.link_here(), None);
    assert_eq!(body_text(&m), "See the site now");
    m.execute("caret.set".into(), r#"{"pos": {"block": 0, "off": 1}}"#.into()).unwrap();
    assert!(!m.select_link(), "no link here");
}

#[test]
fn the_format_state_knows_the_cell_alignment_and_a_note_mark() {
    let m = MobileSession::default();
    assert_eq!((m.format_state().cell_valign, m.format_state().note_here), (None, None));
    m.execute("insert.table".into(), r#"{"rows": 1, "cols": 1}"#.into()).unwrap();
    assert_eq!(m.format_state().cell_valign.as_deref(), Some("top"));
    m.execute("table.cellAlign".into(), r#"{"value": "bottom"}"#.into()).unwrap();
    assert_eq!(m.format_state().cell_valign.as_deref(), Some("bottom"));
    let m = MobileSession::default();
    m.type_text("Fact".into()).unwrap();
    m.execute("references.footnote".into(), r#"{"text": "Source"}"#.into()).unwrap();
    m.execute("references.goToNote".into(), "{}".into()).unwrap();
    assert_eq!(m.format_state().note_here.as_deref(), Some("footnote"), "back at the mark");
    // in the note, a tap on the body's text goes back to the body
    m.execute("references.goToNote".into(), "{}".into()).unwrap();
    assert_eq!(m.format_state().story, "footnote");
    let c = m.selection_view().focus.unwrap();
    let body = {
        let mut s = m.lock();
        s.layout().caret_on(&Pos::body(0, 1), 0).unwrap()
    };
    assert!(body.top < c.top, "the body's text is above the note");
    m.tap(0, body.x, body.top + body.height / 2.0, false).unwrap();
    assert_eq!(m.format_state().story, "body");
}

#[test]
fn engine_requests_are_handed_over_once() {
    let m = MobileSession::default();
    // saving without a path asks the app for a place
    let _ = m.execute("file.save".into(), String::new());
    let first = m.take_events();
    assert!(first.requests.iter().any(|r| r.contains("saveAs")), "{first:?}");
    assert!(m.take_events().requests.is_empty());
}

#[test]
fn the_session_can_move_between_threads() {
    fn send<T: Send + Sync>() {}
    send::<MobileSession>();
    let m = Arc::new(MobileSession::default());
    let m2 = Arc::clone(&m);
    std::thread::spawn(move || m2.type_text("from another thread".into())).join().unwrap().unwrap();
    assert_eq!(body_text(&m), "From another thread");
}

#[test]
fn the_styles_list_shows_what_the_phone_app_shows_by_priority() {
    let m = MobileSession::new();
    let looks = m.style_looks();
    let names: Vec<&str> = looks.iter().map(|l| l.name.as_str()).collect();
    let at = |n: &str| names.iter().position(|x| *x == n).unwrap_or_else(|| panic!("{n} in {names:?}"));
    // by priority, then name: Normal 0, No Spacing 1, the headings 9, Title 10…
    assert!(at("Normal") < at("No Spacing") && at("No Spacing") < at("Heading 1"), "{names:?}");
    assert!(at("Heading 1") < at("Heading 2") && at("Heading 2") < at("Title") && at("Title") < at("Subtitle"));
    assert!(at("Book Title") < at("List Paragraph"));
    // hidden until used: Heading 3 on, captions, headers, contents entries
    for hidden in ["Heading 3", "Caption", "Header", "TOC 1", "Hyperlink"] {
        assert!(!names.contains(&hidden), "{hidden} waits to be used: {names:?}");
    }
    let h1 = &looks[at("Heading 1")];
    assert!(h1.size > looks[at("Normal")].size && !h1.character);
    assert!(looks.iter().any(|l| l.character), "character styles too");
    // a heading used brings the next level
    m.execute("para.style".into(), r#"{"style": "Heading 2"}"#.into()).unwrap();
    let names: Vec<String> = m.style_looks().into_iter().map(|l| l.name).collect();
    assert!(names.iter().any(|n| n == "Heading 3") && !names.iter().any(|n| n == "Heading 4"), "{names:?}");
    assert_eq!(style_display_name("annotation text"), "Comment Text");
    assert_eq!(style_display_name("Footer"), "Footer");
}

#[test]
fn read_aloud_finds_each_word_said() {
    let m = MobileSession::new();
    m.type_text("Read this aloud, please. Then this.".into()).unwrap();
    let sentences = m.read_aloud_sentences(true);
    assert_eq!(sentences, ["Read this aloud, please.", "Then this."]);
    let whole = m.read_aloud_rects(0);
    let aloud = m.read_aloud_word_rects(0, 2);
    assert_eq!(aloud.len(), 1);
    // inside the sentence's own highlight, and narrower
    let (w, r) = (&whole[0], &aloud[0]);
    assert!(r.x > w.x && r.x + r.width < w.x + w.width && r.width < w.width / 2.0, "{r:?} in {w:?}");
    assert!(m.read_aloud_word_rects(0, 9).is_empty());
    assert!(m.read_aloud_word_rects(7, 0).is_empty());
}

#[test]
fn the_keyboard_types_into_the_equation_being_edited() {
    let m = MobileSession::new();
    m.type_text("Area: ".into()).unwrap();
    m.execute("insert.equation".into(), "{}".into()).unwrap();
    // the caret and the equation's shading are in the equation
    let v = m.selection_view();
    assert!(v.equation.is_some() && v.focus.is_some(), "{v:?}");
    m.type_text("a/b ".into()).unwrap();
    m.type_text("+cx".into()).unwrap();
    m.delete_around(1, 0).unwrap();
    m.execute("caret.left".into(), "{}".into()).unwrap();
    m.type_text("1".into()).unwrap();
    let eq: Value = serde_json::from_str(&m.execute("equation.get".into(), "{}".into()).unwrap()).unwrap();
    assert_eq!(eq["linear"], "a/b+1c", "{eq}");
    assert_eq!(eq["editing"], true);
    // nothing went into the paragraph's own text
    assert_eq!(m.text_around(100, 100).before, "");
    // leaving it, typing goes into the paragraph again
    m.execute("equation.exit".into(), "{}".into()).unwrap();
    assert!(m.selection_view().equation.is_none());
    m.type_text(" cm".into()).unwrap();
    assert!(m.text_around(100, 0).before.starts_with("Area: "), "{:?}", m.text_around(100, 0));
    assert!(m.text_around(100, 0).before.ends_with(" cm"));
}

/// A tap on an equation puts the caret in it, to type there.
#[test]
fn a_tap_on_an_equation_edits_it() {
    let m = MobileSession::new();
    m.execute("insert.equation".into(), r#"{"linear": "x^2", "edit": false}"#.into()).unwrap();
    m.execute("caret.docEnd".into(), "{}".into()).unwrap();
    let eq: Value = serde_json::from_str(&m.execute("equation.get".into(), "{}".into()).unwrap_or_else(|_| "{}".into())).unwrap_or_default();
    assert_ne!(eq["editing"], true);
    // the equation's middle, from where its shading is drawn while it's edited
    m.execute("caret.docStart".into(), "{}".into()).unwrap();
    m.execute("equation.edit".into(), "{}".into()).unwrap();
    let zone = m.selection_view().equation.expect("its shading");
    m.execute("equation.exit".into(), "{}".into()).unwrap();
    m.execute("caret.docEnd".into(), "{}".into()).unwrap();
    m.tap(zone.page, zone.x + zone.width / 2.0, zone.y + zone.height / 2.0, false).unwrap();
    let eq: Value = serde_json::from_str(&m.execute("equation.get".into(), "{}".into()).unwrap()).unwrap();
    assert_eq!(eq["editing"], true, "{eq}");
}

#[test]
fn form_controls_at_the_caret_are_reported_for_filling_in() {
    let m = MobileSession::new();
    assert_eq!(m.form_control(), None);
    m.execute("developer.dropDown".into(), r#"{"title": "Size", "items": ["Small", "Medium", "Large"]}"#.into()).unwrap();
    let c = m.form_control().expect("a list at the caret");
    assert_eq!((c.kind.as_str(), c.title.as_str(), c.chosen), ("dropDown", "Size", -1));
    assert_eq!(c.items, ["Small", "Medium", "Large"]);
    // choosing moves the caret past the control; back inside, the choice shows
    m.execute("developer.chooseItem".into(), r#"{"control": "Size", "index": 1}"#.into()).unwrap();
    m.execute("caret.left".into(), "{}".into()).unwrap();
    assert_eq!(m.form_control().map(|c| c.chosen), Some(1));
    m.execute("caret.docEnd".into(), "{}".into()).unwrap();
    m.execute("text.newParagraph".into(), "{}".into()).unwrap();
    m.execute("developer.checkBox".into(), r#"{"title": "Agree"}"#.into()).unwrap();
    let c = m.form_control().expect("a check box at the caret");
    assert_eq!((c.kind.as_str(), c.checked), ("checkBox", false));
    m.execute("developer.toggleCheckBox".into(), "{}".into()).unwrap();
    assert_eq!(m.form_control().map(|c| c.checked), Some(true));
    // other controls aren't filled in by a tap
    m.execute("caret.docEnd".into(), "{}".into()).unwrap();
    m.execute("text.newParagraph".into(), "{}".into()).unwrap();
    m.execute("developer.plainText".into(), "{}".into()).unwrap();
    assert_eq!(m.form_control(), None);
}

#[test]
fn looking_for_a_form_control_leaves_no_message() {
    let m = MobileSession::new();
    // a tap in plain text looks for a control and finds none: nothing to tell the user
    assert_eq!(m.form_control(), None);
    assert_eq!(m.take_events().status, None);
    // a message from a real command is still there after a look
    assert!(m.execute("developer.toggleCheckBox".into(), "{}".into()).is_err());
    assert_eq!(m.form_control(), None);
    assert!(m.take_events().status.is_some_and(|s| s.contains("content control")));
}

#[test]
fn a_text_file_that_is_not_unicode_opens_in_the_encoding_chosen() {
    let dir = std::env::temp_dir().join(format!("wc-enc-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let latin = dir.join("menu.txt");
    std::fs::write(&latin, b"Caf\xe9 cr\xe8me br\xfbl\xe9e").unwrap();
    let utf8 = dir.join("plain.txt");
    std::fs::write(&utf8, "Caf\u{e9}").unwrap();
    let m = MobileSession::new();
    let path = latin.to_string_lossy().to_string();
    let offer = m.text_encoding_offer(path.clone()).expect("a guess to confirm");
    assert_eq!(offer.detected, "windows-1252");
    assert!(offer.encodings.iter().any(|e| e.id == "shift_jis"));
    assert!(m.text_preview(path.clone(), "windows-1252".into()).starts_with("Caf\u{e9}"));
    assert!(m.text_preview(path.clone(), "windows-1251".into()).starts_with("Caf\u{439}"));
    assert_eq!(m.text_encoding_offer(utf8.to_string_lossy().to_string()), None, "UTF-8 opens as it is");
    m.open_text(path, "windows-1251".into()).unwrap();
    assert!(m.text_around(0, 8).after.starts_with("Caf\u{439}"), "read in the encoding chosen");
    let _ = std::fs::remove_dir_all(&dir);
}

#[test]
fn the_picture_styles_gallery_draws_every_style() {
    let m = MobileSession::new();
    let g = m.picture_style_gallery(160);
    assert_eq!(g.len(), wordcraft_engine::cmd::objects::PICTURE_STYLES.len());
    assert_eq!(g[0].id, "simpleFrameWhite");
    assert!(g.iter().all(|p| p.image.width == 160 && p.image.rgba.len() as u32 == p.image.width * p.image.height * 4));
    // each style looks its own
    let mut seen: Vec<&Vec<u8>> = Vec::new();
    for p in &g {
        assert!(!seen.contains(&&p.image.rgba), "{} looks like another style", p.id);
        seen.push(&p.image.rgba);
    }
}

#[test]
fn the_chart_pages_show_the_selected_chart() {
    let m = MobileSession::new();
    assert!(m.chart_state().is_none() && m.chart_gallery("layout".into(), 200).is_empty());
    m.execute("insert.chart".into(), r#"{"type": "column"}"#.into()).unwrap();
    let st = m.chart_state().expect("a chart is selected");
    assert_eq!(
        (st.kind.as_str(), st.family.as_str(), st.title.as_str(), st.labels.as_str(), st.legend.as_str()),
        ("column", "column", "none", "none", "bottom")
    );
    assert!(st.label_places.iter().any(|c| c.id == "outsideEnd" && c.name == "Outside End"));
    let types = m.chart_gallery("type:line".into(), 240);
    assert_eq!(types.len(), 6);
    let first = &types[0];
    assert_eq!(first.id, "line");
    assert_eq!(first.image.width, 240);
    assert_eq!(first.image.rgba.len() as u32, first.image.width * first.image.height * 4);
    // drawn: not all one colour
    assert!(first.image.rgba.chunks(4).any(|p| p != &first.image.rgba[..4]));
    assert_eq!(m.chart_gallery("layout".into(), 120).len(), 7);
    let palettes = m.chart_palettes();
    assert_eq!(palettes.len(), 17);
    assert!(palettes.iter().all(|p| p.colors.len() == 6));
    m.execute("chart.colors".into(), r#"{"palette": "colorful3"}"#.into()).unwrap();
    m.execute("chart.title".into(), r#"{"pos": "overlay"}"#.into()).unwrap();
    let st = m.chart_state().unwrap();
    assert_eq!((st.palette.as_str(), st.title.as_str()), ("colorful3", "overlay"));
}

#[test]
fn a_picture_reports_its_crop_and_whether_it_is_inline() {
    let m = MobileSession::new();
    assert_eq!(m.picture_crop(), None);
    // a 1 x 1 PNG
    let png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=";
    m.execute("insert.picture".into(), json!({ "data": png }).to_string()).unwrap();
    assert_eq!(m.picture_crop(), Some(vec![0.0; 4]));
    // the picture alone is the selection, as a tap on it makes it
    m.execute("select.all".into(), String::new()).unwrap();
    assert!(m.format_state().object_inline);
    m.execute("picture.crop".into(), r#"{"left": 0.1, "bottom": 0.2}"#.into()).unwrap();
    assert_eq!(m.picture_crop(), Some(vec![0.1, 0.0, 0.0, 0.2]));
    m.execute("arrange.wrap".into(), r#"{"wrap": "square"}"#.into()).unwrap();
    assert!(!m.format_state().object_inline);
    assert!(!m.format_state().object_icon);
}

#[test]
fn an_icon_is_a_picture_that_brings_the_graphics_tab() {
    let m = MobileSession::new();
    m.execute("insert.icon".into(), r#"{"id": "heart"}"#.into()).unwrap();
    m.execute("select.all".into(), String::new()).unwrap();
    let f = m.format_state();
    assert_eq!((f.object.as_deref(), f.object_icon), (Some("picture"), true));
    m.execute("graphic.fill".into(), r#"{"color": "00B050"}"#.into()).unwrap();
    m.execute("graphic.outline".into(), r#"{"color": "C00000"}"#.into()).unwrap();
    assert!(m.format_state().object_icon);
}

#[test]
fn a_selected_chart_or_diagram_brings_its_own_tab() {
    let m = MobileSession::new();
    m.execute("insert.chart".into(), r#"{"type": "pie"}"#.into()).unwrap();
    m.execute("select.range".into(), r#"{"anchor": {"block": 0, "off": 0}, "focus": {"block": 0, "off": 3}}"#.into()).unwrap();
    assert_eq!(m.format_state().object.as_deref(), Some("chart"));
    m.execute("document.setText".into(), r#"{"text": ""}"#.into()).unwrap();
    m.execute("insert.smartArt".into(), r#"{"layout": "process"}"#.into()).unwrap();
    m.execute("select.range".into(), r#"{"anchor": {"block": 0, "off": 0}, "focus": {"block": 0, "off": 3}}"#.into()).unwrap();
    assert_eq!(m.format_state().object.as_deref(), Some("smartArt"));
}

#[test]
fn the_alt_text_pane_reads_the_selected_objects_description() {
    let m = MobileSession::new();
    assert!(m.object_alt().is_none(), "nothing selected");
    m.execute("insert.shape".into(), r#"{"preset": "hexagon"}"#.into()).unwrap();
    assert_eq!(m.object_alt(), Some(ObjectAlt { text: String::new(), decorative: false }));
    m.execute("object.altText".into(), r#"{"text": "A six-sided badge", "decorative": true}"#.into()).unwrap();
    assert_eq!(m.object_alt(), Some(ObjectAlt { text: "A six-sided badge".into(), decorative: true }));
}

#[test]
fn the_shape_gallery_draws_every_preset_and_shrugs_off_junk() {
    let groups = shape_gallery();
    assert_eq!(groups.first().map(|g| g.name.as_str()), Some("Lines"));
    assert!(groups.iter().any(|g| g.name == "Flowchart" && g.presets.len() == 28));
    for g in groups.iter().filter(|g| g.name != "Lines") {
        for p in &g.presets {
            let a = shape_aspect(p.clone());
            assert!(a.is_finite() && a > 0.0, "{p}");
            let o = shape_outline(p.clone(), 40.0 * a, 40.0).unwrap_or_else(|| panic!("{p}"));
            assert!(!(o.fill.is_empty() && o.lines.is_empty()), "{p}");
            assert!(
                o.fill.iter().chain(&o.lines).chain(o.faces.iter().map(|f| &f.pts)).all(|r| r.len() % 2 == 0 && r.iter().all(|v| v.is_finite())),
                "{p}"
            );
        }
    }
    assert!(shape_outline("cube".into(), 40.0, 40.0).unwrap().faces.iter().any(|f| f.lift > 0.0), "a lit top");
    assert!(shape_outline("bracePair".into(), 40.0, 40.0).unwrap().line_like && !shape_outline("cube".into(), 40.0, 40.0).unwrap().line_like);
    assert!(shape_outline("noSuchShape".into(), 40.0, 40.0).is_none());
    assert!(shape_outline("hexagon".into(), f32::NAN, f32::INFINITY).is_some(), "junk sizes are clamped");
}

/// As the reference marks text: misspellings and a repeated word (under the second), no grammar.
#[test]
fn proofing_marks_spelling_and_repeated_words_but_no_grammar() {
    let m = MobileSession::default();
    let text = "this one has has  two spaces and a mispeled word.";
    m.execute("text.insert".into(), json!({ "text": text }).to_string()).unwrap();
    let marks: Vec<(String, bool)> = {
        let mut s = m.lock();
        let l = s.layout();
        l.pages
            .iter()
            .flat_map(|p| &p.items)
            .filter_map(|it| if let wordcraft_engine::layout::Placed::Lines { para, .. } = it { Some(para.issues.clone()) } else { None })
            .flatten()
            .map(|(a, b, grammar)| (text[a..b].to_string(), grammar))
            .collect()
    };
    assert_eq!(marks, [("has".to_string(), false), ("mispeled".to_string(), false)]);
    // the pane lists the same, and grammar comes back with its switch
    let listed = |grammar: bool| -> usize {
        let out = m.execute("review.issues".into(), json!({ "grammar": grammar }).to_string()).unwrap();
        serde_json::from_str::<Value>(&out).unwrap().as_array().map_or(0, Vec::len)
    };
    assert_eq!(listed(false), 2);
    assert!(listed(true) > 2);
    // turning grammar marks on draws the pages again
    let before = m.document_info().revision;
    m.execute("file.options".into(), json!({ "markGrammar": true }).to_string()).unwrap();
    assert_ne!(m.document_info().revision, before);
}

/// A comment's anchor line is reported for the mark beside it; a reply adds none.
#[test]
fn comment_marks_point_at_each_threads_line() {
    let m = MobileSession::default();
    m.execute("text.insert".into(), json!({ "text": "First line\nSecond line with a note" }).to_string()).unwrap();
    assert!(m.comment_marks().is_empty());
    m.execute("select.text".into(), json!({ "text": "a note" }).to_string()).unwrap();
    m.execute("review.newComment".into(), json!({ "text": "Check this." }).to_string()).unwrap();
    let marks = m.comment_marks();
    assert_eq!(marks.len(), 1, "{marks:?}");
    let first = &marks[0];
    assert!(first.height > 0.0 && first.y > 0.0, "{first:?}");
}

/// A new document opens at 100%, the zoom it is saved at.
#[test]
fn a_document_opens_at_its_saved_zoom() {
    let m = MobileSession::default();
    assert_eq!(m.document_zoom(), 100);
    // and scrolled past the first page's top margin
    assert!((m.first_text_top() - 72.0).abs() < 0.5, "{}", m.first_text_top());
}

/// Each colour in the colour menus has a name a screen reader says: the theme's by their place
/// and shade, the standard ones and the highlights by their colour.
#[test]
fn colour_swatches_have_names() {
    let c = MobileSession::default().color_choices();
    assert_eq!(c.theme[0][0].label, "Background 1");
    assert_eq!(c.theme[0][4].label, "Accent 1");
    assert!(c.theme[1][4].label.starts_with("Accent 1, "), "{}", c.theme[1][4].label);
    assert_eq!(c.standard[0].label, "Dark Red");
    assert_eq!(c.highlights[0].label, "Yellow");
    assert!(c.theme.iter().flatten().chain(&c.standard).chain(&c.highlights).all(|s| !s.label.is_empty()));
}
