package com.example.flashcardapp.views.fragments;

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
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LibraryFragment extends Fragment {

    private FragmentLibraryBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private HomeDeckAdapter adapter;
    private final List<Map<String, Object>> fullDeckList = new ArrayList<>();
    private final List<Map<String, Object>> filteredList = new ArrayList<>();

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
        loadUserDecksFromFirestore();

        // Chặn bật bàn phím tại Fragment, điều hướng mượt sang màn search chuyên sâu màu xám viên thuốc
        binding.edtLibrarySearch.setFocusable(false);
        binding.edtLibrarySearch.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), UserSearchDecksActivity.class);
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        binding.rvLibraryDecks.setLayoutManager(new LinearLayoutManager(getContext()));

        // ĐỒNG BỘ INTENT GỬI ĐI: Truyền đầy đủ ID, TITLE và cấu trúc bảng cá nhân sang màn chi tiết
        adapter = new HomeDeckAdapter(filteredList, deckData -> {
            if (deckData == null) return;
            String deckId = deckData.containsKey("id") ? String.valueOf(deckData.get("id")) : String.valueOf(deckData.get("document_id"));
            String deckTitle = String.valueOf(deckData.get("title"));

            Intent intent = new Intent(getContext(), DeckDetailActivity.class);
            intent.putExtra("DECK_ID", deckId);
            intent.putExtra("DECK_TITLE", deckTitle);
            intent.putExtra("DECK_TYPE", "personal_decks"); // Ghim cứng tab Thư viện là kho cá nhân
            startActivity(intent);
        });
        binding.rvLibraryDecks.setAdapter(adapter);
    }

    private void loadUserDecksFromFirestore() {
        String currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "unknown";
        binding.progressLibraryLoading.setVisibility(View.VISIBLE);

        mFirestore.collection("personal_decks")
                .whereEqualTo("creatorId", currentUserId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    fullDeckList.clear();
                    filteredList.clear();

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        if (doc.exists()) {
                            Map<String, Object> deckData = doc.getData();
                            if (deckData != null) {
                                deckData.put("document_id", doc.getId());
                                if (!deckData.containsKey("id")) {
                                    deckData.put("id", doc.getId());
                                }
                                fullDeckList.add(deckData);
                            }
                        }
                    }

                    filteredList.addAll(fullDeckList);
                    binding.progressLibraryLoading.setVisibility(View.GONE);

                    if (filteredList.isEmpty()) {
                        binding.txtLibraryEmpty.setVisibility(View.VISIBLE);
                    } else {
                        binding.txtLibraryEmpty.setVisibility(View.GONE);
                    }

                    if (adapter != null) adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    binding.progressLibraryLoading.setVisibility(View.GONE);
                    fetchDataWithoutOrdering(currentUserId);
                });
    }

    private void fetchDataWithoutOrdering(String userId) {
        mFirestore.collection("personal_decks")
                .whereEqualTo("creatorId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    fullDeckList.clear();
                    filteredList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) {
                            data.put("document_id", doc.getId());
                            if (!data.containsKey("id")) {
                                data.put("id", doc.getId());
                            }
                            fullDeckList.add(data);
                        }
                    }
                    filteredList.addAll(fullDeckList);
                    if (filteredList.isEmpty()) binding.txtLibraryEmpty.setVisibility(View.VISIBLE);
                    if (adapter != null) adapter.notifyDataSetChanged();
                });
    }

    private void setupSearchFilter() {
        binding.edtLibrarySearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String queryText = s.toString().trim().toLowerCase();
                filteredList.clear();

                if (queryText.isEmpty()) {
                    filteredList.addAll(fullDeckList);
                } else {
                    for (Map<String, Object> deck : fullDeckList) {
                        String title = (String) deck.get("title");
                        if (title != null && title.toLowerCase().contains(queryText)) {
                            filteredList.add(deck);
                        }
                    }
                }
                if (adapter != null) adapter.notifyDataSetChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}