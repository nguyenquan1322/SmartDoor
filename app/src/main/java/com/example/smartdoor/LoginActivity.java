package com.example.smartdoor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.FirebaseDatabase;

public class LoginActivity extends AppCompatActivity {

    EditText etUsername, etPassword;
    Button btnLogin;
    TextView tvRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin   = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);

        btnLogin.setOnClickListener(v -> {
            String inputUser = etUsername.getText().toString().trim();
            String inputPass = etPassword.getText().toString().trim();

            if (inputUser.isEmpty() || inputPass.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tài khoản và mật khẩu!", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences prefs = getSharedPreferences("SmartDoorPrefs", MODE_PRIVATE);

            // 🔹 Dữ liệu mặc định
            String defaultUser = "admin";
            String defaultPass = "12345";

            // 🔹 Dữ liệu người dùng đã đăng ký
            String savedUser = prefs.getString("username", "");
            String savedPass = prefs.getString("password", "");

            boolean isDefaultLogin = inputUser.equals(defaultUser) && inputPass.equals(defaultPass);
            boolean isRegisteredLogin = inputUser.equals(savedUser) && inputPass.equals(savedPass);

            if (isDefaultLogin || isRegisteredLogin) {
                Toast.makeText(this, "Đăng nhập thành công ✅", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            } else {
                Toast.makeText(this, "Sai tài khoản hoặc mật khẩu ❌", Toast.LENGTH_SHORT).show();
            }
        });

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });
        FirebaseDatabase.getInstance().getReference("test").setValue("Hello from SmartDoor");
    }
}
