package com.example.flashcardapp.views;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class AdminViewUserDecksActivity extends AppCompatActivity {

    private FirebaseFirestore mFirestore;
    private final List<String> deckTitles = new ArrayList<>();
    private ArrayAdapter<String> listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ListView listView = new ListView(this);
        setContentView(listView);

        mFirestore = FirebaseFirestore.getInstance();
        String targetUid = getIntent().getStringExtra("TARGET_USER_UID");
        String targetName = getIntent().getStringExtra("TARGET_USER_NAME");

        setTitle("Bộ thẻ của: " + targetName);

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deckTitles);
        listView.setAdapter(listAdapter);

        if (targetUid != null) {
            mFirestore.collection("personal_decks")
                    .whereEqualTo("userId", targetUid)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        deckTitles.clear();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String title = doc.getString("title");
                            List<?> cards = (List<?>) doc.get("cards");
                            int cardCount = cards != null ? cards.size() : 0;

                            deckTitles.add("📘 " + title + " (" + cardCount + " thẻ)");
                        }
                        if (deckTitles.isEmpty()) {
                            deckTitles.add("Người dùng này chưa tạo bộ thẻ nào.");
                        }
                        listAdapter.notifyDataSetChanged();
                    });
        }
    }
}