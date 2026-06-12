package com.example.flashcardapp;

import com.example.flashcardapp.views.CreateDeckActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.flashcardapp.databinding.ActivityMainBinding;
import com.example.flashcardapp.views.fragments.HomeFragment;
import com.example.flashcardapp.views.fragments.LibraryFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private String userRole = "user"; // Mặc định là user nếu không nhận được dữ liệu

    private FirebaseFirestore mFirestore;
    private ListenerRegistration banCheckListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sử dụng View Binding sạch sẽ
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();

        // Nhận thông tin phân quyền từ LoginActivity gửi sang
        if (getIntent() != null && getIntent().hasExtra("USER_ROLE")) {
            userRole = getIntent().getStringExtra("USER_ROLE");
        }

        // Mặc định khi vừa mở App, hiển thị màn hình Trang chủ (HomeFragment) đầu tiên
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        // Cài đặt sự kiện lắng nghe khi người dùng click vào các Tab điều hướng dưới cùng
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                loadFragment(new HomeFragment());
                return true;
            } else if (itemId == R.id.nav_library) {
                loadFragment(new LibraryFragment());
                return true;
            } else if (itemId == R.id.nav_create) {
                // Khi bấm nút Tạo (+), chúng ta sẽ mở màn hình tạo bộ thẻ mới
                Intent intent = new Intent(MainActivity.this, CreateDeckActivity.class);
                intent.putExtra("USER_ROLE", userRole); // Truyền quyền sang để biết là Admin hay User tạo thẻ
                startActivity(intent);
                return false; // Trả về false để không làm thay đổi vị trí Tab đang chọn hiện tại
            }
            return false;
        });

        // 🌟 KÍCH HOẠT: Bắt đầu lắng nghe trạng thái Khóa tài khoản Realtime từ phía Admin
        startCheckingUserStatusRealtime();
    }

    private void startCheckingUserStatusRealtime() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String currentUid = currentUser.getUid();

        // Lắng nghe trực tiếp document của cơ sở dữ liệu User này
        banCheckListener = mFirestore.collection("users").document(currentUid)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null || binding == null) return;

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");

                        // 🌟 CHÍ MẠNG: Nếu Admin bấm nút Đổi trạng thái thành "banned"
                        if ("banned".equals(status)) {
                            // 1. Giải phóng bộ lắng nghe chạy ngầm ngay lập tức để tránh rò rỉ bộ nhớ
                            if (banCheckListener != null) {
                                banCheckListener.remove();
                                banCheckListener = null;
                            }

                            // 2. Đăng xuất tài khoản sạch sẽ khỏi hệ thống FirebaseAuth
                            FirebaseAuth.getInstance().signOut();

                            // 3. Bật thông báo Toast cảnh báo lý do bị văng ứng dụng
                            Toast.makeText(MainActivity.this,
                                    "Tài khoản của bạn đã bị khóa do vi phạm tiêu chuẩn cộng đồng!",
                                    Toast.LENGTH_LONG).show();

                            // 4. Ép buộc đóng băng và điều hướng người dùng quay lại màn hình đăng nhập cũ
                            // Giả định màn hình đăng nhập của bạn tên là LoginActivity.class
                            try {
                                Class<?> loginActivityClass = Class.forName("com.example.flashcardapp.views.LoginActivity");
                                Intent intent = new Intent(MainActivity.this, loginActivityClass);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            } catch (ClassNotFoundException e) {
                                // Nếu LoginActivity của bạn nằm ở thư mục gốc com.example.flashcardapp thay vì .views
                                try {
                                    Class<?> rootLoginClass = Class.forName("com.example.flashcardapp.LoginActivity");
                                    Intent intent = new Intent(MainActivity.this, rootLoginClass);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                } catch (ClassNotFoundException ex) {
                                    // Fallback thoát app an toàn nếu không tìm thấy cấu trúc class đăng nhập
                                    finishAffinity();
                                }
                            }
                        }
                    }
                });
    }

    // Hàm hoán đổi các Fragment vào khung chứa FrameLayout
    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    // Hàm public lấy ra quyền của User hiện tại
    public String getUserRole() {
        return userRole;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Hủy lắng nghe realtime khi Activity bị tiêu hủy hoàn toàn để tránh leak memory
        if (banCheckListener != null) {
            banCheckListener.remove();
        }
    }
}