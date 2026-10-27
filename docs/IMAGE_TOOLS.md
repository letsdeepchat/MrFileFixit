# Image tools

The Image tools screen (`:feature-image`) has one tab per tool. All of them run on the phone and work on one
image or a batch; batches above `BatchPolicy.INLINE_LIMIT` run in WorkManager so they finish if the app is closed.

| Tab | What it does |
| --- | --- |
| Resize, Crop, Rotate, Compress | Unchanged. |
| Convert | JPG, PNG, WebP and HEIC output (HEIC needs a hardware encoder). HEIC, WebP, PNG and JPG input. **PDF** puts every picked image into one PDF, a page per image at the image's own size. |
| Edit | A look (original, high-contrast black and white, document), brightness, contrast and saturation, a line of text in a chosen font and colour, and the exam name and date bar. Live before/after preview. |
| Enhance | Natural Enhance: denoise, clarity, gentle spot healing, auto contrast and vibrance, with a 0–100% strength slider and optional 2x or 4x Lanczos upscaling. Live before/after preview. |
| Remove background | Cuts out the person (ML Kit selfie segmentation, bundled model) and puts white, exam blue, a see-through background, any colour or a picked photo behind them. Live before/after preview. |
| Remove metadata | Unchanged. |
| Text (OCR) | Opens the OCR screen ([OCR.md](OCR.md)). |

## Edit

* **Document** is the Scan tool's clean-paper filter (`ScanFiltering`, shared with Scan): an illumination map
  flattens shadows and yellow paper to white while ink stays dark.
* **Black and white**, **brightness**, **contrast** and **saturation** are one 4x5 colour matrix
  (`ImageEffectMath.colorMatrix`), applied with `ColorMatrixColorFilter`. Each slider runs from -100 to 100.
  Black and white is a real monochrome print: the photo's 1st and 99th luminance percentiles
  (`ImageEffectMath.grayLevels`) are stretched to black and white with 15% extra punch, so even a dull photo turns
  clearly black and white.
* **Text on the photo**: one line at the top, middle or bottom, sized to 6% of the photo height and shrunk to fit
  the width. Six system fonts (sans, serif, mono, condensed, handwriting, casual) and eight colour swatches plus a
  custom hue and shade picker. Every colour gets a soft outline in the opposite tone, so it reads on any photo.
* A live before/after preview right under the photo updates a moment after each change.
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
4. **Auto contrast, exposure and vibrance** (`ToneVibrance.kt`). The 0.5th to 99.5th luminance percentiles are
   stretched toward the full range (at most 1.4x), pivoting on the photo's own mean, so flat photos gain depth
   without getting brighter. Only a clearly dark or washed-out photo has its brightness nudged (at most 18
   levels). Dull colours get up to 30% more saturation, rich ones less. Skin takes only half of the contrast change
   and a quarter of the vibrance, so faces keep their natural tone, and the hue of every pixel is kept.

The **Strength** slider (0–100%) scales every step, and 0% returns the photo unchanged. **Output size** can be the
original size, or 2x or 4x with Lanczos-3 resampling (`LanczosResampler`, in bands so memory stays low). The long
side is capped at 3072 px. While the tool is open, a live before/after preview is computed on a 1024 px copy a
moment after each change. Drag the split line to compare, or tap to open full screen with pinch-to-zoom.

This is classic signal processing, not an AI model. The app has no network access to download one.

Tests: `NaturalEnhancerTest` checks that zero strength is an identity, that colour means do not move at any
strength, that dark skin is not lightened, that noise drops while texture stays, and that small spots are softened
but text is not. `ToneVibranceTest` checks that a dull photo gains contrast and colour, hue is kept, mean
brightness stays, a dark photo is lifted only a little, and skin keeps its tone. `LanczosResamplerTest` covers the
upscaling. On the emulator, `ImageProcessorTest` runs Enhance through the real engine (a warm photo keeps its tone,
a flat photo gains contrast) and checks that black and white is truly grey and higher in contrast.

## Remove background

`BackgroundRemoval` (`core-processing`) runs ML Kit selfie segmentation on a copy at most 1024 px on its long side.
The model is bundled in the app (`com.google.mlkit:segmentation-selfie`), so nothing is downloaded and the photo
never leaves the phone. The mask is read back with bilinear interpolation and a smoothstep edge
(`BackgroundMath`), whose width follows the **Edge softness** slider, then the person is blended over:

* **White** or **Exam blue** (the light blue many exam and passport forms ask for), or any **Colour**.
* **Transparent**: saved as PNG (or WebP if chosen), since JPEG has no transparency.
* **Your photo**: decoded and cover-scaled to exactly the photo's size.

It is made for people (portraits, selfies, exam photos). When no person is found, the preview and the run say so
(`AppError.NoPersonFound`) instead of returning an empty background. Tests: `BackgroundMathTest` (edge curve,
mask scaling, blending, see-through, the no-person check and JSON round trips) and `BackgroundOptionsTest`.

## Previews

Every image tool shows the picked photo right away, with its size, format and exact byte count. After a run, a
before/after card shows both versions, and each opens full screen with pinch-to-zoom. A finished run belongs to
the tab that made it: switching tabs shows that tool's own options, and the results come back on their own tab
(`ImageToolUiState.shownJob`, tested in `ImageToolUiStateTest`). PDF results are shown as
rendered pages (`PdfRenderer`). Every output is checked to be non-empty before it is offered for saving
(`requireContent`).

## Order and batches

Effects run after the geometry (rotate, crop, resize), in this order: background replacement, Natural Enhance,
document filter, colour matrix, name and date bar, text. They are part of `ImageRequest` and `ImageBatchSpec` as `ImageEffects`, which is
serializable so background batches carry them; batch files written before this change still read back with no
effects.
