# Resume & Biodata

The **Resume & Biodata** tab (also on Home) has two builders. Its first screen offers two large actions,
**Create a professional resume** and **Create a biodata**, followed by the saved drafts of each kind. Everything
runs on the phone, so drafts, photos and exports never leave it.

The resume builder starts from one of 15 templates and exports PDF or Word. The biodata builder is described in
[Biodata](#biodata) below.

## Templates

One template per field, each with its own section order, sample text, font and accent colour:

| Template | Section order (default) | Photo frame |
| --- | --- | --- |
| Software and IT | Summary, Skills, Experience, Projects, Education, Certifications | |
| Core engineering | Summary, Experience, Skills, Projects, Education, Certifications | |
| Arts | Summary, Exhibitions, Experience, Education, Skills, Awards | ✓ |
| Marketing | Summary, Experience, Skills, Education, Certifications | |
| Finance and accounting | Summary, Experience, Education, Skills, Certifications | |
| Data science and AI | Summary, Skills, Experience, Projects, Education, Certifications | |
| Healthcare | Summary, Licences and Certifications, Clinical Experience, Education, Skills | ✓ |
| Teaching | Summary, Teaching Experience, Education, Certifications, Skills | |
| Graphic design | Summary, Skills, Experience, Selected Work, Education | ✓ |
| Fresh graduate | Career Objective, Education, Academic Projects, Internships, Skills, Certifications, Achievements | |
| Business and management | Summary, Experience, Education, Skills, Certifications | |
| Legal | Summary, Bar Admission, Legal Experience, Education, Publications, Skills | |
| Human resources | Summary, Experience, Skills, Education, Certifications | |
| Operations and supply chain | Summary, Experience, Skills, Education, Certifications | |
| Academic CV | Research Interests, Education, Academic Appointments, Publications, Grants and Awards, Teaching, Skills | ✓ |

Changing the template later keeps all text and the user's section order; only the look (font and accent) changes.

## Why the output is ATS-friendly

Applicant tracking systems read text in order and look for standard headings. Every template therefore:

- is a single column of real, selectable text (nothing is drawn as a picture except the optional photo);
- uses real heading paragraphs for section names and real bullet lists in both PDF and DOCX;
- puts the name and contact details in the body, not in a page header or a text box;
- writes email addresses and links as real links;
- places the optional photo in a borderless two-cell row beside the name, so parsers read the name first.

## Editing

- **Sections:** Summary, Experience, Education, Skills, Projects, Certifications and any number of Custom sections.
  Each can be renamed, hidden from the export, moved up or down, or deleted; entries inside a section move too.
- **Look:** font (sans, serif, narrow, mono), body text size 9–13 pt in half points (headings scale with it), accent
  colour, and A4 or US Letter paper.
- **Photo:** picked with the system photo picker, placed in a 35 × 45 frame with pinch and drag, then cropped and
  resized to 413 × 531 px (the 350 × 450 and 413 × 531 sizes asked for in India both fit this frame) and stored as
  JPEG with the draft.
- Drafts save automatically (about half a second after the last change, and when the editor closes).

## Export and re-opening

- **PDF** is laid out by the same engine as Word to PDF (`DocumentPdfWriter`) and previewed in the app from the
  exported file. **DOCX** is written by `:core-office`'s `DocxWriter`.
- Exports go to the job cache first; Save uses the system "Save as" dialog, Share uses the share sheet. Job files
  are deleted when the next export starts, when the editor closes and by the start-up sweep.
- The DOCX carries the draft's JSON in a custom XML part, so **Open an exported Word file** turns it back into an
  editable draft (as a new draft, without the photo). Other Word files are rejected with a clear message.

## Storage

Drafts are `files/resumes/<id>.json` and `files/resumes/<id>.jpg` in app-private storage. Writes go to a
temporary file that is then renamed. Backup is disabled app-wide, so drafts stay on the phone. The photo can only
be stored from this app's own cache (the cropped job output).

## Biodata

There are five designs (`BiodataTemplates`):

| Design | Kind | Look |
| --- | --- | --- |
| Traditional | Marriage | Serif, maroon and gold, ornate double border with corner diamonds, ornamental headings |
| Classic | Marriage | Serif, navy and gold, double border, headings on a tinted band |
| Modern | Marriage | Sans, teal, single frame, underlined headings |
| Photographer | Portfolio | Sans, charcoal and amber, no border, a large showcase photo, then work samples |
| Creative | Portfolio | Sans, rust and indigo, corner brackets, banded headings, photo grid |

**Marriage biodata** has these sections:

* Personal details, with age worked out from the date of birth.
* Cultural and astrological details (religion, caste, gotra, rashi, nakshatra, manglik and so on). The section can
  be switched off with one switch, and each of its fields can be hidden on its own.
* Education and career.
* Family background.
* Partner expectations.
* Contact details.

**Portfolio** has an about section, skills, equipment and services, client experience, and work samples.

**Photos.** Each biodata takes 1 to 5 photos. Each photo is cropped in the app with a portrait 3:4, square, 4:3
or original frame, and stored as a JPEG with a long side of at most 1600 px. A marriage biodata starts with a
portrait frame. A portfolio starts with a square profile photo, and later photos keep their own shape. Any photo
can be made the main one, moved or removed. Printed photos are fitted to their slot without distortion.

**Preview and export.** The editor has an Edit/Preview switch. Preview renders the real PDF with `PdfRenderer`,
and while it is shown the PDF is made again a moment after each change. Export writes a high-resolution PDF
(photos keep their full stored resolution) or a Word file. The printed labels follow the app language, English
or Hindi.

Drafts are stored as `files/biodata/<id>.json`, with photos in `files/biodata/<id>-<key>.jpg`. Ids and keys are
letters and digits only. Orphaned photos are deleted on save.

## Code map

| Piece | Where |
| --- | --- |
| Model, JSON, edits, templates, renderer, use cases | `core-domain/.../resume/` |
| PDF and DOCX export, embedded-data reader | `core-processing/.../convert/AndroidDocumentExporter.kt` |
| Draft storage | `core-storage/.../resume/FileResumeRepository.kt` |
| Biodata model, templates, renderer, use cases | `core-domain/.../biodata/` |
| Biodata storage | `core-storage/.../biodata/FileBiodataRepository.kt` |
| Page borders (biodata designs) | `core-processing/.../convert/PageBorderPainter.kt` |
| UI | `feature-resume` (`ResumeTab`, `BiodataScreens.kt`, `BiodataViewModel`) |

Tests: `ResumeTest` (domain), `ResumeDocxTest` (every template to DOCX and back), `ResumeViewModelTest`,
and on the emulator `ResumeExportTest` (every template to PDF, checked with PDFBox text extraction) and
`ResumeScreensTest`. For biodata: `BiodataModelTest` and `BiodataRendererTest` (every design with 1 to 5 photos),
`BiodataViewModelTest`, and on the emulator `BiodataExportTest` (every design with 1 to 5 photos, written to PDF and
Word and read back).
