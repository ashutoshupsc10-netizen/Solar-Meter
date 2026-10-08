package com.solarmeter.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingFileName;
    private String pendingMime;
    private byte[] pendingBytes;

    private static final int FILE_CHOOSER = 1001;
    private static final int CREATE_FILE = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);

        // =========================
        // WEBVIEW
        // =========================
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
            ) {

                if (url != null && url.startsWith("mailto:")) {
                    try {

                        Intent emailIntent =
                                new Intent(Intent.ACTION_SENDTO);

                        emailIntent.setData(Uri.parse(url));
                        startActivity(emailIntent);

                    } catch (Exception e) {

                        Toast.makeText(
                                MainActivity.this,
                                "No email app found",
                                Toast.LENGTH_LONG
                        ).show();
                    }

                    return true;
                }

                return false;
            }
        });

        // =========================
        // FILE CHOOSER
        // =========================
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {

                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }

                filePathCallback = callback;

                Intent intent =
                        new Intent(Intent.ACTION_OPEN_DOCUMENT);

                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");

                startActivityForResult(
                        intent,
                        FILE_CHOOSER
                );

                return true;
            }
        });

        // =========================
        // JAVASCRIPT BRIDGE
        // =========================
        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );

        // =========================
        // LOAD HTML
        // =========================
        webView.loadUrl(
                "file:///android_asset/index.html"
        );
    }

    // =====================================================
    // ANDROID BRIDGE
    // =====================================================

    public class AndroidBridge {

        @JavascriptInterface
        public void saveFile(
                String fileName,
                String mime,
                String base64
        ) {

            try {

                pendingFileName = fileName;
                pendingMime = mime;

                pendingBytes =
                        Base64.decode(
                                base64,
                                Base64.DEFAULT
                        );

                Intent intent =
                        new Intent(
                                Intent.ACTION_CREATE_DOCUMENT
                        );

                intent.addCategory(
                        Intent.CATEGORY_OPENABLE
                );

                intent.setType(
                        mime == null
                                ? "application/octet-stream"
                                : mime
                );

                intent.putExtra(
                        Intent.EXTRA_TITLE,
                        fileName
                );

                startActivityForResult(
                        intent,
                        CREATE_FILE
                );

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Could not open save dialog",
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }
    }

    // =====================================================
    // ACTIVITY RESULT
    // =====================================================

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        // -------------------------
        // FILE CHOOSER
        // -------------------------

        if (requestCode == FILE_CHOOSER) {

            if (filePathCallback == null) {
                return;
            }

            Uri[] results = null;

            if (
                    resultCode == RESULT_OK &&
                    data != null &&
                    data.getData() != null
            ) {

                results =
                        new Uri[]{
                                data.getData()
                        };
            }

            filePathCallback.onReceiveValue(
                    results
            );

            filePathCallback = null;
        }

        // -------------------------
        // CREATE / SAVE FILE
        // -------------------------

        else if (requestCode == CREATE_FILE) {

            if (
                    resultCode == RESULT_OK &&
                    data != null &&
                    data.getData() != null &&
                    pendingBytes != null
            ) {

                try (
                        OutputStream out =
                                getContentResolver()
                                        .openOutputStream(
                                                data.getData()
                                        )
                ) {

                    out.write(pendingBytes);
                    out.flush();

                    Toast.makeText(
                            this,
                            "File saved successfully",
                            Toast.LENGTH_SHORT
                    ).show();

                } catch (Exception e) {

                    Toast.makeText(
                            this,
                            "File save failed",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }

            pendingBytes = null;
            pendingFileName = null;
            pendingMime = null;
        }
    }

    // =====================================================
    // EXIT CONFIRMATION POPUP
    // =====================================================

    @Override
    public void onBackPressed() {

        // If WebView has previous page/history,
        // go back normally.
        if (webView != null && webView.canGoBack()) {

            webView.goBack();

        } else {

            showExitDialog();
        }
    }

    // =====================================================
    // EXIT DIALOG
    // =====================================================

    private void showExitDialog() {

        new AlertDialog.Builder(this)

                .setTitle("Exit Solar Meter?")

                .setMessage(
                        "Are you sure you want to exit the app?"
                )

                .setNegativeButton(
                        "CANCEL",
                        null
                )

                .setPositiveButton(
                        "EXIT",
                        (dialog, which) -> {

                            finish();
                        }
                )

                .setCancelable(true)

                .show();
    }
}
