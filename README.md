# Mr File Fixit

A free, fully offline Android app for fixing files for government exam forms and everyday paperwork:
resize and compress photos and signatures to exam specs, PDF tools, image tools, and a document scanner.
All processing happens on the device. No uploads, no backend, no logging of file content.
Every feature is free: no ads, no accounts, no sign-in, no premium tier and no usage limits.

> **Disclaimer.** Exam profiles bundled with the app are community-maintained samples. They are **not official**.
> Always verify the photo and signature requirements against the official notification of your exam before submitting.

## Modules

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the architecture diagram, module graph, and design rules.

| Module | Type | Purpose |
| --- | --- | --- |
| `app` | Android application | Navigation shell, DI wiring, adaptive layout |
| `feature-exam` | Android library | Exam profile picker, photo/signature flow, live spec checker |
| `feature-pdf` | Android library | Merge, split, extract, reorder, rotate, delete, compress, image↔PDF, Word/PowerPoint/Excel↔PDF, watermark, page numbers, password |
| `feature-image` | Android library | Resize, crop, rotate, compress, convert, batch, strip EXIF |
| `feature-scan` | Android library | ML Kit document scanner and on-device OCR |
| `core-ui` | Android library | Material 3 theme, components, previews |
| `core-domain` | Pure Kotlin (JVM) | Result types, job pipeline, use cases, processor interfaces, pure algorithms |
| `core-office` | Pure Kotlin (JVM) | DOCX/PPTX/XLSX readers and writers, legacy DOC/PPT/XLS readers, CSV, PDF text layout analysis ([docs/CONVERSIONS.md](docs/CONVERSIONS.md)) |
| `core-processing` | Android library | Image, PDF (compressor), office conversion, OCR and scan-filter engines |
| `core-storage` | Android library | SAF, temp files, FileProvider sharing, DataStore |
| `benchmark` | Android test | Baseline Profile generator and Macrobenchmark startup tests |

## Build

```bash
./gradlew assembleDebug          # debug build
./gradlew check                  # unit tests, Detekt, Android Lint, network isolation check
./gradlew :app:bundleRelease     # release bundle (signed only if an upload keystore is configured)
```

Signing is optional and never in source. If these Gradle properties or environment variables are set, the
release bundle is signed with them; otherwise it is built unsigned:

| Property | Purpose |
| --- | --- |
| `MFF_KEYSTORE_PATH`, `MFF_KEYSTORE_PASSWORD`, `MFF_KEY_ALIAS`, `MFF_KEY_PASSWORD` | Release signing |

Tester builds: pushing a `v*` tag (or running CI from the Actions tab with a version) publishes a debug-signed
APK on the GitHub release for sideloading. See [docs/PLAY_STORE.md](docs/PLAY_STORE.md) for the Play Console
checklist, data-safety answers and privacy policy.

## Exam rules

Exam profiles live in `feature-exam/src/main/assets/exam_rules.json` and follow
[`docs/exam-rules.schema.json`](docs/exam-rules.schema.json). Each profile carries a `sourceUrl` and a
`lastVerified` date. To update a profile, edit the JSON, update `lastVerified`, and run
`./gradlew :feature-exam:testDebugUnitTest` (golden tests guard the parsed result).

Schema v2 bundles 20 exams across SSC, UPSC, Banking, Railways, Defence, NTA/Entrance and Teaching, with an
optional `certificates` list (PDF/JPG uploads with a KB window) and `background` / `ink` notes per document.
`verification` is `community` unless a maintainer checked the values against the official notice page linked in
`sourceUrl` on the `lastVerified` date; only then is it `verified`, and the app shows "Checked on official page"
with that date. The in-app disclaimer is shown for every profile either way.

## Verification status

This code was written in a cloud sandbox that could not reach Google's Maven repository (`dl.google.com` /
`maven.google.com` returned 403). That decides what could and could not be checked:

| Checked | How | Result |
| --- | --- | --- |
| Pure Kotlin code (all of `core-domain`, exam rules parser, PDF layout) | Compiled with Kotlin 2.4.21, `allWarningsAsErrors`, run with JUnit 5 + MockK on a plain JVM | 112 tests pass |
| Style | ktlint 1.7.1 (`android_studio`) and detekt 1.23.8 + formatting, using this repo's config | Zero findings |
| Exam rules JSON | Validated against `docs/exam-rules.schema.json` | Valid |
| CI / Dependabot YAML | Parsed | Valid |

| **Not** checked yet | Why |
| --- | --- |
| Android modules compile (`app`, `feature-*`, `core-ui`, `core-processing`, `core-storage`) | Needs AGP and AndroidX from Google Maven |
| Android Lint, Compose UI tests, Macrobenchmark | Same, plus an emulator/device |
| Library versions listed as unverified at the top of `gradle/libs.versions.toml` (AGP, Compose BOM, AndroidX, Play services, ML Kit) | Could not query Google Maven; pinned to the newest versions known to the author. Dependabot will propose corrections |

The first CI run (`.github/workflows/ci.yml`) is the real compile check. Expect a small number of API-signature
fixes there.

## Docs

* [Architecture](docs/ARCHITECTURE.md)
* [Security and privacy](docs/SECURITY.md)
* [Performance checklist](docs/PERFORMANCE.md)
