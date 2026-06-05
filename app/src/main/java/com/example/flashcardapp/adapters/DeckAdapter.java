package com.example.flashcardapp.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.flashcardapp.databinding.ItemDeckBinding;
import com.example.flashcardapp.models.Deck;
import java.util.List;

public class DeckAdapter extends RecyclerView.Adapter<DeckAdapter.DeckViewHolder> {

    private List<Deck> deckList;
    private OnItemClickListener listener;

    // Interface để xử lý sự kiện khi người dùng click vào một Bộ thẻ để vào học
    public interface OnItemClickListener {
        void onItemClick(Deck deck);
    }

    // Constructor của Adapter
    public DeckAdapter(List<Deck> deckList, OnItemClickListener listener) {
        this.deckList = deckList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DeckViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Sử dụng View Binding để inflate (nạp) giao diện item_deck.xml
        ItemDeckBinding binding = ItemDeckBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new DeckViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DeckViewHolder holder, int position) {
        // Lấy ra đối tượng Bộ thẻ tại vị trí hiện tại trong danh sách
        Deck deck = deckList.get(position);
        holder.bind(deck, listener);
    }

    @Override
    public int getItemCount() {
        return deckList != null ? deckList.size() : 0;
    }

    // Lớp ViewHolder giữ các thành phần giao diện của từng dòng
    static class DeckViewHolder extends RecyclerView.ViewHolder {
        private final ItemDeckBinding binding;

        public DeckViewHolder(@NonNull ItemDeckBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(final Deck deck, final OnItemClickListener listener) {
            // 1. Đổ dữ liệu chữ vào Tiêu đề và Mô tả
            binding.txtDeckTitle.setText(deck.getTitle());
            binding.txtDeckDescription.setText(deck.getDescription());

            // 2. Lấy số liệu tiến trình học từ các hàm tiện ích đã viết ở lớp Deck
            int totalCards = deck.getTotalCount();
            int learnedCards = deck.getLearnedCount();

            // 3. Hiển thị text trạng thái dạng: "X/Y thẻ"
            binding.txtProgressCount.setText(learnedCards + "/" + totalCards + " thẻ");

            // 4. Tính toán phần trăm (%) để đẩy vào thanh ProgressBar
            if (totalCards > 0) {
                // Công thức: (Số thẻ thuộc * 100) / Tổng số thẻ
                int progressPercent = (learnedCards * 100) / totalCards;
                binding.progressLearning.setProgress(progressPercent);
            } else {
                // Nếu bộ thẻ trống rỗng, set tiến trình về 0%
                binding.progressLearning.setProgress(0);
            }

            // 5. Bắt sự kiện khi người dùng click vào toàn bộ ô Thẻ này để chuyển màn hình học
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(deck);
                }
            });
        }
    }
}