package com.example.flashcardapp.views;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.flashcardapp.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminViewUserDecksActivity extends AppCompatActivity {

    private FirebaseFirestore mFirestore;
    private final List<DocumentSnapshot> userDecksList = new ArrayList<>();
    private UserDecksAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_view_user_decks);

        mFirestore = FirebaseFirestore.getInstance();

        // Xử lý nút quay lại màn hình danh sách học viên
        findViewById(R.id.btnBackFromUserDecks).setOnClickListener(v -> finish());

        // Hứng UID và Tên học viên từ màn hình AdminUsersActivity truyền sang
        String userUid = getIntent().getStringExtra("TARGET_USER_UID");
        String userName = getIntent().getStringExtra("TARGET_USER_NAME");

        TextView txtHeaderTitle = findViewById(R.id.txtHeaderTitleUserDecks);
        if (userName != null) {
            txtHeaderTitle.setText("Kho thẻ của: " + userName);
        }

        // Khởi tạo RecyclerView phẳng đồng bộ
        RecyclerView rv = findViewById(R.id.rvAdminUserDecksList);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserDecksAdapter(userDecksList);
        rv.setAdapter(adapter);

        if (userUid != null) {
            loadUserDecksRealtime(userUid);
        }
    }

    private void loadUserDecksRealtime(String uid) {
        // 🌟 CHUẨN LOGIC: Quét personal_decks lọc theo UID học viên
        mFirestore.collection("personal_decks")
                .whereEqualTo("userId", uid)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Lỗi nạp dữ liệu: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshots != null) {
                        userDecksList.clear();

                        // 🌟 BỘ LỌC CHÍ MẠNG: Chỉ lấy học phần tự tạo, loại bỏ học phần hệ thống clone
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            String author = doc.getString("author");
                            if (!"Hệ thống".equals(author)) {
                                userDecksList.add(doc);
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    // ================= ADAPTER QUẢN LÝ LIST ITEM PHẲNG CỦA ADMIN =================
    private static class UserDecksAdapter extends RecyclerView.Adapter<UserDecksAdapter.ViewHolder> {
        private final List<DocumentSnapshot> list;

        public UserDecksAdapter(List<DocumentSnapshot> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Tái sử dụng layout ô vuông xám viền mảnh cực sạch của bạn
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_global_deck, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = list.get(position);
            String title = doc.getString("title");

            // Bóc tách số lượng thẻ bên trong mảng cards
            List<?> cards = (List<?>) doc.get("cards");
            int cardSize = cards != null ? cards.size() : 0;

            // Bóc tách số lượt học của bộ thẻ
            int studyCount = 0;
            Object studyCountObj = doc.get("studyCount");
            if (studyCountObj instanceof Number) {
                studyCount = ((Number) studyCountObj).intValue();
            }

            holder.txtTitle.setText(title != null ? title : "Chưa đặt tên học phần");
            holder.txtCount.setText(cardSize + " thuật ngữ  •  " + studyCount + " lượt học");

            // 🌟 CHỨC NĂNG KIỂM DUYỆT: Tap vào dòng để bung Dialog xem nội dung chi tiết
            holder.itemView.setOnClickListener(v -> {
                if (cards == null || cards.isEmpty()) {
                    Toast.makeText(v.getContext(), "Bộ học phần này trống, chưa có thuật ngữ nào!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Vòng lặp chuyển mảng Card Object từ Firebase thành văn bản liệt kê
                StringBuilder textBuilder = new StringBuilder();
                for (Object item : cards) {
                    if (item instanceof Map) {
                        Map<?, ?> cardMap = (Map<?, ?>) item;
                        String term = String.valueOf(cardMap.get("term"));
                        String definition = String.valueOf(cardMap.get("definition"));

                        textBuilder.append("• ").append(term).append(" : ").append(definition).append("\n\n");
                    }
                }

                // Hiện hộp thoại Dialog phẳng Material nịnh mắt
                new MaterialAlertDialogBuilder(v.getContext())
                        .setTitle("Bộ thẻ: " + (title != null ? title : ""))
                        .setMessage(textBuilder.toString().trim())
                        .setPositiveButton("    Đóng", (dialog, which) -> dialog.dismiss())
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtTitle, txtCount;

            ViewHolder(View iv) {
                super(iv);
                txtTitle = iv.findViewById(R.id.txtAdminDeckTitle);
                txtCount = iv.findViewById(R.id.txtAdminCardCount);
            }
        }
    }
}