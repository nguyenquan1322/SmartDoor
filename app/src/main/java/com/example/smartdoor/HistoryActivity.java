package com.example.smartdoor;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * HistoryActivity (gọn, chỉ 3 loại log):
 * - open_door         -> "Mo cua"
 * - fingerprint_fail  -> "Van tay sai"
 * - keypad_fail       -> "Nhap sai ma PIN"
 *
 * Hiển thị realtime từ /Logs/{username}
 */
public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private LogAdapter adapter;
    private List<LogItem> items = new ArrayList<>();
    private DatabaseReference logsRef;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            finish(); // quay lại màn trước (MainActivity)
        });

        recyclerView = findViewById(R.id.recyclerViewHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LogAdapter(items);
        recyclerView.setAdapter(adapter);

        // Lấy username đã lưu
        SharedPreferences prefs = getSharedPreferences("SmartDoorPrefs", MODE_PRIVATE);
        username = prefs.getString("username", "");
        if (username == null || username.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy user. Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        logsRef = FirebaseDatabase.getInstance().getReference("Logs").child(username);

        // Lắng nghe realtime (thêm mới ở đầu)
        logsRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                if (map == null) return;

                String event = String.valueOf(map.getOrDefault("event", ""));
                // chỉ xử lý 3 loại: open_door, fingerprint_fail, keypad_fail
                if (!"open_door".equals(event) && !"fingerprint_fail".equals(event) && !"keypad_fail".equals(event)) {
                    return; // ignore other events
                }

                long rawTs = parseLong(map.get("timestamp"));
                long ts = normalizeTimestamp(rawTs);

                String message = String.valueOf(map.getOrDefault("message", defaultMessageForEvent(event)));
                String device = String.valueOf(map.getOrDefault("device", "unknown"));

                LogItem li = new LogItem(event, message, device, ts);
                items.add(0, li);
                adapter.notifyItemInserted(0);
                recyclerView.scrollToPosition(0);
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(HistoryActivity.this, "Không thể kết nối logs: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ================= Helpers =================
    private long parseLong(Object o) {
        if (o == null) return 0L;
        try {
            if (o instanceof Long) return (Long) o;
            if (o instanceof Integer) return ((Integer)o).longValue();
            if (o instanceof Double) return ((Double)o).longValue();
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return 0L;
        }
    }

    // Nếu timestamp là seconds convert -> ms. Nếu 0 -> now
    private long normalizeTimestamp(long raw) {
        if (raw <= 0) return System.currentTimeMillis();
        // if looks like seconds (less than 1e11) treat as seconds
        if (raw < 100_000_000_000L) return raw * 1000L;
        return raw;
    }

    private String defaultMessageForEvent(String event) {
        switch (event) {
            case "open_door": return "Mo cua";
            case "fingerprint_fail": return "Van tay sai";
            case "keypad_fail": return "Nhap sai ma PIN";
            default: return "";
        }
    }

    // ================= Model (nhỏ gọn) =================
    private static class LogItem {
        String event;
        String message;
        String device;
        long timestamp;
        LogItem(String e, String m, String d, long t) { event = e; message = m; device = d; timestamp = t; }
    }

    // ================= Adapter =================
    private class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogVH> {
        private final List<LogItem> data;
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy - HH:mm:ss", Locale.getDefault());

        LogAdapter(List<LogItem> list) { data = list; }

        @NonNull
        @Override
        public LogVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_compact, parent, false);
            return new LogVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull LogVH holder, int position) {
            LogItem it = data.get(position);

            // title (translated)
            String title;
            int iconRes;
            int color;

            switch (it.event) {
                case "open_door":
                    title = "🔓 Mở cửa thành công";
                    iconRes = R.drawable.icons_open_door;
                    color = 0xFF2E7D32; // green
                    break;
                case "fingerprint_fail":
                    title = "Vân tay sai";
                    iconRes = R.drawable.fingerprint_error;
                    color = 0xFFD32F2F; // red
                    break;
                case "keypad_fail":
                    title = "Nhập sai mật khẩu";
                    iconRes = R.drawable.password_fail;
                    color = 0xFFD32F2F; // red
                    break;
                default:
                    title = it.message != null && !it.message.isEmpty() ? it.message : "Sự kiện";
                    iconRes = android.R.drawable.ic_menu_info_details;
                    color = 0xFF444444;
            }

            holder.ivIcon.setImageResource(iconRes);
            holder.tvTitle.setText(title);
            holder.tvTitle.setTextColor(color);

            holder.tvMessage.setText(it.message);
            holder.tvDevice.setText(it.device);

            String time = sdf.format(new Date(it.timestamp));
            holder.tvTime.setText(time);
        }

        @Override public int getItemCount() { return data.size(); }

        class LogVH extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvTitle, tvMessage, tvTime, tvDevice;
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
