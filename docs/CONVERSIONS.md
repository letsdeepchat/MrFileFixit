# Office conversions

PDF tools → Convert offers six conversions. All of them run on the phone: office files are parsed by
`:core-office` (pure Kotlin, no third-party code), pages are laid out with Android's text engine and written with
`android.graphics.pdf.PdfDocument`, and PDFs are read with PDFBox. Nothing is uploaded.

| Tool | Reads | Writes |
| --- | --- | --- |
| Word to PDF | DOCX, DOC (Word 97–2003, and Word 6/95 text) | PDF |
| PDF to Word | PDF | DOCX |
| PowerPoint to PDF | PPTX, PPT (97–2003) | PDF, one page per slide at the slide's size |
| PDF to PowerPoint | PDF | PPTX, one slide per page |
| Excel to PDF | XLSX, XLS (97–2003 and Excel 5/95), CSV | PDF |
| PDF to Excel | PDF | XLSX (one sheet per page) or CSV (all pages in one file) |

## What carries over

Office to PDF is a re-layout, not a print of what Word or PowerPoint would draw. It keeps:

- **Word:** paragraphs with bold, italic, underline, size, colour and font family (sans, serif, mono and condensed
  map to the Android system fonts, which also cover Hindi and other Indian scripts, Arabic, Cyrillic and CJK).
  Headings, alignment, spacing, indents, bullet and numbered lists (numbering restarts like Word's), shading and bottom
  rules, tables (column widths, shading, a repeated header row; merged cells keep their text, but the lines between them still show), inline pictures (JPEG, PNG, GIF, BMP and
  WebP), page breaks, and the page size and margins.
- **PowerPoint:** slide size, background colours, text boxes and placeholders with their position, size and vertical
  anchor, the theme's text sizes and bullets, filled or outlined shapes, pictures and tables. A `.ppt` file gives the
  slide titles and body text on a plain slide.
- **Excel:** cell values as Excel shows them (numbers, dates, percentages, booleans and cached formula results), bold
  cells and column widths. Each sheet starts a new page, a bold first row repeats on every page, sheets with seven or
  more columns print in landscape, and sheets wider than ten columns are split into bands of ten (labelled A–J,
  K–T and so on).

PDF to office reads the text the PDF draws, with its positions:

- **Word:** lines join into paragraphs, larger or bold lines become headings, bullet characters become real list items,
  hyphenated line ends rejoin, two-column pages are read column by column, and placed pictures are kept.
- **PowerPoint:** each page becomes a picture of the page *without its text*, with every line of text as its own
  editable text box at the same position. The slide looks like the page and the words can be edited.
- **Excel:** text pieces separated by wide gaps are cells; columns are aligned across all rows of the page. Numbers with
  thousands separators, currency symbols (₹ $ € £ ¥), percentages and accounting negatives such as `(3)` become numbers.

## Known limits

- Charts, SmartArt, WordArt, equations, comments, tracked changes, headers and footers, footnotes, floating (anchored)
  shapes and text boxes in Word, and animations are not drawn. EMF, WMF and TIFF pictures are left out.
- Fonts are replaced by the closest Android system family, so line breaks can differ from Word's.
- Password-protected Office files cannot be opened (the app shows "This document is password-protected").
  Password-protected PDFs need PDF tools → Remove password first.
- A scanned PDF has no text. PDF to Excel reports this; PDF to Word gives the page pictures; PDF to PowerPoint gives
  slides that cannot be edited. Use Scan & OCR first.
- In PDF to PowerPoint, text drawn inside form XObjects stays in the background picture and also appears as a text
  box.
- PDF to Excel finds tables by alignment. Ruled lines, merged cells and multi-line cells are not detected.

## Safety limits

Office files are untrusted input. The XML parser rejects DTDs, so external entities and entity-expansion bombs cannot
run. ZIP packages are capped at 4 000 entries, 64 MiB per entry and 256 MiB expanded in total. Further caps are 500
slides, 100 000 rows, 1 000 columns, 200 000 paragraphs and 2 000 output pages. Above a cap the app shows "This document
is too large or complex to convert on this phone" instead of running out of memory. Macros, embedded OLE objects and
external links are never run or followed.

## Code map

| Piece | Where |
| --- | --- |
| Shared document model (`RichDocument`, `Presentation`, `Workbook`) | `core-domain/.../document/` |
| Conversion API and use case | `core-domain/.../convert/DocumentConversion.kt` |
| DOCX, PPTX and XLSX readers and writers, CSV | `core-office/.../docx/`, `pptx/`, `xlsx/` |
| DOC, PPT and XLS readers | `core-office/.../legacy/` |
| PDF glyphs → paragraphs, tables and slides | `core-office/.../pdftext/` |
| Page layout and PDF writing; PDFBox text and picture reading | `core-processing/.../convert/` |
| UI | `feature-pdf` (`PdfTool.WORD_TO_PDF` and the other conversion tools) |

The pure Kotlin parts are unit-tested on the JVM (`./gradlew :core-office:test`). The Android parts are covered by
`DocumentConverterTest`, an instrumented test that runs every direction on the CI emulator.
