# Support Clue — Native Android WebView

**Website:** `https://supportclue.com/`
**Package:** `com.supportclue.app`
**Version:** `1.0.1` (`versionCode 2`)
**Minimum Android:** Android 7.0 / API 24
**Target / Compile SDK:** API 36

## Architecture

This project is deliberately a **native Android WebView shell**.

Primary content path:

`MainActivity -> android.webkit.WebView -> https://supportclue.com`

It does **not** use:

- Trusted Web Activity (TWA)
- Chrome Custom Tabs
- `androidx.browser`
- Bubblewrap / PWA wrapper
- `com.google.androidbrowserhelper`
- a forced `com.android.chrome` package

Support Clue internal URLs remain inside the app's `android.webkit.WebView`.
External/special URLs such as UPI, WhatsApp, `tel:`, `mailto:`, Maps, Play Store, or genuinely external websites are intentionally handed to an appropriate Android app when required.

> Android's WebView rendering implementation can be supplied by the device's Android System WebView/Chromium component. That is an Android system rendering engine; this app does not launch or route its normal Support Clue browsing through the Chrome browser UI.

## Included functionality

- Native `android.webkit.WebView`
- Persistent cookies/login session
- JavaScript + DOM/local/session storage support
- Internal `supportclue.com` and `www.supportclue.com` links stay in WebView
- HTTPS-only internal domain handling
- SSL errors are rejected, never bypassed
- Android Back navigation through WebView history
- Pull-to-refresh
- Native loading progress bar
- Branded splash screen using the supplied stacked Support Clue logo
- Branded offline screen + Retry
- Round supplied logo used for launcher/adaptive icon assets
- File picker + multiple file selection
- Camera capture through Android FileProvider
- Downloads through Android DownloadManager
- Cookie/User-Agent forwarded for authenticated downloads
- UPI / intent / WhatsApp / phone / email / SMS / Maps-style external scheme handling
- `target="_blank"` / new-window routing
- WebView state restoration on Activity recreation
- Verified Android App Link intent filters
- Exact deep-link path/query preservation
- Cold-start and already-running deep-link handling (`singleTask` + `onNewIntent`)
- No native JavaScript bridge exposed to the website
- WebView debugging only in debug builds

## Branding mapping

- **Launcher/adaptive icon:** supplied round Support Clue logo
- **Splash screen:** supplied stacked Support Clue logo
- **Offline screen:** supplied stacked Support Clue logo

Processed Android-ready copies are already placed in `app/src/main/res/`.

## Build in Android Studio

1. Open this folder in a current Android Studio version.
2. Allow Gradle to sync and install Android SDK 36 if requested.
3. For a quick installable test APK, use **Build > Build APK(s)** with the debug variant.
4. For public direct distribution, create and preserve a production signing key (see below), then build a signed release APK.

## Production signing

A release APK should be signed with **your own permanent keystore**. Do not lose it; future updates to the same installed app require the same package name and signing key.

A helper script is included:

```bash
./scripts/create-release-keystore.sh
```

After creating the keystore, print/copy its SHA-256 fingerprint and put it in:

`website/.well-known/assetlinks.json`

You can also read the fingerprint with:

```bash
keytool -list -v -keystore SupportClue-release.jks -alias <your-existing-alias>
```

Look for `SHA256:`.

### Recommended Android Studio signing

Use **Build > Generate Signed App Bundle or APK > APK**, choose the production keystore, select the `release` build type, and generate the APK.

Keep the keystore/password outside the public source repository.

## Verified Android App Links

The Android manifest contains verified App Link filters for:

- `https://supportclue.com/*`
- `https://www.supportclue.com/*`

After the final APK signing certificate is known:

1. Replace `REPLACE_WITH_RELEASE_CERTIFICATE_SHA256_FINGERPRINT` inside `website/.well-known/assetlinks.json`.
2. Upload the JSON **without changing its name** to:
   - `https://supportclue.com/.well-known/assetlinks.json`
   - and, if `www.supportclue.com` is kept in the manifest as an independently verified host, serve the same file at `https://www.supportclue.com/.well-known/assetlinks.json`.
3. It must be publicly accessible over HTTPS and return JSON directly, ideally with `Content-Type: application/json` and without authentication.
4. Install the APK and test links from WhatsApp, email, Chrome, etc.

Example expected behavior:

`https://supportclue.com/report/?id=123&type=sales`

If the app is installed and the domain association is verified, Android opens the app and the app loads that exact URL in its own WebView. If the app is not installed, the URL remains a normal web URL.

## Direct APK hosting

After producing the signed release APK, you can rename it to:

`support-clue.apk`

and host it at a stable URL such as:

`https://supportclue.com/app/support-clue.apk`

Future releases should use the same package and signing key and increase `versionCode`.

## Chrome/TWA wrapper check

Run:

```bash
./scripts/verify-no-chrome-wrapper.sh
```

The script fails if it finds common TWA/Custom Tabs/browser-wrapper references and shows the actual `android.webkit.WebView` references.

## Important payment/external-link behavior

Normal Support Clue pages stay inside the native WebView. A bank/UPI/payment/WhatsApp/etc. link can intentionally leave the WebView only when Android must invoke the corresponding external application. This is not Chrome routing of the app itself.

## Files to edit later

- Website URL / hosts: `MainActivity.java` constants + manifest App Link hosts
- App name: `res/values/strings.xml`
- Colors: `res/values/colors.xml`
- Splash/offline logo: `res/drawable-nodpi/support_clue_vertical_logo.png` and `support_clue_splash_icon.png`
- Launcher icon: `res/drawable-nodpi/ic_launcher_foreground.png` + legacy mipmap assets
- Version: `app/build.gradle` (`versionCode`, `versionName`)

## GitHub Actions APK Build

This project includes `.github/workflows/main.yml`.

After uploading the extracted project contents to the repository root on the `main` branch:

1. Open the repository's **Actions** tab.
2. Open **Build Support Clue APK & AAB**.
3. Click **Run workflow** if a build did not start automatically.
4. Wait for the workflow to complete.
5. Open the completed run and download the required **Support-Clue** artifact.
6. Extract the artifact ZIP; it contains the installable APK or Play Store AAB.

The workflow builds a debug APK, a signed release APK, and a signed release AAB. Preserve the existing release signing keystore and use its SHA-256 certificate fingerprint in `website/.well-known/assetlinks.json`.
