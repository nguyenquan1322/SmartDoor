package com.example.smartdoor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    LinearLayout btnUnlock, btnSecurity, btnHistory, btnLogout;
    TextView tvGreeting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnUnlock = findViewById(R.id.btnUnlock);
        btnSecurity = findViewById(R.id.btnSecurity);
        btnHistory = findViewById(R.id.btnHistory);
        btnLogout = findViewById(R.id.btnLogout);
        tvGreeting = findViewById(R.id.tvGreeting);

        if (tvGreeting == null) {
            Toast.makeText(this, "⚠️ Layout thiếu tvGreeting!", Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("SmartDoorPrefs", MODE_PRIVATE);
        String name = prefs.getString("display_name", "Người dùng");
        if (name == null || name.isEmpty()) name = "Người dùng";

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour >= 5 && hour < 12)
            greeting = "Chào buổi sáng, " + name + " ☀️";
        else if (hour >= 12 && hour < 18)
            greeting = "Chào buổi chiều, " + name + " 🌤️";
        else
            greeting = "Chào buổi tối, " + name + " 🌙";

        tvGreeting.setText(greeting);

        btnUnlock.setOnClickListener(v ->
                Toast.makeText(this, "🔓 Mở khóa cửa...", Toast.LENGTH_SHORT).show());

        btnSecurity.setOnClickListener(v ->
                Toast.makeText(this, "⚙️ Cài đặt bảo mật!", Toast.LENGTH_SHORT).show());

        btnHistory.setOnClickListener(v ->
                Toast.makeText(this, "📜 Xem lịch sử mở khóa!", Toast.LENGTH_SHORT).show());

        btnLogout.setOnClickListener(v -> {
            Toast.makeText(this, "🚪 Đã đăng xuất!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });
    }
}
