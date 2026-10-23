<p align="center">
  <img src="assets/app-icon/wordcraft-1024.png" alt="WordCraft's app icon" width="128">
</p>

<h1 align="center">WordCraft for Android</h1>

<p align="center">
  <b>WordCraft-android is an open-source reimplementation of the Microsoft Word Android app, created using the same Rust engine, <a href="https://github.com/storytold/wordcraft">WordCraft</a>, as the desktop app, and built with a Kotlin/Jetpack Compose UI framework.</b><br>
  Many credits to the original maintainers, <a href="https://github.com/storytold">storytold</a>.
</p>

<p align="center">
  <a href="https://github.com/artcraft-android/wordcraft-android/releases"><img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="60"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/artcraft-android/wordcraft-android"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="60"></a>
</p>

<p align="center">
  <img alt="Rust engine" src="https://img.shields.io/badge/engine-Rust-lightblue?style=flat&logo=rust&logoColor=white">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/UI-Kotlin%20%C2%B7%20Jetpack%20Compose-blue?style=flat">
  <img alt="Minimum: Android 10 (API 29)" src="https://img.shields.io/badge/min-Android%2010%20%28API%2029%29-blue?style=flat">
  <img alt="Target: Android 16 (API 36)" src="https://img.shields.io/badge/target-Android%2016%20%28API%2036%29-blue?style=flat">
  <img alt="License: MIT OR Apache-2.0" src="https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue?style=flat">
  <img alt="Status: beta" src="https://img.shields.io/badge/status-beta-lightblue?style=flat">
</p>

<p align="center">
  <a href="#reading-and-writing">Reading &amp; writing</a> ·
  <a href="#insert-and-tables">Insert &amp; tables</a> ·
  <a href="#review">Review</a> ·
  <a href="#draw-and-read-aloud">Draw &amp; Read Aloud</a> ·
  <a href="#tell-me-and-home">Tell Me &amp; Home</a> ·
  <a href="#planned-features">Planned features</a> ·
  <a href="#building-instructions">Building</a> ·
  <a href="#license">License</a>
</p>

<br>

<p align="center">
  <img src="docs/images/editor.png" alt="The editor in Print View: the first page of The Open Studio Handbook on a dark page, with its header, headings, a bulleted list, a table of shared equipment and a numbered list, and the floating toolbar with Insert, Dictate, Bold, Italic, Underline, Highlight, Font Color, Bullets and Numbering" width="250">
  <br>
  <sub>Editing in Print View: dark pages, the document's own layout, and the floating toolbar.</sub>
</p>

<br>

## Reading and writing

<table align="center">
<tr>
<td align="center"><img src="docs/images/mobile-view.png" alt="Mobile View: The Open Studio Handbook reflowed to the phone's width in large type, the caret at the title, and the floating toolbar" width="250"></td>
<td align="center"><img src="docs/images/home-tab.png" alt="The Home tab under the page: the font and size, Bold, Italic, Underline and Strikethrough, Highlight, Font Color, Clear Formatting, More Formatting, Bullets and Numbering" width="250"></td>
</tr>
<tr>
<td align="center"><sub>Mobile View: the text reflowed to fit the phone</sub></td>
<td align="center"><sub>The Home tab: fonts, sizes, colours, lists and styles</sub></td>
</tr>
</table>

<br>

## Insert and tables

<table align="center">
<tr>
<td align="center"><img src="docs/images/insert-tab.png" alt="The Insert tab: Blank Page, Scan Document, Table, Pictures, Shapes, Text Box, Link and Comment" width="250"></td>
<td align="center"><img src="docs/images/table.png" alt="The caret in the equipment table and the Table tab: Delete, Insert Rows and Columns, Merge Cells, Split Cells, Style Options, Table Styles and Shading" width="250"></td>
</tr>
<tr>
<td align="center"><sub>Insert: pictures, camera scans, tables, shapes, links and more</sub></td>
<td align="center"><sub>Tables: rows and columns, merging, styles and shading</sub></td>
</tr>
</table>

<br>

## Review

<table align="center">
<tr>
<td align="center"><img src="docs/images/comments.png" alt="Comments: Quiet hours highlighted in the text, and the Comments pane with a comment from Mara Okafor, a reply from Theo Park, and Reply, Resolve and Delete" width="250"></td>
<td align="center"><img src="docs/images/review.png" alt="A tracked change in the table, Two days' notice inserted and 48 h notice struck through, and the Review tab with Resolve, Track Changes turned on, All Markup, Accept and Reject" width="250"></td>
</tr>
<tr>
<td align="center"><sub>Comments with replies</sub></td>
<td align="center"><sub>Track changes, accept and reject</sub></td>
</tr>
</table>

<br>

## Draw and Read Aloud

<table align="center">
<tr>
<td align="center"><img src="docs/images/draw.png" alt="A red pen circle drawn round the tracked change, and the Draw tab: Draw with Touch, Stroke Eraser, Pen and Highlighter, four pen colours, Color and Thickness" width="250"></td>
<td align="center"><img src="docs/images/read-aloud.png" alt="Read Aloud on the first page: the sentence being read highlighted, the word being spoken marked, and the bar with previous, pause, next, settings and close" width="250"></td>
</tr>
<tr>
<td align="center"><sub>Draw with touch: pens and highlighters</sub></td>
<td align="center"><sub>Read Aloud with the phone's own voices</sub></td>
</tr>
</table>

<br>

## Tell Me and Home

<table align="center">
<tr>
<td align="center"><img src="docs/images/tellme.png" alt="Tell Me: a search field, Recently Used (Headings, Equation) and Suggested (Styles, Table, Pictures, Track Changes, Word Count)" width="250"></td>
<td align="center"><img src="docs/images/home.png" alt="Home: Recent with two documents, and the New sheet: Blank document, Sample, Letter, Resume and Report" width="250"></td>
</tr>
<tr>
<td align="center"><sub>Tell Me: every command, searched by name</sub></td>
<td align="center"><sub>Home: recent documents, and new ones from templates</sub></td>
</tr>
</table>

<br>

## Planned features

WordCraft for Android is in beta.

| Area | Status |
|---|---|
| Open .docx, .docm, .dotx, .doc, .rtf, .odt and .txt; AutoSave; recent documents; Add to Home screen | ✅ |
| Mobile View and Print View, dark pages, zoom, the Headings pane, Find and Replace | ✅ |
| Home: fonts, sizes, bold, italic, underline and the rest, highlight and font colour, styles, bullets and numbering, spacing and indents | ✅ |
| Insert: pictures and camera scans, tables, shapes, text boxes, links, comments, header and footer, page numbers, equations, footnotes and endnotes | ✅ |
| Pictures, icons, shapes, charts and SmartArt: styles, effects, fill and outline, crop, wrap text, arrange, size, alt text | ✅ |
| Draw with touch: pens and highlighters | ✅ |
| Layout: margins, orientation, paper size, columns, breaks | ✅ |
| Review: spelling, comments with replies, track changes, accept and reject, word count | ✅ |
| Read Aloud with the phone's voices, dictation | ✅ |
| Tell Me: search every command by name | ✅ |
| Templates: Sample, Letter, Resume, Report; Save As, share and print | ✅ |
| Copilot and other generative AI, cloud storage, accounts, co-authoring | 🚫 WordCraft is a local app |

<sub>✅ works today · 🚧 in progress · ⬜ not started · 🚫 not planned</sub>

<br>

## Building Instructions
*One Gradle command builds the whole app: it compiles the Rust engine with [cargo-ndk](https://github.com/bbqsrc/cargo-ndk), generates the Kotlin bindings and fetches the open document fonts (Carlito, Caladea, Liberation) from pinned sources.*

* Clone this repository
* Make sure you have at least 4 GB of free disk space
* enter `apps/wordcraft-android/android` and run `./gradlew assembleRelease`, or open that folder in Android Studio and press Run.

Build time dependencies:

<details>
  <summary>required tools</summary>

  ```
      rustup (it installs the Rust release and Android targets rust-toolchain.toml names)
      cargo install cargo-ndk --version 4.1.2 --locked
      Android SDK (API 37) and NDK 29.0.14206865
      JDK 17 or newer
  ```
</details>

*Builds are only tested on Windows 11.*

<br>

## License

[MIT](LICENSE-MIT) OR [Apache-2.0](LICENSE-APACHE), at your option. WordCraft for Android is not affiliated with Microsoft;
Word is a trademark of Microsoft Corporation.

<br>

## Misc info for myself

Try to use [semantic commit](https://kapeli.com/cheat_sheets/Semantic_Commits.docset/Contents/Resources/Documents/index)
messages. Use `feat:` / `fix:` / `chore:` / `docs:` / etc.
