package com.example.flashcardapp.views;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.flashcardapp.databinding.ActivityFlashcardLearnBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlashcardLearnActivity extends AppCompatActivity {

    private ActivityFlashcardLearnBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private String baseDeckId;
    private String targetDocumentId;

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

    private boolean isSessionCounted = false;

    // Quản lý các bộ lắng nghe Realtime tránh rò rỉ bộ nhớ
    private ListenerRegistration personalDeckRealtimeListener;
    private ListenerRegistration adminDeckRealtimeListener;

    // 🌟 CỜ CHẶN LOOP VÔ HẠN: Khóa không cho trình nghe Realtime tự reset hàng đợi khi User đang vuốt thẻ
    private boolean isUserSwiping = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFlashcardLearnBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        String rawDeckId = getIntent().getStringExtra("DECK_ID");
        baseDeckId = rawDeckId;

        // 🌟 GIẢI PHÁP LÀM SẠCH ID CHÍ MẠNG: Nếu trang chủ truyền sang ID đã bọc sẵn UID (Ví dụ: UID_baseId), tiến hành bóc tách lấy ID gốc Admin
        if (rawDeckId != null && mAuth.getCurrentUser() != null) {
            String currentUid = mAuth.getCurrentUser().getUid();
            if (rawDeckId.startsWith(currentUid + "_")) {
                baseDeckId = rawDeckId.replace(currentUid + "_", "");
            }
        }

        targetDocumentId = baseDeckId;

        binding.btnLearnSettings.setVisibility(View.GONE);
        binding.btnLearnClose.setOnClickListener(v -> finish());
        binding.btnBackToLibrary.setOnClickListener(v -> finish());

        setupCardGestures();

        if (baseDeckId != null && mAuth.getCurrentUser() != null) {
            String currentUid = mAuth.getCurrentUser().getUid();
            String localizedPersonalId = currentUid + "_" + baseDeckId;

            checkAndLoadDataFlow(localizedPersonalId);
        }
    }

    private void checkAndLoadDataFlow(String localizedId) {
        long currentTime = System.currentTimeMillis();

        // 🌟 REALTIME TOÀN DIỆN PHÍA USER: Theo dõi liên tục bảng cá nhân, gộp dữ liệu tức thì
        personalDeckRealtimeListener = mFirestore.collection("personal_decks").document(localizedId)
                .addSnapshotListener((personalDoc, error) -> {
                    if (error != null || binding == null) return;

                    if (personalDoc != null && personalDoc.exists()) {
                        targetDocumentId = localizedId;
                        increaseStudyCountInFirebase(localizedId);

                        // CHỈ cho phép gộp cập nhật nạp giao diện nếu người dùng KHÔNG trong tiến trình vuốt thẻ
                        if (!isUserSwiping) {
                            processCardsData(personalDoc.get("cards"), currentTime);
                            startAdminRealtimeListening(personalDoc, currentTime);
                        }
                    } else {
                        // Nếu chưa từng có dữ liệu tiến trình, thực hiện nạp từ bài gốc hệ thống
                        mFirestore.collection("personal_decks").document(baseDeckId).get()
                                .addOnSuccessListener(originPersonalDoc -> {
                                    if (binding == null) return;
                                    if (originPersonalDoc.exists()) {
                                        targetDocumentId = baseDeckId;
                                        increaseStudyCountInFirebase(baseDeckId);
                                        processCardsData(originPersonalDoc.get("cards"), currentTime);
                                    } else {
                                        loadAdminGlobalDataFallback(currentTime, localizedId);
                                    }
                                })
                                .addOnFailureListener(e -> loadAdminGlobalDataFallback(currentTime, localizedId));
                    }
                });
    }

    // 🌟 THUẬT TOÁN REALTIME CHÉO TỪ ADMIN: Phát hiện sửa nghĩa, ép hạ cấp về trạng thái chưa thuộc
    private void startAdminRealtimeListening(DocumentSnapshot userDoc, long currentTime) {
        if (adminDeckRealtimeListener != null) return;

        adminDeckRealtimeListener = mFirestore.collection("global_decks").document(baseDeckId)
                .addSnapshotListener((adminSnapshot, error) -> {
                    if (error != null || binding == null || adminSnapshot == null || !adminSnapshot.exists()) return;

                    List<Map<String, Object>> adminCards = (List<Map<String, Object>>) adminSnapshot.get("cards");
                    if (adminCards == null) return;

                    List<Map<String, Object>> currentUserCards = new ArrayList<>(originalCards);

                    Map<String, Map<String, Object>> userProgressMap = new HashMap<>();
                    for (Map<String, Object> uCard : currentUserCards) {
                        if (uCard.get("term") != null) {
                            String term = String.valueOf(uCard.get("term")).trim().toLowerCase();
                            userProgressMap.put(term, uCard);
                        }
                    }

                    List<Map<String, Object>> syncedCardsList = new ArrayList<>();
                    final boolean[] hasAnyDefinitionChanged = {false};

                    for (Map<String, Object> aCard : adminCards) {
                        Map<String, Object> newCard = new HashMap<>(aCard);
                        String adminTerm = String.valueOf(aCard.get("term")).trim().toLowerCase();
                        String adminDef = String.valueOf(aCard.get("definition")).trim();

                        if (userProgressMap.containsKey(adminTerm)) {
                            Map<String, Object> oldCard = userProgressMap.get(adminTerm);
                            String oldDef = String.valueOf(oldCard.get("definition")).trim();

                            // ⚡ LOGIC ĐỐI CHIẾU NGHĨA SỬA ĐỔI
                            if (!adminDef.equals(oldDef)) {
                                // Admin sửa nội dung nghĩa sai -> Hạ cấp về học lại từ đầu để cập nhật kiến thức đúng
                                newCard.put("isLearned", false);
                                newCard.put("learned", false);
                                newCard.put("status", "learning");
                                newCard.put("nextReviewTime", 0L);
                                hasAnyDefinitionChanged[0] = true;
                            } else {
                                // Nội dung chuẩn khớp -> Giữ nguyên tiến độ cũ của User
                                newCard.put("isLearned", oldCard.get("isLearned"));
                                if (oldCard.containsKey("learned")) newCard.put("learned", oldCard.get("learned"));
                                newCard.put("status", oldCard.get("status"));
                                newCard.put("nextReviewTime", oldCard.get("nextReviewTime"));
                            }
                        } else {
                            // Thẻ hoàn toàn mới tinh do Admin vừa chèn thêm vào hệ thống
                            newCard.put("isLearned", false);
                            newCard.put("learned", false);
                            newCard.put("status", "learning");
                            newCard.put("nextReviewTime", 0L);
                            hasAnyDefinitionChanged[0] = true;
                        }
                        syncedCardsList.add(newCard);
                    }

                    // Đẩy dữ liệu đồng bộ đè lên Firestore cá nhân (Lệnh này sẽ tự động kích hoạt Listener ở hàm checkAndLoad làm mới UI)
                    if (currentUserCards.size() != syncedCardsList.size() || hasAnyDefinitionChanged[0]) {
                        mFirestore.collection("personal_decks").document(userDoc.getId())
                                .update("cards", syncedCardsList)
                                .addOnSuccessListener(aVoid -> {
                                    if (hasAnyDefinitionChanged[0]) {
                                        Toast.makeText(FlashcardLearnActivity.this, "♻️ Admin vừa sửa định nghĩa sai, các thẻ liên quan đã được đưa về luồng học lại!", Toast.LENGTH_LONG).show();
                                    }
                                });
                    }
                });
    }

    private void loadAdminGlobalDataFallback(long currentTime, String localizedId) {
        mFirestore.collection("global_decks").document(baseDeckId).get()
                .addOnSuccessListener(adminSnapshot -> {
                    if (binding == null) return;

                    if (adminSnapshot.exists()) {
                        String currentUid = mAuth.getCurrentUser().getUid();
                        Map<String, Object> personalCopy = adminSnapshot.getData();

                        if (personalCopy != null) {
                            personalCopy.put("id", localizedId);
                            personalCopy.put("userId", currentUid);
                            personalCopy.put("creatorId", adminSnapshot.getString("creatorId") != null ? adminSnapshot.getString("creatorId") : adminSnapshot.getString("userId"));
                            personalCopy.put("lastModified", System.currentTimeMillis());
                            personalCopy.put("author", "Hệ thống");

                            targetDocumentId = localizedId;
                            mFirestore.collection("personal_decks").document(localizedId).set(personalCopy)
                                    .addOnSuccessListener(aVoid -> Log.d("FirebaseClone", "Khởi tạo tiến trình độc lập lần đầu thành công!"))
                                    .addOnFailureListener(e -> Log.e("FirebaseClone", "Lỗi nhân bản: " + e.getMessage()));
                        }
                    } else {
                        Toast.makeText(FlashcardLearnActivity.this, "Không tìm thấy dữ liệu học phần hệ thống gốc!", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(FlashcardLearnActivity.this, "Lỗi kết nối: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void increaseStudyCountInFirebase(String docId) {
        if (isSessionCounted || docId == null) return;
        isSessionCounted = true;

        mFirestore.collection("personal_decks").document(docId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    int currentCount = 0;
                    if (documentSnapshot.exists()) {
                        if (documentSnapshot.contains("studyCount") && documentSnapshot.get("studyCount") != null) {
                            currentCount = documentSnapshot.getLong("studyCount").intValue();
                        }
                    }
                    int newCount = currentCount + 1;

                    Map<String, Object> updateMap = new HashMap<>();
                    updateMap.put("studyCount", newCount);

                    mFirestore.collection("personal_decks").document(docId)
                            .set(updateMap, com.google.firebase.firestore.SetOptions.merge());
                });
    }

    private void processCardsData(Object cardsObject, long currentTime) {
        if (cardsObject instanceof List) {
            List<Map<String, Object>> cards = (List<Map<String, Object>>) cardsObject;

            originalCards.clear();
            originalCards.addAll(cards);

            List<Map<String, Object>> validCardsForToday = new ArrayList<>();
            for (Map<String, Object> c : cards) {
                long nextReviewTime = 0;
                if (c.containsKey("nextReviewTime") && c.get("nextReviewTime") != null) {
                    nextReviewTime = ((Number) c.get("nextReviewTime")).longValue();
                }

                if (nextReviewTime <= currentTime) {
                    validCardsForToday.add(c);
                }
            }

            if (validCardsForToday.isEmpty()) {
                Toast.makeText(FlashcardLearnActivity.this, "Tất cả các thẻ đã thuộc đang được đóng băng chờ hồi chiêu!", Toast.LENGTH_LONG).show();
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
        currentIndexPosition = 1;

        totalRoundSize = studyQueue.size();
        binding.txtCountLearned.setText("0");
        binding.txtCountNotLearned.setText("0");

        binding.llStudyWorkspace.setVisibility(View.VISIBLE);
        binding.rlResultScreen.setVisibility(View.GONE);
        binding.rlCountBar.setVisibility(View.VISIBLE);

        updateCardUI();
    }

    private void updateCardUI() {
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

        // BẬT CỜ TƯƠNG TÁC: Khóa không cho SnapshotListener nạp lại mảng thẻ lúc đang vuốt dở hiệp học
        isUserSwiping = true;

        Map<String, Object> currentCard = studyQueue.remove(0);

        if (isSwipedRight) {
            countLearned++;
            binding.txtCountLearned.setText(String.valueOf(countLearned));

            long threeMinutesMillis = 3L * 60 * 1000;
            currentCard.put("nextReviewTime", System.currentTimeMillis() + threeMinutesMillis);
            currentCard.put("status", "mastered");

            updateCardInLocalList(currentCard);
        } else {
            countNotLearned++;
            binding.txtCountNotLearned.setText(String.valueOf(countNotLearned));

            currentCard.put("nextReviewTime", 0);
            currentCard.put("status", "learning");

            updateCardInLocalList(currentCard);
            nonBelongedCardsToday.add(currentCard);
        }

        currentIndexPosition++;

        binding.cardSwipeContainer.animate().translationX(isSwipedRight ? 1000f : -1000f).alpha(0f).setDuration(200)
                .withEndAction(() -> {
                    binding.cardSwipeContainer.setX(0f);
                    binding.cardSwipeContainer.setRotation(0f);
                    binding.cardSwipeContainer.setAlpha(1f);
                    updateCardUI();
                }).start();
    }

    private void updateCardInLocalList(Map<String, Object> updatedCard) {
        for (int i = 0; i < originalCards.size(); i++) {
            if (String.valueOf(originalCards.get(i).get("term")).equals(updatedCard.get("term"))) {
                originalCards.set(i, updatedCard);
                break;
            }
        }
    }

    private void saveAllProgressToFirebase() {
        if (targetDocumentId == null || originalCards.isEmpty()) return;

        mFirestore.collection("personal_decks").document(targetDocumentId)
                .update(
                        "cards", originalCards,
                        "lastModified", System.currentTimeMillis()
                )
                .addOnSuccessListener(aVoid -> {
                    Log.d("FirebaseSave", "Đã lưu tiến trình học tập.");
                    // Đã lưu ghi đè thành công lên DB -> Hạ cờ tương tác để mở khóa luồng hứng Realtime
                    isUserSwiping = false;
                })
                .addOnFailureListener(e -> isUserSwiping = false);
    }

    private void displayResultScreen() {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Gỡ bỏ toàn bộ các trình lắng nghe Realtime khi thoát màn hình để triệt tiêu lỗi rò rỉ RAM bộ nhớ
        if (personalDeckRealtimeListener != null) {
            personalDeckRealtimeListener.remove();
        }
        if (adminDeckRealtimeListener != null) {
            adminDeckRealtimeListener.remove();
        }
    }
}