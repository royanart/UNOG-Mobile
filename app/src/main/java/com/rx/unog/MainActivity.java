package com.rx.unog;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private WebView myWebView;
    private ProgressBar progressBar;

    // Sesi Aktif
    private String activeUser = "";
    private String activeSession = "";

    // Fitur Auto-Lock
    private boolean isAuthenticated = false;

    // Timer untuk Screen Always On (1 Menit)
    private final Handler screenHandler = new Handler(Looper.getMainLooper());
    private Runnable screenRunnable;

    // URL Utama App Script
    private final String BASE_URL = "https://script.google.com/macros/s/AKfycbzG3Ch46IBO-ypHTJ3Md_tWqBibDgVPlPYrNulovwG7TQVQoobaOJyNDtocFCu_sTRQZg/exec";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🛡️ Screenshot Protection
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        // 💡 Inisialisasi Screen Always On
        setupScreenAlwaysOn();

        setContentView(R.layout.activity_main);

        setupStatusBar();
        initViews();
        setupWebView();
        setupBackNavigation();
    }

    /**
     * Jembatan komunikasi antara WebApp dan Android Native
     */
    public class WebAppInterface {
        @JavascriptInterface
        public void saveLoginData(String user, String session) {
            activeUser = user;
            activeSession = session;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isAuthenticated) {
            checkBiometricAuth();
        }

        if (myWebView != null) {
            myWebView.onResume();
            myWebView.resumeTimers();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAuthenticated = false;

        if (myWebView != null) {
            myWebView.onPause();
            myWebView.pauseTimers();
        }
    }

    private void setupScreenAlwaysOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        screenRunnable = () -> {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Toast.makeText(MainActivity.this, "Mode hemat daya aktif", Toast.LENGTH_SHORT).show();
        };
        screenHandler.postDelayed(screenRunnable, 60000);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (screenRunnable != null) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            screenHandler.removeCallbacks(screenRunnable);
            screenHandler.postDelayed(screenRunnable, 60000);
        }
    }

    private void setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#EEEEEE"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
    }

    private void initViews() {
        myWebView = findViewById(R.id.webview_unog);
        progressBar = findViewById(R.id.progress_bar);

        Button btnCbt = findViewById(R.id.btn_cbt);
        Button btnOsce = findViewById(R.id.btn_osce);
        Button btnRefresh = findViewById(R.id.btn_refresh);
        TextView tvAppTitle = findViewById(R.id.tv_app_title);

        btnCbt.setOnClickListener(v -> {
            myWebView.loadUrl(BASE_URL + "?page=cbt");
        });

        btnOsce.setOnClickListener(v -> {
            if (activeSession != null && !activeSession.isEmpty()) {
                String url = BASE_URL + "?page=osce&username=" + activeUser + "&sessionId=" + activeSession;
                myWebView.loadUrl(url);
            } else {
                Toast.makeText(this, "Silakan Login CBT Terlebih Dahulu!", Toast.LENGTH_LONG).show();
                myWebView.loadUrl(BASE_URL + "?page=cbt");
            }
        });

        btnRefresh.setOnClickListener(v -> {
            myWebView.reload();
            Toast.makeText(this, "Memperbarui halaman... 🔄", Toast.LENGTH_SHORT).show();
        });

        tvAppTitle.setOnClickListener(v -> showAboutDialog());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = myWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // Wajib untuk Google Apps Script agar redirect dan sesi tidak terputus
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(myWebView, true);
        }

        // Menambahkan Jembatan Interface
        myWebView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36");

        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);

        myWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(newProgress);
                if (newProgress == 100) progressBar.setVisibility(View.GONE);
            }
        });

        myWebView.setWebViewClient(new WebViewClient() {
            // Support untuk API 24 ke atas
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return handleUrlLoading(view, request.getUrl().toString());
                }
                return false;
            }

            // Support untuk API di bawah 24
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlLoading(view, url);
            }

            private boolean handleUrlLoading(WebView view, String url) {
                // 1. Blokir Banner Abuse
                if (url.contains("drive.google.com/abuse") ||
                        url.contains("developers.google.com/apps-script") ||
                        url.contains("report-abuse")) {
                    Toast.makeText(MainActivity.this, "Akses dibatasi.", Toast.LENGTH_SHORT).show();
                    return true; // true = mencegah WebView memuat URL ini
                }

                // 2. Penanganan Materi Luar (OneDrive, Sharepoint, Google Docs)
                if (url.contains("onedrive.live.com") ||
                        url.contains("sharepoint.com") ||
                        url.contains("docs.google.com") ||
                        url.contains("drive.google.com")) {

                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Tidak ada aplikasi untuk membuka file ini.", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }

                // 3. Pastikan Apps Script tetap di dalam WebView (Google Redirects)
                if (url.contains("script.google.com") || url.contains("script.googleusercontent.com")) {
                    return false; // false = biarkan WebView yang menangani URL
                }

                return false;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                loadErrorPage();
            }
        });
    }

    private void loadErrorPage() {
        String errorHtml = "<html><body style='text-align:center;padding-top:100px;font-family:sans-serif;background:#f8f9fa;'>" +
                "<h1 style='font-size:50px;'>🩺</h1>" +
                "<h2>Koneksi Terputus</h2>" +
                "<p style='color:#666;'>Gagal memuat instrumen UNOG.</p>" +
                "<button onclick='location.reload()' style='padding:12px 25px;background:#007bff;color:white;border:none;border-radius:50px;font-weight:bold;'>COBA LAGI</button>" +
                "</body></html>";
        myWebView.loadData(errorHtml, "text/html", "UTF-8");
    }

    private void setupBackNavigation() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (myWebView.canGoBack()) {
                    myWebView.goBack();
                } else {
                    showExitDialog();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void checkBiometricAuth() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG | DEVICE_CREDENTIAL);

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            runBiometricPrompt();
        } else {
            loadInitialUrl();
        }
    }

    private void runBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                isAuthenticated = true;
                loadInitialUrl();
            }
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    finish();
                }
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Keamanan UNOG Mobile")
                .setSubtitle("Otorisasi sidik jari/kunci layar diperlukan")
                .setAllowedAuthenticators(BIOMETRIC_STRONG | DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void loadInitialUrl() {
        if (myWebView.getUrl() == null) {
            myWebView.loadUrl(BASE_URL + "?page=cbt");
        }
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("About UNOG Mobile v5")
                .setMessage("Specialist CBT & OSCE Simulation System\n" +
                        "Faculty of Medicine - Diponegoro University\n" +
                        "Developed by RX\n" +
                        "© 2026 - UNOG System")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Konfirmasi")
                .setMessage("Yakin ingin menutup aplikasi?")
                .setPositiveButton("Ya, Keluar", (dialog, which) -> finish())
                .setNegativeButton("Batal", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (myWebView != null) {
            myWebView.destroy();
        }
        if (screenHandler != null && screenRunnable != null) {
            screenHandler.removeCallbacks(screenRunnable);
        }
        super.onDestroy();
    }
}