package com.rx.unogmobile;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.navigation.NavigationView;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private WebView myWebView;
    private ProgressBar progressBar;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private String activeUser = "";
    private String activeSession = "";

    private boolean isAuthenticated = false;
    private long lastPauseTime = 0;
    private final long BIOMETRIC_TIMEOUT_MS = 5 * 60 * 1000;

    private final Handler screenHandler = new Handler(Looper.getMainLooper());
    private Runnable screenRunnable;

    private final String BASE_URL = "https://script.google.com/macros/s/AKfycbzG3Ch46IBO-ypHTJ3Md_tWqBibDgVPlPYrNulovwG7TQVQoobaOJyNDtocFCu_sTRQZg/exec";

    // URL Web App dari Google Apps Script untuk sistem update
    private final String UPDATE_URL_API = "https://script.google.com/macros/s/AKfycbwxPbz8LMHMx6CVFqtcxCmCerwGP7XqnntigZOKNAXGU6CACgptnEnDjdrxAFTh1NV2Rg/exec";

    // Variabel Global untuk Kalkulator USG
    private Calendar selectedUsgDate = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setupScreenAlwaysOn();
        setContentView(R.layout.activity_main);
        hideSystemUI();

        initViews();
        setupWebView();
        setupBackNavigation();

        // Pemicu 1: Cek update senyap di latar belakang saat aplikasi dibuka
        checkForUpdates(false);
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

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
        if (lastPauseTime != 0) {
            long timeAway = System.currentTimeMillis() - lastPauseTime;
            if (timeAway > BIOMETRIC_TIMEOUT_MS) {
                isAuthenticated = false;
            }
        }
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
        lastPauseTime = System.currentTimeMillis();
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

    private void initViews() {
        myWebView = findViewById(R.id.webview_unog);
        progressBar = findViewById(R.id.progress_bar);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        ImageButton btnMenu = findViewById(R.id.btn_menu);

        Button btnCbt = findViewById(R.id.btn_cbt);
        Button btnOsce = findViewById(R.id.btn_osce);
        Button btnRefresh = findViewById(R.id.btn_refresh);

        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_calculator) {
                showCalculatorSelectionMenu();
            } else if (id == R.id.nav_about) {
                showAboutDialog();
            } else if (id == R.id.nav_update) {
                // Pemicu 2: Cek update secara manual melalui Sidebar
                checkForUpdates(true);
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        btnCbt.setOnClickListener(v -> myWebView.loadUrl(BASE_URL + "?page=cbt"));

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
            Toast.makeText(this, "Memperbarui halaman... \uD83D\uDD04", Toast.LENGTH_SHORT).show();
        });
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

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(myWebView, true);
        }

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
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return handleUrlLoading(view, request.getUrl().toString());
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlLoading(view, url);
            }

            private boolean handleUrlLoading(WebView view, String url) {
                if (url.contains("drive.google.com/abuse") ||
                        url.contains("developers.google.com/apps-script") ||
                        url.contains("report-abuse")) {
                    Toast.makeText(MainActivity.this, "Akses dibatasi.", Toast.LENGTH_SHORT).show();
                    return true;
                }
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
                if (url.contains("script.google.com") || url.contains("script.googleusercontent.com")) {
                    return false;
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
                "<h1 style='font-size:50px;'>\uD83E\uDE7A</h1>" +
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
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else if (myWebView.canGoBack()) {
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

    // =========================================================
    // MENU SELEKSI KALKULATOR
    // =========================================================
    private void showCalculatorSelectionMenu() {
        String[] options = {
                "1. Usia Gestasi & Taksiran Lahir (EDD)",
                "2. Bishop Score",
                "3. Taksiran Berat Janin (TBJ)",
                "4. Mean Arterial Pressure (MAP)",
                "5. VBAC (Flamm Score)",
                "6. RMI (Kista Ovarium)"
        };

        new AlertDialog.Builder(this)
                .setTitle("Pilih Kalkulator Medis")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showBottomSheetCalculator("hpht_usg");
                    else if (which == 1) showBottomSheetCalculator("bishop");
                    else if (which == 2) showBottomSheetCalculator("tbj");
                    else if (which == 3) showBottomSheetCalculator("map");
                    else if (which == 4) showBottomSheetCalculator("vbac");
                    else if (which == 5) showBottomSheetCalculator("rmi");
                })
                .show();
    }

    // =========================================================
    // FITUR: KALKULATOR MEDIS OBGYN (BOTTOM SHEET)
    // =========================================================
    private void showBottomSheetCalculator(String moduleType) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_calc, null);
        bottomSheetDialog.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tv_calc_title);
        LinearLayout layoutHphtUsg = view.findViewById(R.id.layout_hpht_usg);
        LinearLayout layoutBishop = view.findViewById(R.id.layout_bishop);
        LinearLayout layoutTbj = view.findViewById(R.id.layout_tbj);
        LinearLayout layoutMap = view.findViewById(R.id.layout_map);
        LinearLayout layoutVbac = view.findViewById(R.id.layout_vbac);
        LinearLayout layoutRmi = view.findViewById(R.id.layout_rmi);
        Button btnClose = view.findViewById(R.id.btn_close_calc);

        // Atur Tampilan Berdasarkan Pilihan
        if (moduleType.equals("hpht_usg")) {
            tvTitle.setText("Usia Gestasi & HPL");
            layoutHphtUsg.setVisibility(View.VISIBLE);
            setupHphtUsgModule(view);
        } else if (moduleType.equals("bishop")) {
            tvTitle.setText("Bishop Score");
            layoutBishop.setVisibility(View.VISIBLE);
            setupBishopModule(view);
        } else if (moduleType.equals("tbj")) {
            tvTitle.setText("Taksiran Berat Janin");
            layoutTbj.setVisibility(View.VISIBLE);
            setupTbjModule(view);
        } else if (moduleType.equals("map")) {
            tvTitle.setText("Mean Arterial Pressure");
            layoutMap.setVisibility(View.VISIBLE);
            setupMapModule(view);
        } else if (moduleType.equals("vbac")) {
            tvTitle.setText("VBAC (Flamm Score)");
            layoutVbac.setVisibility(View.VISIBLE);
            setupVbacModule(view);
        } else if (moduleType.equals("rmi")) {
            tvTitle.setText("RMI (Kista Ovarium)");
            layoutRmi.setVisibility(View.VISIBLE);
            setupRmiModule(view);
        }

        btnClose.setOnClickListener(v -> bottomSheetDialog.dismiss());
        bottomSheetDialog.show();
    }

    // =========================================================
    // 1. HPHT & USG
    // =========================================================
    private void setupHphtUsgModule(View view) {
        RadioGroup rgMetode = view.findViewById(R.id.rg_metode_hitung);
        RadioButton rbHpht = view.findViewById(R.id.rb_hpht);
        Button btnPickDate = view.findViewById(R.id.btn_pick_date);
        LinearLayout layoutInputUsg = view.findViewById(R.id.layout_input_usg);
        EditText etUsgMinggu = view.findViewById(R.id.et_usg_minggu);
        EditText etUsgHari = view.findViewById(R.id.et_usg_hari);
        Button btnHitung = view.findViewById(R.id.btn_hitung_edd);
        TextView tvHphtResult = view.findViewById(R.id.tv_hpht_result);

        selectedUsgDate = null;

        rgMetode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_hpht) {
                btnPickDate.setText("PILIH TANGGAL HPHT");
                layoutInputUsg.setVisibility(View.GONE);
                btnHitung.setVisibility(View.GONE);
                tvHphtResult.setText("Usia Kehamilan: -\nHPL (EDD): -");
            } else {
                btnPickDate.setText("PILIH TANGGAL USG DILAKUKAN");
                layoutInputUsg.setVisibility(View.VISIBLE);
                btnHitung.setVisibility(View.VISIBLE);
                tvHphtResult.setText("Usia Kehamilan: -\nHPL (EDD): -");
            }
        });

        btnPickDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            DatePickerDialog datePickerDialog = new DatePickerDialog(this, (datePicker, year, month, day) -> {
                Calendar pickedDate = Calendar.getInstance();
                pickedDate.set(year, month, day);
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID"));

                if (rbHpht.isChecked()) {
                    btnPickDate.setText("HPHT: " + sdf.format(pickedDate.getTime()));
                    Calendar edd = (Calendar) pickedDate.clone();
                    edd.add(Calendar.DATE, 280);

                    long diffInMillis = Calendar.getInstance().getTimeInMillis() - pickedDate.getTimeInMillis();
                    long diffInDays = diffInMillis / (1000 * 60 * 60 * 24);
                    long weeks = diffInDays / 7;
                    long days = diffInDays % 7;

                    if (diffInMillis < 0) {
                        tvHphtResult.setText("Tanggal HPHT tidak boleh di masa depan!");
                    } else {
                        tvHphtResult.setText("Usia Kehamilan Hari Ini: " + weeks + " minggu " + days + " hari\n" +
                                "HPL (EDD): " + sdf.format(edd.getTime()));
                    }
                } else {
                    selectedUsgDate = pickedDate;
                    btnPickDate.setText("Tgl USG: " + sdf.format(pickedDate.getTime()));
                }
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.show();
        });

        btnHitung.setOnClickListener(v -> {
            if (selectedUsgDate == null) {
                Toast.makeText(this, "Silakan pilih Tanggal USG dilakukan!", Toast.LENGTH_SHORT).show();
                return;
            }
            String strMinggu = TextUtils.isEmpty(etUsgMinggu.getText()) ? "0" : etUsgMinggu.getText().toString();
            String strHari = TextUtils.isEmpty(etUsgHari.getText()) ? "0" : etUsgHari.getText().toString();

            int usiaUsgMinggu = Integer.parseInt(strMinggu);
            int usiaUsgHari = Integer.parseInt(strHari);

            int totalHariUsg = (usiaUsgMinggu * 7) + usiaUsgHari;
            int sisaHariLahir = 280 - totalHariUsg;

            Calendar edd = (Calendar) selectedUsgDate.clone();
            edd.add(Calendar.DATE, sisaHariLahir);

            long selisihWaktu = Calendar.getInstance().getTimeInMillis() - selectedUsgDate.getTimeInMillis();
            long selisihHari = selisihWaktu / (1000 * 60 * 60 * 24);

            long totalUsiaHariIni = totalHariUsg + selisihHari;
            long currentWeeks = totalUsiaHariIni / 7;
            long currentDays = totalUsiaHariIni % 7;

            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", new Locale("id", "ID"));

            if(selisihWaktu < 0) {
                tvHphtResult.setText("Tanggal USG tidak boleh di masa depan!");
            } else {
                tvHphtResult.setText("Usia Kehamilan Hari Ini: " + currentWeeks + " minggu " + currentDays + " hari\n" +
                        "HPL (EDD): " + sdf.format(edd.getTime()));
            }
        });
    }

    // =========================================================
    // 2. BISHOP SCORE
    // =========================================================
    private void setupBishopModule(View view) {
        Spinner spinDilatasi = view.findViewById(R.id.spin_dilatasi);
        Spinner spinPendataran = view.findViewById(R.id.spin_pendataran);
        Spinner spinPenurunan = view.findViewById(R.id.spin_penurunan);
        Spinner spinKonsistensi = view.findViewById(R.id.spin_konsistensi);
        Spinner spinPosisi = view.findViewById(R.id.spin_posisi);
        TextView tvBishopResult = view.findViewById(R.id.tv_bishop_result);

        setupSpinner(spinDilatasi, new String[]{"Tertutup (0)", "1-2 cm (1)", "3-4 cm (2)", ">= 5 cm (3)"});
        setupSpinner(spinPendataran, new String[]{"0-30% (0)", "40-50% (1)", "60-70% (2)", ">= 80% (3)"});
        setupSpinner(spinPenurunan, new String[]{"-3 (0)", "-2 (1)", "-1 / 0 (2)", "+1 / +2 (3)"});
        setupSpinner(spinKonsistensi, new String[]{"Kaku (0)", "Sedang (1)", "Lunak (2)"});
        setupSpinner(spinPosisi, new String[]{"Posterior (0)", "Tengah / Searah Sumbu (1)", "Anterior (2)"});

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int score = spinDilatasi.getSelectedItemPosition() +
                        spinPendataran.getSelectedItemPosition() +
                        spinPenurunan.getSelectedItemPosition() +
                        spinKonsistensi.getSelectedItemPosition() +
                        spinPosisi.getSelectedItemPosition();

                String interpretasi = score > 5 ? "Matang (Favorable) - Induksi dapat dilakukan" : "Belum Matang (Unfavorable)";
                tvBishopResult.setText("Total Skor: " + score + "\nInterpretasi: " + interpretasi);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinDilatasi.setOnItemSelectedListener(listener);
        spinPendataran.setOnItemSelectedListener(listener);
        spinPenurunan.setOnItemSelectedListener(listener);
        spinKonsistensi.setOnItemSelectedListener(listener);
        spinPosisi.setOnItemSelectedListener(listener);
    }

    // =========================================================
    // 3. TBJ (Johnson-Toshach)
    // =========================================================
    private void setupTbjModule(View view) {
        EditText etTfu = view.findViewById(R.id.et_tfu);
        Spinner spinPenurunan = view.findViewById(R.id.spin_penurunan_tbj);
        Button btnHitung = view.findViewById(R.id.btn_hitung_tbj);
        TextView tvResult = view.findViewById(R.id.tv_tbj_result);

        setupSpinner(spinPenurunan, new String[]{
                "Konvergen / Hodge I-II (Belum Masuk PAP)",
                "Sejajar / Hodge III (Masuk Separuh)",
                "Divergen / Hodge IV (Masuk Sepenuhnya)"
        });

        btnHitung.setOnClickListener(v -> {
            if (TextUtils.isEmpty(etTfu.getText())) {
                Toast.makeText(this, "Masukkan nilai TFU!", Toast.LENGTH_SHORT).show();
                return;
            }
            double tfu = Double.parseDouble(etTfu.getText().toString());
            int pos = spinPenurunan.getSelectedItemPosition();

            // Logika Johnson-Toshach
            int n = 12;
            if (pos == 0) n = 13;
            else if (pos == 1) n = 12;
            else if (pos == 2) n = 11;

            double tbj = (tfu - n) * 155;
            if (tbj < 0) tbj = 0; // Menghindari hasil minus jika input TFU terlalu kecil

            tvResult.setText(String.format(Locale.getDefault(), "Taksiran Berat Janin: %,.0f gram", tbj));
        });
    }

    // =========================================================
    // 4. MAP (Mean Arterial Pressure)
    // =========================================================
    private void setupMapModule(View view) {
        EditText etSys = view.findViewById(R.id.et_sistolik);
        EditText etDia = view.findViewById(R.id.et_diastolik);
        Button btnHitung = view.findViewById(R.id.btn_hitung_map);
        TextView tvResult = view.findViewById(R.id.tv_map_result);

        btnHitung.setOnClickListener(v -> {
            if (TextUtils.isEmpty(etSys.getText()) || TextUtils.isEmpty(etDia.getText())) {
                Toast.makeText(this, "Masukkan Sistolik dan Diastolik!", Toast.LENGTH_SHORT).show();
                return;
            }
            double sys = Double.parseDouble(etSys.getText().toString());
            double dia = Double.parseDouble(etDia.getText().toString());

            // Rumus MAP
            double map = (sys + (2 * dia)) / 3.0;
            String risiko = map >= 90 ? "Tinggi (Waspada Preeklampsia)" : "Normal";

            tvResult.setText(String.format(Locale.getDefault(), "Nilai MAP: %.1f mmHg\nRisiko: %s", map, risiko));
        });
    }

    // =========================================================
    // 5. VBAC (Flamm Score)
    // =========================================================
    private void setupVbacModule(View view) {
        Spinner spinUsia = view.findViewById(R.id.spin_vbac_usia);
        Spinner spinRiwayat = view.findViewById(R.id.spin_vbac_riwayat);
        Spinner spinAlasan = view.findViewById(R.id.spin_vbac_alasan_sc);
        Spinner spinEff = view.findViewById(R.id.spin_vbac_effacement);
        Spinner spinDil = view.findViewById(R.id.spin_vbac_dilatasi);
        TextView tvResult = view.findViewById(R.id.tv_vbac_result);

        setupSpinner(spinUsia, new String[]{"< 40 Tahun (2 poin)", ">= 40 Tahun (0 poin)"});
        setupSpinner(spinRiwayat, new String[]{
                "Belum pernah partus pervaginam (0 poin)",
                "Partus pervaginam SEBELUM SC (1 poin)",
                "Partus pervaginam SETELAH SC (2 poin)",
                "Partus pervaginam SEBELUM & SETELAH SC (4 poin)"
        });
        setupSpinner(spinAlasan, new String[]{"Indikasi Berulang / CPD dsb (0 poin)", "Indikasi Tidak Berulang / Letak Sungsang dsb (1 poin)"});
        setupSpinner(spinEff, new String[]{"< 25% (0 poin)", "25 - 75% (1 poin)", "> 75% (2 poin)"});
        setupSpinner(spinDil, new String[]{"< 4 cm (0 poin)", ">= 4 cm (1 poin)"});

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int score = 0;
                score += spinUsia.getSelectedItemPosition() == 0 ? 2 : 0;

                int rPos = spinRiwayat.getSelectedItemPosition();
                if (rPos == 1) score += 1;
                else if (rPos == 2) score += 2;
                else if (rPos == 3) score += 4;

                score += spinAlasan.getSelectedItemPosition() == 1 ? 1 : 0;
                score += spinEff.getSelectedItemPosition(); // index match
                score += spinDil.getSelectedItemPosition(); // index match

                String peluang;
                if (score <= 2) peluang = "49%";
                else if (score == 3) peluang = "60%";
                else if (score == 4) peluang = "67%";
                else if (score == 5) peluang = "77%";
                else if (score == 6) peluang = "89%";
                else if (score == 7) peluang = "93%";
                else peluang = "> 95%";

                tvResult.setText("Flamm Score: " + score + "\nPeluang Keberhasilan VBAC: " + peluang);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinUsia.setOnItemSelectedListener(listener);
        spinRiwayat.setOnItemSelectedListener(listener);
        spinAlasan.setOnItemSelectedListener(listener);
        spinEff.setOnItemSelectedListener(listener);
        spinDil.setOnItemSelectedListener(listener);
    }

    // =========================================================
    // 6. RMI (Kista Ovarium)
    // =========================================================
    private void setupRmiModule(View view) {
        Spinner spinMeno = view.findViewById(R.id.spin_rmi_menopause);
        CheckBox cbMulti = view.findViewById(R.id.cb_rmi_multilocular);
        CheckBox cbSolid = view.findViewById(R.id.cb_rmi_solid);
        CheckBox cbBilateral = view.findViewById(R.id.cb_rmi_bilateral);
        CheckBox cbAsites = view.findViewById(R.id.cb_rmi_asites);
        CheckBox cbMetas = view.findViewById(R.id.cb_rmi_metastasis);
        EditText etCa125 = view.findViewById(R.id.et_rmi_ca125);
        Button btnHitung = view.findViewById(R.id.btn_hitung_rmi);
        TextView tvResult = view.findViewById(R.id.tv_rmi_result);

        setupSpinner(spinMeno, new String[]{"Premenopause (Skor 1)", "Postmenopause (Skor 3)"});

        btnHitung.setOnClickListener(v -> {
            if (TextUtils.isEmpty(etCa125.getText())) {
                Toast.makeText(this, "Masukkan nilai CA-125!", Toast.LENGTH_SHORT).show();
                return;
            }

            int m = spinMeno.getSelectedItemPosition() == 0 ? 1 : 3;

            int uCount = 0;
            if (cbMulti.isChecked()) uCount++;
            if (cbSolid.isChecked()) uCount++;
            if (cbBilateral.isChecked()) uCount++;
            if (cbAsites.isChecked()) uCount++;
            if (cbMetas.isChecked()) uCount++;

            int u;
            if (uCount == 0) u = 0;
            else if (uCount == 1) u = 1;
            else u = 3;

            double ca125 = Double.parseDouble(etCa125.getText().toString());

            // Rumus RMI = U x M x CA125
            double rmi = u * m * ca125;
            String risiko = rmi > 200 ? "Tinggi (> 200)" : "Rendah/Sedang";

            tvResult.setText(String.format(Locale.getDefault(), "Nilai RMI: %.1f\nRisiko Keganasan: %s", rmi, risiko));
        });
    }

    private void setupSpinner(Spinner spinner, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
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

    // =========================================================
    // FITUR: CEK UPDATE OTOMATIS & MANUAL
    // =========================================================
    private void checkForUpdates(boolean isManual) {
        if (isManual) {
            Toast.makeText(this, "Mengecek pembaruan sistem...", Toast.LENGTH_SHORT).show();
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                URL url = new URL(UPDATE_URL_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                int latestVersionCode = jsonResponse.getInt("latest_version_code");
                String latestVersionName = jsonResponse.getString("latest_version_name");

                // Me-replace \n dari string JSON menjadi enter betulan di Java
                String releaseNotes = jsonResponse.getString("release_notes").replace("\\n", "\n");
                boolean forceUpdate = jsonResponse.getBoolean("force_update");
                String updateUrl = jsonResponse.getString("update_url");

                int currentVersionCode = 0;
                try {
                    PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        currentVersionCode = (int) pInfo.getLongVersionCode();
                    } else {
                        currentVersionCode = pInfo.versionCode;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                int finalCurrentVersionCode = currentVersionCode;
                handler.post(() -> {
                    if (latestVersionCode > finalCurrentVersionCode) {
                        showUpdateDialog(latestVersionName, releaseNotes, forceUpdate, updateUrl);
                    } else if (isManual) {
                        Toast.makeText(MainActivity.this, "UNOG Mobile sudah dalam versi terbaru.", Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                handler.post(() -> {
                    if (isManual) {
                        Toast.makeText(MainActivity.this, "Gagal terhubung ke server update.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showUpdateDialog(String versionName, String releaseNotes, boolean forceUpdate, String updateUrl) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Update Tersedia (" + versionName + ")")
                .setMessage(releaseNotes)
                .setCancelable(!forceUpdate);

        builder.setPositiveButton("Update Sekarang", (dialog, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl));
            startActivity(intent);
            if (forceUpdate) {
                finish();
            }
        });

        if (!forceUpdate) {
            builder.setNegativeButton("Nanti Saja", (dialog, which) -> dialog.dismiss());
        }

        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(!forceUpdate);
        dialog.show();
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