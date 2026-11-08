package com.example.flashcardapp.views.fragments;

import com.example.flashcardapp.views.UserSearchDecksActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.flashcardapp.R;
import com.example.flashcardapp.adapters.HomeDeckAdapter;
import com.example.flashcardapp.databinding.FragmentHomeBinding;
import com.example.flashcardapp.views.DeckDetailActivity;
import com.example.flashcardapp.views.ProfileActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment implements HomeDeckAdapter.OnDeckClickListener {

    private FragmentHomeBinding binding;
    private FirebaseFirestore mFirestore;
    private FirebaseAuth mAuth;

    private final List<Map<String, Object>> adminDecksList = new ArrayList<>();
    private final List<Map<String, Object>> recentDecksList = new ArrayList<>();

    private HomeDeckAdapter adminAdapter;
    private HomeDeckAdapter recentAdapter;

    // Realtime snapshot listeners
    private ListenerRegistration continueStudyListener;
    private ListenerRegistration adminGlobalDecksListener;
    private ListenerRegistration recentUserDecksListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        setupRecyclerViews();
        setupUserAvatar();

        binding.edtHomeSearch.setFocusable(false);
        binding.edtHomeSearch.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), UserSearchDecksActivity.class);
            startActivity(intent);
        });

        if (mAuth.getCurrentUser() != null) {
            startRealtimeDashboardListening();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void setupRecyclerViews() {
        binding.rvAdminGlobalDecks.setLayoutManager(new LinearLayoutManager(getActivity()));
        adminAdapter = new HomeDeckAdapter(adminDecksList, this);
        binding.rvAdminGlobalDecks.setAdapter(adminAdapter);

        binding.rvRecentUserDecks.setLayoutManager(new LinearLayoutManager(getActivity()));
        recentAdapter = new HomeDeckAdapter(recentDecksList, this);
        binding.rvRecentUserDecks.setAdapter(recentAdapter);
    }

    private void setupUserAvatar() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.getDisplayName() != null) {
            String name = currentUser.getDisplayName().trim();
            if (!name.isEmpty()) {
                String firstLetter = String.valueOf(name.charAt(0)).toUpperCase();
                binding.txtAvatarLetter.setText(firstLetter);
            }
        } else {
            binding.txtAvatarLetter.setText("N");
        }

        // Open profile screen with user role
        binding.cardUserAvatarWrapper.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ProfileActivity.class);
            intent.putExtra("USER_ROLE", "user");
            startActivity(intent);
        });
    }

    // Realtime listeners for home dashboard sections
    private void startRealtimeDashboardListening() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        String currentUid = user.getUid();

        // 1. Continue learning progress banner
        continueStudyListener = mFirestore.collection("personal_decks")
                .whereEqualTo("userId", currentUid)
                .orderBy("lastModified", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || binding == null) return;
                    if (snapshots != null && !snapshots.isEmpty()) {
                        processAndDisplayDeckProgress(snapshots.getDocuments().get(0));
                    } else {
                        fetchDeckWithFallbackFlowRealtime(currentUid);
                    }
                });

        // 2. Latest global system decks
        adminGlobalDecksListener = mFirestore.collection("global_decks")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || binding == null) return;
                    adminDecksList.clear();

                    if (snapshots != null && !snapshots.isEmpty()) {
                        binding.lblAdminGlobalDecks.setVisibility(View.VISIBLE);

                        List<Map<String, Object>> tempList = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Map<String, Object> data = doc.getData();
                            data.put("id", doc.getId());
                            data.put("author", "Hệ thống");
                            data.put("origin_collection", "global_decks");
                            tempList.add(data);
                        }

                        // Sort by lastModified descending
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            tempList.sort((o1, o2) -> {
                                long t1 = o1.containsKey("lastModified") ? ((Number) o1.get("lastModified")).longValue() : 0L;
                                long t2 = o2.containsKey("lastModified") ? ((Number) o2.get("lastModified")).longValue() : 0L;
                                return Long.compare(t2, t1);
                            });
                        }

                        for (int i = 0; i < Math.min(3, tempList.size()); i++) {
                            adminDecksList.add(tempList.get(i));
                        }
                    } else {
                        binding.lblAdminGlobalDecks.setVisibility(View.GONE);
                    }
                    adminAdapter.notifyDataSetChanged();
                });

        // 3. Recently studied user decks
        recentUserDecksListener = mFirestore.collection("personal_decks")
                .whereEqualTo("userId", currentUid)
                .orderBy("lastModified", Query.Direction.DESCENDING)
                .limit(3)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || binding == null) return;
                    recentDecksList.clear();

                    if (snapshots != null && !snapshots.isEmpty()) {
                        binding.lblRecentUserDecks.setVisibility(View.VISIBLE);
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Map<String, Object> data = doc.getData();
                            data.put("id", doc.getId());
                            if (!data.containsKey("author")) {
                                data.put("author", "bạn");
                            }
                            data.put("origin_collection", "personal_decks");
                            recentDecksList.add(data);
                        }
                    } else {
                        binding.lblRecentUserDecks.setVisibility(View.GONE);
                    }
                    recentAdapter.notifyDataSetChanged();
                });
    }

    private void fetchDeckWithFallbackFlowRealtime(String currentUid) {
        mFirestore.collection("personal_decks")
                .whereEqualTo("creatorId", currentUid)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (binding == null) return;
                    if (!snapshots.isEmpty()) {
                        processAndDisplayDeckProgress(snapshots.getDocuments().get(0));
                    } else {
                        mFirestore.collection("personal_decks")
                                .whereEqualTo("userId", currentUid)
                                .get()
                                .addOnSuccessListener(snapshots2 -> {
                                    if (binding == null) return;
                                    if (!snapshots2.isEmpty()) {
                                        processAndDisplayDeckProgress(snapshots2.getDocuments().get(0));
                                    } else {
                                        handleNewUserFlow();
                                    }
                                })
                                .addOnFailureListener(e -> handleNewUserFlow());
                    }
                })
                .addOnFailureListener(e -> handleNewUserFlow());
    }

    private void processAndDisplayDeckProgress(com.google.firebase.firestore.DocumentSnapshot doc) {
        List<Map<String, Object>> cards = (List<Map<String, Object>>) doc.get("cards");
        String deckId = doc.getId();
        String deckTitle = doc.getString("title");
        String dbType = doc.getReference().getPath().contains("global_decks") ? "global_decks" : "personal_decks";

        int totalCards = 0;
        int masteredCount = 0;

        if (cards != null && !cards.isEmpty()) {
            totalCards = cards.size();
            for (Map<String, Object> card : cards) {
                if (card.containsKey("status") && "mastered".equals(card.get("status"))) {
                    masteredCount++;
                }
            }
        }

        binding.txtContinueDeckTitle.setText(deckTitle != null ? deckTitle : "Học phần chưa đặt tên");
        binding.txtContinueProgressIndicator.setText("Đã học " + masteredCount + " / " + totalCards + " thẻ");

        binding.progressHomeDeck.setMax(totalCards > 0 ? totalCards : 100);
        binding.progressHomeDeck.setProgress(masteredCount);
        binding.btnHomeContinueAction.setEnabled(true);

        binding.btnHomeContinueAction.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), DeckDetailActivity.class);
            intent.putExtra("DECK_ID", deckId);
            intent.putExtra("DECK_TITLE", deckTitle);
            intent.putExtra("DECK_TYPE", dbType);
            startActivity(intent);
        });
    }

    private void handleNewUserFlow() {
        binding.txtContinueDeckTitle.setText("Chưa có học phần nào");
        binding.txtContinueProgressIndicator.setText("Hãy tạo hoặc chọn một học phần để bắt đầu!");
        binding.progressHomeDeck.setProgress(0);
        binding.btnHomeContinueAction.setEnabled(true);

        binding.btnHomeContinueAction.setOnClickListener(v -> {
            if (getActivity() != null) {
                BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_navigation);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_library);
                }
            }
        });
    }

    @Override
    public void onDeckClick(Map<String, Object> deckData) {
        if (deckData == null) return;
        String deckId = String.valueOf(deckData.get("id"));
        String deckTitle = String.valueOf(deckData.get("title"));
        String originCollection = deckData.containsKey("origin_collection") ?
                String.valueOf(deckData.get("origin_collection")) : "personal_decks";

        Intent intent = new Intent(getActivity(), DeckDetailActivity.class);
        intent.putExtra("DECK_ID", deckId);
        intent.putExtra("DECK_TITLE", deckTitle);
        intent.putExtra("DECK_TYPE", originCollection);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Detach listeners to prevent memory leaks
        if (continueStudyListener != null) continueStudyListener.remove();
        if (adminGlobalDecksListener != null) adminGlobalDecksListener.remove();
        if (recentUserDecksListener != null) recentUserDecksListener.remove();
        binding = null;
    }
}
