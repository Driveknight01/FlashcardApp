package com.example.flashcardapp.views;

import android.annotation.SuppressLint;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminViewUserDecksActivity extends AppCompatActivity {

    private FirebaseFirestore mFirestore;
    private final List<DocumentSnapshot> userDecksList = new ArrayList<>();
    private UserDecksAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_view_user_decks);

        mFirestore = FirebaseFirestore.getInstance();

        findViewById(R.id.btnBackFromUserDecks).setOnClickListener(v -> finish());

        // Retrieve target user UID and name from intent extras
        String userUid = getIntent().getStringExtra("TARGET_USER_UID");
        String userName = getIntent().getStringExtra("TARGET_USER_NAME");

        TextView txtHeaderTitle = findViewById(R.id.txtHeaderTitleUserDecks);
        if (userName != null) {
            txtHeaderTitle.setText("Kho thẻ của: " + userName);
        }

        RecyclerView rv = findViewById(R.id.rvAdminUserDecksList);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserDecksAdapter(userDecksList);
        rv.setAdapter(adapter);

        if (userUid != null) {
            loadUserDecksRealtime(userUid);
        }
    }

    private void loadUserDecksRealtime(String uid) {
        // Query personal decks for the target user
        mFirestore.collection("personal_decks")
                .whereEqualTo("userId", uid)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Lỗi nạp dữ liệu: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshots != null) {
                        userDecksList.clear();

                        // Exclude decks cloned from system templates
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            String author = doc.getString("author");
                            if (!"Hệ thống".equals(author)) {
                                userDecksList.add(doc);
                            }
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    // Adapter for displaying user decks in admin inspector
    private static class UserDecksAdapter extends RecyclerView.Adapter<UserDecksAdapter.ViewHolder> {
        private final List<DocumentSnapshot> list;

        public UserDecksAdapter(List<DocumentSnapshot> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_global_deck, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DocumentSnapshot doc = list.get(position);
            String title = doc.getString("title");

            List<?> cards = (List<?>) doc.get("cards");
            int cardSize = cards != null ? cards.size() : 0;

            int studyCount = 0;
            Object studyCountObj = doc.get("studyCount");
            if (studyCountObj instanceof Number) {
                studyCount = ((Number) studyCountObj).intValue();
            }

            holder.txtTitle.setText(title != null ? title : "Chưa đặt tên học phần");
            holder.txtCount.setText(cardSize + " thuật ngữ  •  " + studyCount + " lượt học");

            // Show dialog previewing flashcard contents
            holder.itemView.setOnClickListener(v -> {
                if (cards == null || cards.isEmpty()) {
                    Toast.makeText(v.getContext(), "Bộ học phần này trống, chưa có thuật ngữ nào!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Format card objects as readable preview text
                StringBuilder textBuilder = new StringBuilder();
                for (Object item : cards) {
                    if (item instanceof Map) {
                        Map<?, ?> cardMap = (Map<?, ?>) item;
                        String term = String.valueOf(cardMap.get("term"));
                        String definition = String.valueOf(cardMap.get("definition"));

                        textBuilder.append("• ").append(term).append(" : ").append(definition).append("\n\n");
                    }
                }

                new MaterialAlertDialogBuilder(v.getContext())
                        .setTitle("Bộ thẻ: " + (title != null ? title : ""))
                        .setMessage(textBuilder.toString().trim())
                        .setPositiveButton("    Đóng", (dialog, which) -> dialog.dismiss())
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtTitle, txtCount;

            ViewHolder(View iv) {
                super(iv);
                txtTitle = iv.findViewById(R.id.txtAdminDeckTitle);
                txtCount = iv.findViewById(R.id.txtAdminCardCount);
            }
        }
    }
}
