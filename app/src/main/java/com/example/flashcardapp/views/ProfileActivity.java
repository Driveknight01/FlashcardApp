package com.example.flashcardapp.views;

import android.content.Intent;
import android.os.Bundle;
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

    // User role flag
    private String userRole = "user";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        // Retrieve role passed from caller
        if (getIntent() != null && getIntent().hasExtra("USER_ROLE")) {
            userRole = getIntent().getStringExtra("USER_ROLE");
        }

        binding.toolbarProfile.setNavigationOnClickListener(v -> handleBackNavigation());

        // Load current user profile details
        loadUserProfile();

        // Sign out with user confirmation dialog
        binding.btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Đăng xuất tài khoản?")
                    .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản này không?")
                    .setPositiveButton("Đăng xuất", (dialog, which) -> {
                        mAuth.signOut();

                        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        handleBackNavigation();
    }

    private void handleBackNavigation() {
        finish();
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();

            // Highlight admin profile name color
            if ("admin".equals(userRole)) {
                binding.txtProfileName.setTextColor(android.graphics.Color.parseColor("#E91E63"));
            }

            // Ensure initial avatar text is visible
            binding.txtProfileAvatarLetter.setVisibility(android.view.View.VISIBLE);

            mFirestore.collection("users").document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (binding == null) return;
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            if (name != null && !name.trim().isEmpty()) {
                                if ("admin".equals(userRole)) {
                                    binding.txtProfileName.setText(name + " (Quản trị viên)");
                                } else {
                                    binding.txtProfileName.setText(name);
                                }

                                // Extract first initial for avatar letter
                                String firstLetter = name.trim().substring(0, 1).toUpperCase();
                                binding.txtProfileAvatarLetter.setText(firstLetter);
                            }
                        } else {
                            String displayName = currentUser.getDisplayName();
                            binding.txtProfileName.setText(displayName != null ? displayName : ("admin".equals(userRole) ? "Quản trị viên" : "Học viên"));

                            // Fallback initial if Firestore profile is absent
                            String fallbackName = binding.txtProfileName.getText().toString();
                            binding.txtProfileAvatarLetter.setText(fallbackName.substring(0, 1).toUpperCase());
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (currentUser.getDisplayName() != null) {
                            binding.txtProfileName.setText(currentUser.getDisplayName());
                            String firstLetter = currentUser.getDisplayName().trim().substring(0, 1).toUpperCase();
                            binding.txtProfileAvatarLetter.setText(firstLetter);
                        }
                    });
        }
    }
}
