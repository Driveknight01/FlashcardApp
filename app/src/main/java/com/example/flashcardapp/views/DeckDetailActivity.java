package com.example.flashcardapp.views;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.flashcardapp.databinding.ActivityDeckDetailBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.List;

public class DeckDetailActivity extends AppCompatActivity {

    private ActivityDeckDetailBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private String deckId;
    private String deckCollection = "personal_decks";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeckDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        deckId = getIntent().getStringExtra("DECK_ID");
        String deckTitle = getIntent().getStringExtra("DECK_TITLE");

        if (getIntent().hasExtra("DECK_TYPE")) {
            deckCollection = getIntent().getStringExtra("DECK_TYPE");
        }

        // 🔍 TẦNG KIỂM TRA 1 (LOGCAT): In ra màn hình kiểm thử xem Intent truyền sang cái gì
        Log.d("CHECK_BUG", "==== THÔNG TIN NHẬN TỪ INTENT ====");
        Log.d("CHECK_BUG", "DECK_ID nhận được: " + deckId);
        Log.d("CHECK_BUG", "DECK_TITLE nhận được: " + deckTitle);
        Log.d("CHECK_BUG", "DECK_TYPE (Collection) nhận được: " + deckCollection);

        // Mặc định ban đầu khi vừa mở màn hình: KHÓA ẨN LUÔN 2 nút để an toàn, tránh bị lag tự bật
        binding.llOwnerActions.setVisibility(View.GONE);

        binding.txtDetailTitle.setText(deckTitle != null ? deckTitle : "...");
        binding.toolbarDetail.setNavigationOnClickListener(v -> finish());

        if (deckId != null && !deckId.trim().isEmpty()) {
            loadDeckDetails();
        } else {
            Log.e("CHECK_BUG", "LỖI NGUY HIỂM: Màn hình trước KHÔNG truyền DECK_ID sang!");
            binding.txtDetailInfo.setText("Lỗi: Không tìm thấy ID học phần");
        }

        binding.btnStartLearning.setOnClickListener(v -> {
            if (deckId == null) return;
            Intent intent = new Intent(DeckDetailActivity.this, FlashcardLearnActivity.class);
            intent.putExtra("DECK_ID", deckId);
            intent.putExtra("DECK_TYPE", deckCollection);
            startActivity(intent);
            finish();
        });
    }

    private void loadDeckDetails() {
        mFirestore.collection(deckCollection).document(deckId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (binding == null) return;

                    if (documentSnapshot.exists()) {
                        Log.d("CHECK_BUG", "-> Đã tìm thấy Document trên Firestore thành công!");

                        List<?> cards = (List<?>) documentSnapshot.get("cards");
                        int size = cards != null ? cards.size() : 0;

                        String dbAuthor = documentSnapshot.getString("author");
                        String creatorId = documentSnapshot.getString("creatorId");
                        if (creatorId == null) {
                            creatorId = documentSnapshot.getString("userId");
                        }

                        Log.d("CHECK_BUG", "creatorId trong DB: " + creatorId);
                        Log.d("CHECK_BUG", "author trong DB: " + dbAuthor);

                        String ADMIN_UID = "L4O3R0p5G7P6zZfS1gD4z7T1m683";
                        String currentUid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "";

                        // 🔍 TẦNG KIỂM TRA 2: So sánh trực tiếp cả Tiêu đề chữ nếu ID bị clone đè
                        String currentDisplayTitle = binding.txtDetailTitle.getText().toString().trim();
                        boolean isSystemTitle = "Côn Trùng".equals(currentDisplayTitle) || "Côn trùng".equals(currentDisplayTitle);
                        boolean isAdminDeckId = "8fa804be-18b3-4f93-b6be-d8869c9b5a2b".equals(deckId);

                        // 🔒 TẦNG BẢO MẬT TỐI CAO (QUYẾT ĐỊNH):
                        // Chỉ cần dính 1 trong các điều kiện hệ thống này là KHÓA TỰ ĐỘNG
                        if (isAdminDeckId || isSystemTitle || "Hệ thống".equals(dbAuthor) || ADMIN_UID.equals(creatorId) || "global_decks".equals(deckCollection)) {
                            Log.d("CHECK_BUG", "==> KẾT LUẬN: Đây là bài ADMIN. Ép ẩn nút Sửa/Xóa!");
                            binding.txtDetailInfo.setText(size + " thuật ngữ  •  Tác giả: Hệ thống");
                            binding.llOwnerActions.setVisibility(View.GONE); // Khóa xích vĩnh viễn
                        }
                        else if (currentUid.equals(creatorId)) {
                            Log.d("CHECK_BUG", "==> KẾT LUẬN: Đây là bài của USER tự tạo. Mở nút!");
                            binding.txtDetailInfo.setText(size + " thuật ngữ  •  Tác giả: bạn");
                            binding.llOwnerActions.setVisibility(View.VISIBLE);
                            setupOwnerClickListeners();
                        } else {
                            binding.txtDetailInfo.setText(size + " thuật ngữ  •  Tác giả: Người dùng khác");
                            binding.llOwnerActions.setVisibility(View.GONE);
                        }
                    } else {
                        // 🔍 TẦNG KIỂM TRA 3: Nếu Firebase báo Document không tồn tại (Do truyền sai ID)
                        Log.e("CHECK_BUG", "-> THẤT BẠI: Document không tồn tại trong bảng " + deckCollection + " với ID: " + deckId);
                        binding.txtDetailInfo.setText("Học phần không tồn tại hoặc đã bị xóa");
                        binding.llOwnerActions.setVisibility(View.GONE); // Cấm hiện nút
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("CHECK_BUG", "Lỗi kết nối Firestore: " + e.getMessage());
                    binding.llOwnerActions.setVisibility(View.GONE);
                });
    }

    private void setupOwnerClickListeners() {
        binding.btnEditDeck.setOnClickListener(v -> {
            Intent intent = new Intent(DeckDetailActivity.this, CreateDeckActivity.class);
            intent.putExtra("DECK_ID", deckId);
            intent.putExtra("USER_ROLE", "user");
            intent.putExtra("IS_EDIT_MODE", true);
            startActivity(intent);
        });

        binding.btnDeleteDeck.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Xóa học phần?")
                    .setMessage("Bạn có chắc chắn muốn xóa vĩnh viễn bộ học phần này không? Thao tác này không thể hoàn tác.")
                    .setPositiveButton("Xóa", (dialog, which) -> {
                        mFirestore.collection("personal_decks").document(deckId)
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(DeckDetailActivity.this, "Đã xóa bộ học phần thành công!", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(DeckDetailActivity.this, "Lỗi khi xóa: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });
    }
}