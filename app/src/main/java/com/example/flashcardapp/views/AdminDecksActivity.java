package com.example.flashcardapp.views;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.flashcardapp.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminDecksActivity extends AppCompatActivity {

    private FirebaseFirestore mFirestore;
    private final List<DocumentSnapshot> globalDecksList = new ArrayList<>();
    private final Map<String, Integer> globalStudyCountMap = new HashMap<>();
    private GlobalDecksAdapter adapter;

    private ListenerRegistration personalDecksListener;
    private ListenerRegistration globalDecksListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_decks);

        mFirestore = FirebaseFirestore.getInstance();

        findViewById(R.id.btnBackToMainAdmin).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvAdminGlobalDecksList);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GlobalDecksAdapter(globalDecksList, globalStudyCountMap, this::executeGlobalDeleteFlow);
        rv.setAdapter(adapter);

        loadGlobalAndCalculatedCountsRealtime();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadGlobalAndCalculatedCountsRealtime() {
        // Aggregate study counts across personal decks created from system decks
        personalDecksListener = mFirestore.collection("personal_decks")
                .addSnapshotListener((personalSnapshots, pError) -> {
                    if (personalSnapshots == null) return;

                    globalStudyCountMap.clear();
                    for (DocumentSnapshot doc : personalSnapshots.getDocuments()) {
                        String title = doc.getString("title");
                        String author = doc.getString("author");

                        if (title != null && "Hệ thống".equals(author)) {
                            int count = 0;
                            Object studyCountObj = doc.get("studyCount");
                            if (studyCountObj instanceof Number) {
                                count = ((Number) studyCountObj).intValue();
                            }
                            globalStudyCountMap.put(title, globalStudyCountMap.getOrDefault(title, 0) + count);
                        }
                    }
                    if (adapter != null) adapter.notifyDataSetChanged();
                });

        // Listen for global deck changes and keep items sorted by lastModified descending
        globalDecksListener = mFirestore.collection("global_decks")
                .addSnapshotListener((globalSnapshots, gError) -> {
                    if (globalSnapshots == null) return;

                    List<DocumentSnapshot> docs = new ArrayList<>(globalSnapshots.getDocuments());

                    Collections.sort(docs, (d1, d2) -> {
                        long t1 = d1.contains("lastModified") && d1.get("lastModified") != null ? d1.getLong("lastModified") : 0L;
                        long t2 = d2.contains("lastModified") && d2.get("lastModified") != null ? d2.getLong("lastModified") : 0L;
                        return Long.compare(t2, t1);
                    });

                    globalDecksList.clear();
                    globalDecksList.addAll(docs);
                    adapter.notifyDataSetChanged();
                });
    }

    // Cascades deletion of a global deck and all associated personal deck progress
    private void executeGlobalDeleteFlow(String deckId, String deckTitle) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa bộ thẻ hệ thống?")
                .setMessage("Bạn có chắc chắn muốn xóa vĩnh viễn bộ thẻ \"" + deckTitle + "\" không?\n\nThao tác này sẽ xóa  toàn bộ tiến trình học tập của tất cả người dùng.")
                .setPositiveButton("Xóa vĩnh viễn", (dialog, which) -> {

                    // 1. Delete source document from global_decks
                    mFirestore.collection("global_decks").document(deckId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {

                                // 2. Batch-delete related personal decks and progress records
                                mFirestore.collection("personal_decks").get()
                                        .addOnSuccessListener(snapshots -> {
                                            WriteBatch batch = mFirestore.batch();
                                            int countCleaned = 0;

                                            for (QueryDocumentSnapshot doc : snapshots) {
                                                String docId = doc.getId();
                                                // Matches copies cloned as UID_deckId or direct ID references
                                                if (docId.endsWith("_" + deckId) || docId.equals(deckId)) {
                                                    batch.delete(doc.getReference());
                                                    countCleaned++;
                                                }
                                            }

                                            if (countCleaned > 0) {
                                                batch.commit().addOnSuccessListener(aVoid2 ->
                                                        Log.d("BATCH_CLEAN", "Đã dọn dẹp tiến trình của người dùng."));
                                            }

                                            Toast.makeText(AdminDecksActivity.this, "Đã xóa bộ thẻ hệ thống thành công!", Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e -> Toast.makeText(AdminDecksActivity.this, "Lỗi Firebase: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private static class GlobalDecksAdapter extends RecyclerView.Adapter<GlobalDecksAdapter.ViewHolder> {
        private final List<DocumentSnapshot> list;
        private final Map<String, Integer> countMap;
        private final OnDeckLongClickListener longClickListener;

        public interface OnDeckLongClickListener {
            void onDeckLongClick(String deckId, String deckTitle);
        }

        public GlobalDecksAdapter(List<DocumentSnapshot> list, Map<String, Integer> countMap, OnDeckLongClickListener longClickListener) {
            this.list = list;
            this.countMap = countMap;
            this.longClickListener = longClickListener;
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

            int totalStudyCount = 0;
            if (title != null && countMap.containsKey(title)) {
                totalStudyCount = countMap.get(title);
            }

            holder.txtTitle.setText(title != null ? title : "Chưa đặt tên học phần");
            holder.txtCount.setText(cardSize + " thuật ngữ  •  " + totalStudyCount + " lượt học");

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), CreateDeckActivity.class);
                intent.putExtra("DECK_ID", doc.getId());
                intent.putExtra("USER_ROLE", "admin");
                intent.putExtra("IS_EDIT_MODE", true);
                v.getContext().startActivity(intent);
            });

            // Long-click triggers confirmation dialog for deck deletion
            holder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null && title != null) {
                    longClickListener.onDeckLongClick(doc.getId(), title);
                    return true;
                }
                return false;
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (personalDecksListener != null) personalDecksListener.remove();
        if (globalDecksListener != null) globalDecksListener.remove();
    }
}
