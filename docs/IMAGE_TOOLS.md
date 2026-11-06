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
* **Text on the photo**: one line at the top, middle or bottom, 2–20% of the photo height (6% by default, the
  **Text size** slider) and shrunk to fit the width. Six bundled fonts that cover both English and Hindi
  (Devanagari): Modern (Poppins), Classic (Martel), Typewriter (Glegoo), Tall (Khand), Handwriting (Kalam) and Fun
  (Yatra One), about 1.7 MB in all, SIL Open Font License (`core-processing/src/main/assets/fonts/OFL.txt`). The
  phone's own font families were dropped because many phones map several of them to one font, and none of them
  changes Hindi text. A sample line shows the typed text in the chosen font and colour as you pick them. Eight
  colour swatches plus a custom hue and shade picker; every colour gets a soft outline in the opposite tone, so it
  reads on any photo.
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
4. **Detail recovery.** A stronger unsharp mask at the scale a soft photo lost (1 to 4 px, growing with the photo)
   with noise-aware coring, and overshoot control: a pixel may not leave the range of its 3 x 3 neighbourhood by
   more than 6 levels, which keeps strong sharpening free of halos and bright rims. Skin gets 15% of it.
5. **Large-scale clarity** (`LocalContrast.kt`). The luminance is split into an edge-preserving base (a guided
   filter computed on a 256 px copy, with its coefficients read back at full size, a "fast guided filter") and the
   detail above it, and the detail is amplified (up to 2.4x at full strength). Flat and hazy photos look crisp and
   three-dimensional. Skin, deep shadows and bright highlights get less of it.
6. **Auto contrast, exposure and vibrance** (`ToneVibrance.kt`). The 0.5th to 99.5th luminance percentiles are
   stretched toward the full range (at most 1.4x), pivoting on the photo's own mean, so flat photos gain depth
   without getting brighter. Only a clearly dark or washed-out photo has its brightness nudged (at most 18
   levels). Dull colours get up to 30% more saturation, rich ones less. Skin takes only half of the contrast change
   and a quarter of the vibrance, so faces keep their natural tone, and the hue of every pixel is kept.

The **Strength** slider (0–100%) scales every step, and 0% returns the photo unchanged. **Output size** can be the
original size, or 2x or 4x with Lanczos-3 resampling (`LanczosResampler`, in bands so memory stays low). The long
side is capped at 3072 px. While the tool is open, a live before/after preview is computed on a 1024 px copy a
moment after each change. Drag the split line to compare, or tap to open full screen with pinch-to-zoom.

This is classic signal processing, not an AI model. The app has no network access to download one.

Tests: `LocalContrastTest` checks that a hazy picture gains local contrast without changing its brightness, that
banded and whole-picture results match, that only luminance moves and that skin gets much less. `NaturalEnhancerTest`
also checks that a blurred edge gets clearly steeper without dark or bright rims, that zero strength is an identity, that colour means do not move at any
strength, that dark skin is not lightened, that noise drops while texture stays, and that small spots are softened
but text is not. `ToneVibranceTest` checks that a dull photo gains contrast and colour, hue is kept, mean
brightness stays, a dark photo is lifted only a little, and skin keeps its tone. `LanczosResamplerTest` covers the
upscaling. On the emulator, `ImageProcessorTest` runs Enhance through the real engine (a warm photo keeps its tone,
a flat photo gains contrast) and checks that black and white is truly grey and higher in contrast.

## Remove background

`BackgroundRemoval` (`core-processing`) runs ML Kit selfie segmentation on a copy at most 640 px on its long side.
The model is bundled in the app (`com.google.mlkit:segmentation-selfie`), so nothing is downloaded and the photo
never leaves the phone. The model's mask is coarse, so `PersonMatte` (`core-domain`) cleans it up in plain code:

1. **Edge refinement.** A colour guided filter (He et al.) with the photo as the guide snaps the outline to the
   photo's real edges of hair, ears and shoulders and gives see-through strands partial opacity. Its coefficients
   are computed on the 640 px copy and read back at full size, so the edge follows full-resolution detail.
2. **Colour clean-up.** Edge pixels mix the person with the old background, which leaves a coloured fringe on a new
   background. Blur-fusion foreground estimation (Forte and Pitié 2020) estimates the local person and background
   colours and removes the old background's share.

A smoothstep curve (`BackgroundMath`), whose width follows the **Edge softness** slider, then sets how firm the
edge is, and the person is blended over:

* **White** or **Exam blue** (the light blue many exam and passport forms ask for), or any **Colour**.
* **Transparent**: saved as PNG (or WebP if chosen), since JPEG has no transparency.
* **Your photo**: decoded and cover-scaled to exactly the photo's size.

It is made for people (portraits, selfies, exam photos). When no person is found, the preview and the run say so
(`AppError.NoPersonFound`) instead of returning an empty background. Tests: `PersonMatteTest` (the refined
outline has less than half the error of the coarse mask, full-size reads keep the edge sharp, an edge pixel loses
the old background's colour, banded cutting matches per-pixel reads), `BackgroundMathTest` (edge curve,
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

Pictures with effects are worked on at no more than 24 megapixels (`ImagePlan.MAX_EFFECT_PIXELS`, for example
6000 x 4000). Effects keep several full-size copies of the picture, so a 50 MP photo at full size would need about
400 MB and could be killed on phones with 2 to 3 GB of RAM. Smaller photos, and runs that already resize, are not
affected.
