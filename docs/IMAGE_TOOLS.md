# Image tools

The Image tools screen (`:feature-image`) has one tab per tool. All of them run on the phone and work on one
image or a batch; batches above `BatchPolicy.INLINE_LIMIT` run in WorkManager so they finish if the app is closed.

| Tab | What it does |
| --- | --- |
| Resize, Crop, Rotate, Compress | Unchanged. |
| Convert | JPG, PNG, WebP and HEIC output (HEIC needs a hardware encoder). HEIC, WebP, PNG and JPG input. **PDF** puts every picked image into one PDF, a page per image at the image's own size. |
| Edit | A look (original, black and white, document), brightness, contrast and saturation, a line of text, and the exam name and date bar. |
| Enhance | 2x or 4x upscaling with sharpening. |
| Remove metadata | Unchanged. |
| Text (OCR) | Opens the OCR screen ([OCR.md](OCR.md)). |

## Edit

* **Document** is the Scan tool's clean-paper filter (`ScanFiltering`, shared with Scan): an illumination map
  flattens shadows and yellow paper to white while ink stays dark.
* **Black and white**, **brightness**, **contrast** and **saturation** are one 4x5 colour matrix
  (`ImageEffectMath.colorMatrix`), applied with `ColorMatrixColorFilter`. Each slider runs from -100 to 100.
* **Text on the photo**: one line at the top, middle or bottom, sized to 6% of the photo height and shrunk to fit
  the width. Light text gets a dark outline so it reads on bright photos.
* **Name and date bar**: the strip many exam forms ask for. A white (or black) band across the bottom fifth of the
  photo with up to two centred lines, name and date (today's date is filled in). With "Keep the file between 20 and
  50 KB" on, the output is a JPEG searched into 20 KiB to 50 kB, so it is inside the window whichever way a portal
  counts a kilobyte; if even the lowest quality is too big the photo is scaled down, and if the smallest file is
  too small it is padded.

## Enhance

Enhance enlarges the photo in 2x bilinear steps and then applies an unsharp mask (`ImageEffectMath.sharpen`, done
in bands of 256 rows so a 4x result needs little extra memory). The long side is capped at 3072 px. This is
classic upscaling, not an AI model: no super-resolution model is bundled yet, and the app has no network access to
download one.

## Order and batches

Effects run after the geometry (rotate, crop, resize), in this order: upscale, document filter, colour matrix,
name and date bar, text. They are part of `ImageRequest` and `ImageBatchSpec` as `ImageEffects`, which is
serializable so background batches carry them; batch files written before this change still read back with no
effects.
