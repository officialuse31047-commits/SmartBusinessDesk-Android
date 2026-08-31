# Smart Business Desk — Native Android WebView

**Website:** `https://smartbusinessdesk.com/`  
**Package:** `com.smartbusinessdesk.app`  
**Version:** `1.0.0` (`versionCode 1`)  
**Minimum Android:** Android 7.0 / API 24  
**Target / Compile SDK:** API 35

## Architecture

This project is deliberately a **native Android WebView shell**.

Primary content path:

`MainActivity -> android.webkit.WebView -> https://smartbusinessdesk.com`

It does **not** use:

- Trusted Web Activity (TWA)
- Chrome Custom Tabs
- `androidx.browser`
- Bubblewrap / PWA wrapper
- `com.google.androidbrowserhelper`
- a forced `com.android.chrome` package

Smart Business Desk internal URLs remain inside the app's `android.webkit.WebView`.
External/special URLs such as UPI, WhatsApp, `tel:`, `mailto:`, Maps, Play Store, or genuinely external websites are intentionally handed to an appropriate Android app when required.

> Android's WebView rendering implementation can be supplied by the device's Android System WebView/Chromium component. That is an Android system rendering engine; this app does not launch or route its normal Smart Business Desk browsing through the Chrome browser UI.

## Included functionality

- Native `android.webkit.WebView`
- Persistent cookies/login session
- JavaScript + DOM/local/session storage support
- Internal `smartbusinessdesk.com` and `www.smartbusinessdesk.com` links stay in WebView
- HTTPS-only internal domain handling
- SSL errors are rejected, never bypassed
- Android Back navigation through WebView history
- Pull-to-refresh
- Native loading progress bar
- Branded splash screen using the supplied flat Smart Business Desk logo
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

- **Launcher/adaptive icon:** supplied round Smart Business Desk logo
- **Splash screen:** supplied flat Smart Business Desk logo
- **Offline screen:** supplied flat Smart Business Desk logo

Processed Android-ready copies are already placed in `app/src/main/res/`.

## Build in Android Studio

1. Open this folder in a current Android Studio version.
2. Allow Gradle to sync and install Android SDK 35 if requested.
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
keytool -list -v -keystore smart-business-desk-release.jks -alias smartbusinessdesk
```

Look for `SHA256:`.

### Recommended Android Studio signing

Use **Build > Generate Signed App Bundle or APK > APK**, choose the production keystore, select the `release` build type, and generate the APK.

Keep the keystore/password outside the public source repository.

## Verified Android App Links

The Android manifest contains verified App Link filters for:

- `https://smartbusinessdesk.com/*`
- `https://www.smartbusinessdesk.com/*`

After the final APK signing certificate is known:

1. Replace `REPLACE_WITH_RELEASE_CERTIFICATE_SHA256_FINGERPRINT` inside `website/.well-known/assetlinks.json`.
2. Upload the JSON **without changing its name** to:
   - `https://smartbusinessdesk.com/.well-known/assetlinks.json`
   - and, if `www.smartbusinessdesk.com` is kept in the manifest as an independently verified host, serve the same file at `https://www.smartbusinessdesk.com/.well-known/assetlinks.json`.
3. It must be publicly accessible over HTTPS and return JSON directly, ideally with `Content-Type: application/json` and without authentication.
4. Install the APK and test links from WhatsApp, email, Chrome, etc.

Example expected behavior:

`https://smartbusinessdesk.com/report/?id=123&type=sales`

If the app is installed and the domain association is verified, Android opens the app and the app loads that exact URL in its own WebView. If the app is not installed, the URL remains a normal web URL.

## Direct APK hosting

After producing the signed release APK, you can rename it to:

`smart-business-desk.apk`

and host it at a stable URL such as:

`https://smartbusinessdesk.com/app/smart-business-desk.apk`

Future releases should use the same package and signing key and increase `versionCode`.

## Chrome/TWA wrapper check

Run:

```bash
./scripts/verify-no-chrome-wrapper.sh
```

The script fails if it finds common TWA/Custom Tabs/browser-wrapper references and shows the actual `android.webkit.WebView` references.

## Important payment/external-link behavior

Normal Smart Business Desk pages stay inside the native WebView. A bank/UPI/payment/WhatsApp/etc. link can intentionally leave the WebView only when Android must invoke the corresponding external application. This is not Chrome routing of the app itself.

## Files to edit later

- Website URL / hosts: `MainActivity.java` constants + manifest App Link hosts
- App name: `res/values/strings.xml`
- Colors: `res/values/colors.xml`
- Flat splash/offline logo: `res/drawable-nodpi/sbd_flat_logo.png` and `sbd_splash_icon.png`
- Launcher icon: `res/drawable-nodpi/ic_launcher_foreground.png` + legacy mipmap assets
- Version: `app/build.gradle` (`versionCode`, `versionName`)

## GitHub Actions APK Build

This project includes `.github/workflows/build-apk.yml`.

After uploading the extracted project contents to the repository root on the `main` branch:

1. Open the repository's **Actions** tab.
2. Open **Build Smart Business Desk APK**.
3. Click **Run workflow** if a build did not start automatically.
4. Wait for the workflow to complete.
5. Open the completed run and download the artifact named **Smart-Business-Desk-APK**.
6. Extract the artifact ZIP; it contains **Smart-Business-Desk.apk**, which is directly installable for testing.

The workflow builds a debug-signed APK. For long-term production distribution and Verified Android App Links, create and preserve a release signing keystore, then use its SHA-256 certificate fingerprint in `website/.well-known/assetlinks.json`.
