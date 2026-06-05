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
import java.util.List;
import java.util.Map;

public class AdminSearchDecksActivity extends AppCompatActivity {

    private ActivityAdminSearchDecksBinding binding;
    private FirebaseFirestore mFirestore;

    private final List<Map<String, Object>> allDecksList = new ArrayList<>();
    private final List<Map<String, Object>> filteredList = new ArrayList<>();

    private SuggestionAdapter suggestionAdapter;
    private FinalCardAdapter finalCardAdapter;

    private boolean isDisplayingFinalCard = false; // Cờ kiểm soát trạng thái hiển thị

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminSearchDecksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mFirestore = FirebaseFirestore.getInstance();

        // Cấu hình các nút chức năng đầu trang
        binding.btnSearchBack.setOnClickListener(v -> finish());
        binding.btnClearSearchText.setOnClickListener(v -> {
            binding.edtRealSearch.setText("");
            switchToSuggestionState();
        });

        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(this));

        // Khởi tạo sẵn 2 bộ Adapter phân tầng
        suggestionAdapter = new SuggestionAdapter(filteredList);
        finalCardAdapter = new FinalCardAdapter(filteredList);

        binding.rvSearchResults.setAdapter(suggestionAdapter); // Mặc định dùng tầng gợi ý chữ

        preloadGlobalDecks();

        // 🎯 LOGIC TÌM KIẾM PHÂN TẦNG REALTIME NÂNG CAO
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

                    // Nếu người dùng gõ thêm chữ khi đang xem thẻ bài -> Trả lại tầng gợi ý chữ thô
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

    private void preloadGlobalDecks() {
        mFirestore.collection("global_decks").get().addOnSuccessListener(snapshots -> {
            if (snapshots == null) return;
            allDecksList.clear();
            for (QueryDocumentSnapshot doc : snapshots) {
                Map<String, Object> data = doc.getData();
                data.put("docId", doc.getId());
                allDecksList.add(data);
            }
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

    // Luồng xử lý ẩn bàn phím và kích hoạt vẽ Khối thẻ bài chi tiết
    private void onSuggestionClicked(Map<String, Object> selectedDeck) {
        isDisplayingFinalCard = true;

        // 1. Ẩn bàn phím điện thoại xuống
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }

        // 2. Thay đổi nội dung ô nhập chữ bằng đúng tên bộ thẻ được chọn
        binding.edtRealSearch.removeTextChangedListener(null); // Tạm dừng lắng nghe để tránh lặp vô hạn
        binding.edtRealSearch.setText(String.valueOf(selectedDeck.get("title")));

        // 3. Cô lập mảng dữ liệu, chỉ giữ lại đúng 1 bộ thẻ được click chọn
        filteredList.clear();
        filteredList.add(selectedDeck);

        // 4. Tráo đổi adapter sang tầng vẽ Khối thẻ bài Doanh nghiệp
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

        String[] options = {"✏️ Biên tập nội dung học phần", "❌ Xóa học phần này"};
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

    // ================= TẦNG 1: ADAPTER CHỈ HIỂN THỊ DÒNG CHỮ GỢI Ý =================
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
            // Bấm vào dòng chữ -> Đổi trạng thái hiển thị khối thẻ bài
            holder.itemView.setOnClickListener(v -> onSuggestionClicked(deck));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtSuggest;
            ViewHolder(View iv) { super(iv); txtSuggest = iv.findViewById(R.id.txtSuggestionText); }
        }
    }

    // ================= TẦNG 2: ADAPTER HIỂN THỊ KHỐI THẺ BÀI CHI TIẾT SỬA/XÓA =================
    private class FinalCardAdapter extends RecyclerView.Adapter<FinalCardAdapter.ViewHolder> {
        private final List<Map<String, Object>> list;
        public FinalCardAdapter(List<Map<String, Object>> list) { this.list = list; }

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