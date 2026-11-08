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
    private String userRole = "user"; // Default role fallback

    private FirebaseFirestore mFirestore;
    private ListenerRegistration banCheckListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();

        if (getIntent() != null && getIntent().hasExtra("USER_ROLE")) {
            userRole = getIntent().getStringExtra("USER_ROLE");
        }

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                loadFragment(new HomeFragment());
                return true;
            } else if (itemId == R.id.nav_library) {
                loadFragment(new LibraryFragment());
                return true;
            } else if (itemId == R.id.nav_create) {
                Intent intent = new Intent(MainActivity.this, CreateDeckActivity.class);
                intent.putExtra("USER_ROLE", userRole);
                startActivity(intent);
                return false; // Prevent switching bottom tab selection
            }
            return false;
        });

        startCheckingUserStatusRealtime();
    }

    private void startCheckingUserStatusRealtime() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String currentUid = currentUser.getUid();

        // Listen for account status changes in real time
        banCheckListener = mFirestore.collection("users").document(currentUid)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null || binding == null) return;

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");

                        if ("banned".equals(status)) {
                            // Detach listener immediately to prevent callback leaks
                            if (banCheckListener != null) {
                                banCheckListener.remove();
                                banCheckListener = null;
                            }

                            FirebaseAuth.getInstance().signOut();

                            Toast.makeText(MainActivity.this,
                                    "Tài khoản của bạn đã bị khóa do vi phạm tiêu chuẩn cộng đồng!",
                                    Toast.LENGTH_LONG).show();

                            // Redirect to LoginActivity and clear backstack
                            try {
                                Class<?> loginActivityClass = Class.forName("com.example.flashcardapp.views.LoginActivity");
                                Intent intent = new Intent(MainActivity.this, loginActivityClass);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            } catch (ClassNotFoundException e) {
                                try {
                                    Class<?> rootLoginClass = Class.forName("com.example.flashcardapp.LoginActivity");
                                    Intent intent = new Intent(MainActivity.this, rootLoginClass);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                } catch (ClassNotFoundException ex) {
                                    finishAffinity();
                                }
                            }
                        }
                    }
                });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public String getUserRole() {
        return userRole;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (banCheckListener != null) {
            banCheckListener.remove();
        }
    }
}
