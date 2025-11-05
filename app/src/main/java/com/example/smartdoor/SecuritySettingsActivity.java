package com.example.smartdoor;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.FirebaseDatabase;

public class SecuritySettingsActivity extends AppCompatActivity {

    Button btnFingerprint, btnChangePass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_settings);

        btnFingerprint = findViewById(R.id.btnFingerprint);
        btnChangePass  = findViewById(R.id.btnChangePass);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            finish(); // trở về màn hình trước (MainActivity)
        });

        // 🔹 Cài đặt vân tay
        btnFingerprint.setOnClickListener(v -> {
            Intent intent = new Intent(SecuritySettingsActivity.this, FingerprintManagementActivity.class);
            startActivity(intent);
        });

        // 🔹 Cài đặt mật khẩu mở cửa
        btnChangePass.setOnClickListener(v -> {
            Intent intent = new Intent(SecuritySettingsActivity.this, PasswordManagementActivity.class);
            startActivity(intent);
        });
    }
}
