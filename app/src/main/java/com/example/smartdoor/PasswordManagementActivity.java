package com.example.smartdoor;

import android.os.Bundle;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;

public class PasswordManagementActivity extends AppCompatActivity {

    private EditText etNewPassword;
    private Button btnChangePassword;
    private ImageButton btnBack;
    private TextView tvCurrentPassword;

    private DatabaseReference passRef;
    private final String DEVICE_ID = "esp32-frontdoor-01";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_management);

        etNewPassword = findViewById(R.id.etNewPassword);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnBack = findViewById(R.id.btnBack);
        tvCurrentPassword = findViewById(R.id.tvCurrentPassword);

        // 🔹 Firebase path chứa mật khẩu chung
        passRef = FirebaseDatabase.getInstance()
                .getReference("Devices")
                .child(DEVICE_ID)
                .child("config")
                .child("password");

        // ✅ Đảm bảo có mật khẩu mặc định nếu chưa tồn tại
        passRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists() || snapshot.getValue() == null) {
                passRef.setValue("1234");
                tvCurrentPassword.setText("Mật khẩu hiện tại: 1234");
            }
        });

        // 🔁 Lấy mật khẩu hiện tại realtime từ Firebase
        passRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String currentPass = snapshot.getValue(String.class);
                tvCurrentPassword.setText("Mật khẩu hiện tại: " + (currentPass == null ? "—" : currentPass));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PasswordManagementActivity.this, "Không tải được mật khẩu!", Toast.LENGTH_SHORT).show();
            }
        });

        btnBack.setOnClickListener(v -> finish());

        // 🔒 Đổi mật khẩu chung
        btnChangePassword.setOnClickListener(v -> {
            String newPass = etNewPassword.getText().toString().trim();

            if (newPass.isEmpty()) {
                Toast.makeText(this, "Nhập mật khẩu mới!", Toast.LENGTH_SHORT).show();
                return;
            }

            // ✅ Cập nhật trực tiếp vào Firebase
            passRef.setValue(newPass)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "✅ Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show();
                        etNewPassword.setText("");
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "❌ Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });
    }
}
