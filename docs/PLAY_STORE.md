# Play Store checklist

Mr File Fixit is free, has no ads, no accounts and no in-app purchases. Every file is processed on the
device. This page lists what the Play Console asks for and the answers that match the code.

## Build

| Item | Value |
| --- | --- |
| Package | `com.mrfilefixit.app` |
| targetSdk / compileSdk | 36 |
| minSdk | 26 |
| Format | Android App Bundle (`./gradlew :app:bundleRelease`) |
| Shrinking | R8 full mode with resource shrinking |
| Debuggable | No (release build type) |

### Upload key

Play requires every upload to be signed with an upload key. The project does not ship one, by design: the
tester APK on GitHub releases is debug-signed and is only for sideloading. When you are ready for Play,
create a key once and add these repository secrets; the `release` CI job then builds a signed bundle:

```
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 upload.jks   # paste into MFF_KEYSTORE_B64
```

Secrets: `MFF_KEYSTORE_B64`, `MFF_KEYSTORE_PASSWORD`, `MFF_KEY_ALIAS`, `MFF_KEY_PASSWORD`. Turn on Play App
Signing in the console so Google holds the app signing key.

## Permissions

| Permission | Why |
| --- | --- |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PROCESSING` | Large batches keep running with a visible notification. The service declares `dataSync|mediaProcessing`. |
| `POST_NOTIFICATIONS` | Progress notification for that service. Asked at runtime on Android 13+. |
| `INTERNET` (merged from ML Kit) | Only for ML Kit's model delivery and its anonymous diagnostics. The app's own code has no network calls, which the `verifyNetworkIsolation` CI task enforces. |

Removed on purpose: `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`,
`READ_MEDIA_IMAGES` and `AD_ID`. Files arrive through the system photo picker and document picker, so no
Photo and Video Permissions declaration is needed. CI fails if any of them reappears in the merged manifest.

### Foreground service declaration

Play Console > App content > Foreground service permissions:

* **Data sync**: copying user-selected files into the app's private cache and writing results back to a
  user-chosen folder.
* **Media processing**: converting, compressing and resizing user-selected images and PDFs.

Video for the declaration: start a batch of 20 images in Image tools, leave the app, and show the progress
notification.

## Data safety form

| Question | Answer |
| --- | --- |
| Does the app collect or share any of the required user data types? | **No** |
| Is all user data encrypted in transit? | Not applicable, no user data leaves the device |
| Can users request that data is deleted? | Not applicable; temporary files are deleted automatically |

Notes for reviewers: documents, images and scans never leave the device. Google ML Kit (document scanner
and text recognition) may send anonymous usage and performance statistics to Google; this is not user
data under Play's definition, and the in-app privacy section says so.

## Other App content answers

* **Ads**: No, the app contains no ads.
* **App access**: All functionality is available without login.
* **Target audience**: 18 and over (exam aspirants). Not designed for children.
* **Content rating**: Utility / productivity, no user-generated content, no sharing between users, no
  location, no purchases. Expected rating: Everyone / 3+.
* **News app / health / financial features / government app**: No. The exam presets are a convenience
  and the app shows a disclaimer that it is not affiliated with any exam body; always check the official
  notification.

## Donations

The app has optional donation links (UPI and PayPal) that open outside the app and unlock nothing. Google Play's
Payments policy allows donations through external links for apps where the donation buys no digital content or
feature. Even so, review the current policy before release. Individual developers may also need to state who
receives the money in the store listing. If in doubt, build with `-Pmff.donations=false`: the Support screen, the
Home card and the Settings row all disappear, and the `<queries>` entries for UPI apps are the only trace left.

* **Data safety:** unchanged. Donations happen in other apps, and this app collects nothing about them.
* **Financial features:** No. The app does not process payments.

## Privacy policy

Use this URL in the console:
`https://github.com/letsdeepchat/MrFileFixit/blob/main/docs/PLAY_STORE.md#privacy-policy-text`

### Privacy policy text

_Last updated: 24 October 2026_

Mr File Fixit ("the app") is a free offline file tool. This policy explains what happens to your data.

**What the app accesses.** Only the files, photos and scans you pick. The app has no storage permission;
Android hands it the files you choose.

**Where it is processed.** On your device only. The app does not upload, store on a server or share your
files, and it has no accounts, ads, analytics or tracking of its own.

**Temporary files.** Working copies live in the app's private cache and are deleted when you leave a tool;
anything left behind is removed the next time the app starts. Results are saved only where you choose.

**Metadata.** Image exports strip location, camera details and other EXIF data, and PDF compression
removes document metadata, so outputs do not reveal where a photo was taken.

**Third-party components.** The document scanner and text recognition use Google ML Kit, which runs on
the device. ML Kit may send anonymous usage and performance statistics to Google
([Google privacy policy](https://policies.google.com/privacy)). It does not send your documents.

**Children.** The app is not directed at children under 13 and collects no personal data from anyone.

**Changes.** Updates to this policy are published at this address with a new date.

**Contact.** Open an issue at <https://github.com/letsdeepchat/MrFileFixit/issues>.

## Store listing assets

* Icon 512 x 512 PNG, feature graphic 1024 x 500, at least 2 phone screenshots (Home, Exam, PDF, Image,
  Scan screens in light and dark).
* Short description (80 chars): "Fix photos, signatures and PDFs for exam forms. Free, offline, no ads."
* Category: Productivity.
