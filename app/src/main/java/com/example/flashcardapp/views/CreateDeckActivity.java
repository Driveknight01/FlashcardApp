package com.example.flashcardapp.views;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.flashcardapp.R;
import com.example.flashcardapp.adapters.CreateCardAdapter;
import com.example.flashcardapp.databinding.ActivityCreateDeckBinding;
import com.example.flashcardapp.models.Flashcard;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CreateDeckActivity extends AppCompatActivity {

    private ActivityCreateDeckBinding binding;
    private CreateCardAdapter adapter;
    private List<Flashcard> cardList;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private String userRole = "user";
    private boolean isEditMode = false;
    private String editDeckId = "";

    // Activity result launcher for OCR scanning results
    private final ActivityResultLauncher<Intent> scanActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String jsonCards = result.getData().getStringExtra("SCANNED_CARDS_JSON");
                    if (jsonCards != null && !jsonCards.isEmpty()) {
                        Type type = new TypeToken<ArrayList<Flashcard>>(){}.getType();
                        ArrayList<Flashcard> newlyScannedCards = new Gson().fromJson(jsonCards, type);

                        // Merge scanned cards into empty rows or append to card list
                        if (newlyScannedCards != null && !newlyScannedCards.isEmpty()) {
                            for (Flashcard newCard : newlyScannedCards) {
                                boolean merged = false;
                                for (Flashcard existingCard : cardList) {
                                    if (existingCard.getTerm().isEmpty() && existingCard.getDefinition().isEmpty()) {
                                        existingCard.setTerm(newCard.getTerm());
                                        existingCard.setDefinition(newCard.getDefinition());
                                        merged = true;
                                        break;
                                    }
                                }
                                if (!merged) cardList.add(newCard);
                            }
                            adapter.notifyDataSetChanged();
                            Toast.makeText(this, "Đã thêm " + newlyScannedCards.size() + " thẻ quét OCR!", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateDeckBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        cardList = new ArrayList<>();

        binding.rvCreateCards.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CreateCardAdapter(cardList, null);
        binding.rvCreateCards.setAdapter(adapter);

        if (getIntent() != null) {
            if (getIntent().hasExtra("USER_ROLE")) {
                userRole = getIntent().getStringExtra("USER_ROLE");
            }
            isEditMode = getIntent().getBooleanExtra("IS_EDIT_MODE", false);

            if (isEditMode) {
                editDeckId = getIntent().getStringExtra("DECK_ID");
                String editTitle = getIntent().getStringExtra("DECK_TITLE");
                String cardsJson = getIntent().getStringExtra("DECK_CARDS_JSON");

                if ("admin".equals(userRole)) {
                    binding.toolbarCreate.setTitle("Sửa bộ thẻ hệ thống");
                } else {
                    binding.toolbarCreate.setTitle("Sửa bộ thẻ của bạn");
                }

                // Case 1: Cards JSON payload passed via Intent
                if (editTitle != null) binding.edtDeckTitle.setText(editTitle);
                if (cardsJson != null && !cardsJson.isEmpty()) {
                    Type type = new TypeToken<ArrayList<Flashcard>>(){}.getType();
                    ArrayList<Flashcard> loadedCards = new Gson().fromJson(cardsJson, type);
                    if (loadedCards != null) {
                        cardList.clear();
                        cardList.addAll(loadedCards);
                        adapter.notifyDataSetChanged();
                    }
                }

                // Case 2: Only deck ID provided; fetch deck data from Firestore
                if ((editTitle == null || cardsJson == null) && !editDeckId.isEmpty()) {
                    fetchTargetDeckDataForEdit(editDeckId);
                }
            }
        }

        // Initialize with 2 empty card slots for new decks
        if (!isEditMode && cardList.isEmpty()) {
            binding.toolbarCreate.setTitle("Tạo học phần (" + userRole + ")");
            cardList.add(new Flashcard("", ""));
            cardList.add(new Flashcard("", ""));
            adapter.notifyDataSetChanged();
        }

        binding.toolbarCreate.setNavigationOnClickListener(v -> finish());
        binding.toolbarCreate.inflateMenu(R.menu.menu_create_deck);
        binding.toolbarCreate.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                saveDeckToFirebase();
                return true;
            }
            return false;
        });

        binding.btnGlobalOcrScan.setOnClickListener(v -> {
            Intent intent = new Intent(CreateDeckActivity.this, OcrScanActivity.class);
            scanActivityLauncher.launch(intent);
        });

        binding.fabAddCardSlot.setOnClickListener(v -> {
            cardList.add(new Flashcard("", ""));
            adapter.notifyItemInserted(cardList.size() - 1);
            binding.rvCreateCards.scrollToPosition(cardList.size() - 1);
        });
    }

    // Fetches existing deck details when editing via deck ID
    private void fetchTargetDeckDataForEdit(String deckId) {
        String collectionPath = "admin".equals(userRole) ? "global_decks" : "personal_decks";

        mFirestore.collection(collectionPath).document(deckId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (binding == null) return;
                    if (documentSnapshot.exists()) {
                        String title = documentSnapshot.getString("title");
                        binding.edtDeckTitle.setText(title);

                        List<Map<String, Object>> cardsMapList = (List<Map<String, Object>>) documentSnapshot.get("cards");
                        if (cardsMapList != null) {
                            cardList.clear();
                            for (Map<String, Object> map : cardsMapList) {
                                String term = map.containsKey("term") ? String.valueOf(map.get("term")) : "";
                                String definition = map.containsKey("definition") ? String.valueOf(map.get("definition")) : "";
                                String status = map.containsKey("status") ? String.valueOf(map.get("status")) : "learning";
                                long nextReviewTime = map.containsKey("nextReviewTime") ? ((Number) map.get("nextReviewTime")).longValue() : 0L;
                                boolean isLearned = (map.containsKey("learned") && (boolean) map.get("learned")) || (map.containsKey("isLearned") && (boolean) map.get("isLearned"));

                                Flashcard card = new Flashcard(term, definition);
                                card.setStatus(status);
                                card.setNextReviewTime(nextReviewTime);
                                card.setLearned(isLearned);
                                cardList.add(card);
                            }
                            adapter.notifyDataSetChanged();
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e("FirestoreFetchEdit", "Lỗi nạp bài sửa: " + e.getMessage()));
    }

    // Serializes card list and commits deck to Firestore
    private void saveDeckToFirebase() {
        String title = binding.edtDeckTitle.getText().toString().trim();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String currentUserId = currentUser != null ? currentUser.getUid() : "unknown";

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "Vui lòng nhập tiêu đề bộ thẻ!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Map Flashcard objects to Firestore-compatible structures
        List<Map<String, Object>> firebaseCardsList = new ArrayList<>();
        for (Flashcard card : cardList) {
            String term = card.getTerm().trim();
            String definition = card.getDefinition().trim();

            if (!TextUtils.isEmpty(term) && !TextUtils.isEmpty(definition)) {
                Map<String, Object> cardMap = new HashMap<>();
                cardMap.put("term", term);
                cardMap.put("definition", definition);
                cardMap.put("status", card.getStatus() != null ? card.getStatus() : "learning");
                cardMap.put("nextReviewTime", card.getNextReviewTime() != 0L ? card.getNextReviewTime() : 0L);
                cardMap.put("isLearned", card.isLearned());
                cardMap.put("learned", card.isLearned());
                firebaseCardsList.add(cardMap);
            }
        }

        if (firebaseCardsList.size() < 2) {
            Toast.makeText(this, "Cần có tối thiểu từ 2 thẻ trở lên để tạo!", Toast.LENGTH_SHORT).show();
            return;
        }

        String deckId = isEditMode ? editDeckId : UUID.randomUUID().toString();
        String collectionPath = userRole.equals("admin") ? "global_decks" : "personal_decks";

        // Update existing deck
        if (isEditMode) {
            binding.fabAddCardSlot.setEnabled(false);

            mFirestore.collection(collectionPath).document(deckId)
                    .update("title", title, "cards", firebaseCardsList)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(CreateDeckActivity.this, "Cập nhật học phần thành công!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        binding.fabAddCardSlot.setEnabled(true);
                        Toast.makeText(CreateDeckActivity.this, "Lỗi Firebase: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
            return;
        }

        // Create new deck
        Map<String, Object> deckMap = new HashMap<>();
        deckMap.put("id", deckId);
        deckMap.put("title", title);
        deckMap.put("creatorId", currentUserId);
        deckMap.put("userId", currentUserId);
        deckMap.put("cards", firebaseCardsList);
        deckMap.put("lastModified", System.currentTimeMillis());

        if (userRole.equals("admin")) {
            deckMap.put("author", "Hệ thống");
        } else {
            deckMap.put("author", "bạn");
        }

        binding.fabAddCardSlot.setEnabled(false);

        mFirestore.collection(collectionPath).document(deckId)
                .set(deckMap)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(CreateDeckActivity.this, "Lưu bộ thẻ thành công!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    binding.fabAddCardSlot.setEnabled(true);
                    Toast.makeText(CreateDeckActivity.this, "Lỗi Firebase: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
