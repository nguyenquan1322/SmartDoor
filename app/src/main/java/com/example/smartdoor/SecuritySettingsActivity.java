package com.example.smartdoor;

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
            Toast.makeText(this, "🔒 Đang mở màn hình cài đặt vân tay...", Toast.LENGTH_SHORT).show();

            // Mô phỏng ghi trạng thái lên Firebase
            FirebaseDatabase.getInstance().getReference("SystemLogs")
                    .push().setValue("User opened fingerprint setup screen");

            // Ở đây sau này bạn có thể mở activity khác để quét / enroll vân tay thật
        });

        // 🔹 Cài đặt mật khẩu mở cửa
        btnChangePass.setOnClickListener(v -> {
            Toast.makeText(this, "🧩 Mở giao diện đổi mật khẩu mở cửa...", Toast.LENGTH_SHORT).show();

            FirebaseDatabase.getInstance().getReference("SystemLogs")
                    .push().setValue("User opened door password change screen");
        });
    }
}
