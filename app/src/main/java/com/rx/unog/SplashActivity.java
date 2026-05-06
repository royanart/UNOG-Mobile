package com.rx.unog;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Memasang layout splash screen
        setContentView(R.layout.activity_splash);

        // ⏱️ Delay 2.5 detik (2500ms) untuk branding, lalu pindah ke MainActivity
        // Menggunakan Looper.getMainLooper() agar aman di Android versi terbaru
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);

            // Memberikan efek transisi halus (opsional)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

            // Menutup SplashActivity agar pengguna tidak kembali ke sini saat menekan tombol back
            finish();
        }, 2500);
    }
}