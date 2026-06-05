package com.example.flashcardapp.views;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.flashcardapp.R;
import com.example.flashcardapp.databinding.ActivityAdminUsersBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminUsersActivity extends AppCompatActivity {

    private ActivityAdminUsersBinding binding;
    private FirebaseFirestore mFirestore;
    private final List<Map<String, Object>> userList = new ArrayList<>();
    private UserAdapter userAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminUsersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();

        // Cấu hình Toolbar có nút Back quay lại trang chủ Admin
        binding.toolbarAdminUsers.setNavigationOnClickListener(v -> finish());

        // Cấu hình RecyclerView danh sách dọc quản lý tài khoản người dùng
        binding.rvAdminUsersList.setLayoutManager(new LinearLayoutManager(this));
        userAdapter = new UserAdapter(userList);
        binding.rvAdminUsersList.setAdapter(userAdapter);

        loadUserSystemData();
    }

    // 🚀 THUẬT TOÁN ĐẾM LUỒNG KÉP CHUẨN XÁC: Đồng bộ số lượng học phần dựa theo ID người dùng
    private void loadUserSystemData() {
        mFirestore.collection("users").get().addOnSuccessListener(queryDocumentSnapshots -> {
            userList.clear();
            for (QueryDocumentSnapshot userDoc : queryDocumentSnapshots) {
                Map<String, Object> userData = userDoc.getData();

                // Loại trừ tài khoản Admin, chỉ hiển thị tài khoản User thông thường
                if (!"admin".equals(userData.get("role"))) {
                    String uid = userDoc.getId();
                    userData.put("uid", uid);
                    userData.put("deckCount", 0);
                    userData.put("totalCards", 0);

                    List<String> titles = new ArrayList<>();
                    userData.put("deckTitlesList", titles);
                    userList.add(userData);

                    // Quét toàn bộ bảng personal_decks để tính toán số liệu bất đồng bộ an toàn
                    mFirestore.collection("personal_decks").get().addOnSuccessListener(deckSnapshots -> {
                        int count = 0;
                        int cardsTotal = 0;
                        titles.clear();

                        for (QueryDocumentSnapshot deckDoc : deckSnapshots) {
                            String cId = deckDoc.getString("creatorId");
                            String uId = deckDoc.getString("userId");

                            if (uid.equals(cId) || uid.equals(uId)) {
                                count++;
                                String title = deckDoc.getString("title");
                                List<?> cards = (List<?>) deckDoc.get("cards");
                                int size = (cards != null) ? cards.size() : 0;
                                cardsTotal += size;
                                titles.add("• " + title + " (" + size + " thẻ)");
                            }
                        }

                        // Khống chế khớp mã định danh UID để cập nhật đúng vị trí sườn màn hình
                        for (int i = 0; i < userList.size(); i++) {
                            if (uid.equals(userList.get(i).get("uid"))) {
                                userList.get(i).put("deckCount", count);
                                userList.get(i).put("totalCards", cardsTotal);
                                userAdapter.notifyItemChanged(i);
                                break;
                            }
                        }
                    });
                }
            }
            userAdapter.notifyDataSetChanged();
        });
    }

    // Bung cửa sổ Material Dialog chứa thông tin hồ sơ cụ thể của học viên
    private void showUserDetailPopup(Map<String, Object> u) {
        String name = String.valueOf(u.get("name"));
        String email = String.valueOf(u.get("email"));
        String uid = String.valueOf(u.get("uid"));
        int deckCount = (int) u.get("deckCount");
        int totalCards = (int) u.get("totalCards");
        List<String> deckTitles = (List<String>) u.get("deckTitlesList");

        StringBuilder details = new StringBuilder();
        details.append("📧 Email: ").append(email).append("\n🆔 UID: ").append(uid).append("\n");
        details.append("📊 Đã tạo: ").append(deckCount).append(" bộ học phần (Tổng ").append(totalCards).append(" thẻ)\n\n📂 DANH SÁCH BỘ THẺ THỰC TẾ:\n");

        if (deckTitles != null && !deckTitles.isEmpty()) {
            for (String t : deckTitles) details.append(t).append("\n");
        } else {
            details.append("(Chưa có học phần cá nhân nào)");
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Chi tiết học viên: " + (name.equals("null") ? "Ẩn danh" : name))
                .setMessage(details.toString())
                .setPositiveButton("Đóng lại", null).show();
    }

    // --- ADAPTER DANH SÁCH USER (Dùng chung layout item_admin_user) ---
    private class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {
        private final List<Map<String, Object>> list;
        public UserAdapter(List<Map<String, Object>> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> u = list.get(position);
            String name = String.valueOf(u.get("name"));

            holder.txtName.setText(name.equals("null") ? "Học viên ẩn danh" : name);
            holder.txtEmail.setText(String.valueOf(u.get("email")));
            holder.txtDeckCount.setText("📚 Số bộ thẻ: " + u.get("deckCount"));
            holder.txtCardCount.setText("🎴 Số thẻ: " + u.get("totalCards"));

            holder.itemView.setOnClickListener(v -> showUserDetailPopup(u));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtName, txtEmail, txtDeckCount, txtCardCount;
            ViewHolder(View iv) {
                super(iv);
                txtName = iv.findViewById(R.id.txtAdminUserName);
                txtEmail = iv.findViewById(R.id.txtAdminUserEmail);
                txtDeckCount = iv.findViewById(R.id.txtAdminUserDeckCount);
                txtCardCount = iv.findViewById(R.id.txtAdminUserCardCount);
            }
        }
    }
}