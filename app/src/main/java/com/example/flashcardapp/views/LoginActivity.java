package com.example.flashcardapp.views;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.flashcardapp.MainActivity;
import com.example.flashcardapp.R;
import com.example.flashcardapp.databinding.ActivityLoginBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        // 🌟 ĐÃ FIX: Điền trực tiếp Web Client ID chính chủ của Nam để triệt tiêu lỗi "Cannot resolve symbol"
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("846504189626-clcknqdqiodhrrn7277c7e88dn6on3rl.apps.googleusercontent.com")
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Đăng ký cổng tiếp nhận tài khoản Google trả về
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            if (account != null) {
                                firebaseAuthWithGoogle(account.getIdToken());
                            }
                        } catch (ApiException e) {
                            Toast.makeText(this, "Lỗi kết nối Google: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // Đăng nhập bằng Email & Mật khẩu thông thường
        binding.btnLogin.setOnClickListener(v -> performLogin());

        // Bấm nút Đăng nhập bằng Google
        binding.btnGoogleSignIn.setOnClickListener(v -> {
            mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
        });

        binding.txtNavigateToRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });
    }

    private void performLogin() {
        String email = binding.edtLoginEmail.getText().toString().trim();
        String password = binding.edtLoginPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Vui lòng nhập Email", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String uid = mAuth.getCurrentUser().getUid();
                        checkRoleAndNavigate(uid);
                    } else {
                        Toast.makeText(LoginActivity.this, "Đăng nhập thất bại: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkRoleAndNavigate(String uid) {
        mFirestore.collection("users").document(uid)
                .get()
                .addOnCompleteListener(dbTask -> {
                    if (dbTask.isSuccessful() && dbTask.getResult() != null) {
                        DocumentSnapshot document = dbTask.getResult();
                        if (document.exists()) {
                            String role = document.getString("role");
                            String name = document.getString("name");
                            String status = document.getString("status");

                            // AN TOÀN TUYỆT ĐỐI: Chặn đăng nhập nếu tài khoản bị khóa từ trước
                            if ("banned".equals(status)) {
                                mAuth.signOut();
                                Toast.makeText(LoginActivity.this, "🔒 Tài khoản này đã bị khóa do vi phạm!", Toast.LENGTH_LONG).show();
                                return;
                            }

                            Toast.makeText(LoginActivity.this, "Chào mừng " + (name != null ? name : "") + "!", Toast.LENGTH_SHORT).show();
                            navigateBasedOnRole(role);
                        } else {
                            Toast.makeText(LoginActivity.this, "Không tìm thấy thông tin tài khoản!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, "Lỗi kết nối dữ liệu!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        String uid = user.getUid();
                        String email = user.getEmail();
                        String name = user.getDisplayName();

                        mFirestore.collection("users").document(uid).get()
                                .addOnSuccessListener(documentSnapshot -> {
                                    if (documentSnapshot.exists()) {
                                        String role = documentSnapshot.getString("role");
                                        String status = documentSnapshot.getString("status");

                                        if ("banned".equals(status)) {
                                            mAuth.signOut();
                                            Toast.makeText(LoginActivity.this, "🔒 Tài khoản của bạn đã bị khóa!", Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        navigateBasedOnRole(role);
                                    } else {
                                        // Đồng bộ tạo mới tài khoản nếu lần đầu dùng Google đăng nhập
                                        Map<String, Object> userData = new HashMap<>();
                                        userData.put("uid", uid);
                                        userData.put("email", email);
                                        userData.put("name", name != null ? name : "Học viên Google");
                                        userData.put("role", "user");
                                        userData.put("status", "active");

                                        mFirestore.collection("users").document(uid).set(userData)
                                                .addOnSuccessListener(aVoid -> navigateBasedOnRole("user"))
                                                .addOnFailureListener(e -> Toast.makeText(LoginActivity.this, "Lỗi tạo tài khoản: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Xác thực Firebase thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void navigateBasedOnRole(String role) {
        Intent intent;
        if ("admin".equals(role)) {
            intent = new Intent(LoginActivity.this, AdminMainActivity.class);
        } else {
            intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra("USER_ROLE", role);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}