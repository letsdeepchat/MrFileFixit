# Performance checklist

| Rule | Where | Notes |
| --- | --- | --- |
| Decode at target size | `ImageDecoding.decodeRegion` | `BitmapRegionDecoder` decodes only the crop rectangle, with a power-of-two `inSampleSize` chosen for the output size; `safeSampleSize` raises it further if the bitmap would exceed a quarter of the free heap |
| Render PDF one page at a time | `PdfBoxPdfProcessor.forEachRenderedPage`, `MlKitOcrProcessor.recognizePdf` | `PdfRenderer` page is opened, rendered into one bitmap, used, recycled, closed before the next |
| KB targeting capped at 8 encodes | `QualitySearch` | Tries max quality first, then binary search; at most 8 encodes per size, at most 3 downscale steps |
| Thumbnails cached | `ThumbnailCache` | `LruCache` sized in KB at 1/8 of the app heap; thumbnails come from `ContentResolver.loadThumbnail` on API 29+ |
| Temp file cleanup | `TempFileStore` | See docs/SECURITY.md |
| Large batches off the UI process lifecycle | `BatchImageWorker` | Batches above 10 files run in WorkManager as expedited work; foreground service (`mediaProcessing` on 35+, `dataSync` below) only while running |
| Cancellation | `JobRunner`, all engines | `ensureActive()` between pages/images, so Cancel stops within one unit of work |
| Startup | `MffApplication`, `MainActivity` | WorkManager is initialised on demand (startup initializer removed); ads consent runs after the first frame; exam rules are parsed lazily on first use |
| Baseline Profiles | `:benchmark` | `./gradlew :app:generateReleaseBaselineProfile` on an API 28+ device; output is checked in under `app/src/main/generated/baselineProfiles` |
| Macrobenchmark | `:benchmark` `StartupBenchmark` | Cold start with/without profile, frame timing while switching tabs |
| R8 full mode | `gradle.properties` | `android.enableR8.fullMode=true`; `minifyEnabled` and `shrinkResources` in release |

## Budget targets (to confirm on a device)

* Cold start (Pixel 6a class, with profile): under 600 ms to first frame.
* Exam photo (12 MP input) to compliant 20 to 50 KB JPEG: under 1.5 s.
* Peak memory for a 12 MP image: under 120 MB.

These are targets, not measurements: nothing has been run on a device yet.
