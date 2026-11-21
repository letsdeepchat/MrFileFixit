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
| `ACCESS_NETWORK_STATE` (merged from ML Kit and WorkManager) | Only reads whether a network exists. It cannot send anything. |
| `CAMERA` | Asked for at runtime right before the document scanner opens. Photos stay on the phone. |
| `READ_MEDIA_IMAGES` (Android 13+) | Asked for at runtime right before an image is picked for a tool. |
| `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`) | The Android 12-and-older equivalent of `READ_MEDIA_IMAGES`. |

Removed on purpose: `INTERNET`, `FOREGROUND_SERVICE` (and its `DATA_SYNC` / `MEDIA_PROCESSING` types),
`POST_NOTIFICATIONS`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`,
`com.android.vending.BILLING` and `AD_ID`. Without `INTERNET` the app cannot open a network connection at all. CI
fails if any of them reappears in the merged manifest, or if `READ_EXTERNAL_STORAGE` loses its `maxSdkVersion`.

### Photo and Video Permissions declaration

Since 1.0.9 the app declares `READ_MEDIA_IMAGES`, so Play Console asks for the **Photo and Video Permissions**
declaration (App content). Google allows the permission only for apps whose core purpose needs broad photo access;
others are expected to use the photo picker. Describe the image tools (resize, compress, exam photo/signature
fixer, scan to PDF) as the core use. If review rejects it, remove `READ_MEDIA_IMAGES` and `READ_EXTERNAL_STORAGE`
from the manifest: every picker keeps working, because the app still opens images through the system photo picker.

### Foreground services

None. The app declares no foreground service and no foreground service permission, so Play Console asks for no
foreground service declaration or video. Large image batches run as ordinary WorkManager background work.

## Data safety form

| Question | Answer |
| --- | --- |
| Does the app collect or share any of the required user data types? | **No** |
| Is all user data encrypted in transit? | Not applicable, no user data leaves the device |
| Can users request that data is deleted? | Not applicable; temporary files are deleted automatically |

Why "No" is accurate: the release build has no `INTERNET` permission (CI fails if one appears), so neither the
app nor any SDK inside it can send data. Text recognition and Remove background use ML Kit's bundled models; ML
Kit's usage statistics, which Google's ML Kit data disclosure lists as device and app information and identifiers,
can never be uploaded. The document scanner UI runs inside Google Play services, which is covered by Google's own
disclosures, not this app's.

## Other App content answers

* **Ads**: No, the app contains no ads.
* **App access**: All functionality is available without login.
* **Target audience**: 18 and over (exam aspirants). Not designed for children.
* **Content rating**: Utility / productivity, no user-generated content, no sharing between users, no
  location, no purchases. Expected rating: Everyone / 3+.
* **News app / health / financial features / government app**: No. The exam presets are a convenience
  and the app shows a disclaimer that it is not affiliated with any exam body; always check the official
  notification. Say the same in the store listing (below), because Play's Misrepresentation policy looks for
  apps that imply a government connection.

## Donations

Since 1.0.8 the Play build carries the same optional **UPI and PayPal** support screen as the tester build, and
**no Google Play Billing**: the billing library is gone and the manifest removes `com.android.vending.BILLING`
(`tools:node="remove"`). CI fails if Play Billing appears in the merged manifest. Every tool stays free and support
unlocks nothing.

The "In-app purchases" label on the store listing is set by Play Console, not only by the app. It goes away once
(1) every in-app product and subscription under **Monetize with Play → Products** is deactivated or deleted, and
(2) no release on any track (production, open, closed or internal testing) still serves an older bundle that has
the `BILLING` permission. Roll out 1.0.8 or later to every track, then wait for the listing to refresh (can take a
day or more).

The UPI section offers **Copy UPI ID**, **Save QR to Gallery** (PNG in `Pictures/MrFileFixit` via MediaStore) and
**Share QR code**. There is no `upi://pay` button: the UPI ID is a personal one, and Google Pay and PhonePe reject
such links from a third-party app with a false "You have exceeded the bank limit" error.

**Policy risk, accepted by the owner:** Google Play's Payments policy asks apps to use Google Play billing for
in-app payments to the developer and exempts only tax-exempt charitable donations. Links to the developer's own
UPI ID or PayPal can be flagged in review. If that happens, build with `-Pmff.donations=false` to drop the screen.

* **Data safety:** unchanged.
* **Financial features:** No. The app does not process payments.

## Privacy policy

The repository is private, so a GitHub link to this file returns 404 for Play reviewers. Host
[`docs/privacy-policy.html`](privacy-policy.html) (a standalone page with the text below) at a public address and
use that URL in the console, for example:

* **GitHub Pages** from a small public repository that holds only that file, or
* **Vercel**: drag the file into a new static project, or
* any website you own.

Replace `PRIVACY_CONTACT_EMAIL` in the page with an address you read before publishing. The policy must stay
reachable for as long as the app is listed.

### Privacy policy text

_Last updated: 29 October 2026_

Mr File Fixit ("the app") is a free offline file tool. This policy explains what happens to your data.

**What the app accesses.** Only the files, photos and scans you pick. The app has no storage permission;
Android hands it the files you choose.

**Where it is processed.** On your device only. The app does not upload, store on a server or share your
files, and it has no accounts, ads, analytics or tracking of its own.

**Temporary files.** Working copies live in the app's private cache and are deleted when you leave a tool;
anything left behind is removed the next time the app starts. Results are saved only where you choose.

**Resume and biodata drafts.** What you type into a resume or biodata, and the photos you add to it, are saved
only in the app's private storage on your phone so you can come back to them. They are not backed up, and they
are removed when you delete the draft or uninstall the app.

**Metadata.** Image exports strip location, camera details and other EXIF data, and PDF compression
removes document metadata, so outputs do not reveal where a photo was taken.

**No internet access.** The app does not have Android's internet permission, so it cannot send anything, and
neither can any component inside it.

**Third-party components.** Text recognition and Remove background use Google ML Kit models that are built into
the app and run on the device. The document scanner is shown by Google Play services, which processes the scan on
your device and hands the pages back to the app ([Google privacy policy](https://policies.google.com/privacy)).

**Children.** The app is not directed at children under 13 and collects no personal data from anyone.

**Changes.** Updates to this policy are published at this address with a new date.

**Contact.** Email PRIVACY_CONTACT_EMAIL.

## Store listing assets

* Icon 512 x 512 PNG, feature graphic 1024 x 500, at least 2 phone screenshots (Home, Exam, PDF, Image,
  Scan screens in light and dark).
* Short description (80 chars): "Fix photos, signatures and PDFs for exam forms. Free, offline, no ads."
* Category: Productivity.
* Full description: open with the disclaimer that the app is independent, is not a government app and is not
  affiliated with any exam body, then an "Official sources" list with one homepage per exam body named in the app
  (the same URLs as `sourceUrl` in exam_rules.json). Play rejected earlier versions under Misleading Claims for
  missing source links, then for an unreachable one (afcat.cdac.in, which Play's reviewers could not open, so the
  AFCAT preset was removed). Avoid C-DAC and regional hosts. The current text is in the project's
  `store-assets/full-description-*.txt`.
* Don't use exam bodies' logos or emblems in the icon, screenshots or feature graphic.

## Before the first upload

Things only the account owner can do:

1. **Upload key.** Create it and add the four `MFF_KEYSTORE_*` secrets (see "Upload key" above). Then push a
   `v*` tag; the `release` job uploads `app-release-bundle` (the `.aab`) as a workflow artifact.
2. **Privacy policy.** Host `docs/privacy-policy.html` publicly and paste its URL in Play Console > App content.
3. **Exam presets.** Check each profile in `feature-exam/src/main/assets/exam_rules.json` against its latest
   official notification and set `lastVerified`. Until then the app shows "Not verified" on it.
4. **Console forms.** Data safety: "No". Ads: No. No foreground service declaration is needed.
   Target audience 18+.
5. **Device check.** Install the release build from CI (`app-release-ci` artifact) and try OCR, Scan and Remove
   background once, to confirm they work without internet.
