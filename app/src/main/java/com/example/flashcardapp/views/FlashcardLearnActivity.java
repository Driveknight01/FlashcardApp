package com.example.flashcardapp.views;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.flashcardapp.databinding.ActivityFlashcardLearnBinding;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FlashcardLearnActivity extends AppCompatActivity {

    private ActivityFlashcardLearnBinding binding;
    private FirebaseFirestore mFirestore;
    private String currentDeckId;

    // originalCards: Giữ mảng gốc nạp từ Firebase để cuối hiệp ghi đè lưu tổng lực
    private final List<Map<String, Object>> originalCards = new ArrayList<>();
    private final List<Map<String, Object>> studyQueue = new ArrayList<>();
    private final List<Map<String, Object>> nonBelongedCardsToday = new ArrayList<>();

    private int countLearned = 0;
    private int countNotLearned = 0;
    private int totalRoundSize = 0;
    private int currentIndexPosition = 1;
    private boolean isShowingFrontFace = true;

    private float dX, initialX;
    private final float SWIPE_THRESHOLD = 350f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFlashcardLearnBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        currentDeckId = getIntent().getStringExtra("DECK_ID");

        binding.btnLearnSettings.setVisibility(View.GONE); // Ẩn hoàn toàn nút cài đặt rườm rà
        binding.btnLearnClose.setOnClickListener(v -> finish());
        binding.btnBackToLibrary.setOnClickListener(v -> finish());

        setupCardGestures();

        if (currentDeckId != null) {
            loadFirebaseData(currentDeckId);
        }
    }

    // 🚀 LUỒNG TỰ ĐỘNG NHẬN DIỆN VÀ XOAY TRỤC DATA THÔNG MINH
    private void loadFirebaseData(String deckId) {
        long currentTime = System.currentTimeMillis();

        // Bước 1: Thử tìm kiếm bộ thẻ trong kho Cá nhân trước
        mFirestore.collection("personal_decks").document(deckId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (binding == null) return;

                    if (documentSnapshot.exists()) {
                        // NẾU TÌM THẤY TRONG KHO CÁ NHÂN -> Tiến hành học luôn trên bản tiến trình cá nhân
                        processCardsData(documentSnapshot.get("cards"), currentTime, "personal_decks");
                    } else {
                        // NẾU KHÔNG THẤY -> Kích hoạt cơ chế tự động nhân bản bộ thẻ hệ thống của Admin
                        loadAdminGlobalDataFallback(deckId, currentTime);
                    }
                })
                .addOnFailureListener(e -> loadAdminGlobalDataFallback(deckId, currentTime));
    }

    // 🌟 THUẬT TOÁN NHÂN BẢN (CLONE FLOW): Sao chép bài Admin về kho cá nhân để kích hoạt Spaced Repetition và mục Tiếp tục học
    private void loadAdminGlobalDataFallback(String deckId, long currentTime) {
        mFirestore.collection("global_decks").document(deckId).get()
                .addOnSuccessListener(adminSnapshot -> {
                    if (binding == null) return;

                    if (adminSnapshot.exists()) {
                        String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
                        Map<String, Object> personalCopy = adminSnapshot.getData();

                        if (personalCopy != null) {
                            // Bổ sung các nhãn cá nhân hóa để hợp thức hóa thuộc về học viên này
                            personalCopy.put("userId", currentUid);
                            personalCopy.put("creatorId", currentUid);
                            personalCopy.put("lastModified", System.currentTimeMillis()); // Đánh dấu mốc thời gian thực hiện học

                            // Tiến hành lưu bản sao này vào collection cá nhân
                            mFirestore.collection("personal_decks").document(deckId).set(personalCopy)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d("FirebaseClone", "Đã nhân bản học phần hệ thống về kho cá nhân thành công!");
                                        // Chuyển hướng sang nạp dữ liệu từ bản sao cá nhân để áp dụng thuật toán đóng băng ngắt quãng
                                        processCardsData(personalCopy.get("cards"), currentTime, "personal_decks");
                                    });
                        }
                    } else {
                        Toast.makeText(FlashcardLearnActivity.this, "Không tìm thấy dữ liệu học phần này!", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(FlashcardLearnActivity.this, "Lỗi tải dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    // Hàm bóc tách xử lý logic nạp thẻ và lọc thuật toán Spaced Repetition hồi chiêu
    private void processCardsData(Object cardsObject, long currentTime, String currentDeckType) {
        if (cardsObject instanceof List) {
            List<Map<String, Object>> cards = (List<Map<String, Object>>) cardsObject;

            originalCards.clear();
            originalCards.addAll(cards); // Bảo lưu mảng gốc phục vụ cập nhật Firebase

            List<Map<String, Object>> validCardsForToday = new ArrayList<>();
            for (Map<String, Object> c : cards) {
                long nextReviewTime = 0;
                if (c.containsKey("nextReviewTime") && c.get("nextReviewTime") != null) {
                    nextReviewTime = ((Number) c.get("nextReviewTime")).longValue();
                }

                // Thuật toán ngắt quãng thời gian thực (Lúc này đã chạy độc lập trên bản sao personal_decks nên hoạt động hoàn mỹ)
                if (nextReviewTime <= currentTime) {
                    validCardsForToday.add(c);
                }
            }

            if (validCardsForToday.isEmpty()) {
                Toast.makeText(FlashcardLearnActivity.this, "Tất cả các thẻ đã thuộc đang được đóng băng (chờ hồi chiêu 3 phút)!", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            resetLearningSession(validCardsForToday);
        } else {
            Toast.makeText(FlashcardLearnActivity.this, "Học phần này chưa có thẻ nào!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void resetLearningSession(List<Map<String, Object>> targetList) {
        studyQueue.clear();
        studyQueue.addAll(targetList);

        nonBelongedCardsToday.clear();
        countLearned = 0;
        countNotLearned = 0;
        currentIndexPosition = 1; // Reset chỉ số tiến trình về 1 tuyệt đối cho hiệp mới

        totalRoundSize = studyQueue.size();
        binding.txtCountLearned.setText("0");
        binding.txtCountNotLearned.setText("0");

        binding.llStudyWorkspace.setVisibility(View.VISIBLE);
        binding.rlResultScreen.setVisibility(View.GONE);
        binding.rlCountBar.setVisibility(View.VISIBLE);

        updateCardUI();
    }

    private void updateCardUI() {
        // FIXED: Khi tiến trình vượt quá tổng số thẻ của hiệp -> Kết thúc luôn, không cho chạy cố
        if (currentIndexPosition > totalRoundSize || studyQueue.isEmpty()) {
            displayResultScreen();
            return;
        }

        binding.progressLearnHorizontal.setMax(totalRoundSize);
        binding.progressLearnHorizontal.setProgress(currentIndexPosition);
        binding.txtLearnIndicator.setText(currentIndexPosition + " / " + totalRoundSize);

        Map<String, Object> currentCard = studyQueue.get(0);
        isShowingFrontFace = true;
        binding.txtLearnCardContent.setText(String.valueOf(currentCard.get("term")));
        binding.txtLearnCardContent.setTextColor(android.graphics.Color.parseColor("#212121"));
    }

    private void executeSwipeDecision(boolean isSwipedRight) {
        if (studyQueue.isEmpty()) return;

        // Bốc thẻ đầu hàng đợi ra để xử lý phân loại
        Map<String, Object> currentCard = studyQueue.remove(0);

        if (isSwipedRight) {
            countLearned++;
            binding.txtCountLearned.setText(String.valueOf(countLearned));

            // ĐÃ THUỘC: Đóng băng ngắt quãng đúng 3 phút để phục vụ bạn test nhanh
            long threeMinutesMillis = 3L * 60 * 1000;
            currentCard.put("nextReviewTime", System.currentTimeMillis() + threeMinutesMillis);
            currentCard.put("status", "mastered");

            updateCardInLocalList(currentCard);
        } else {
            countNotLearned++;
            binding.txtCountNotLearned.setText(String.valueOf(countNotLearned));

            // CHƯA THUỘC: Đặt hạn bằng 0
            currentCard.put("nextReviewTime", 0);
            currentCard.put("status", "learning");

            updateCardInLocalList(currentCard);

            // FIX TRIỆT ĐỂ: KHÔNG add ngược lại vào studyQueue nữa để tránh lỗi phình mảng vuốt nhiều lần (11/7).
            // Thẻ lỗi này sẽ được gom vào mảng nonBelongedCardsToday và đợi đến hiệp sau mới lôi ra học lại.
            nonBelongedCardsToday.add(currentCard);
        }

        currentIndexPosition++; // Tăng chỉ số tiến trình sau khi hoàn thành 1 cái vuốt hợp lệ

        binding.cardSwipeContainer.animate().translationX(isSwipedRight ? 1000f : -1000f).alpha(0f).setDuration(200)
                .withEndAction(() -> {
                    binding.cardSwipeContainer.setX(0f);
                    binding.cardSwipeContainer.setRotation(0f);
                    binding.cardSwipeContainer.setAlpha(1f);
                    updateCardUI(); // Cập nhật và kiểm tra chuyển màn kết quả nếu hết từ
                }).start();
    }

    // Ghi đè trạng thái thẻ mới vào mảng nhớ cục bộ (Local Memory)
    private void updateCardInLocalList(Map<String, Object> updatedCard) {
        for (int i = 0; i < originalCards.size(); i++) {
            if (String.valueOf(originalCards.get(i).get("term")).equals(updatedCard.get("term"))) {
                originalCards.set(i, updatedCard);
                break;
            }
        }
    }

    // 🔥 ĐỒNG BỘ KHÔI PHỤC CHUẨN: Đẩy thẳng mốc lưu ngắt quãng ngập tràn lên đám mây personal_decks
    private void saveAllProgressToFirebase() {
        if (currentDeckId == null || originalCards.isEmpty()) return;

        mFirestore.collection("personal_decks").document(currentDeckId)
                .update(
                        "cards", originalCards,
                        "lastModified", System.currentTimeMillis() // Kích hoạt ghi vết thời gian để lọt vào mục Tiếp tục học thời gian thực
                )
                .addOnSuccessListener(aVoid -> Log.d("FirebaseSave", "Đã lưu tiến trình ngắt quãng tổng lực!"));
    }

    private void displayResultScreen() {
        // Đồng bộ toàn bộ dữ liệu ngắt quãng lên Firebase
        saveAllProgressToFirebase();

        binding.llStudyWorkspace.setVisibility(View.GONE);
        binding.rlResultScreen.setVisibility(View.VISIBLE);

        binding.txtResultSummary.setText("Lượt học hoàn tất!\nĐã thuộc: " + countLearned + "  |  Chưa thuộc: " + nonBelongedCardsToday.size());

        if (nonBelongedCardsToday.isEmpty()) {
            binding.btnContinueRemaining.setVisibility(View.GONE);
        } else {
            binding.btnContinueRemaining.setVisibility(View.VISIBLE);
            binding.btnContinueRemaining.setText("Tiếp tục ôn " + nonBelongedCardsToday.size() + " thẻ chưa thuộc");
            binding.btnContinueRemaining.setOnClickListener(v -> {
                // Nạp hiệp mới chứa ĐÚNG số từ chưa thuộc, thanh chỉ số trả về 1/X chuẩn chỉ
                List<Map<String, Object>> failedCopy = new ArrayList<>(nonBelongedCardsToday);
                resetLearningSession(failedCopy);
            });
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupCardGestures() {
        binding.cardSwipeContainer.setOnClickListener(v -> {
            if (studyQueue.isEmpty()) return;
            binding.cardSwipeContainer.animate().rotationY(isShowingFrontFace ? 90f : -90f).setDuration(120)
                    .withEndAction(() -> {
                        isShowingFrontFace = !isShowingFrontFace;
                        Map<String, Object> currentCard = studyQueue.get(0);
                        binding.txtLearnCardContent.setText(String.valueOf(isShowingFrontFace ? currentCard.get("term") : currentCard.get("definition")));
                        binding.txtLearnCardContent.setTextColor(isShowingFrontFace ? android.graphics.Color.parseColor("#212121") : android.graphics.Color.parseColor("#3F51B5"));
                        binding.cardSwipeContainer.setRotationY(isShowingFrontFace ? -90f : 90f);
                        binding.cardSwipeContainer.animate().rotationY(0f).setDuration(120).start();
                    }).start();
        });

        binding.cardSwipeContainer.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = event.getRawX();
                    dX = v.getX() - event.getRawX();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float targetX = event.getRawX() + dX;
                    v.setX(targetX);
                    v.setRotation((targetX / 20) * 1f);
                    break;
                case MotionEvent.ACTION_UP:
                    float deltaX = event.getRawX() - initialX;
                    if (Math.abs(deltaX) > SWIPE_THRESHOLD) {
                        executeSwipeDecision(deltaX > 0);
                    } else {
                        v.animate().x(0f).rotation(0f).setDuration(200).start();
                        if (Math.abs(deltaX) < 10) v.performClick();
                    }
                    break;
            }
            return true;
        });
    }
}