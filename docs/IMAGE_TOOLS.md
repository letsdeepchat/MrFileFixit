# Image tools

The Image tools screen (`:feature-image`) has one tab per tool. All of them run on the phone and work on one
image or a batch; batches above `BatchPolicy.INLINE_LIMIT` run in WorkManager so they finish if the app is closed.

| Tab | What it does |
| --- | --- |
| Resize, Crop, Rotate, Compress | Unchanged. |
| Convert | JPG, PNG, WebP and HEIC output (HEIC needs a hardware encoder). HEIC, WebP, PNG and JPG input. **PDF** puts every picked image into one PDF, a page per image at the image's own size. |
| Edit | A look (original, black and white, document), brightness, contrast and saturation, a line of text, and the exam name and date bar. |
| Enhance | Natural Enhance: denoise, clarity and gentle spot healing on luminance only, with a 0–100% strength slider and optional 2x or 4x Lanczos upscaling. |
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

Natural Enhance (`core-domain/.../image/enhance/NaturalEnhancer.kt`) restores clarity without changing colour or
skin tone. It works only on luminance (Y of BT.601 YCbCr) and adds the final change equally to R, G and B, so Cb and
Cr, the colour of each pixel, stay exactly as they were. Dark and brown skin is never lightened or tinted.

1. **Edge-preserving denoise.** A self-guided filter on Y removes sensor noise and JPEG speckle. Its strength
   follows the noise measured in the picture, and part of the original is mixed back so skin keeps its texture.
2. **Clarity.** A two-scale unsharp mask with noise coring restores fine detail and local contrast. It is softer on
   skin, so faces do not look plastic.
3. **Gentle spot healing.** Inside skin-coloured areas, small dark spots are lifted part of the way toward their
   surroundings. Eyes, brows, lips, hair and text are left alone.

The **Strength** slider (0–100%) scales every step, and 0% returns the photo unchanged. **Output size** can be the
original size, or 2x or 4x with Lanczos-3 resampling (`LanczosResampler`, in bands so memory stays low). The long
side is capped at 3072 px. While the tool is open, a live before/after preview is computed on a 1024 px copy a
moment after each change. Drag the split line to compare, or tap to open full screen with pinch-to-zoom.

This is classic signal processing, not an AI model. The app has no network access to download one.

Tests: `NaturalEnhancerTest` checks that zero strength is an identity, that colour means do not move at any
strength, that dark skin is not lightened, that noise drops while texture stays, and that small spots are softened
but text is not. `LanczosResamplerTest` covers the upscaling. On the emulator, `ImageProcessorTest` runs Enhance
through the real engine and checks the channel means again.

## Previews

Every image tool shows the picked photo right away, with its size, format and exact byte count. After a run, a
before/after card shows both versions, and each opens full screen with pinch-to-zoom. PDF results are shown as
rendered pages (`PdfRenderer`). Every output is checked to be non-empty before it is offered for saving
(`requireContent`).

## Order and batches

Effects run after the geometry (rotate, crop, resize), in this order: Natural Enhance, document filter, colour matrix,
name and date bar, text. They are part of `ImageRequest` and `ImageBatchSpec` as `ImageEffects`, which is
serializable so background batches carry them; batch files written before this change still read back with no
effects.
