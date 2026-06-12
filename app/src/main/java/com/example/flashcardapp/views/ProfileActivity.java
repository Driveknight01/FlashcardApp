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

    // 🌟 Biến cờ hiệu nhận biết phân quyền để xử lý luồng giao diện thông minh
    private String userRole = "user";

    // Launcher đảm nhận mở kho ảnh máy và lấy URI ảnh về
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        binding.imgProfileAvatar.setImageURI(selectedImageUri);
                        binding.txtProfileAvatarLetter.setVisibility(android.view.View.GONE);
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

        // 🌟 BẮT INTENT: Hứng biến phân quyền từ Trang chủ gửi sang
        if (getIntent() != null && getIntent().hasExtra("USER_ROLE")) {
            userRole = getIntent().getStringExtra("USER_ROLE");
        }

        // Đóng màn hình quay lại đúng trang chủ ban đầu khi click Toolbar nút Back
        binding.toolbarProfile.setNavigationOnClickListener(v -> handleBackNavigation());

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

                        // Điều hướng dứt điểm về LoginActivity và xóa sạch các Activity cũ trong Stack
                        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });
    }

    // Ép bắt cả sự kiện bấm nút Back vật lý hoặc cử chỉ vuốt viền dưới đáy điện thoại
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        handleBackNavigation();
    }

    // 🌟 LUỒNG ĐIỀU HƯỚNG QUAY LẠI CHUẨN XÁC
    private void handleBackNavigation() {
        // Chỉ cần lệnh finish() để giải phóng ProfileActivity hiện hành khỏi Stack,
        // hệ thống Android sẽ tự động đưa người dùng về lại Activity trang chủ nằm liền kề trước đó.
        finish();
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();

            // Nhận diện Admin: Đổi màu chữ tên tài khoản sang sắc hồng thẫm nổi bật
            if ("admin".equals(userRole)) {
                binding.txtProfileName.setTextColor(android.graphics.Color.parseColor("#E91E63"));
            }

            mFirestore.collection("users").document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (binding == null) return;
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            if (name != null && !name.trim().isEmpty()) {
                                // Nếu là Admin hệ thống, đính kèm thêm nhãn text hiển thị
                                if ("admin".equals(userRole)) {
                                    binding.txtProfileName.setText(name + " (Quản trị viên)");
                                } else {
                                    binding.txtProfileName.setText(name);
                                }

                                String firstLetter = name.trim().substring(0, 1).toUpperCase();
                                binding.txtProfileAvatarLetter.setText(firstLetter);
                            }
                        } else {
                            String displayName = currentUser.getDisplayName();
                            binding.txtProfileName.setText(displayName != null ? displayName : ("admin".equals(userRole) ? "Quản trị viên" : "Học viên"));
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