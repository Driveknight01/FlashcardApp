package com.example.flashcardapp.views;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import java.util.Locale;

public class AdminUsersActivity extends AppCompatActivity {

    private FirebaseFirestore mFirestore;
    private final List<DocumentSnapshot> originalUsersList = new ArrayList<>();
    private final List<DocumentSnapshot> filteredUsersList = new ArrayList<>();
    private UsersManagementAdapter adapter;
    private EditText edtSearchUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_users);

        mFirestore = FirebaseFirestore.getInstance();
        edtSearchUser = findViewById(R.id.edtSearchUser);

        findViewById(R.id.btnBackFromUsers).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvAdminUsersList);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UsersManagementAdapter(filteredUsersList);
        rv.setAdapter(adapter);

        loadAllUsersRealtime();
        setupSearchLogic();
    }

    private void loadAllUsersRealtime() {
        mFirestore.collection("users")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    originalUsersList.clear();
                    originalUsersList.addAll(snapshots.getDocuments());

                    // Thực hiện lọc lại danh sách dựa theo chuỗi text hiện tại trong ô tìm kiếm
                    filterUserList(edtSearchUser.getText().toString());
                });
    }

    private void setupSearchLogic() {
        edtSearchUser.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterUserList(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void filterUserList(String text) {
        String query = text.trim().toLowerCase();
        filteredUsersList.clear();

        if (query.isEmpty()) {
            filteredUsersList.addAll(originalUsersList);
        } else {
            for (DocumentSnapshot doc : originalUsersList) {
                String name = doc.getString("name");
                String email = doc.getString("email");

                if ((name != null && name.toLowerCase().contains(query)) ||
                        (email != null && email.toLowerCase().contains(query))) {
                    filteredUsersList.add(doc);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    // 🌟 HỘP THOẠI HÀNH ĐỘNG QUẢN TRỊ VIÊN
    private void showUserActionMenu(DocumentSnapshot userDoc) {
        String uId = userDoc.getId(); // Lấy chuẩn UID tài khoản (ví dụ: 2w0gW...)
        String name = userDoc.getString("name") != null ? userDoc.getString("name") : "Học viên ẩn danh";

        // 🌟 VÁ LỖI AN TOÀN: Nếu trường status chưa tồn tại (null), mặc định hiểu là "active"
        String currentStatus = userDoc.getString("status") != null ? userDoc.getString("status") : "active";

        String statusOption = "active".equals(currentStatus) ? "  Khóa tài khoản này" : "  Mở khóa tài khoản";
        String[] options = {statusOption, "  Xem kho thẻ người dùng"};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Người dùng: " + name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Logic Khóa / Mở khóa
                        String newStatus = "active".equals(currentStatus) ? "banned" : "active";

                        mFirestore.collection("users").document(uId)
                                .update("status", newStatus)
                                .addOnSuccessListener(aVoid -> {
                                    String msg = "active".equals(newStatus) ? "Đã mở khóa tài khoản!" : "Đã khóa tài khoản thành công!";
                                    Toast.makeText(AdminUsersActivity.this, msg, Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> Toast.makeText(AdminUsersActivity.this, "Thao tác thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    } else {
                        // 🌟 Logic Xem kho thẻ cá nhân: Truyền UID và Tên sang Activity phụ
                        Intent intent = new Intent(AdminUsersActivity.this, AdminViewUserDecksActivity.class);
                        intent.putExtra("TARGET_USER_UID", uId);
                        intent.putExtra("TARGET_USER_NAME", name);
                        startActivity(intent);
                    }
                }).show();
    }

    // ================= ADAPTER HIỂN THỊ PHẲNG ĐỒNG BỘ =================
    private class UsersManagementAdapter extends RecyclerView.Adapter<UsersManagementAdapter.ViewHolder> {
        private final List<DocumentSnapshot> list;

        public UsersManagementAdapter(List<DocumentSnapshot> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = list.get(position);
            String name = doc.getString("name");
            String email = doc.getString("email");
            String role = doc.getString("role");
            String status = doc.getString("status");

            holder.txtName.setText(name != null ? name : "Học viên ẩn danh");
            holder.txtStats.setText(email != null ? email : "Không có thông tin email");

            // Xử lý vẽ ký tự đầu tiên lên ô vuông Avatar phẳng
            if (name != null && !name.trim().isEmpty()) {
                holder.txtAvatarLetter.setText(name.substring(0, 1).toUpperCase(Locale.ROOT));
            } else {
                holder.txtAvatarLetter.setText("U");
            }

            // Kiểm tra phân quyền để ẩn/hiện nhãn ADMIN
            if ("admin".equals(role)) {
                holder.txtRoleBadge.setVisibility(View.VISIBLE);
            } else {
                holder.txtRoleBadge.setVisibility(View.GONE);
            }

            // Kiểm tra trạng thái khóa tài khoản để bật cảnh báo màu đỏ mép phải
            if ("banned".equals(status)) {
                holder.txtStatusAlert.setVisibility(View.VISIBLE);
            } else {
                holder.txtStatusAlert.setVisibility(View.GONE);
            }

            // Bắt sự kiện Tap vào dòng học viên để hiển thị menu tính năng tối cao
            holder.itemView.setOnClickListener(v -> showUserActionMenu(doc));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtAvatarLetter, txtName, txtStats, txtRoleBadge, txtStatusAlert;
            ViewHolder(View iv) {
                super(iv);
                txtAvatarLetter = iv.findViewById(R.id.txtUserAvatarLetter);
                txtName = iv.findViewById(R.id.txtAdminUserName);
                txtStats = iv.findViewById(R.id.txtAdminUserStats);
                txtRoleBadge = iv.findViewById(R.id.txtUserRoleBadge);
                txtStatusAlert = iv.findViewById(R.id.txtUserStatusAlert);
            }
        }
    }
}