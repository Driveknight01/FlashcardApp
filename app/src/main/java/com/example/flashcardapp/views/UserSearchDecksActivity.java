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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.flashcardapp.R;
import com.example.flashcardapp.databinding.ActivityUserSearchDecksBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserSearchDecksActivity extends AppCompatActivity {

    private ActivityUserSearchDecksBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private final List<Map<String, Object>> masterDecksList = new ArrayList<>();
    private final List<Map<String, Object>> filteredList = new ArrayList<>();

    private SuggestionAdapter suggestionAdapter;
    private FinalCardAdapter finalCardAdapter;
    private boolean isDisplayingFinalCard = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserSearchDecksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        binding.btnUserSearchBack.setOnClickListener(v -> finish());
        binding.btnUserClearSearchText.setOnClickListener(v -> {
            binding.edtUserRealSearch.setText("");
            switchToSuggestionState();
        });

        binding.rvUserSearchResults.setLayoutManager(new LinearLayoutManager(this));

        suggestionAdapter = new SuggestionAdapter(filteredList);
        finalCardAdapter = new FinalCardAdapter(filteredList);
        binding.rvUserSearchResults.setAdapter(suggestionAdapter);

        setupUserAvatar();
        loadSecureSearchData();

        // Realtime filter logic for search suggestions
        binding.edtUserRealSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase();

                if (query.isEmpty()) {
                    binding.btnUserClearSearchText.setVisibility(View.GONE);
                    filteredList.clear();
                    suggestionAdapter.notifyDataSetChanged();
                } else {
                    binding.btnUserClearSearchText.setVisibility(View.VISIBLE);
                    if (isDisplayingFinalCard) {
                        switchToSuggestionState();
                    }
                    filterSearchData(query);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupUserAvatar() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            binding.txtUserSearchAvatarLetter.setText(String.valueOf(user.getDisplayName().trim().charAt(0)).toUpperCase());
        }
    }

    // Query system decks and personal decks belonging to current user
    private void loadSecureSearchData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        String currentUid = currentUser.getUid();

        masterDecksList.clear();

        // 1. Global system decks
        mFirestore.collection("global_decks").get().addOnSuccessListener(snapshots -> {
            if (snapshots != null) {
                for (QueryDocumentSnapshot doc : snapshots) {
                    Map<String, Object> data = doc.getData();
                    data.put("id", doc.getId());
                    data.put("author", "Hệ thống");
                    masterDecksList.add(data);
                }
            }

            // 2. Current user personal decks
            mFirestore.collection("personal_decks")
                    .whereEqualTo("userId", currentUid)
                    .get()
                    .addOnSuccessListener(personalSnapshots -> {
                        if (personalSnapshots != null) {
                            for (QueryDocumentSnapshot doc : personalSnapshots) {
                                Map<String, Object> data = doc.getData();
                                data.put("id", doc.getId());
                                data.put("author", "bạn");
                                masterDecksList.add(data);
                            }
                        }
                    });
        });
    }

    private void filterSearchData(String text) {
        filteredList.clear();
        for (Map<String, Object> item : masterDecksList) {
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

        binding.edtUserRealSearch.setText(String.valueOf(selectedDeck.get("title")));

        filteredList.clear();
        filteredList.add(selectedDeck);

        binding.rvUserSearchResults.setAdapter(finalCardAdapter);
        finalCardAdapter.notifyDataSetChanged();
    }

    private void switchToSuggestionState() {
        isDisplayingFinalCard = false;
        binding.rvUserSearchResults.setAdapter(suggestionAdapter);
    }

    // Adapter for search suggestions
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

    // Adapter for selected deck preview card
    private class FinalCardAdapter extends RecyclerView.Adapter<FinalCardAdapter.ViewHolder> {
        private final List<Map<String, Object>> list;
        public FinalCardAdapter(List<Map<String, Object>> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_deck, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> deck = list.get(position);
            holder.txtTitle.setText(String.valueOf(deck.get("title")));

            String author = deck.containsKey("author") ? String.valueOf(deck.get("author")) : "bạn";
            List<?> cards = (List<?>) deck.get("cards");
            holder.txtSubtitle.setText("Đã lưu • " + (cards != null ? cards.size() : 0) + " thuật ngữ • Tác giả: " + author);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(UserSearchDecksActivity.this, DeckDetailActivity.class);
                intent.putExtra("DECK_ID", String.valueOf(deck.get("id")));
                intent.putExtra("DECK_TITLE", String.valueOf(deck.get("title")));
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtTitle, txtSubtitle;
            ViewHolder(View iv) {
                super(iv);
                txtTitle = iv.findViewById(R.id.txtItemDeckTitle);
                txtSubtitle = iv.findViewById(R.id.txtItemDeckSubtitle);
            }
        }
    }
}
