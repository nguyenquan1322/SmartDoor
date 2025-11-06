package com.example.smartdoor;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import java.util.HashSet;
import java.util.Set;

public class PasswordManagementActivity extends AppCompatActivity {

    private EditText etNewPassword;
    private Button btnChangePassword;
    private ImageButton btnBack;
    private TextView tvCurrentPassword;

    private DatabaseReference passRef;
    private ChildEventListener logListener;
    private final String DEVICE_ID = "esp32-frontdoor-01";
    private String currentUser = "";
    private AlertDialog progressDialog;

    // 🔒 Cờ kiểm tra Activity đang sống
    private boolean isActivityActive = false;

    // 🕓 Dùng để lọc log cũ
    private long screenOpenTime = 0;
    private final Set<String> processedIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_management);

        isActivityActive = true;
        screenOpenTime = System.currentTimeMillis();

        etNewPassword = findViewById(R.id.etNewPassword);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnBack = findViewById(R.id.btnBack);
        tvCurrentPassword = findViewById(R.id.tvCurrentPassword);

        // 🔹 Lấy user hiện tại
        currentUser = getSharedPreferences("SmartDoorPrefs", MODE_PRIVATE)
                .getString("username", "");

        if (currentUser.isEmpty()) {
            Toast.makeText(this, "⚠️ Bạn chưa đăng nhập!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 🔹 Firebase path chứa mật khẩu hiện tại
        passRef = FirebaseDatabase.getInstance()
                .getReference("Devices")
                .child(DEVICE_ID)
                .child("config")
                .child("password");

        // ✅ Nếu chưa có mật khẩu, tạo mặc định
        passRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists() || snapshot.getValue() == null) {
                passRef.setValue("1234");
                tvCurrentPassword.setText("Mật khẩu hiện tại: 1234");
            }
        });

        // 🔁 Hiển thị realtime mật khẩu hiện tại
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
        btnChangePassword.setOnClickListener(v -> changePassword());

        // 👂 Lắng nghe log phản hồi từ ESP
        listenForPasswordChangeLogs();
    }

    // 🔄 Gửi lệnh đổi mật khẩu
    private void changePassword() {
        String newPass = etNewPassword.getText().toString().trim();

        if (newPass.isEmpty()) {
            Toast.makeText(this, "Nhập mật khẩu mới!", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseCommandHelper.sendCommand(this, DEVICE_ID, "change_pass", newPass);
        showMessage("🔄 Đang gửi lệnh đổi mật khẩu...", false);

        passRef.setValue(newPass);
        etNewPassword.setText("");
    }

    // 👂 Lắng nghe log phản hồi ESP — chỉ xử lý log mới thật
    // 👂 Lắng nghe log phản hồi ESP — chỉ xử lý log mới thật
    private void listenForPasswordChangeLogs() {
        DatabaseReference logRef = FirebaseDatabase.getInstance()
                .getReference("Logs")
                .child(currentUser);

        logListener = logRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                if (!isActivityActive) return;

                String logId = snapshot.getKey();
                if (logId == null || processedIds.contains(logId)) return;

                String event = snapshot.child("event").getValue(String.class);
                String message = snapshot.child("message").getValue(String.class);
                Object tsObj = snapshot.child("timestamp").getValue();

                long logTime = 0;

                // 🔧 Hỗ trợ cả 2 kiểu timestamp: Long và String
                if (tsObj instanceof Long) {
                    logTime = (Long) tsObj;
                } else if (tsObj instanceof String) {
                    String ts = (String) tsObj;
                    try {
                        // Nếu là chuỗi số (ví dụ: 1730932000000)
                        if (ts.matches("\\d+")) {
                            logTime = Long.parseLong(ts);
                        } else {
                            // Nếu là chuỗi thời gian dạng "yyyy-MM-dd HH:mm:ss"
                            java.text.SimpleDateFormat sdf =
                                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            logTime = sdf.parse(ts).getTime();
                        }
                    } catch (Exception e) {
                        logTime = 0;
                    }
                }

                // ⏱️ Nếu timestamp lỗi hoặc quá cũ -> bỏ qua
                if (logTime == 0 || logTime < screenOpenTime) return;

                processedIds.add(logId);

                if (event == null || message == null) return;

                System.out.println("📡 Log hợp lệ mới: " + event + " | " + message);

                runOnUiThread(() -> {
                    if (!isActivityActive) return;
                    if (progressDialog != null && progressDialog.isShowing())
                        progressDialog.dismiss();

                    if (event.equals("change_pass")) {
                        if (message.contains("change_success")) {
                            updateMessageWithOk("✅ Đổi mật khẩu thành công!");
                        } else if (message.contains("change_failed")) {
                            updateMessageWithOk("❌ Đổi mật khẩu thất bại!");
                        }
                    }
                });
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }


    // 🪄 Popup chờ
    private void showMessage(String message, boolean cancelable) {
        if (!isActivityActive) return;
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();

        runOnUiThread(() -> {
            if (!isActivityActive) return;
            progressDialog = new AlertDialog.Builder(this)
                    .setMessage(message)
                    .setCancelable(cancelable)
                    .create();
            progressDialog.show();
        });
    }

    // 🪄 Popup kết quả
    private void updateMessageWithOk(String message) {
        if (!isActivityActive) return;

        runOnUiThread(() -> {
            if (!isActivityActive) return;
            if (progressDialog != null && progressDialog.isShowing())
                progressDialog.dismiss();

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(message)
                    .setCancelable(true)
                    .setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityActive = false;

        if (progressDialog != null && progressDialog.isShowing())
            progressDialog.dismiss();

        if (logListener != null && currentUser != null && !currentUser.isEmpty()) {
            FirebaseDatabase.getInstance()
                    .getReference("Logs")
                    .child(currentUser)
                    .removeEventListener(logListener);
        }
    }
}
