//! Plain text: UTF-8 (with or without BOM), UTF-16 LE/BE (BOM or sniffed), else the legacy
//! encoding the bytes look like (GBK/GB18030, Big5, Shift_JIS, EUC-KR, Windows code pages…;
//! #778), Windows-1252 when nothing fits better.
//! Export writes UTF-8 with CRLF line ends; list paragraphs get their label and a tab, table
//! cells are separated by tabs.

use wordcraft_doc::Document;

pub use wordcraft_doc::encoding::cp1252;

use crate::model::{FBlock, ListCounter, from_doc};

fn utf16(b: &[u8], le: bool) -> String {
    let units: Vec<u16> = b.as_chunks::<2>().0.iter().map(|c| if le { u16::from_le_bytes(*c) } else { u16::from_be_bytes(*c) }).collect();
    String::from_utf16_lossy(&units)
}

/// UTF-16 without a BOM, told by its zero bytes: `Some(true)` little-endian, `Some(false)`
/// big-endian.
fn unmarked_utf16(b: &[u8]) -> Option<bool> {
    if b.len() < 4 || !b.len().is_multiple_of(2) {
        return None;
    }
    let sample = b.get(..b.len().min(512))?;
    let even_zero = sample.iter().step_by(2).filter(|x| **x == 0).count();
    let odd_zero = sample.iter().skip(1).step_by(2).filter(|x| **x == 0).count();
    let half = sample.len() / 2;
    if odd_zero * 10 >= half * 4 && even_zero * 10 < half {
        return Some(true);
    }
    if even_zero * 10 >= half * 4 && odd_zero * 10 < half {
        return Some(false);
    }
    None
}

/// Does `b` look like plain text, so a file with an extension WordCraft doesn't know (a
/// playlist, a log, a config file…) can open as text the way Word's text import opens it?
///
/// Text has a BOM, or reads as UTF-16 by its zero bytes, or has no NUL byte and next to no
/// other control characters in its first 64 KB. PDFs are refused by their signature (they
/// start as text). An empty file is text.
pub fn is_text(b: &[u8]) -> bool {
    if b.starts_with(&[0xEF, 0xBB, 0xBF]) || b.starts_with(&[0xFF, 0xFE]) || b.starts_with(&[0xFE, 0xFF]) {
        return true;
    }
    if b.starts_with(b"%PDF-") {
        return false;
    }
    let sample = b.get(..b.len().min(64 << 10)).unwrap_or(b);
    // Tab, line feed, form feed, carriage return, DOS end-of-file (^Z) and escape appear in text;
    // other controls in more than 1 of 100 characters mean binary.
    let odd = |c: u32| c == 0 || (c < 0x20 && !matches!(c, 0x09 | 0x0A | 0x0C | 0x0D | 0x1A | 0x1B)) || c == 0x7F;
    if let Some(le) = unmarked_utf16(sample) {
        let units = sample.as_chunks::<2>().0;
        let n = units.iter().filter(|c| odd(u32::from(if le { u16::from_le_bytes(**c) } else { u16::from_be_bytes(**c) }))).count();
        return n.saturating_mul(100) <= units.len();
    }
    if sample.contains(&0) {
        return false;
    }
    sample.iter().filter(|c| odd(u32::from(**c))).count().saturating_mul(100) <= sample.len()
}

/// Decode text bytes: BOMs first, then UTF-16 without BOM (by its zero bytes), UTF-8, and the
/// legacy encoding the bytes look like ([`legacy`]) as the last resort.
pub fn decode(b: &[u8]) -> String {
    if let Some(rest) = b.strip_prefix(&[0xEF, 0xBB, 0xBF]) {
        return String::from_utf8_lossy(rest).into_owned();
    }
    if let Some(rest) = b.strip_prefix(&[0xFF, 0xFE]) {
        return utf16(rest, true);
    }
    if let Some(rest) = b.strip_prefix(&[0xFE, 0xFF]) {
        return utf16(rest, false);
    }
    if let Some(le) = unmarked_utf16(b) {
        return utf16(b, le);
    }
    match std::str::from_utf8(b) {
        Ok(s) => s.to_string(),
        Err(_) => legacy(b),
    }
}

/// Most bytes looked at to guess a legacy encoding.
const SNIFF: usize = 1 << 20;

/// The legacy encoding bytes that aren't UTF-8 look like (chardetng, the detector Firefox uses
/// for unlabelled pages).
fn guess(b: &[u8]) -> &'static encoding_rs::Encoding {
    let mut det = chardetng::EncodingDetector::new(chardetng::Iso2022JpDetection::Deny);
    det.feed(b.get(..SNIFF).unwrap_or(b), b.len() <= SNIFF);
    det.guess(None, chardetng::Utf8Detection::Deny)
}

/// Bytes that aren't UTF-8 in the legacy encoding they look like ([`guess`]): GBK/GB18030 for
/// Simplified Chinese, Big5, Shift_JIS or EUC-JP, EUC-KR, or a single-byte Windows / ISO code
/// page; Windows-1252 as before when the guess is a single-byte Western one.
pub fn legacy(b: &[u8]) -> String {
    let enc = guess(b);
    if enc == encoding_rs::WINDOWS_1252 {
        return b.iter().map(|c| cp1252(*c)).collect();
    }
    enc.decode_without_bom_handling(b).0.into_owned()
}

/// Decode bytes in a named encoding (a WHATWG label such as `gbk`, `gb18030`, `big5`,
/// `shift_jis`, `euc-kr`, `utf-16le`, `windows-1251`); `None` when the label isn't known.
pub fn decode_as(b: &[u8], label: &str) -> Option<String> {
    let enc = encoding_rs::Encoding::for_label(label.trim().as_bytes())?;
    Some(enc.decode(b).0.into_owned())
}

/// The encodings a text file can be read in when it isn't Unicode, so its encoding is a guess
/// and can be chosen: each one's name and its Encoding Standard label. Every encoding [`guess`]
/// answers is here.
pub const ENCODINGS: &[(&str, &str)] = &[
    ("Western (Windows-1252)", "windows-1252"),
    ("Central European (Windows-1250)", "windows-1250"),
    ("Cyrillic (Windows-1251)", "windows-1251"),
    ("Greek (Windows-1253)", "windows-1253"),
    ("Turkish (Windows-1254)", "windows-1254"),
    ("Hebrew (Windows-1255)", "windows-1255"),
    ("Arabic (Windows-1256)", "windows-1256"),
    ("Baltic (Windows-1257)", "windows-1257"),
    ("Vietnamese (Windows-1258)", "windows-1258"),
    ("Thai (Windows-874)", "windows-874"),
    ("Japanese (Shift_JIS)", "shift_jis"),
    ("Japanese (EUC-JP)", "euc-jp"),
    ("Chinese Simplified (GBK)", "gbk"),
    ("Chinese Simplified (GB18030)", "gb18030"),
    ("Chinese Traditional (Big5)", "big5"),
    ("Korean (EUC-KR)", "euc-kr"),
    ("Central European (ISO-8859-2)", "iso-8859-2"),
    ("South European (ISO-8859-3)", "iso-8859-3"),
    ("Baltic (ISO-8859-4)", "iso-8859-4"),
    ("Cyrillic (ISO-8859-5)", "iso-8859-5"),
    ("Arabic (ISO-8859-6)", "iso-8859-6"),
    ("Greek (ISO-8859-7)", "iso-8859-7"),
    ("Hebrew (ISO-8859-8)", "iso-8859-8"),
    ("Baltic (ISO-8859-13)", "iso-8859-13"),
    ("Western (ISO-8859-15)", "iso-8859-15"),
    ("Cyrillic (KOI8-R)", "koi8-r"),
    ("Cyrillic (KOI8-U)", "koi8-u"),
    ("Cyrillic (DOS 866)", "ibm866"),
    ("Western (Mac)", "macintosh"),
    ("Unicode (UTF-8)", "utf-8"),
    ("Unicode (UTF-16LE)", "utf-16le"),
    ("Unicode (UTF-16BE)", "utf-16be"),
];

/// Whether a text file's encoding is a guess, so it can be chosen: it has no BOM, doesn't read
/// as UTF-16 by its zero bytes and isn't UTF-8.
pub fn encoding_is_guessed(b: &[u8]) -> bool {
    let marked = b.starts_with(&[0xEF, 0xBB, 0xBF]) || b.starts_with(&[0xFF, 0xFE]) || b.starts_with(&[0xFE, 0xFF]);
    !marked && unmarked_utf16(b).is_none() && std::str::from_utf8(b).is_err()
}

/// The label (one of [`ENCODINGS`]) of the encoding [`decode`] reads `b` in.
pub fn detected_label(b: &[u8]) -> &'static str {
    let enc = if b.starts_with(&[0xEF, 0xBB, 0xBF]) {
        encoding_rs::UTF_8
    } else if b.starts_with(&[0xFF, 0xFE]) {
        encoding_rs::UTF_16LE
    } else if b.starts_with(&[0xFE, 0xFF]) {
        encoding_rs::UTF_16BE
    } else if let Some(le) = unmarked_utf16(b) {
        if le { encoding_rs::UTF_16LE } else { encoding_rs::UTF_16BE }
    } else if std::str::from_utf8(b).is_ok() {
        encoding_rs::UTF_8
    } else {
        guess(b)
    };
    ENCODINGS.iter().find(|(_, l)| encoding_rs::Encoding::for_label(l.as_bytes()) == Some(enc)).map_or("windows-1252", |(_, l)| l)
}

/// The start of text bytes read in encoding `label`: at most `chars` characters, from the
/// first 64 KB. `None` when the label isn't known.
pub fn preview(b: &[u8], label: &str, chars: usize) -> Option<String> {
    let text = decode_as(b.get(..b.len().min(64 << 10)).unwrap_or(b), label)?;
    Some(text.chars().take(chars).collect())
}

/// A document with one paragraph per line.
pub fn import(bytes: &[u8]) -> Document {
    from_text(&decode(bytes))
}

/// [`import`] reading the bytes in encoding `label` (an Encoding Standard label, such as those
/// in [`ENCODINGS`]) instead of the one detected; `None` when the label isn't known.
pub fn import_as(bytes: &[u8], label: &str) -> Option<Document> {
    decode_as(bytes, label).map(|t| from_text(&t))
}

/// A document with one paragraph per line of `text`, without its control characters.
fn from_text(text: &str) -> Document {
    let text = text.replace("\r\n", "\n").replace('\r', "\n");
    let clean: String = text.chars().filter(|c| !c.is_control() || matches!(c, '\n' | '\t' | '\u{000C}')).collect();
    Document::from_text(&clean)
}

/// [`import`] onto `page`: plain text gives no page size (#1381).
pub fn import_on(bytes: &[u8], page: &wordcraft_doc::SectionProps) -> Document {
    let mut doc = import(bytes);
    doc.last_section = page.clone();
    doc
}

fn blocks_text(blocks: &[FBlock], out: &mut Vec<String>, lists: &mut ListCounter, depth: usize) {
    for b in blocks {
        match b {
            FBlock::Para(p) => {
                let mut line = String::new();
                match p.list {
                    Some(li) => {
                        line.push_str(&"\t".repeat(li.level as usize));
                        line.push_str(&lists.label(li));
                        line.push('\t');
                    }
                    None => lists.reset(),
                }
                line.push_str(&p.shown_text().replace('\n', "\r\n").replace(['\u{000C}', '\u{000E}'], ""));
                out.push(line);
            }
            FBlock::Table(t) => {
                lists.reset();
                for row in &t.rows {
                    let cells: Vec<String> = row
                        .iter()
                        .map(|c| {
                            if depth > 8 {
                                return String::new();
                            }
                            let mut v = Vec::new();
                            blocks_text(&c.blocks, &mut v, &mut ListCounter::default(), depth + 1);
                            v.join(" ")
                        })
                        .collect();
                    out.push(cells.join("\t"));
                }
            }
        }
    }
}

/// UTF-8 text, CRLF line ends.
pub fn export(doc: &Document) -> Vec<u8> {
    let flow = from_doc(doc);
    let mut lines = Vec::new();
    blocks_text(&flow.blocks, &mut lines, &mut ListCounter::default(), 0);
    lines.join("\r\n").into_bytes()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn every_encoding_offered_is_known_and_once() {
        let mut seen = Vec::new();
        for (name, label) in ENCODINGS {
            let enc = encoding_rs::Encoding::for_label(label.as_bytes()).unwrap_or_else(|| panic!("{label}"));
            assert!(!seen.contains(&enc), "{name}: offered twice");
            seen.push(enc);
        }
        // what text is read in without a choice is always one of them
        let latin = b"Caf\xe9 cr\xe8me br\xfbl\xe9e";
        assert!(encoding_is_guessed(latin));
        assert_eq!(detected_label(latin), "windows-1252");
        assert!(!encoding_is_guessed("Café".as_bytes()));
        assert_eq!(detected_label("Café".as_bytes()), "utf-8");
        assert_eq!(detected_label(&[0xFF, 0xFE, b'a', 0]), "utf-16le");
        assert_eq!(preview(latin, "windows-1251", 4).as_deref(), Some("Caf\u{439}"));
        assert_eq!(preview(latin, "klingon", 4), None);
        let doc = import_as(latin, "windows-1251").unwrap();
        assert!(doc.plain_text(wordcraft_doc::StoryRef::Body).starts_with("Caf\u{439}"));
    }
}
