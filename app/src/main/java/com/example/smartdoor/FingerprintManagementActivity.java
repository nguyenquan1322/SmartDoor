package com.example.smartdoor;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class FingerprintManagementActivity extends AppCompatActivity {

    private ListView listFingerprints;
    private Button btnAddFingerprint;
    private ImageButton btnBack;
    private ArrayList<String> fingerprints;
    private ArrayList<String> fpKeys;
    private ArrayAdapter<String> adapter;

    private DatabaseReference fpRef;
    private final String DEVICE_ID = "esp32-frontdoor-01";
    private String currentUser = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fingerprint_management);

        btnBack = findViewById(R.id.btnBack);
        btnAddFingerprint = findViewById(R.id.btnAddFingerprint);
        listFingerprints = findViewById(R.id.listFingerprints);

        SharedPreferences prefs = getSharedPreferences("SmartDoorPrefs", MODE_PRIVATE);
        currentUser = prefs.getString("username", "");

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

        // 🔁 Theo dõi danh sách vân tay chung của thiết bị
        fpRef = FirebaseDatabase.getInstance()
                .getReference("Fingerprints")
                .child(DEVICE_ID);

        fpRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                fingerprints.clear();
                fpKeys.clear();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String key = child.getKey(); // fp_1, fp_2, ...
                    String username = child.child("username").getValue(String.class);
                    Long addedAt = child.child("addedAt").getValue(Long.class);

                    // ✅ Chỉ hiển thị vân tay của user hiện tại
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
                Toast.makeText(FingerprintManagementActivity.this, "❌ Lỗi tải danh sách vân tay!", Toast.LENGTH_SHORT).show();
            }
        });

        // ➕ Thêm vân tay
        btnAddFingerprint.setOnClickListener(v -> addNextFingerprint());

        // ❌ Xóa vân tay
        listFingerprints.setOnItemClickListener((parent, view, position, id) -> confirmDelete(position));
    }

    // ✅ Tự động lấy ID kế tiếp
    private void addNextFingerprint() {
        fpRef.get().addOnSuccessListener(snapshot -> {
            int nextId = 1; // mặc định bắt đầu từ 1

            if (snapshot.exists()) {
                int maxId = 0;
                for (DataSnapshot child : snapshot.getChildren()) {
                    String key = child.getKey(); // fp_1, fp_2...
                    if (key != null && key.startsWith("fp_")) {
                        try {
                            int id = Integer.parseInt(key.replace("fp_", ""));
                            if (id > maxId) maxId = id;
                        } catch (NumberFormatException ignored) {}
                    }
                }
                nextId = maxId + 1;
            }

            // Gửi lệnh tới ESP: "add_fingerprint", "3,quan"
            String value = nextId + "";
            FirebaseCommandHelper.sendCommand(this, DEVICE_ID, "add_fingerprint", value);

            Toast.makeText(this, "📲 Gửi lệnh thêm vân tay ID = " + nextId, Toast.LENGTH_SHORT).show();

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "⚠️ Không thể đọc danh sách vân tay hiện tại!", Toast.LENGTH_SHORT).show();
        });
    }

    // ❌ Xóa vân tay được chọn
    private void confirmDelete(int position) {
        String fpKey = fpKeys.get(position);
        String fpName = fingerprints.get(position);

        new AlertDialog.Builder(this)
                .setTitle("Xóa vân tay")
                .setMessage("Bạn có chắc muốn xóa \"" + fpName + "\" không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    String idStr = fpKey.replace("fp_", "");
                    String value = idStr + ""; // gửi "2,quan"
                    FirebaseCommandHelper.sendCommand(this, DEVICE_ID, "delete_fingerprint", value);
                    Toast.makeText(this, "📤 Đã gửi lệnh xóa vân tay #" + idStr, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}
