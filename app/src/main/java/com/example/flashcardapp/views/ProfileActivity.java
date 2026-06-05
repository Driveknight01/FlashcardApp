package com.example.flashcardapp.views;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.flashcardapp.databinding.ActivityProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    // Launcher đảm nhận mở kho ảnh máy và lấy URI ảnh về
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        // Đổ ảnh vừa chọn lên ImageView ngay lập tức
                        binding.imgProfileAvatar.setImageURI(selectedImageUri);
                        binding.txtProfileAvatarLetter.setVisibility(android.view.View.GONE);

                        // Ở đây bạn có thể viết thêm hàm upload selectedImageUri lên Firebase Storage
                        // rồi lưu URL vào Firestore nếu muốn lưu vĩnh viễn trên mây.
                        Toast.makeText(this, "Đã cập nhật ảnh đại diện tạm thời!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        binding.toolbarProfile.setNavigationOnClickListener(v -> finish());

        // 1. Tải thông tin cá nhân của User hiện tại lên màn hình
        loadUserProfile();

        // 2. SỰ KIỆN CLICK VÀO AVATAR ĐỂ ĐỔI ẢNH
        binding.cardProfileAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });

        // 3. SỰ KIỆN BẤM NÚT ĐĂNG XUẤT (Có hộp thoại xác nhận an toàn)
        binding.btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Đăng xuất tài khoản?")
                    .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi ứng dụng học phần không?")
                    .setPositiveButton("Đăng xuất", (dialog, which) -> {
                        mAuth.signOut(); // Xóa session đăng nhập trên hệ thống Firebase

                        // Điều hướng dứt điểm về LoginActivity và xóa sạch các Activity cũ
                        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();

            // Đọc tên từ Firestore bảng users
            mFirestore.collection("users").document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (binding == null) return;
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            if (name != null && !name.trim().isEmpty()) {
                                binding.txtProfileName.setText(name);

                                // Thiết lập chữ cái đầu tiên làm avatar dự phòng giống trang chủ
                                String firstLetter = name.trim().substring(0, 1).toUpperCase();
                                binding.txtProfileAvatarLetter.setText(firstLetter);
                            }
                        } else {
                            // Nếu bảng users chưa có dữ liệu tên, lấy tạm DisplayName bên Auth
                            String displayName = currentUser.getDisplayName();
                            binding.txtProfileName.setText(displayName != null ? displayName : "Học viên");
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (currentUser.getDisplayName() != null) {
                            binding.txtProfileName.setText(currentUser.getDisplayName());
                        }
                    });
        }
    }
}