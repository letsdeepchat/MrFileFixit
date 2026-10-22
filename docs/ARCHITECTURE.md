# Mr File Fixit — Architecture

Mr File Fixit is a fully offline Android app. Every byte of user content is processed on-device.
The only component allowed to use the network is `core-ads` (AdMob + UMP consent).

## 1. Layers

```mermaid
flowchart LR
    subgraph UI["ui (Jetpack Compose, MVVM)"]
        Screen["Screen composable"] -->|intent / event| VM["ViewModel"]
        VM -->|StateFlow&lt;UiState&gt;| Screen
    end
    subgraph Domain["domain (pure Kotlin)"]
        UC["Use case"]
        IF["Interfaces:\nImageProcessor, PdfProcessor,\nOcrProcessor, FileRepository,\nExamRulesRepository"]
        ALG["Pure algorithms:\nSpecChecker, QualitySearch,\nFilenameSanitizer, InputLimits"]
    end
    subgraph Data["data (Android implementations)"]
        PROC["AndroidImageProcessor\nPdfBoxPdfProcessor\nMlKitOcrProcessor"]
        REPO["SafFileRepository\nAssetExamRulesRepository\nDataStore prefs"]
    end
    VM -->|invoke| UC
    UC --> IF
    UC --> ALG
    PROC -. implements .-> IF
    REPO -. implements .-> IF
```

* **Unidirectional data flow.** Screens send events to a ViewModel. The ViewModel calls use cases and exposes one
  immutable `StateFlow<UiState>`. Screens never call use cases directly.
* **Errors are values.** Every layer returns `Result<T, AppError>`. Android/library exceptions are caught at the
  data-layer boundary (`runCatchingApp {}`) and mapped to `AppError`. `CancellationException` is always rethrown.
* **Jobs.** Each tool run is a `JobRunner` job exposing `StateFlow<JobState<T>>`
  (`Idle → Running(progress) → Success | Failed | Cancelled`).

## 2. Module graph

```mermaid
flowchart TD
    app[":app"]
    bench[":benchmark (test only)"]
    fexam[":feature-exam"]
    fpdf[":feature-pdf"]
    fimg[":feature-image"]
    fscan[":feature-scan"]
    cui[":core-ui"]
    cads[":core-ads"]
    cstore[":core-storage"]
    cproc[":core-processing"]
    cdom[":core-domain (pure JVM)"]

    app --> fexam & fpdf & fimg & fscan
    app --> cads & cstore & cproc & cui & cdom
    bench -. instruments .-> app

    fexam --> cui & cdom
    fpdf --> cui & cdom
    fimg --> cui & cdom
    fscan --> cui & cdom

    cads --> cui & cdom
    cui --> cdom
    cstore --> cdom
    cproc --> cdom
```

Rules enforced by the graph:

| Rule | How it is enforced |
| --- | --- |
| Dependencies point inward only | `core-domain` is a `kotlin("jvm")` module, so it cannot see Android or any other module. Features only see `core-ui` and `core-domain`. |
| Features never see implementations | `core-processing` and `core-storage` bind their implementations with Hilt `@Binds`. Only `:app` puts them on the classpath. |
| Features never see ads | `core-ui` defines `AdSlots` (banner slot + `ResultGate`). `core-ads` implements them; `:app` provides them via a `CompositionLocal`. |
| Processing never touches the network | `mrfilefixit.network-isolation` convention plugin fails the build if any non-ads module resolves a networking library or declares `INTERNET`; Detekt `ForbiddenImport` bans `java.net.*`, `okhttp3.*`, etc. |

## 3. Package layout per module

```
core-domain/      com.mrfilefixit.core.domain
  result/         Result, AppError, runCatchingApp
  dispatchers/    DispatcherProvider
  logging/        AppLogger (no file names/paths/content ever)
  job/            JobState, JobRunner, ProgressReporter
  file/           FileRef, OutputFile, FileRepository, TempFileStore
  security/       FilenameSanitizer, InputLimits
  image/          ImageProcessor, ImageFormat, QualitySearch, requests
  exam/           ExamProfile, DocumentSpec, SpecChecker, ExamRulesRepository, use cases
  pdf/            PdfProcessor, PdfOperation
  ocr/            OcrProcessor

core-processing/  com.mrfilefixit.core.processing   (data layer: engines)
core-storage/     com.mrfilefixit.core.storage      (data layer: SAF, cache, FileProvider, DataStore)
core-ui/          com.mrfilefixit.core.ui           (theme, components, adaptive scaffolds, previews)
core-ads/         com.mrfilefixit.core.ads          (AdManager, ConsentManager, frequency cap)
feature-*/        com.mrfilefixit.feature.<name>    (ui + feature data such as bundled exam rules)
```

## 4. Threading

* `DispatcherProvider` is injected everywhere. Decoding/encoding runs on `default`, file I/O on `io`.
* All loops call `ensureActive()` / `coroutineContext.ensureActive()` between steps, so cancelling a job stops it
  within one image or one PDF page.
* Batches above `BatchPolicy.INLINE_LIMIT` files are handed to WorkManager. The worker promotes itself to a
  foreground service (`mediaProcessing` on API 35+, `dataSync` below) only while it is running.

## 5. Privacy boundaries

```mermaid
flowchart LR
    SAF["Storage Access Framework\n(user picks file)"] -->|content:// read| Validate["InputLimits\n(size, pixels, pages)"]
    Validate --> Engine["Processor\n(on-device)"]
    Engine --> Cache["app-private cache/jobs/<id>/"]
    Cache -->|FileProvider, read grant, single URI| Share["Share sheet / Save As"]
    Cache -->|deleted after job| Trash["TempFileStore.cleanup"]
    Ads["core-ads"] -->|only network user| Net(("Internet"))
```

## 6. Assumptions

* minSdk 26, compileSdk/targetSdk 36. HEIC decoding needs API 28+, HEIC encoding needs a device HEVC encoder,
  lossy WebP encoding uses `WEBP_LOSSY` on API 30+ and the legacy `WEBP` format below.
* PDF structure operations (merge/split/encrypt) use `pdfbox-android` (Apache-2.0). Page rendering uses the
  platform `PdfRenderer`, one page at a time.
* The ML Kit document scanner is delivered through Google Play services. Its model is downloaded once by Play
  services; scans and OCR are processed on-device. Text recognition uses the bundled Latin model and runs fully
  offline. Devanagari OCR is available as an optional bundled model (adds ~4 MB).
