package com.example.flashcardapp.views;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.flashcardapp.R;
import com.example.flashcardapp.databinding.ActivityAdminDecksBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminDecksActivity extends AppCompatActivity {

    private ActivityAdminDecksBinding binding;
    private FirebaseFirestore mFirestore;
    private final List<Map<String, Object>> globalDecksList = new ArrayList<>();
    private DeckListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminDecksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();

        // Cấu hình Toolbar có nút Back quay lại trang chủ Admin
        binding.toolbarAdminDecks.setNavigationOnClickListener(v -> finish());

        // Cấu hình RecyclerView hiển thị danh sách dọc phẳng
        binding.rvAdminDecksList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeckListAdapter(globalDecksList);
        binding.rvAdminDecksList.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAdminGlobalDecks(); // Tự động làm mới dữ liệu realtime khi quay lại màn hình
    }

    // Tải danh sách bộ thẻ từ collection global_decks
    private void loadAdminGlobalDecks() {
        mFirestore.collection("global_decks").get().addOnSuccessListener(snapshots -> {
            globalDecksList.clear();
            for (QueryDocumentSnapshot doc : snapshots) {
                Map<String, Object> data = doc.getData();
                data.put("docId", doc.getId());
                globalDecksList.add(data);
            }
            adapter.notifyDataSetChanged();
        });
    }

    // Bật Dialog hỏi Admin muốn Sửa nội dung hay Xóa hẳn học phần
    private void showActionDialog(Map<String, Object> deck) {
        String docId = String.valueOf(deck.get("docId"));
        String title = String.valueOf(deck.get("title"));

        String[] options = {"✏️ Biên tập nội dung học phần", "❌ Xóa học phần này"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Kích hoạt CreateDeckActivity ở chế độ Sửa (Edit Mode)
                        Intent intent = new Intent(AdminDecksActivity.this, CreateDeckActivity.class);
                        intent.putExtra("USER_ROLE", "admin");
                        intent.putExtra("IS_EDIT_MODE", true);
                        intent.putExtra("DECK_ID", docId);
                        intent.putExtra("DECK_TITLE", title);
                        intent.putExtra("DECK_CARDS_JSON", new com.google.gson.Gson().toJson(deck.get("cards")));
                        startActivity(intent);
                    } else {
                        // Xóa hoàn toàn tài liệu trên Firestore
                        mFirestore.collection("global_decks").document(docId).delete()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Đã xóa học phần hệ thống thành công!", Toast.LENGTH_SHORT).show();
                                    loadAdminGlobalDecks();
                                });
                    }
                }).show();
    }

    // --- ADAPTER DANH SÁCH DỌC (Dùng chung layout item_admin_global_deck đã làm phẳng) ---
    private class DeckListAdapter extends RecyclerView.Adapter<DeckListAdapter.ViewHolder> {
        private final List<Map<String, Object>> list;
        public DeckListAdapter(List<Map<String, Object>> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_global_deck, parent, false);
            // Ép lại kích thước match_parent chiều rộng để hiển thị danh sách dọc chuẩn chỉnh
            v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> deck = list.get(position);
            holder.txtTitle.setText(String.valueOf(deck.get("title")));
            List<?> cards = (List<?>) deck.get("cards");
            holder.txtCount.setText((cards != null ? cards.size() : 0) + " thuật ngữ");

            holder.itemView.setOnClickListener(v -> showActionDialog(deck));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtTitle, txtCount;
            ViewHolder(View iv) {
                super(iv);
                txtTitle = iv.findViewById(R.id.txtGlobalDeckTitle);
                txtCount = iv.findViewById(R.id.txtGlobalCardCount);
            }
        }
    }
}