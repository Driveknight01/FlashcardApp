package com.example.flashcardapp.views.fragments;

import com.example.flashcardapp.R;
import com.example.flashcardapp.views.UserSearchDecksActivity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.flashcardapp.adapters.HomeDeckAdapter;
import com.example.flashcardapp.databinding.FragmentLibraryBinding;
import com.example.flashcardapp.views.DeckDetailActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LibraryFragment extends Fragment {

    private FragmentLibraryBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private HomeDeckAdapter adapter;

    // Separate data stores for user decks and system decks
    private final List<Map<String, Object>> userDecksList = new ArrayList<>();
    private final List<Map<String, Object>> adminDecksList = new ArrayList<>();

    // Current filtered list bound to the adapter
    private final List<Map<String, Object>> filteredList = new ArrayList<>();

    // Selected tab state: true for user decks, false for system decks
    private boolean isUserTabActive = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        setupRecyclerView();
        setupExcelTabsClick();

        loadUserDecksFromFirestore();
        loadAdminDecksFromFirestore();

        binding.edtLibrarySearch.setFocusable(false);
        binding.edtLibrarySearch.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), UserSearchDecksActivity.class);
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        binding.rvLibraryDecks.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new HomeDeckAdapter(filteredList, deckData -> {
            if (deckData == null) return;
            String deckId = String.valueOf(deckData.get("id"));
            String deckTitle = String.valueOf(deckData.get("title"));

            String dbType = deckData.containsKey("origin_collection") ?
                    String.valueOf(deckData.get("origin_collection")) : "personal_decks";

            Intent intent = new Intent(getContext(), DeckDetailActivity.class);
            intent.putExtra("DECK_ID", deckId);
            intent.putExtra("DECK_TITLE", deckTitle);
            intent.putExtra("DECK_TYPE", dbType);
            startActivity(intent);
        });
        binding.rvLibraryDecks.setAdapter(adapter);
    }

    private void setupExcelTabsClick() {
        binding.tabUserDecks.setOnClickListener(v -> {
            if (isUserTabActive) return;
            isUserTabActive = true;
            updateTabUi();
            switchDisplayData();
        });

        binding.tabAdminDecks.setOnClickListener(v -> {
            if (!isUserTabActive) return;
            isUserTabActive = false;
            updateTabUi();
            switchDisplayData();
        });
    }

    // Update tab typography and color based on active tab state
    private void updateTabUi() {
        if (isUserTabActive) {
            binding.tabUserDecks.setTextColor(android.graphics.Color.parseColor("#4D62FF"));
            binding.tabUserDecks.setTypeface(null, android.graphics.Typeface.BOLD);

            binding.tabAdminDecks.setTextColor(android.graphics.Color.parseColor("#757575"));
            binding.tabAdminDecks.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else {
            binding.tabAdminDecks.setTextColor(android.graphics.Color.parseColor("#4D62FF"));
            binding.tabAdminDecks.setTypeface(null, android.graphics.Typeface.BOLD);

            binding.tabUserDecks.setTextColor(android.graphics.Color.parseColor("#757575"));
            binding.tabUserDecks.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    // Switches data source in the adapter according to the active tab
    private void switchDisplayData() {
        filteredList.clear();
        if (isUserTabActive) {
            filteredList.addAll(userDecksList);
            binding.txtLibraryEmpty.setText("Thư viện chưa có bộ học phần nào!");
            binding.txtLibraryEmpty.setVisibility(userDecksList.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            filteredList.addAll(adminDecksList);
            binding.txtLibraryEmpty.setText("Hệ thống chưa tạo bộ học phần nào!");
            binding.txtLibraryEmpty.setVisibility(adminDecksList.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    // Fetch user-created decks excluding system clones
    private void loadUserDecksFromFirestore() {
        String currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "unknown";
        binding.progressLibraryLoading.setVisibility(View.VISIBLE);

        mFirestore.collection("personal_decks")
                .whereEqualTo("creatorId", currentUserId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    userDecksList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Map<String, Object> data = doc.getData();

                        // Exclude cloned system decks from user tab
                        if (data.containsKey("isCloned") && (Boolean) data.get("isCloned")) {
                            continue;
                        }

                        data.put("id", doc.getId());
                        if (!data.containsKey("author")) {
                            data.put("author", "bạn");
                        }
                        data.put("origin_collection", "personal_decks");
                        userDecksList.add(data);
                    }

                    // Sort user decks by creation timestamp descending
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        userDecksList.sort((o1, o2) -> {
                            long t1 = o1.containsKey("createdAt") ? ((Number) o1.get("createdAt")).longValue() : 0L;
                            long t2 = o2.containsKey("createdAt") ? ((Number) o2.get("createdAt")).longValue() : 0L;
                            return Long.compare(t2, t1);
                        });
                    }

                    if (isUserTabActive) {
                        binding.progressLibraryLoading.setVisibility(View.GONE);
                        switchDisplayData();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isUserTabActive) binding.progressLibraryLoading.setVisibility(View.GONE);
                });
    }

    // Fetch all global system decks
    private void loadAdminDecksFromFirestore() {
        mFirestore.collection("global_decks")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    adminDecksList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Map<String, Object> data = doc.getData();
                        data.put("id", doc.getId());
                        data.put("author", "Hệ thống");
                        data.put("origin_collection", "global_decks");
                        adminDecksList.add(data);
                    }

                    // Sort system decks by last modified timestamp descending
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        adminDecksList.sort((o1, o2) -> {
                            long t1 = o1.containsKey("lastModified") ? ((Number) o1.get("lastModified")).longValue() : 0L;
                            long t2 = o2.containsKey("lastModified") ? ((Number) o2.get("lastModified")).longValue() : 0L;
                            return Long.compare(t2, t1);
                        });
                    }

                    if (!isUserTabActive) {
                        binding.progressLibraryLoading.setVisibility(View.GONE);
                        switchDisplayData();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isUserTabActive) binding.progressLibraryLoading.setVisibility(View.GONE);
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
