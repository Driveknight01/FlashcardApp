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

    // Realtime snapshot listeners
    private ListenerRegistration personalDeckRealtimeListener;
    private ListenerRegistration adminDeckRealtimeListener;

    // Prevents snapshot updates from resetting the active study queue during gesture interaction
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

        // Strip UID prefix if ID was provided in user-prefixed format
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

        // Listen for user personal deck state; fall back to source system deck on first study session
        personalDeckRealtimeListener = mFirestore.collection("personal_decks").document(localizedId)
                .addSnapshotListener((personalDoc, error) -> {
                    if (error != null || binding == null) return;

                    if (personalDoc != null && personalDoc.exists()) {
                        targetDocumentId = localizedId;
                        increaseStudyCountInFirebase(localizedId);

                        if (!isUserSwiping) {
                            processCardsData(personalDoc.get("cards"), currentTime);
                            startAdminRealtimeListening(personalDoc, currentTime);
                        }
                    } else {
                        // Fallback to origin template deck when personal copy is not yet created
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

    // Syncs admin template modifications into the user copy, resetting modified cards for re-learning
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

                            // Reset card learning state if admin definition changed
                            if (!adminDef.equals(oldDef)) {
                                newCard.put("isLearned", false);
                                newCard.put("learned", false);
                                newCard.put("status", "learning");
                                newCard.put("nextReviewTime", 0L);
                                hasAnyDefinitionChanged[0] = true;
                            } else {
                                // Retain existing progress when content matches
                                newCard.put("isLearned", oldCard.get("isLearned"));
                                if (oldCard.containsKey("learned")) newCard.put("learned", oldCard.get("learned"));
                                newCard.put("status", oldCard.get("status"));
                                newCard.put("nextReviewTime", oldCard.get("nextReviewTime"));
                            }
                        } else {
                            // New card added by admin
                            newCard.put("isLearned", false);
                            newCard.put("learned", false);
                            newCard.put("status", "learning");
                            newCard.put("nextReviewTime", 0L);
                            hasAnyDefinitionChanged[0] = true;
                        }
                        syncedCardsList.add(newCard);
                    }

                    // Push synchronized cards back to personal copy if modifications detected
                    if (currentUserCards.size() != syncedCardsList.size() || hasAnyDefinitionChanged[0]) {
                        mFirestore.collection("personal_decks").document(userDoc.getId())
                                .update("cards", syncedCardsList)
                                .addOnSuccessListener(aVoid -> {
                                    if (hasAnyDefinitionChanged[0]) {
                                        Toast.makeText(FlashcardLearnActivity.this, " Admin vừa sửa một số thẻ, các thẻ liên quan đã được đưa về luồng học lại!", Toast.LENGTH_LONG).show();
                                    }
                                });
                    }
                });
    }

    // Initializes a personal copy of a global system deck for the current user
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

    // Increments cumulative study count for deck
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

    // Filters cards due for review based on nextReviewTime timestamp
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
                Toast.makeText(FlashcardLearnActivity.this, "Tất cả các thẻ đã được học thuộc!", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            resetLearningSession(validCardsForToday);
        } else {
            Toast.makeText(FlashcardLearnActivity.this, "Bộ thẻ này chưa có thẻ nào!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // Initializes or resets study round with eligible cards
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

        // Lock queue updates during active swipe
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
                    // Release swipe lock after persisting progress
                    isUserSwiping = false;
                })
                .addOnFailureListener(e -> isUserSwiping = false);
    }

    // Displays round completion screen and summary
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

    // Configures flip animation and swipe gestures for the flashcard view
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
        // Clean up listeners to prevent memory leaks
        if (personalDeckRealtimeListener != null) {
            personalDeckRealtimeListener.remove();
        }
        if (adminDeckRealtimeListener != null) {
            adminDeckRealtimeListener.remove();
        }
    }
}
