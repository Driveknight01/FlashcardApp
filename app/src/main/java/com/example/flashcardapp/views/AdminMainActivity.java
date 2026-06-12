package com.example.flashcardapp.views;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.flashcardapp.R;
import com.example.flashcardapp.databinding.ActivityAdminMainBinding;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminMainActivity extends AppCompatActivity {

    private ActivityAdminMainBinding binding;
    private FirebaseFirestore mFirestore;

    // Danh sách và Adapter riêng cho Luồng 1 (Top 3 Ô Xu hướng Card độc lập)
    private final List<Map.Entry<String, Integer>> trendingDecksList = new ArrayList<>();
    private AdminTrendingAdapter trendingDecksAdapter;

    // Danh sách và Adapter riêng cho Luồng 2 (Học viên hoạt động trong ngày)
    private final List<Map<String, Object>> activeUsersDashboardList = new ArrayList<>();
    private ActiveUserHomeStyleAdapter userDashboardAdapter;

    private ListenerRegistration trendingListener;
    private ListenerRegistration activeUsersListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();

        setupRecyclerViews();
        setupClickListeners();
        startRealtimeDashboardListening();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) {
            binding.txtActiveUserCount.setText("Top 5 hôm nay");
        }
    }

    private void setupRecyclerViews() {
        // 1. Cấu hình danh sách hiển thị Top 3 ô Xu hướng Card riêng biệt
        binding.rvAdminTrendingDecks.setLayoutManager(new LinearLayoutManager(this));

        // Chặn cuộn riêng biệt để tích hợp vào luồng cuộn toàn màn hình mượt mà
        binding.rvAdminTrendingDecks.setHasFixedSize(false);
        binding.rvAdminTrendingDecks.setNestedScrollingEnabled(false);

        trendingDecksAdapter = new AdminTrendingAdapter(trendingDecksList);
        binding.rvAdminTrendingDecks.setAdapter(trendingDecksAdapter);

        // 2. Cấu hình danh sách Học viên hoạt động hôm nay
        binding.rvAdminActiveUsersToday.setLayoutManager(new LinearLayoutManager(this));

        // Khóa chặt tính năng cuộn riêng, ép trải phẳng ra để vuốt được toàn bộ trang chủ
        binding.rvAdminActiveUsersToday.setHasFixedSize(false);
        binding.rvAdminActiveUsersToday.setNestedScrollingEnabled(false);

        userDashboardAdapter = new ActiveUserHomeStyleAdapter(activeUsersDashboardList);
        binding.rvAdminActiveUsersToday.setAdapter(userDashboardAdapter);
    }

    private void setupClickListeners() {
        binding.btnFakeSearchContainer.setOnClickListener(v ->
                startActivity(new Intent(AdminMainActivity.this, AdminSearchDecksActivity.class)));

        binding.btnMenuCreateDeck.setOnClickListener(v -> {
            Intent intent = new Intent(AdminMainActivity.this, CreateDeckActivity.class);
            intent.putExtra("USER_ROLE", "admin");
            intent.putExtra("IS_EDIT_MODE", false);
            startActivity(intent);
        });

        // Click mở màn hình Sửa bộ thẻ hệ thống đã làm đồng bộ màu xanh đen với bên User
        binding.btnMenuGlobalDecks.setOnClickListener(v ->
                startActivity(new Intent(AdminMainActivity.this, AdminDecksActivity.class)));

        binding.btnMenuUserList.setOnClickListener(v ->
                startActivity(new Intent(AdminMainActivity.this, AdminUsersActivity.class)));

        // 🌟 ĐÃ ĐỒNG BỘ ĐÚNG ID XML: Đấu nối sự kiện click cụm Avatar nhảy sang Profile dùng chung kèm cờ định danh "admin"
        if (binding.cardAdminAvatarWrapper != null) {
            binding.cardAdminAvatarWrapper.setOnClickListener(v -> {
                Intent intent = new Intent(AdminMainActivity.this, ProfileActivity.class);
                intent.putExtra("USER_ROLE", "admin");
                startActivity(intent);
            });
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void startRealtimeDashboardListening() {
        // 🌟 LUỒNG 1: THỐNG KÊ XU HƯỚNG REALTIME - TOP 3 Ô CARD ĐỘC LẬP CHẠY NGẦM
        trendingListener = mFirestore.collection("personal_decks")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || binding == null) return;

                    if (snapshots != null && !snapshots.isEmpty()) {
                        new Thread(() -> {
                            final java.util.Map<String, Integer> deckCountMap = new java.util.HashMap<>();

                            for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots.getDocuments()) {
                                String title = doc.getString("title");
                                String author = doc.getString("author");

                                if (title != null && !title.trim().isEmpty() && "Hệ thống".equals(author)) {
                                    int currentDocCount = 1;
                                    Object studyCountObj = doc.get("studyCount");
                                    if (studyCountObj instanceof Long) {
                                        currentDocCount = ((Long) studyCountObj).intValue();
                                    } else if (studyCountObj instanceof Integer) {
                                        currentDocCount = (Integer) studyCountObj;
                                    }
                                    deckCountMap.put(title, deckCountMap.getOrDefault(title, 0) + currentDocCount);
                                }
                            }

                            List<Map.Entry<String, Integer>> fullSortedList = new ArrayList<>(deckCountMap.entrySet());
                            Collections.sort(fullSortedList, (o1, o2) -> o2.getValue().compareTo(o1.getValue()));

                            final List<Map.Entry<String, Integer>> top3List = new ArrayList<>();
                            int limit = Math.min(3, fullSortedList.size());
                            for (int i = 0; i < limit; i++) {
                                top3List.add(fullSortedList.get(i));
                            }

                            runOnUiThread(() -> {
                                if (binding != null) {
                                    trendingDecksList.clear();
                                    trendingDecksList.addAll(top3List);
                                    trendingDecksAdapter.notifyDataSetChanged();
                                }
                            });
                        }).start();

                    } else {
                        runOnUiThread(() -> {
                            if (binding != null) {
                                trendingDecksList.clear();
                                trendingDecksAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                });

        // 🌟 LUỒNG 2: DANH SÁCH HỌC VIÊN HOẠT ĐỘNG REALTIME TRONG NGÀY
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long startOfToday = calendar.getTimeInMillis();

        activeUsersListener = mFirestore.collection("personal_decks")
                .whereGreaterThanOrEqualTo("lastModified", startOfToday)
                .addSnapshotListener((deckSnapshots, error) -> {
                    if (error != null || binding == null || deckSnapshots == null) return;

                    Map<String, Long> userLastActiveMap = new HashMap<>();
                    List<String> activeUserIds = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : deckSnapshots) {
                        String uId = doc.getString("userId");
                        Long lastTime = doc.getLong("lastModified");
                        if (uId != null && lastTime != null) {
                            if (!userLastActiveMap.containsKey(uId) || lastTime > userLastActiveMap.get(uId)) {
                                userLastActiveMap.put(uId, lastTime);
                            }
                            if (!activeUserIds.contains(uId)) {
                                activeUserIds.add(uId);
                            }
                        }
                    }

                    if (!activeUserIds.isEmpty()) {
                        mFirestore.collection("users").get().addOnSuccessListener(userSnapshots -> {
                            if (binding == null || userSnapshots == null) return;
                            activeUsersDashboardList.clear();

                            for (String currentActiveUid : activeUserIds) {
                                for (QueryDocumentSnapshot userDoc : userSnapshots) {
                                    if (currentActiveUid.equals(userDoc.getId())) {
                                        Map<String, Object> userData = userDoc.getData();

                                        long lastActiveTime = userLastActiveMap.containsKey(currentActiveUid) ? userLastActiveMap.get(currentActiveUid) : 0L;
                                        userData.put("user_last_modified_time", lastActiveTime);

                                        activeUsersDashboardList.add(userData);
                                        break;
                                    }
                                }
                            }
                            userDashboardAdapter.notifyDataSetChanged();
                        });
                    } else {
                        activeUsersDashboardList.clear();
                        userDashboardAdapter.notifyDataSetChanged();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (trendingListener != null) trendingListener.remove();
        if (activeUsersListener != null) activeUsersListener.remove();
    }

    // ADAPTER 1: Vẽ giao diện 3 ô Card Xu hướng độc lập kèm viên thuốc Capsule
    private static class AdminTrendingAdapter extends RecyclerView.Adapter<AdminTrendingAdapter.ViewHolder> {
        private final List<Map.Entry<String, Integer>> list;

        public AdminTrendingAdapter(List<Map.Entry<String, Integer>> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_trending, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map.Entry<String, Integer> entry = list.get(position);
            holder.txtTitle.setText((position + 1) + ". " + entry.getKey());
            holder.txtCapsule.setText("Tổng số: " + entry.getValue() + " lượt học");
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtTitle, txtCapsule;

            ViewHolder(View iv) {
                super(iv);
                txtTitle = iv.findViewById(R.id.txtTrendingDeckTitle);
                txtCapsule = iv.findViewById(R.id.txtTrendingDeckCountCapsule);
            }
        }
    }

    // ADAPTER 2: Quản lý luồng chấm đèn trạng thái hoạt động trực tuyến của Học viên
    private static class ActiveUserHomeStyleAdapter extends RecyclerView.Adapter<ActiveUserHomeStyleAdapter.ViewHolder> {
        private final List<Map<String, Object>> list;

        public ActiveUserHomeStyleAdapter(List<Map<String, Object>> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_deck, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> u = list.get(position);
            String name = u.containsKey("name") ? String.valueOf(u.get("name")) : "Học viên";

            holder.txtTitle.setText(name.equals("null") ? "Học viên ẩn danh" : name);

            long lastTime = u.containsKey("user_last_modified_time") ? (long) u.get("user_last_modified_time") : 0L;
            long now = System.currentTimeMillis();

            holder.txtSubtitle.setCompoundDrawablePadding(16);

            if (now - lastTime <= 180000) {
                holder.txtSubtitle.setText("Đang hoạt động");
                holder.txtSubtitle.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                holder.txtSubtitle.setCompoundDrawablesWithIntrinsicBounds(R.drawable.bg_dot_online, 0, 0, 0);
            } else {
                holder.txtSubtitle.setText("Không hoạt động");
                holder.txtSubtitle.setTextColor(android.graphics.Color.parseColor("#9E9E9E"));
                holder.txtSubtitle.setCompoundDrawablesWithIntrinsicBounds(R.drawable.bg_dot_offline, 0, 0, 0);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtTitle, txtSubtitle;

            ViewHolder(View iv) {
                super(iv);
                txtTitle = iv.findViewById(R.id.txtItemDeckTitle);
                txtSubtitle = iv.findViewById(R.id.txtItemDeckSubtitle);
            }
        }
    }
}