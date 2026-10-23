//! Original field-boundary fixtures from the public OOXML complex-field contract.
use std::io::Write;

use wordcraft_doc::{InlineObject, Path, StoryRef};

fn package(body: &str) -> Vec<u8> {
    let xml = format!(r#"<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>{body}</w:body></w:document>"#);
    let mut zip = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
    for (name, value) in [
        ("word/document.xml", xml.as_str()),
        (
            "_rels/.rels",
            r#"<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="main" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"#,
        ),
    ] {
        zip.start_file(name, zip::write::SimpleFileOptions::default()).unwrap();
        zip.write_all(value.as_bytes()).unwrap();
    }
    zip.finish().unwrap().into_inner()
}

fn start(code: &str) -> String {
    format!(
        r#"<w:r><w:fldChar w:fldCharType="begin" w:fldLock="true"/></w:r><w:r><w:instrText>{code}</w:instrText></w:r><w:r><w:fldChar w:fldCharType="separate"/></w:r>"#
    )
}

const END: &str = r#"<w:r><w:fldChar w:fldCharType="end"/></w:r>"#;
const PAGE: &str = r#"<w:fldSimple w:instr="PAGEREF Original"><w:r><w:t>7</w:t></w:r></w:fldSimple>"#;
const ENTRY: &str = r#"<w:hyperlink w:anchor="Original"><w:r><w:rPr><w:b/><w:i/></w:rPr><w:t>Scope</w:t></w:r><w:r><w:tab/></w:r>"#;

#[test]
fn unstyled_toc_keeps_range_nested_field_and_formatting_through_two_edited_saves() {
    for split in [false, true] {
        let content = if split {
            format!("{ENTRY}{PAGE}</w:hyperlink></w:p><w:p><w:r><w:t>Delivery</w:t></w:r>")
        } else {
            format!("{ENTRY}{PAGE}</w:hyperlink>")
        };
        let body = format!(
            r#"<w:p><w:r><w:t>Before.</w:t></w:r></w:p><w:p>{}{content}{END}</w:p><w:p><w:r><w:t>After.</w:t></w:r></w:p>"#,
            start(r#"TOC \o "1-2" \h"#)
        );
        let mut doc = wordcraft_docx::read(&package(&body)).unwrap();
        for cycle in 0..=2 {
            let ranges = doc.field_ranges(StoryRef::Body);
            assert_eq!(ranges.len(), 1);
            assert_eq!(ranges[0].instr, r#"TOC \o "1-2" \h"#);
            assert!(ranges[0].locked);
            assert_eq!(ranges[0].start.path, Path::top(1));
            assert_eq!(ranges[0].start.off, 0);
            assert_eq!(ranges[0].end.path, Path::top(if split { 2 } else { 1 }));
            let entry = doc.body[1].as_para().unwrap();
            assert_eq!(entry.plain_text(), "Scope\t7");
            let props = entry.props_of_char(wordcraft_doc::para::OBJ.len_utf8());
            assert_eq!(props.bold, Some(true));
            assert_eq!(props.italic, Some(true));
            assert_eq!(props.link.as_deref(), Some("#Original"));
            assert!(
                entry.objects.iter().any(|o| matches!(o, InlineObject::Field { instr, result, .. } if instr == "PAGEREF Original" && result == "7"))
            );
            assert_eq!(doc.body.last().unwrap().as_para().unwrap().plain_text(), "After.");
            if cycle == 2 {
                break;
            }
            doc.para_mut(StoryRef::Body, &Path::top(0)).unwrap().insert_text(0, "Independent edit. ", &Default::default()).unwrap();
            doc = wordcraft_docx::read(&wordcraft_docx::write(&doc).unwrap()).unwrap();
        }
    }
}

#[test]
fn styled_heading_does_not_expand_partial_custom_or_short_ranges() {
    let heading = r#"<w:p><w:pPr><w:pStyle w:val="TOCHeading"/></w:pPr><w:r><w:t>Contents</w:t></w:r></w:p>"#;
    let extra = r#"<w:p><w:pPr><w:pStyle w:val="TOC1"/></w:pPr><w:r><w:t>Outside entry.</w:t></w:r></w:p>"#;
    for (style, prefix, suffix, following) in
        [("TOC1", "Prefix.", "", ""), ("TOC1", "", "Suffix.", ""), ("TOC1", "", "", extra), ("CompanyContents", "", "", "")]
    {
        let body = format!(
            r#"{heading}<w:p><w:pPr><w:pStyle w:val="{style}"/></w:pPr><w:r><w:t>{prefix}</w:t></w:r>{}<w:r><w:t>Scope</w:t></w:r>{END}<w:r><w:t>{suffix}</w:t></w:r></w:p>{following}<w:p><w:r><w:t>Body.</w:t></w:r></w:p>"#,
            start("TOC")
        );
        let mut doc = wordcraft_docx::read(&package(&body)).unwrap();
        let text = doc.plain_text(StoryRef::Body);
        for cycle in 0..=2 {
            let ranges = doc.field_ranges(StoryRef::Body);
            assert_eq!(ranges.len(), 1, "{style} {prefix} {suffix} {following}");
            assert_eq!(ranges[0].start.path, Path::top(1));
            assert_eq!(ranges[0].end.path, Path::top(1));
            assert_eq!(ranges[0].start.off, prefix.len());
            assert_eq!(ranges[0].end.off, prefix.len() + wordcraft_doc::para::OBJ.len_utf8() + "Scope".len());
            assert_eq!(doc.plain_text(StoryRef::Body), text);
            if cycle == 2 {
                break;
            }
            doc = wordcraft_docx::read(&wordcraft_docx::write(&doc).unwrap()).unwrap();
        }
    }
}

#[test]
fn simple_lowercase_toc_and_multiple_tocs_keep_their_own_ranges() {
    let body = format!(
        r#"<w:p><w:fldSimple w:instr="toc \h" w:fldLock="true">{ENTRY}{PAGE}</w:hyperlink></w:fldSimple><w:r><w:t>Between.</w:t></w:r>{}<w:r><w:t>Other result.</w:t></w:r>{END}<w:r><w:t>After.</w:t></w:r></w:p>"#,
        start(r#"TOC \o "1-1""#)
    );
    let mut doc = wordcraft_docx::read(&package(&body)).unwrap();
    for cycle in 0..=2 {
        let r = doc.field_ranges(StoryRef::Body);
        assert_eq!(r.len(), 2);
        assert_eq!(r[0].instr, r#"toc \h"#);
        assert_eq!(r[1].instr, r#"TOC \o "1-1""#);
        assert!(r.iter().all(|r| r.locked));
        assert!(r[0].end.off < r[1].start.off);
        assert_eq!(doc.plain_text(StoryRef::Body), "Scope\t7Between.Other result.After.");
        if cycle == 2 {
            break;
        }
        doc = wordcraft_docx::read(&wordcraft_docx::write(&doc).unwrap()).unwrap();
    }
}

#[test]
fn toc_in_table_cell_does_not_capture_the_next_cell() {
    let body = format!(
        r#"<w:tbl><w:tr><w:tc><w:p>{}{ENTRY}{PAGE}</w:hyperlink>{END}</w:p></w:tc><w:tc><w:p><w:r><w:t>Other cell.</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"#,
        start("TOC")
    );
    let mut doc = wordcraft_docx::read(&package(&body)).unwrap();
    for cycle in 0..=2 {
        let ranges = doc.field_ranges(StoryRef::Body);
        assert_eq!(ranges.len(), 1);
        assert_eq!(ranges[0].start.path, wordcraft_doc::Path(vec![0, 0, 0, 0]));
        assert_eq!(ranges[0].end.path, ranges[0].start.path);
        assert_eq!(doc.body[0].as_table().unwrap().rows[0].cells[1].blocks[0].as_para().unwrap().plain_text(), "Other cell.");
        if cycle == 2 {
            break;
        }
        doc = wordcraft_docx::read(&wordcraft_docx::write(&doc).unwrap()).unwrap();
    }
}

#[test]
fn missing_end_and_excessive_nesting_stay_bounded_and_save_balanced() {
    for (starts, ends) in [(1, 0), (40, 40)] {
        let body = format!("<w:p>{}<w:r><w:t>Visible result.</w:t></w:r>{}</w:p>", start("TOC").repeat(starts), END.repeat(ends));
        let doc = wordcraft_docx::read(&package(&body)).unwrap();
        let saved = wordcraft_docx::read(&wordcraft_docx::write(&doc).unwrap()).unwrap();
        assert!(!saved.has_unbalanced_field_ranges());
        assert_eq!(saved.plain_text(StoryRef::Body), "Visible result.");
        assert!(saved.field_ranges(StoryRef::Body).len() <= 32);
    }
}

#[test]
fn section_break_keeps_an_explicit_range_and_toc_prefix_is_not_a_toc() {
    let body = format!(
        r#"<w:p><w:pPr><w:pStyle w:val="TOCHeading"/></w:pPr><w:r><w:t>Contents</w:t></w:r></w:p><w:p><w:pPr><w:pStyle w:val="TOC1"/><w:sectPr/></w:pPr>{}<w:r><w:t>Scope</w:t></w:r>{END}</w:p><w:p>{}<w:r><w:t>Other.</w:t></w:r>{END}</w:p>"#,
        start("TOC"),
        start("TOC_OTHER")
    );
    let doc = wordcraft_docx::read(&package(&body)).unwrap();
    for d in [&doc, &wordcraft_docx::read(&wordcraft_docx::write(&doc).unwrap()).unwrap()] {
        let ranges = d.field_ranges(StoryRef::Body);
        assert_eq!(ranges.len(), 1);
        assert_eq!(ranges[0].start.path, Path::top(1));
        assert!(d.body[1].as_para().unwrap().section.is_some());
        assert!(
            d.body[2]
                .as_para()
                .unwrap()
                .objects
                .iter()
                .any(|o| matches!(o, InlineObject::Field { instr, result, .. } if instr == "TOC_OTHER" && result == "Other."))
        );
    }
}
