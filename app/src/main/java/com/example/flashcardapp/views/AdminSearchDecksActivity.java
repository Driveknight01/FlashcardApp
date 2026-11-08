package com.example.flashcardapp.views;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.flashcardapp.R;
import com.example.flashcardapp.databinding.ActivityAdminSearchDecksBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminSearchDecksActivity extends AppCompatActivity {

    private ActivityAdminSearchDecksBinding binding;
    private FirebaseFirestore mFirestore;

    private final List<Map<String, Object>> allDecksList = new ArrayList<>();
    private final List<Map<String, Object>> filteredList = new ArrayList<>();

    // Study count cache mapped by deck title
    private final Map<String, Integer> searchStudyCountMap = new HashMap<>();

    private SuggestionAdapter suggestionAdapter;
    private FinalCardAdapter finalCardAdapter;

    private boolean isDisplayingFinalCard = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminSearchDecksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();

        binding.btnSearchBack.setOnClickListener(v -> finish());
        binding.btnClearSearchText.setOnClickListener(v -> {
            binding.edtRealSearch.setText("");
            switchToSuggestionState();
        });

        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(this));

        suggestionAdapter = new SuggestionAdapter(filteredList);
        finalCardAdapter = new FinalCardAdapter(filteredList, searchStudyCountMap);

        binding.rvSearchResults.setAdapter(suggestionAdapter);

        preloadGlobalDecksAndCounts();

        binding.edtRealSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase();

                if (query.isEmpty()) {
                    binding.btnClearSearchText.setVisibility(View.GONE);
                    filteredList.clear();
                    suggestionAdapter.notifyDataSetChanged();
                } else {
                    binding.btnClearSearchText.setVisibility(View.VISIBLE);

                    if (isDisplayingFinalCard) {
                        switchToSuggestionState();
                    }

                    filterSuggestions(query);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void preloadGlobalDecksAndCounts() {
        // Aggregate study counts from personal_decks before loading global decks
        mFirestore.collection("personal_decks").get().addOnSuccessListener(personalSnapshots -> {
            if (personalSnapshots != null) {
                searchStudyCountMap.clear();
                for (QueryDocumentSnapshot doc : personalSnapshots) {
                    String title = doc.getString("title");
                    String author = doc.getString("author");
                    if (title != null && "Hệ thống".equals(author)) {
                        int count = 0;
                        Object studyCountObj = doc.get("studyCount");
                        if (studyCountObj instanceof Number) {
                            count = ((Number) studyCountObj).intValue();
                        }
                        searchStudyCountMap.put(title, searchStudyCountMap.getOrDefault(title, 0) + count);
                    }
                }
            }

            // Load global decks after calculating study counts
            mFirestore.collection("global_decks").get().addOnSuccessListener(snapshots -> {
                if (snapshots == null) return;
                allDecksList.clear();
                for (QueryDocumentSnapshot doc : snapshots) {
                    Map<String, Object> data = doc.getData();
                    data.put("docId", doc.getId());
                    allDecksList.add(data);
                }
            });
        });
    }

    private void filterSuggestions(String text) {
        filteredList.clear();
        for (Map<String, Object> item : allDecksList) {
            if (item != null && item.containsKey("title")) {
                String title = String.valueOf(item.get("title")).toLowerCase();
                if (title.contains(text)) {
                    filteredList.add(item);
                }
            }
        }
        suggestionAdapter.notifyDataSetChanged();
    }

    private void onSuggestionClicked(Map<String, Object> selectedDeck) {
        isDisplayingFinalCard = true;

        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

        binding.edtRealSearch.setText(String.valueOf(selectedDeck.get("title")));

        filteredList.clear();
        filteredList.add(selectedDeck);

        binding.rvSearchResults.setAdapter(finalCardAdapter);
        finalCardAdapter.notifyDataSetChanged();
    }

    private void switchToSuggestionState() {
        isDisplayingFinalCard = false;
        binding.rvSearchResults.setAdapter(suggestionAdapter);
    }

    private void showActionDialog(Map<String, Object> deck) {
        String docId = String.valueOf(deck.get("docId"));
        String title = String.valueOf(deck.get("title"));

        String[] options = {"✏️ Sửa nội dung thẻ", "❌ Xóa thẻ này"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(AdminSearchDecksActivity.this, CreateDeckActivity.class);
                        intent.putExtra("USER_ROLE", "admin");
                        intent.putExtra("IS_EDIT_MODE", true);
                        intent.putExtra("DECK_ID", docId);
                        intent.putExtra("DECK_TITLE", title);
                        intent.putExtra("DECK_CARDS_JSON", new com.google.gson.Gson().toJson(deck.get("cards")));
                        startActivity(intent);
                        finish();
                    } else {
                        mFirestore.collection("global_decks").document(docId).delete()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Đã xóa thành công!", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                    }
                }).show();
    }

    private class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.ViewHolder> {
        private final List<Map<String, Object>> list;
        public SuggestionAdapter(List<Map<String, Object>> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_suggestion, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> deck = list.get(position);
            holder.txtSuggest.setText(String.valueOf(deck.get("title")));
            holder.itemView.setOnClickListener(v -> onSuggestionClicked(deck));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtSuggest;
            ViewHolder(View iv) { super(iv); txtSuggest = iv.findViewById(R.id.txtSuggestionText); }
        }
    }

    private class FinalCardAdapter extends RecyclerView.Adapter<FinalCardAdapter.ViewHolder> {
        private final List<Map<String, Object>> list;
        private final Map<String, Integer> countMap;

        public FinalCardAdapter(List<Map<String, Object>> list, Map<String, Integer> countMap) {
            this.list = list;
            this.countMap = countMap;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_global_deck, parent, false);
            v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> deck = list.get(position);
            String title = String.valueOf(deck.get("title"));

            holder.txtTitle.setText(title);

            List<?> cards = (List<?>) deck.get("cards");
            int cardSize = cards != null ? cards.size() : 0;

            // Resolve cumulative study count for deck title
            int totalStudyCount = 0;
            if (countMap.containsKey(title)) {
                totalStudyCount = countMap.get(title);
            }

            holder.txtCount.setText(cardSize + " thuật ngữ  •  " + totalStudyCount + " lượt học");
            holder.itemView.setOnClickListener(v -> showActionDialog(deck));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtTitle, txtCount;
            ViewHolder(View iv) {
                super(iv);
                txtTitle = iv.findViewById(R.id.txtAdminDeckTitle);
                txtCount = iv.findViewById(R.id.txtAdminCardCount);
            }
        }
    }
}
