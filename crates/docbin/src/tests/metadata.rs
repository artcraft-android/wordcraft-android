//! OLE property sets constructed from MS-OLEPS, exercised through the public DOC reader.
use super::{Piece, body_texts, text_doc};
use std::io::{Cursor, Seek, SeekFrom, Write};

fn summary(cp: u16, values: &[(u32, u32, Vec<u8>)]) -> Vec<u8> {
    let mut props = vec![(1, [2u32.to_le_bytes().as_slice(), &cp.to_le_bytes(), &[0, 0]].concat())];
    for (id, kind, bytes) in values {
        let size = if *kind == 31 { bytes.len() / 2 } else { bytes.len() } as u32;
        let mut value = [kind.to_le_bytes().as_slice(), &size.to_le_bytes(), bytes].concat();
        while !value.len().is_multiple_of(4) {
            value.push(0);
        }
        props.push((*id, value));
    }
    let mut section = vec![0; 8 + 8 * props.len()];
    section[4..8].copy_from_slice(&(props.len() as u32).to_le_bytes());
    for (i, (id, value)) in props.iter().enumerate() {
        section[8 + i * 8..12 + i * 8].copy_from_slice(&id.to_le_bytes());
        let offset = section.len() as u32;
        section[12 + i * 8..16 + i * 8].copy_from_slice(&offset.to_le_bytes());
        section.extend_from_slice(value);
    }
    let size = section.len() as u32;
    section[..4].copy_from_slice(&size.to_le_bytes());
    let mut header = vec![0; 48];
    header[..2].copy_from_slice(&[0xfe, 0xff]);
    header[24..28].copy_from_slice(&1u32.to_le_bytes());
    header[28..44].copy_from_slice(&[0xe0, 0x85, 0x9f, 0xf2, 0xf9, 0x4f, 0x68, 0x10, 0xab, 0x91, 0x08, 0, 0x2b, 0x27, 0xb3, 0xd9]);
    header[44..48].copy_from_slice(&48u32.to_le_bytes());
    header.extend(section);
    header
}

fn open_with_metadata(bytes: &[u8], template: bool) -> wordcraft_doc::Document {
    let file = text_doc(&[Piece { compressed: false, text: "Body stays readable\r".into() }], &[], 0);
    let mut cfb = cfb::CompoundFile::open(Cursor::new(file)).unwrap();
    cfb.create_stream("/\u{5}SummaryInformation").unwrap().write_all(bytes).unwrap();
    if template {
        let mut stream = cfb.open_stream("/WordDocument").unwrap();
        stream.seek(SeekFrom::Start(0x0a)).unwrap();
        stream.write_all(&0x1001u16.to_le_bytes()).unwrap();
    }
    let doc = crate::read(&cfb.into_inner().into_inner()).unwrap();
    assert_eq!(body_texts(&doc), ["Body stays readable"]);
    doc
}

#[test]
fn doc_and_dot_import_summary_text_properties() {
    let data = summary(
        1252,
        &[
            (2, 30, b"R\xe9sum\xe9\0".to_vec()),
            (3, 30, b"Subject\0".to_vec()),
            (4, 30, b"Writer\0".to_vec()),
            (5, 30, b"one;two\0".to_vec()),
            (6, 30, b"Description\0".to_vec()),
            (8, 30, b"Editor\0".to_vec()),
            (9, 30, b"12\0".to_vec()),
        ],
    );
    for template in [false, true] {
        let c = open_with_metadata(&data, template).core;
        assert_eq!(c.title, "Résumé");
        assert_eq!(c.subject, "Subject");
        assert_eq!(c.creator, "Writer");
        assert_eq!(c.keywords, "one;two");
        assert_eq!(c.description, "Description");
        assert_eq!(c.last_modified_by, "Editor");
        assert_eq!(c.revision, 12);
    }
}

#[test]
fn summary_handles_codepages_and_utf16_length_units() {
    let wide: Vec<u8> = "Unicode ä 文 😀\0".encode_utf16().flat_map(u16::to_le_bytes).collect();
    for kind in [30, 31] {
        assert_eq!(open_with_metadata(&summary(1200, &[(2, kind, wide.clone())]), false).core.title, "Unicode ä 文 😀");
    }
    assert_eq!(open_with_metadata(&summary(65001, &[(2, 30, "UTF-8 ä\0".as_bytes().to_vec())]), false).core.title, "UTF-8 ä");
    assert_eq!(open_with_metadata(&summary(1251, &[(2, 30, vec![0xcf, 0xf0, 0xe8, 0xe2, 0xe5, 0xf2, 0])]), false).core.title, "Привет");
}

#[test]
fn malformed_or_oversized_summary_does_not_break_doc_open() {
    let valid = summary(1252, &[(2, 30, b"Title\0".to_vec())]);
    for n in [0, 2, 24, 44, 47, 55, valid.len() - 5] {
        assert!(open_with_metadata(&valid[..n], false).core.title.is_empty());
    }
    for (at, value) in [(44, u32::MAX), (48, u32::MAX), (52, u32::MAX), (68, 4), (84, u32::MAX)] {
        let mut bad = valid.clone();
        bad[at..at + 4].copy_from_slice(&value.to_le_bytes());
        assert!(open_with_metadata(&bad, false).core.title.is_empty(), "offset {at}");
    }
    let huge = summary(1252, &[(2, 30, vec![b'x'; (64 << 10) + 1])]);
    assert!(open_with_metadata(&huge, false).core.title.is_empty());
    assert!(open_with_metadata(&vec![0; (2 << 20) + 1], false).core.title.is_empty());
}
