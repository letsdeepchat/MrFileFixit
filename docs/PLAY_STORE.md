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
| `ACCESS_NETWORK_STATE` (merged from ML Kit and WorkManager) | Only reads whether a network exists. It cannot send anything. |

Removed on purpose: `INTERNET`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`,
`READ_MEDIA_IMAGES` and `AD_ID`. Without `INTERNET` the app cannot open a network connection at all. Files arrive through the system photo picker and document picker, so no
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

The Play build has **no donation feature**. CI's release job builds the bundle with `-Pmff.donations=false`, which
removes the Support screen, the Home card and the Settings row; the `<queries>` entries for UPI apps are the only
trace left. Google Play's Payments policy requires Google Play billing for in-app payments and forbids leading users
to other payment methods, with an exception for tax-exempt (charitable) donations only. A tip to the developer's
own UPI ID or PayPal is not covered, and reviewers flag it.

The sideloaded tester build on GitHub releases keeps donations. To accept tips on Play later, add Google Play
Billing consumable products (for example "Tip ₹51") that unlock nothing.

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
* Full description must include, near the top: "Mr File Fixit is not affiliated with, endorsed by or connected to
  SSC, UPSC, IBPS, RRB, NTA or any government body. Photo and signature presets are a convenience; always check
  the official notification for your exam."
* Don't use exam bodies' logos or emblems in the icon, screenshots or feature graphic.

## Before the first upload

Things only the account owner can do:

1. **Upload key.** Create it and add the four `MFF_KEYSTORE_*` secrets (see "Upload key" above). Then push a
   `v*` tag; the `release` job uploads `app-release-bundle` (the `.aab`) as a workflow artifact.
2. **Privacy policy.** Host `docs/privacy-policy.html` publicly and paste its URL in Play Console > App content.
3. **Exam presets.** Check each profile in `feature-exam/src/main/assets/exam_rules.json` against its latest
   official notification and set `lastVerified`. Until then the app shows "Not verified" on it.
4. **Console forms.** Data safety: "No". Ads: No. Foreground service declaration with the video described above.
   Target audience 18+.
5. **Device check.** Install the release build from CI (`app-release-ci` artifact) and try OCR, Scan and Remove
   background once, to confirm they work without internet.
