package com.example.smartdoor;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class FingerprintManagementActivity extends AppCompatActivity {

    private ListView listFingerprints;
    private Button btnAddFingerprint;
    private ImageButton btnBack;
    private ArrayList<String> fingerprints;
    private ArrayList<String> fpKeys;
    private ArrayAdapter<String> adapter;

    private DatabaseReference fpRef;
    private ChildEventListener logListener;
    private final String DEVICE_ID = "esp32-frontdoor-01";
    private String currentUser = "";
    private AlertDialog progressDialog;

    private boolean isActivityActive = false;
    private long startTime = 0;
    private String lastProcessedLogId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fingerprint_management);

        isActivityActive = true;
        startTime = System.currentTimeMillis();

        btnBack = findViewById(R.id.btnBack);
        btnAddFingerprint = findViewById(R.id.btnAddFingerprint);
        listFingerprints = findViewById(R.id.listFingerprints);

        SharedPreferences prefs = getSharedPreferences("SmartDoorPrefs", MODE_PRIVATE);
        currentUser = prefs.getString("username", "");
        lastProcessedLogId = prefs.getString("lastLogId_" + currentUser, "");

        if (currentUser.isEmpty()) {
            Toast.makeText(this, "⚠️ Bạn chưa đăng nhập!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fingerprints = new ArrayList<>();
        fpKeys = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, fingerprints);
        listFingerprints.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());

        fpRef = FirebaseDatabase.getInstance()
                .getReference("Fingerprints")
                .child(DEVICE_ID);

        loadFingerprintList();

        btnAddFingerprint.setOnClickListener(v -> addNextFingerprint());
        listFingerprints.setOnItemClickListener((parent, view, position, id) -> confirmDelete(position));

        listenForFingerprintLogs();
    }

    // 🔁 Load danh sách vân tay
    private void loadFingerprintList() {
        fpRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                fingerprints.clear();
                fpKeys.clear();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String key = child.getKey();
                    String username = child.child("username").getValue(String.class);
                    Object addedAtObj = child.child("addedAt").getValue();
                    Long addedAt = null;
                    if (addedAtObj instanceof Long) addedAt = (Long) addedAtObj;
                    else if (addedAtObj instanceof String) {
                        try { addedAt = Long.parseLong((String) addedAtObj); } catch (Exception ignored) {}
                    }

                    if (username != null && username.equals(currentUser)) {
                        String display = key;
                        if (addedAt != null) {
                            String date = new SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
                                    .format(new Date(addedAt));
                            display += " (" + date + ")";
                        }
                        fingerprints.add(display);
                        fpKeys.add(key);
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(FingerprintManagementActivity.this,
                        "❌ Lỗi tải danh sách vân tay!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ➕ Thêm vân tay
    private void addNextFingerprint() {
        fpRef.get().addOnSuccessListener(snapshot -> {
            int nextId = 1;
            if (snapshot.exists()) {
                int maxId = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    String key = child.getKey();
                    if (key != null && key.startsWith("fp_")) {
                        try {
                            int id = Integer.parseInt(key.replace("fp_", ""));
                            if (id > maxId) maxId = id;
                        } catch (NumberFormatException ignored) {}
                    }
                }
                nextId = maxId + 1;
            }
            String value = String.valueOf(nextId);
            FirebaseCommandHelper.sendCommand(this, DEVICE_ID, "add_fingerprint", value);
            showMessage("👉 Vui lòng đặt ngón tay lên cảm biến...", false);
        }).addOnFailureListener(e ->
                Toast.makeText(this, "⚠️ Không thể đọc danh sách vân tay!", Toast.LENGTH_SHORT).show());
    }

    // ❌ Xóa vân tay
    private void confirmDelete(int position) {
        String fpKey = fpKeys.get(position);
        String fpName = fingerprints.get(position);

        new AlertDialog.Builder(this)
                .setTitle("Xóa vân tay")
                .setMessage("Bạn có chắc muốn xóa \"" + fpName + "\" không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    String idStr = fpKey.replace("fp_", "");
                    FirebaseCommandHelper.sendCommand(this, DEVICE_ID, "delete_fingerprint", idStr);
                    showMessage("🗑️ Đang xóa vân tay...", false);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // 👂 Lắng nghe log Firebase (chỉ log mới)
    private void listenForFingerprintLogs() {
        DatabaseReference logRef = FirebaseDatabase.getInstance()
                .getReference("Logs")
                .child(currentUser);

        logListener = logRef.limitToLast(30).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                if (!isActivityActive) return;

                String logId = snapshot.getKey();
                if (logId == null) return;

                // 🔹 Bỏ qua log cũ (nếu logId trùng hoặc log nằm trong 1s đầu)
                if (logId.equals(lastProcessedLogId) || System.currentTimeMillis() - startTime < 1000) return;

                lastProcessedLogId = logId;
                saveLastProcessedLog(logId);

                String event = snapshot.child("event").getValue(String.class);
                String message = snapshot.child("message").getValue(String.class);
                if (event == null || message == null) return;

                System.out.println("📡 Log mới: " + event + " | " + message);

                runOnUiThread(() -> {
                    if (!isActivityActive) return;
                    if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();

                    if (event.equals("add_fingerprint")) {
                        if (message.contains("add_enroll_success")) {
                            showResultDialog("✅ Thêm vân tay thành công!");
                        } else if (message.contains("add_failed")) {
                            showResultDialog("❌ Thêm vân tay thất bại!");
                        }
                    } else if (event.equals("delete_fingerprint")) {
                        if (message.contains("delete_success")) {
                            showResultDialog("🗑️ Xóa vân tay thành công!");
                        } else if (message.contains("delete_failed")) {
                            showResultDialog("❌ Xóa vân tay thất bại!");
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

    // 📦 Lưu log ID cuối cùng đã xử lý
    private void saveLastProcessedLog(String logId) {
        getSharedPreferences("SmartDoorPrefs", MODE_PRIVATE)
                .edit()
                .putString("lastLogId_" + currentUser, logId)
                .apply();
    }

    // Popup chờ
    private void showMessage(String message, boolean cancelable) {
        if (!isActivityActive) return;
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();

        progressDialog = new AlertDialog.Builder(this)
                .setMessage(message)
                .setCancelable(cancelable)
                .create();
        progressDialog.show();
    }

    // Popup kết quả
    private void showResultDialog(String message) {
        if (!isActivityActive) return;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton("OK", (d, w) -> d.dismiss())
                .create();
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityActive = false;
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        if (logListener != null && currentUser != null && !currentUser.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("Logs").child(currentUser)
                    .removeEventListener(logListener);
        }
    }
}
