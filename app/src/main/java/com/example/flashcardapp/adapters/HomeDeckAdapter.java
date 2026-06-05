package com.example.flashcardapp.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.flashcardapp.R;
import java.util.List;
import java.util.Map;

public class HomeDeckAdapter extends RecyclerView.Adapter<HomeDeckAdapter.ViewHolder> {

    private final List<Map<String, Object>> deckList;

    public interface OnDeckClickListener {
        void onDeckClick(Map<String, Object> deckData);
    }

    private final OnDeckClickListener clickListener;

    public HomeDeckAdapter(List<Map<String, Object>> deckList, OnDeckClickListener clickListener) {
        this.deckList = deckList;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_deck, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> data = deckList.get(position);
        if (data != null) {
            String title = (String) data.get("title");

            // 🌟 ĐÃ FIX: Lấy nhãn tác giả động dựa theo nguồn bài nạp vào
            String author = data.containsKey("author") ? (String) data.get("author") : "bạn";

            int cardCount = 0;
            if (data.get("cards") instanceof List) {
                cardCount = ((List<?>) data.get("cards")).size();
            }

            holder.txtTitle.setText(title != null && !title.isEmpty() ? title : "Chưa đặt tiêu đề");
            holder.txtSubtitle.setText("Đã lưu • " + cardCount + " thuật ngữ • Tác giả: " + author);

            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onDeckClick(data);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return deckList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtSubtitle;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtItemDeckTitle);
            txtSubtitle = itemView.findViewById(R.id.txtItemDeckSubtitle);
        }
    }
}