package com.example.flashcardapp;
import com.example.flashcardapp.views.CreateDeckActivity;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.flashcardapp.databinding.ActivityMainBinding;
import com.example.flashcardapp.views.fragments.HomeFragment;
import com.example.flashcardapp.views.fragments.LibraryFragment;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private String userRole = "user"; // Mặc định là user nếu không nhận được dữ liệu

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sử dụng View Binding sạch sẽ
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
                // Tạm thời để Toast hoặc Intent trống, chúng ta sẽ xây dựng màn hình này ngay sau đây
                Intent intent = new Intent(MainActivity.this, CreateDeckActivity.class);
                intent.putExtra("USER_ROLE", userRole); // Truyền quyền sang để biết là Admin hay User tạo thẻ
                startActivity(intent);
                return false; // Trả về false để không làm thay đổi vị trí Tab đang chọn hiện tại
            }
            return false;
        });
    }

    // Hàm tiện ích dùng để hoán đổi các Fragment vào khung chứa FrameLayout
    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    // Hàm public để các Fragment con sau này có thể lấy ra quyền của User hiện tại
    public String getUserRole() {
        return userRole;
    }
}