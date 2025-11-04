package com.example.smartdoor;

import android.content.Context;
import android.widget.Toast;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;

public class FirebaseCommandHelper {

    public static void sendCommand(Context context, String deviceId, String type, String value) {
        DatabaseReference cmdRef = FirebaseDatabase.getInstance()
                .getReference("Commands")
                .child(deviceId);

        String requestId = "req_" + System.currentTimeMillis();

        HashMap<String, Object> cmd = new HashMap<>();
        cmd.put("type", type);
        cmd.put("value", value == null ? "" : value);
        cmd.put("timestamp", System.currentTimeMillis());
        cmd.put("requestId", requestId);

        cmdRef.setValue(cmd)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(context, "✅ Gửi lệnh thành công: " + type, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(context, "❌ Lỗi gửi lệnh: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
