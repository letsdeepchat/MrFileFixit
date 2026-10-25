# Text recognition (OCR)

Text recognition has its own screen (`:feature-ocr`), opened from three places:

* **PDF tools**, last tile of the Convert group.
* **Image tools**, the last tab ("Text (OCR)").
* **Scan**, under the OCR section: "Searchable PDF, Word and more languages" hands over the scanned pages (or the
  files picked there), so nothing has to be picked twice. Scan keeps its quick inline OCR as before.

Inputs are JPG, PNG, WebP and PDF, picked with the system file picker (no storage permission).

## Languages

All recognizers are Google ML Kit's **bundled** models, so recognition is fully offline and never downloads a
model. The app has no `INTERNET` permission of its own, which is why there is no in-app model downloader.

| Choice | Recognizer | Languages |
| --- | --- | --- |
| Auto-detect | All of the below on the first page | Picks the best one, then reads the remaining pages with it |
| English and Latin | `text-recognition` | English, French, German, Spanish, Portuguese, Italian, Dutch, Polish, Turkish, Indonesian, Malay, Swahili and other Latin-script languages |
| Hindi and Devanagari | `text-recognition-devanagari` | Hindi, Marathi, Nepali, Sanskrit |
| Chinese | `text-recognition-chinese` | Simplified and Traditional Chinese |
| Japanese | `text-recognition-japanese` | Japanese |
| Korean | `text-recognition-korean` | Korean |

Each non-Latin recognizer also reads the English words on a page.

**Auto-detect** (`ScriptDetector`, pure JVM, unit tested) counts the letters each recognizer found in its own
script. A non-Latin recognizer wins only with at least 6 such letters and at least a quarter as many as the Latin
recognizer's Latin letters, so a few stray marks on an English page do not switch scripts. Chinese and Japanese
models both read Han characters; Japanese wins only when its text also has kana. A blank first page defers the
choice to the next page.

Scripts ML Kit does not cover (Arabic, Cyrillic, Bengali, Tamil, Telugu and others) are not offered. Adding them
would need a second engine such as Tesseract with its own language files.

## Outputs

* **Copy** and **Share text**. Copied text is marked sensitive, so Android 13+ hides it in the clipboard preview.
* **Searchable PDF.** Original PDF pages are kept as they are (pages with a `/Rotate` entry are re-drawn as JPEG);
  each image becomes an A4-wide page. The recognized lines are drawn over the page with the platform
  `PdfDocument` at the lowest non-zero alpha, each line scaled to its box, and stamped onto the pages with PDFBox
  as a form XObject. The page looks unchanged, and any PDF reader can search, select and copy the text. The
  platform writer shapes and embeds fonts for every script, which PDFBox alone cannot do for Devanagari or CJK.
* **Text file** (UTF-8), **Word** (one paragraph per line, a page break per source page) and a **text-only PDF**,
  written by the same `DocumentExporter` the resume builder uses.

Exports go to a job folder in the cache that is deleted when a new export starts, the screen is cleared or closed,
and on the next app start at the latest.

## Size

The three extra bundled recognizers add to the APK; Play's per-ABI split of the App Bundle keeps the download to
one architecture's native code.
