package com.smartbusinessdesk.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Smart Business Desk Android shell.
 *
 * IMPORTANT ARCHITECTURE NOTE:
 * The primary content container is android.webkit.WebView. This Activity does NOT use
 * external browser-wrapper APIs or a forced browser package.
 * smartbusinessdesk.com and www.smartbusinessdesk.com stay inside this WebView.
 */
public class MainActivity extends Activity {

    private static final String HOME_URL = "https://smartbusinessdesk.com/";
    private static final String HOST_PRIMARY = "smartbusinessdesk.com";
    private static final String HOST_WWW = "www.smartbusinessdesk.com";

    private static final int REQUEST_FILE_CHOOSER = 4101;
    private static final int REQUEST_STORAGE_PERMISSION = 4102;

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar pageProgress;
    private View offlineView;
    private Button retryButton;

    private ValueCallback<Uri[]> filePathCallback;
    private Uri pendingCameraUri;
    private PendingDownload pendingDownload;

    private String lastIntendedUrl = HOME_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Manifest uses the splash theme for the Android starting window; switch immediately
        // to the normal app theme as the Activity is created.
        setTheme(R.style.Theme_SmartBusinessDesk);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        pageProgress = findViewById(R.id.pageProgress);
        offlineView = findViewById(R.id.offlineView);
        retryButton = findViewById(R.id.retryButton);

        configureWebView();
        configureRefreshAndOfflineUi();

        String deepLink = extractInternalDeepLink(getIntent());
        if (!TextUtils.isEmpty(deepLink)) {
            loadInternalUrl(deepLink);
        } else if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            String restored = webView.getUrl();
            if (!TextUtils.isEmpty(restored)) {
                lastIntendedUrl = restored;
            }
        } else {
            loadInternalUrl(HOME_URL);
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setGeolocationEnabled(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        webView.setWebViewClient(new SbdWebViewClient());
        webView.setWebChromeClient(new SbdWebChromeClient());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                handleDownload(url, userAgent, contentDisposition, mimeType));
    }

    private void configureRefreshAndOfflineUi() {
        swipeRefresh.setColorSchemeResources(R.color.sbd_green, R.color.sbd_navy);
        swipeRefresh.setOnChildScrollUpCallback((parent, child) -> webView.getScrollY() > 0);
        swipeRefresh.setOnRefreshListener(() -> {
            if (isOnline()) {
                hideOffline();
                webView.reload();
            } else {
                swipeRefresh.setRefreshing(false);
                showOffline(lastIntendedUrl);
            }
        });

        retryButton.setOnClickListener(v -> {
            if (isOnline()) {
                hideOffline();
                loadInternalUrl(lastIntendedUrl);
            } else {
                Toast.makeText(this, R.string.offline_title, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadInternalUrl(String url) {
        String safeUrl = normalizeInternalUrl(url);
        lastIntendedUrl = safeUrl;
        if (!isOnline()) {
            showOffline(safeUrl);
            return;
        }
        hideOffline();
        webView.loadUrl(safeUrl);
    }

    /** Forces Smart Business Desk internal HTTP URLs to HTTPS and rejects other hosts. */
    private String normalizeInternalUrl(String rawUrl) {
        if (TextUtils.isEmpty(rawUrl)) {
            return HOME_URL;
        }
        try {
            Uri uri = Uri.parse(rawUrl);
            String host = uri.getHost();
            if (!isInternalHost(host)) {
                return HOME_URL;
            }
            Uri.Builder builder = uri.buildUpon().scheme("https");
            return builder.build().toString();
        } catch (Exception ignored) {
            return HOME_URL;
        }
    }

    private boolean isInternalHost(String host) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.US);
        return HOST_PRIMARY.equals(normalized) || HOST_WWW.equals(normalized);
    }

    private String extractInternalDeepLink(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return null;
        }
        Uri data = intent.getData();
        if (data == null || !isInternalHost(data.getHost())) {
            return null;
        }
        return normalizeInternalUrl(data.toString());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String deepLink = extractInternalDeepLink(intent);
        if (!TextUtils.isEmpty(deepLink)) {
            loadInternalUrl(deepLink);
        }
    }

    private boolean routeUrl(String rawUrl) {
        if (TextUtils.isEmpty(rawUrl)) return false;

        Uri uri;
        try {
            uri = Uri.parse(rawUrl);
        } catch (Exception e) {
            return true;
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);

        if ("http".equals(scheme) || "https".equals(scheme)) {
            if (isInternalHost(uri.getHost())) {
                // Never route Smart Business Desk through Chrome or another browser.
                if ("http".equals(scheme)) {
                    loadInternalUrl(rawUrl);
                    return true;
                }
                lastIntendedUrl = rawUrl;
                return false; // WebView loads it itself.
            }
            return openExternalIntent(new Intent(Intent.ACTION_VIEW, uri), null);
        }

        if ("intent".equals(scheme)) {
            return openAndroidIntentUri(rawUrl);
        }

        if ("tel".equals(scheme)
                || "mailto".equals(scheme)
                || "sms".equals(scheme)
                || "smsto".equals(scheme)
                || "upi".equals(scheme)
                || "whatsapp".equals(scheme)
                || "market".equals(scheme)
                || "geo".equals(scheme)) {
            return openExternalIntent(new Intent(Intent.ACTION_VIEW, uri), null);
        }

        // Unknown non-web schemes may belong to a payment/bank/app handler.
        if (!TextUtils.isEmpty(scheme)) {
            return openExternalIntent(new Intent(Intent.ACTION_VIEW, uri), null);
        }
        return true;
    }

    private boolean openAndroidIntentUri(String rawUrl) {
        try {
            Intent intent = Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            intent.setSelector(null);
            try {
                startActivity(intent);
                return true;
            } catch (ActivityNotFoundException missingApp) {
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (!TextUtils.isEmpty(fallback)) {
                    Uri fallbackUri = Uri.parse(fallback);
                    if (isInternalHost(fallbackUri.getHost())) {
                        loadInternalUrl(fallback);
                    } else {
                        openExternalIntent(new Intent(Intent.ACTION_VIEW, fallbackUri), null);
                    }
                } else {
                    Toast.makeText(this, "Required app is not installed.", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open this link.", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private boolean openExternalIntent(Intent intent, String failureMessage) {
        try {
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    TextUtils.isEmpty(failureMessage) ? "No compatible app is installed." : failureMessage,
                    Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private class SbdWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return routeUrl(request.getUrl().toString());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return routeUrl(url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (!TextUtils.isEmpty(url) && isInternalHost(Uri.parse(url).getHost())) {
                lastIntendedUrl = url;
            }
            pageProgress.setVisibility(View.VISIBLE);
            pageProgress.setProgress(8);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            pageProgress.setProgress(100);
            pageProgress.setVisibility(View.GONE);
            swipeRefresh.setRefreshing(false);
            CookieManager.getInstance().flush();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                int code = error.getErrorCode();
                if (!isOnline()
                        || code == ERROR_HOST_LOOKUP
                        || code == ERROR_CONNECT
                        || code == ERROR_TIMEOUT
                        || code == ERROR_IO) {
                    showOffline(request.getUrl().toString());
                }
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            if (!isOnline()
                    || errorCode == ERROR_HOST_LOOKUP
                    || errorCode == ERROR_CONNECT
                    || errorCode == ERROR_TIMEOUT
                    || errorCode == ERROR_IO) {
                showOffline(failingUrl);
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            // Never bypass invalid SSL certificates.
            handler.cancel();
            Toast.makeText(MainActivity.this,
                    "Secure connection could not be verified.", Toast.LENGTH_LONG).show();
        }
    }

    private class SbdWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            pageProgress.setProgress(newProgress);
            if (newProgress >= 100) {
                pageProgress.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
            } else {
                pageProgress.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public boolean onShowFileChooser(WebView webView,
                                         ValueCallback<Uri[]> filePathCallback,
                                         FileChooserParams fileChooserParams) {
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }
            MainActivity.this.filePathCallback = filePathCallback;

            Intent contentIntent = new Intent(Intent.ACTION_GET_CONTENT);
            contentIntent.addCategory(Intent.CATEGORY_OPENABLE);

            String[] acceptTypes = cleanedAcceptTypes(fileChooserParams.getAcceptTypes());
            if (acceptTypes.length == 1) {
                contentIntent.setType(acceptTypes[0]);
            } else {
                contentIntent.setType("*/*");
                if (acceptTypes.length > 1) {
                    contentIntent.putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes);
                }
            }
            contentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                    fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);

            List<Intent> extraIntents = new ArrayList<>();
            if (acceptsImages(acceptTypes) || fileChooserParams.isCaptureEnabled()) {
                Intent cameraIntent = createCameraIntent();
                if (cameraIntent != null) {
                    extraIntents.add(cameraIntent);
                }
            }

            Intent chooser = Intent.createChooser(contentIntent, "Choose file");
            if (!extraIntents.isEmpty()) {
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                        extraIntents.toArray(new Intent[0]));
            }

            try {
                startActivityForResult(chooser, REQUEST_FILE_CHOOSER);
                return true;
            } catch (ActivityNotFoundException e) {
                MainActivity.this.filePathCallback = null;
                Toast.makeText(MainActivity.this, "No file picker is available.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                      android.os.Message resultMsg) {
            WebView popupWebView = new WebView(MainActivity.this);
            popupWebView.getSettings().setJavaScriptEnabled(true);
            popupWebView.setWebViewClient(new WebViewClient() {
                private boolean consumed = false;

                private void consume(String url) {
                    if (consumed || TextUtils.isEmpty(url)) return;
                    consumed = true;
                    Uri uri = Uri.parse(url);
                    if (isInternalHost(uri.getHost())) {
                        loadInternalUrl(url);
                    } else {
                        routeUrl(url);
                    }
                    popupWebView.stopLoading();
                    popupWebView.destroy();
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    consume(request.getUrl().toString());
                    return true;
                }

                @Override
                @SuppressWarnings("deprecation")
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    consume(url);
                    return true;
                }

                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    consume(url);
                }
            });

            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popupWebView);
            resultMsg.sendToTarget();
            return true;
        }
    }

    private String[] cleanedAcceptTypes(String[] rawTypes) {
        if (rawTypes == null || rawTypes.length == 0) {
            return new String[]{"*/*"};
        }
        List<String> result = new ArrayList<>();
        for (String type : rawTypes) {
            if (!TextUtils.isEmpty(type)) {
                result.add(type);
            }
        }
        return result.isEmpty() ? new String[]{"*/*"} : result.toArray(new String[0]);
    }

    private boolean acceptsImages(String[] acceptTypes) {
        if (acceptTypes == null) return true;
        for (String type : acceptTypes) {
            if ("*/*".equals(type) || type.toLowerCase(Locale.US).startsWith("image/")) {
                return true;
            }
        }
        return false;
    }

    private Intent createCameraIntent() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) == null) {
            return null;
        }
        try {
            File directory = new File(getExternalCacheDir(), "Pictures");
            if (!directory.exists() && !directory.mkdirs()) {
                return null;
            }
            File image = File.createTempFile("sbd_camera_", ".jpg", directory);
            pendingCameraUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    image);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return cameraIntent;
        } catch (IOException | IllegalArgumentException e) {
            pendingCameraUri = null;
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FILE_CHOOSER || filePathCallback == null) {
            return;
        }

        Uri[] results = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                results = new Uri[clipData.getItemCount()];
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    results[i] = clipData.getItemAt(i).getUri();
                }
            } else if (data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            } else if (pendingCameraUri != null) {
                results = new Uri[]{pendingCameraUri};
            }
        }

        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
        pendingCameraUri = null;
    }

    private void handleDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        if (TextUtils.isEmpty(url)) return;
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            Toast.makeText(this, "This download type cannot be saved directly.", Toast.LENGTH_LONG).show();
            return;
        }

        PendingDownload data = new PendingDownload(url, userAgent, contentDisposition, mimeType);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = data;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            return;
        }
        enqueueDownload(data);
    }

    private void enqueueDownload(PendingDownload data) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(data.url));
            String fileName = URLUtil.guessFileName(data.url, data.contentDisposition, data.mimeType);
            request.setTitle(fileName);
            request.setDescription("Downloading from Smart Business Desk");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            if (!TextUtils.isEmpty(data.mimeType)) {
                request.setMimeType(data.mimeType);
            }
            if (!TextUtils.isEmpty(data.userAgent)) {
                request.addRequestHeader("User-Agent", data.userAgent);
            }
            String cookies = CookieManager.getInstance().getCookie(data.url);
            if (!TextUtils.isEmpty(cookies)) {
                request.addRequestHeader("Cookie", cookies);
            }
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this, "Download started.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Unable to start download.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION && pendingDownload != null) {
            PendingDownload download = pendingDownload;
            pendingDownload = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enqueueDownload(download);
            } else {
                Toast.makeText(this, "Storage permission is required to save this download.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isOnline() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void showOffline(String intendedUrl) {
        if (!TextUtils.isEmpty(intendedUrl)) {
            Uri uri = Uri.parse(intendedUrl);
            if (isInternalHost(uri.getHost())) {
                lastIntendedUrl = normalizeInternalUrl(intendedUrl);
            }
        }
        pageProgress.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
        swipeRefresh.setVisibility(View.GONE);
        offlineView.setVisibility(View.VISIBLE);
    }

    private void hideOffline() {
        offlineView.setVisibility(View.GONE);
        swipeRefresh.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        outState.putString("lastIntendedUrl", lastIntendedUrl);
        if (pendingCameraUri != null) {
            outState.putString("pendingCameraUri", pendingCameraUri.toString());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        String restoredUrl = savedInstanceState.getString("lastIntendedUrl");
        if (!TextUtils.isEmpty(restoredUrl)) {
            lastIntendedUrl = restoredUrl;
        }
        String cameraUri = savedInstanceState.getString("pendingCameraUri");
        if (!TextUtils.isEmpty(cameraUri)) {
            pendingCameraUri = Uri.parse(cameraUri);
        }
    }

    @Override
    public void onBackPressed() {
        if (offlineView.getVisibility() == View.VISIBLE) {
            super.onBackPressed();
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    private static class PendingDownload {
        final String url;
        final String userAgent;
        final String contentDisposition;
        final String mimeType;

        PendingDownload(String url, String userAgent, String contentDisposition, String mimeType) {
            this.url = url;
            this.userAgent = userAgent;
            this.contentDisposition = contentDisposition;
            this.mimeType = mimeType;
        }
    }
}
