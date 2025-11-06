package com.example.smartdoor;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.*;
import java.text.*;
import java.util.*;

/**
 * Giao diện đơn giản – đẹp:
 * - Chỉ 2 nút nhỏ gọn 📅 và 📋
 * - Nút bo góc, màu dịu nhẹ
 * - Tự lọc log open_door, fingerprint_fail, keypad_fail
 */
public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private LogAdapter adapter;
    private final List<LogItem> allLogs = new ArrayList<>();
    private final List<LogItem> filteredLogs = new ArrayList<>();

    private DatabaseReference logsRef;
    private String username;
    private Date selectedDate = null;

    private TextView tvSelectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerViewHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LogAdapter(filteredLogs);
        recyclerView.setAdapter(adapter);

        // 🎨 Thanh lọc nhỏ gọn, bo góc và nhẹ mắt
        LinearLayout filterBar = new LinearLayout(this);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filterBar.setGravity(Gravity.CENTER_VERTICAL);
        filterBar.setPadding(16, 12, 16, 12);
        filterBar.setBackground(createRoundedBackground(0xFFE3F2FD, 20));
        filterBar.setElevation(5);

        // 📅 Nút chọn ngày
        Button btnPickDate = new Button(this);
        btnPickDate.setText("📅");
        styleMiniButton(btnPickDate, 0xFF42A5F5);
        btnPickDate.setOnClickListener(v -> showDatePicker());

        // 📋 Nút xem tất cả
        Button btnShowAll = new Button(this);
        btnShowAll.setText("📋");
        styleMiniButton(btnShowAll, 0xFF43A047);
        btnShowAll.setOnClickListener(v -> {
            selectedDate = null;
            tvSelectedDate.setText("Tất cả ngày");
            applyFilter();
        });

        // 🕓 Text hiển thị ngày đã chọn
        tvSelectedDate = new TextView(this);
        tvSelectedDate.setText("Tất cả ngày");
        tvSelectedDate.setTextSize(14);
        tvSelectedDate.setTextColor(0xFF0D47A1);
        tvSelectedDate.setPadding(16, 0, 0, 0);

        filterBar.addView(btnPickDate);
        filterBar.addView(btnShowAll);
        filterBar.addView(tvSelectedDate);

        // ✅ Thêm filterBar vào layout
        LinearLayout root = findViewById(R.id.rootHistoryLayout);
        root.addView(filterBar, 1);

        // 🔹 Lấy user
        SharedPreferences prefs = getSharedPreferences("SmartDoorPrefs", MODE_PRIVATE);
        username = prefs.getString("username", "");
        if (username == null || username.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy user!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        logsRef = FirebaseDatabase.getInstance().getReference("Logs").child(username);
        listenLogs();
    }

    // 👂 Lắng nghe log từ Firebase
    private void listenLogs() {
        logsRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                if (map == null) return;

                String event = String.valueOf(map.getOrDefault("event", ""));
                if (!Arrays.asList("open_door", "fingerprint_fail", "keypad_fail").contains(event))
                    return;

                String message = String.valueOf(map.getOrDefault("message", ""));
                String device = String.valueOf(map.getOrDefault("device", "unknown"));
                long ts = normalizeTimestamp(map.get("timestamp"));

                allLogs.add(0, new LogItem(event, message, device, ts));
                applyFilter();
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HistoryActivity.this, "Lỗi: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
        });
    }

    // 📅 Chọn ngày
    private void showDatePicker() {
        final Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(y, m, d, 0, 0, 0);
            selectedDate = picked.getTime();

            SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            tvSelectedDate.setText("Ngày: " + df.format(selectedDate));
            applyFilter();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // 🔍 Lọc log theo ngày
    private void applyFilter() {
        filteredLogs.clear();
        if (selectedDate == null) {
            filteredLogs.addAll(allLogs);
        } else {
            Calendar sel = Calendar.getInstance();
            sel.setTime(selectedDate);
            int selYear = sel.get(Calendar.YEAR);
            int selDay = sel.get(Calendar.DAY_OF_YEAR);
            for (LogItem item : allLogs) {
                Calendar logCal = Calendar.getInstance();
                logCal.setTime(new Date(item.timestamp));
                if (logCal.get(Calendar.YEAR) == selYear &&
                        logCal.get(Calendar.DAY_OF_YEAR) == selDay)
                    filteredLogs.add(item);
            }
        }
        adapter.notifyDataSetChanged();
        recyclerView.scrollToPosition(0);
    }

    // ================== UI Helper ==================
    private void styleMiniButton(Button btn, int color) {
        btn.setBackground(createRoundedBackground(color, 25));
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(15);
        btn.setAllCaps(false);
        btn.setPadding(25, 8, 25, 8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(5, 0, 5, 0);
        btn.setLayoutParams(lp);
    }

    private android.graphics.drawable.GradientDrawable createRoundedBackground(int color, int radius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setCornerRadius(radius * getResources().getDisplayMetrics().density / 2);
        shape.setColor(color);
        return shape;
    }

    private long normalizeTimestamp(Object t) {
        try {
            long raw = Long.parseLong(String.valueOf(t));
            return raw < 1e11 ? raw * 1000 : raw;
        } catch (Exception e) { return System.currentTimeMillis(); }
    }

    // ================== Model ==================
    private static class LogItem {
        String event, message, device;
        long timestamp;
        LogItem(String e, String m, String d, long t) { event = e; message = m; device = d; timestamp = t; }
    }

    // ================== Adapter ==================
    private class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogVH> {
        private final List<LogItem> data;
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss", Locale.getDefault());
        LogAdapter(List<LogItem> list) { data = list; }

        @NonNull @Override
        public LogVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_compact, parent, false);
            return new LogVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull LogVH h, int pos) {
            LogItem it = data.get(pos);
            int color;
            switch (it.event) {
                case "open_door": h.ivIcon.setImageResource(R.drawable.icons_open_door); color = 0xFF2E7D32; h.tvTitle.setText("🔓 Mở cửa thành công"); break;
                case "fingerprint_fail": h.ivIcon.setImageResource(R.drawable.fingerprint_error); color = 0xFFD32F2F; h.tvTitle.setText("Vân tay sai"); break;
                default: h.ivIcon.setImageResource(R.drawable.password_fail); color = 0xFFD32F2F; h.tvTitle.setText("Nhập sai mật khẩu");
            }
            h.tvTitle.setTextColor(color);
            h.tvMessage.setText(it.message);
            h.tvDevice.setText(it.device);
            h.tvTime.setText(sdf.format(new Date(it.timestamp)));
        }

        @Override public int getItemCount() { return data.size(); }

        class LogVH extends RecyclerView.ViewHolder {
            ImageView ivIcon; TextView tvTitle, tvMessage, tvTime, tvDevice;
            LogVH(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.ivIcon);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvMessage = itemView.findViewById(R.id.tvMessage);
                tvTime = itemView.findViewById(R.id.tvTime);
                tvDevice = itemView.findViewById(R.id.tvDevice);
            }
        }
    }
}
