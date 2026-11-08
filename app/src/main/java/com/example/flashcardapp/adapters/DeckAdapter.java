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

    public interface OnItemClickListener {
        void onItemClick(Deck deck);
    }

    public DeckAdapter(List<Deck> deckList, OnItemClickListener listener) {
        this.deckList = deckList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DeckViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDeckBinding binding = ItemDeckBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new DeckViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DeckViewHolder holder, int position) {
        Deck deck = deckList.get(position);
        holder.bind(deck, listener);
    }

    @Override
    public int getItemCount() {
        return deckList != null ? deckList.size() : 0;
    }

    static class DeckViewHolder extends RecyclerView.ViewHolder {
        private final ItemDeckBinding binding;

        public DeckViewHolder(@NonNull ItemDeckBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(final Deck deck, final OnItemClickListener listener) {
            binding.txtDeckTitle.setText(deck.getTitle());
            binding.txtDeckDescription.setText(deck.getDescription());

            int totalCards = deck.getTotalCount();
            int learnedCards = deck.getLearnedCount();

            binding.txtProgressCount.setText(learnedCards + "/" + totalCards + " thẻ");

            // Calculate learning completion percentage
            if (totalCards > 0) {
                int progressPercent = (learnedCards * 100) / totalCards;
                binding.progressLearning.setProgress(progressPercent);
            } else {
                binding.progressLearning.setProgress(0);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(deck);
                }
            });
        }
    }
}
