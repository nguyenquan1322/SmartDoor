package com.example.smartdoor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    LinearLayout btnUnlock, btnSecurity, btnHistory, btnLogout;
    TextView tvGreeting;
    DatabaseReference userRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnUnlock = findViewById(R.id.btnUnlock);
        btnSecurity = findViewById(R.id.btnSecurity);
        btnHistory = findViewById(R.id.btnHistory);
        btnLogout = findViewById(R.id.btnLogout);
        tvGreeting = findViewById(R.id.tvGreeting);

        // Kiểm tra layout
        if (tvGreeting == null) {
            Toast.makeText(this, "⚠️ Layout thiếu tvGreeting!", Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("SmartDoorPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "");
        if (username.isEmpty()) {
            // Nếu không có user thì quay lại Login
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        userRef = FirebaseDatabase.getInstance().getReference("Users").child(username);

        // 🔹 Lấy displayName từ Firebase
        userRef.child("displayName").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = snapshot.getValue(String.class);
                if (name == null || name.isEmpty()) name = "Người dùng";

                String greeting = buildGreeting(name);
                tvGreeting.setText(greeting);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvGreeting.setText("Xin chào, Người dùng!");
            }
        });

        // 🔐 Chức năng mô phỏng
        btnUnlock.setOnClickListener(v ->
                Toast.makeText(this, "🔓 Mở khóa cửa...", Toast.LENGTH_SHORT).show());

        btnSecurity.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SecuritySettingsActivity.class);
            startActivity(intent);
        });

        btnHistory.setOnClickListener(v ->
                Toast.makeText(this, "📜 Xem lịch sử mở khóa!", Toast.LENGTH_SHORT).show());

        // 🚪 Đăng xuất
        btnLogout.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();

            DatabaseReference deviceRef = FirebaseDatabase.getInstance()
                    .getReference("Devices").child("esp32-frontdoor-01");
            deviceRef.removeValue();

            Toast.makeText(this, "Đã đăng xuất ✅", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    // 🕐 Hàm chào theo giờ
    private String buildGreeting(String name) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12)
            return "Chào buổi sáng, " + name + " ☀️";
        else if (hour >= 12 && hour < 18)
            return "Chào buổi chiều, " + name + " 🌤️";
        else
            return "Chào buổi tối, " + name + " 🌙";
    }
}
