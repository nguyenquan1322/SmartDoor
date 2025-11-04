package com.example.smartdoor;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class FingerprintManagementActivity extends AppCompatActivity {

    private ArrayList<String> fingerprints;
    private ArrayAdapter<String> adapter;
    private ListView listFingerprints;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fingerprint_management);

        ImageButton btnBack = findViewById(R.id.btnBack);
        Button btnAddFingerprint = findViewById(R.id.btnAddFingerprint);
        listFingerprints = findViewById(R.id.listFingerprints);

        // Giả lập danh sách vân tay
        fingerprints = new ArrayList<>();
        fingerprints.add("Vân tay #1 - Quân");
        fingerprints.add("Vân tay #2 - Tú");
        fingerprints.add("Vân tay #3 - Khách");

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, fingerprints);
        listFingerprints.setAdapter(adapter);

        // Quay lại màn hình trước
        btnBack.setOnClickListener(v -> finish());

        // Thêm vân tay (giả lập phần cứng)
        btnAddFingerprint.setOnClickListener(v -> simulateHardwareAction("Thêm vân tay thành công!", true));

        // Xóa vân tay khi nhấn vào từng dòng
        listFingerprints.setOnItemClickListener((parent, view, position, id) -> {
            String name = fingerprints.get(position);
            confirmDelete(name, position);
        });
    }

    private void confirmDelete(String name, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa vân tay")
                .setMessage("Bạn có chắc muốn xóa \"" + name + "\" không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    simulateHardwareAction("Xóa thành công!", false);
                    fingerprints.remove(position);
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // Giả lập thông báo phần cứng
    private void simulateHardwareAction(String message, boolean add) {
        new AlertDialog.Builder(this)
                .setTitle("Thông báo từ thiết bị")
                .setMessage(message + "\n(Hiện tại: dữ liệu giả lập)")
                .setPositiveButton("OK", null)
                .show();

        if (add) {
            fingerprints.add("Vân tay #" + (fingerprints.size() + 1) + " - Mới");
            adapter.notifyDataSetChanged();
        }
    }
}
