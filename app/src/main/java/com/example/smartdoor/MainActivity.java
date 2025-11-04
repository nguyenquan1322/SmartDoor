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
    TextView tvGreeting, tvDoorStatus;
    DatabaseReference userRef, statusRef;
    String deviceId = "esp32-frontdoor-01";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnUnlock = findViewById(R.id.btnUnlock);
        btnSecurity = findViewById(R.id.btnSecurity);
        btnHistory = findViewById(R.id.btnHistory);
        btnLogout = findViewById(R.id.btnLogout);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvDoorStatus = findViewById(R.id.tvDoorStatus);

        SharedPreferences prefs = getSharedPreferences("SmartDoorPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "");

        if (username.isEmpty()) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        userRef = FirebaseDatabase.getInstance().getReference("Users").child(username);

        // ✅ Hiển thị lời chào
        userRef.child("displayName").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = snapshot.getValue(String.class);
                if (name == null || name.isEmpty()) name = "Người dùng";
                tvGreeting.setText(buildGreeting(name));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvGreeting.setText("Xin chào, Người dùng!");
            }
        });

        // 🔹 Theo dõi trạng thái cửa realtime từ ESP
        statusRef = FirebaseDatabase.getInstance()
                .getReference("Devices")
                .child(deviceId)
                .child("status");

        statusRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if (status != null && !status.isEmpty()) {
                    tvDoorStatus.setText("📡 Trạng thái: " + status);
                } else {
                    tvDoorStatus.setText("📡 Trạng thái: Chưa có phản hồi");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvDoorStatus.setText("⚠️ Không đọc được trạng thái thiết bị");
            }
        });

        // 🔓 MỞ CỬA — gửi lệnh xuống Firebase
        btnUnlock.setOnClickListener(v -> {
            FirebaseCommandHelper.sendCommand(this, deviceId, "open_door", "");
            tvDoorStatus.setText("🔁 Đang gửi lệnh mở cửa...");
        });

        // ⚙️ Chuyển sang phần bảo mật
        btnSecurity.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SecuritySettingsActivity.class);
            startActivity(intent);
        });

        // 📜 Lịch sử mở khóa
        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        // 🚪 Đăng xuất
        btnLogout.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();

            FirebaseDatabase.getInstance().getReference("Devices")
                    .child(deviceId).child("assignedUser").removeValue();

            Toast.makeText(this, "Đã đăng xuất ✅", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    // 🕐 Hàm tạo lời chào theo giờ
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
