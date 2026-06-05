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
import com.example.flashcardapp.views.FlashcardLearnActivity;
import com.example.flashcardapp.views.ProfileActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
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

        // Chặn bàn phím, điều hướng sang trang tìm kiếm viên thuốc đồng bộ
        binding.edtHomeSearch.setFocusable(false);
        binding.edtHomeSearch.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), UserSearchDecksActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAuth.getCurrentUser() != null) {
            fetchLastActiveDeckForContinueStudy();
            fetchTop3AdminDecks();
            fetchTop3RecentUserDecks();
        }
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

        // ĐỒNG BỘ: Ấn vào cụm bọc Avatar góc phải sẽ nhảy sang trang Hồ sơ cá nhân đổi ảnh đại diện
        binding.cardUserAvatarWrapper.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ProfileActivity.class);
            startActivity(intent);
        });
    }

    private void fetchLastActiveDeckForContinueStudy() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        String currentUid = user.getUid();

        mFirestore.collection("personal_decks")
                .whereEqualTo("userId", currentUid)
                .orderBy("lastModified", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (binding == null) return;
                    if (!queryDocumentSnapshots.isEmpty()) {
                        processAndDisplayDeckProgress(queryDocumentSnapshots.getDocuments().get(0));
                    } else {
                        fetchDeckWithFallbackFlow(currentUid);
                    }
                })
                .addOnFailureListener(e -> fetchDeckWithFallbackFlow(currentUid));
    }

    private void fetchDeckWithFallbackFlow(String currentUid) {
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
                .addOnFailureListener(e -> {
                    if (binding != null) handleNewUserFlow();
                });
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

        // Đấu nối nút Học tiếp từ khối tiến độ
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

    private void fetchTop3AdminDecks() {
        mFirestore.collection("global_decks")
                .limit(3)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (binding == null) return;
                    adminDecksList.clear();

                    if (!snapshots.isEmpty()) {
                        binding.lblAdminGlobalDecks.setVisibility(View.VISIBLE);
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Map<String, Object> data = doc.getData();
                            data.put("id", doc.getId());
                            data.put("author", "Hệ thống");
                            data.put("origin_collection", "global_decks"); // Gài nhãn nguồn bảo vệ
                            adminDecksList.add(data);
                        }
                    } else {
                        binding.lblAdminGlobalDecks.setVisibility(View.GONE);
                    }
                    adminAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    if (binding != null) binding.lblAdminGlobalDecks.setVisibility(View.GONE);
                });
    }

    private void fetchTop3RecentUserDecks() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        mFirestore.collection("personal_decks")
                .whereEqualTo("userId", user.getUid())
                .orderBy("lastModified", Query.Direction.DESCENDING)
                .limit(3)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (binding == null) return;
                    recentDecksList.clear();

                    if (!snapshots.isEmpty()) {
                        binding.lblRecentUserDecks.setVisibility(View.VISIBLE);
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Map<String, Object> data = doc.getData();
                            data.put("id", doc.getId());
                            // Nếu bài được clone từ Admin sang, giữ nguyên nhãn tác giả gốc lưu trong Db
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
                })
                .addOnFailureListener(e -> {
                    if (binding != null) binding.lblRecentUserDecks.setVisibility(View.GONE);
                });
    }

    // 🚀 ĐỒNG BỘ LUỒNG PHÂN PHỐI INTENT TUYỆT ĐỐI CHO SỰ KIỆN CLICK RECYCLERVIEW
    @Override
    public void onDeckClick(Map<String, Object> deckData) {
        if (deckData == null) return;
        String deckId = String.valueOf(deckData.get("id"));
        String deckTitle = String.valueOf(deckData.get("title"));

        // Đọc cờ phân loại bảng để truyền sang cho Detail chặn nút Sửa/Xóa khẩn cấp
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
        binding = null;
    }
}