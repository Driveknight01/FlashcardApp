package com.example.flashcardapp.views.fragments;

import com.example.flashcardapp.R; // Đã fix import R chính xác
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

    // PHÂN TÁCH 2 KHO LƯU TRỮ RIÊNG BIỆT KIỂU EXCEL
    private final List<Map<String, Object>> userDecksList = new ArrayList<>();
    private final List<Map<String, Object>> adminDecksList = new ArrayList<>();

    // Danh sách hiển thị thực tế lên RecyclerView sau khi lọc tìm kiếm hoặc đổi tab
    private final List<Map<String, Object>> filteredList = new ArrayList<>();

    // Biến cờ đánh dấu vị trí Tab hiện tại (Mặc định ban đầu là Tab User)
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
        setupExcelTabsClick(); // Khởi tạo sự kiện bấm chuyển đổi Tab Sheet

        // Tải song song cả hai nguồn dữ liệu từ Firestore
        loadUserDecksFromFirestore();
        loadAdminDecksFromFirestore();

        // Chặn bật bàn phím tại Fragment, điều hướng mượt sang màn search chuyên sâu
        binding.edtLibrarySearch.setFocusable(false);
        binding.edtLibrarySearch.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), UserSearchDecksActivity.class);
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        binding.rvLibraryDecks.setLayoutManager(new LinearLayoutManager(getContext()));

        // ĐỒNG BỘ INTENT TUYỆT ĐỐI: Bấm dòng nào truyền đúng loại bảng nguồn của dòng đó
        adapter = new HomeDeckAdapter(filteredList, deckData -> {
            if (deckData == null) return;
            String deckId = String.valueOf(deckData.get("id"));
            String deckTitle = String.valueOf(deckData.get("title"));

            // Bóc tách nhãn xuất xứ để màn Detail biết đường hiển thị nút Sửa/Xóa tương ứng
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

    // QUẢN LÝ LUỒNG CLICK TAB SHEET EXCEL
    private void setupExcelTabsClick() {
        binding.tabUserDecks.setOnClickListener(v -> {
            if (isUserTabActive) return; // Đang đứng ở tab này rồi thì không xử lý lại
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

    // Cập nhật giao diện đậm/nhạt, đổi màu chữ khi hoán đổi Tab Sheet Excel bằng mã màu trực tiếp
    private void updateTabUi() {
        if (isUserTabActive) {
            // Tab "Của bạn" - Active: Chuyển màu xanh dương chủ đạo (#4D62FF) và viết ĐẬM
            binding.tabUserDecks.setTextColor(android.graphics.Color.parseColor("#4D62FF"));
            binding.tabUserDecks.setTypeface(null, android.graphics.Typeface.BOLD);

            // Tab "Hệ thống" - Inactive: Chuyển màu xám (#757575) và viết THƯỜNG
            binding.tabAdminDecks.setTextColor(android.graphics.Color.parseColor("#757575"));
            binding.tabAdminDecks.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else {
            // Tab "Hệ thống" - Active: Chuyển màu xanh dương chủ đạo (#4D62FF) và viết ĐẬM
            binding.tabAdminDecks.setTextColor(android.graphics.Color.parseColor("#4D62FF"));
            binding.tabAdminDecks.setTypeface(null, android.graphics.Typeface.BOLD);

            // Tab "Của bạn" - Inactive: Chuyển màu xám (#757575) và viết THƯỜNG
            binding.tabUserDecks.setTextColor(android.graphics.Color.parseColor("#757575"));
            binding.tabUserDecks.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    // Hoán đổi nguồn dữ liệu đổ vào Adapter khi click đổi tab
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

    // LUỒNG 1: Tải học phần ĐÚNG NGHĨA của User cá nhân (Loại trừ bài clone từ hệ thống)
    private void loadUserDecksFromFirestore() {
        String currentUserId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "unknown";
        binding.progressLibraryLoading.setVisibility(View.VISIBLE);

        // 🌟 KHÔI PHỤC LOGIC GỐC: Truy vấn đúng theo trường creatorId của Nam
        mFirestore.collection("personal_decks")
                .whereEqualTo("creatorId", currentUserId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    userDecksList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Map<String, Object> data = doc.getData();

                        // 🌟 BỘ LỌC THÔNG MINH: Nếu trường dữ liệu đánh dấu đây là bộ thẻ clone từ Admin, ta bỏ qua không hiện ở tab "Của bạn"
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

                    // Sắp xếp bằng Java thuần tuyệt đối an toàn cho kho cá nhân theo thời gian tạo mới nhất
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        userDecksList.sort((o1, o2) -> {
                            long t1 = o1.containsKey("createdAt") ? ((Number) o1.get("createdAt")).longValue() : 0L;
                            long t2 = o2.containsKey("createdAt") ? ((Number) o2.get("createdAt")).longValue() : 0L;
                            return Long.compare(t2, t1); // Mới nhất xếp lên đầu
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

    // LUỒNG 2: Tải toàn bộ học phần gốc của Hệ thống (Admin)
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

                    // Sắp xếp Java thuần tuyệt đối an toàn cho kho Admin hệ thống
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        adminDecksList.sort((o1, o2) -> {
                            long t1 = o1.containsKey("lastModified") ? ((Number) o1.get("lastModified")).longValue() : 0L;
                            long t2 = o2.containsKey("lastModified") ? ((Number) o2.get("lastModified")).longValue() : 0L;
                            return Long.compare(t2, t1); // Mới nhất xếp lên đầu
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