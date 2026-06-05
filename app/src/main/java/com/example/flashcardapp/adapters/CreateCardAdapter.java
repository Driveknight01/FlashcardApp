package com.example.flashcardapp.adapters;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.flashcardapp.databinding.ItemCreateCardBinding;
import com.example.flashcardapp.models.Flashcard;
import java.util.List;

public class CreateCardAdapter extends RecyclerView.Adapter<CreateCardAdapter.CardInputViewHolder> {

    private final List<Flashcard> cardList;
    private OnCardFocusChangeListener focusListener;

    // Interface truyền thông tin ô đang focus ra ngoài Activity
    public interface OnCardFocusChangeListener {
        void onFocusChanged(int position, boolean isTerm);
    }

    public CreateCardAdapter(List<Flashcard> cardList, OnCardFocusChangeListener focusListener) {
        this.cardList = cardList;
        this.focusListener = focusListener;
    }

    @NonNull
    @Override
    public CardInputViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCreateCardBinding binding = ItemCreateCardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CardInputViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CardInputViewHolder holder, int position) {
        holder.bind(cardList.get(position), position, focusListener);
    }

    @Override
    public int getItemCount() {
        return cardList != null ? cardList.size() : 0;
    }

    static class CardInputViewHolder extends RecyclerView.ViewHolder {
        private final ItemCreateCardBinding binding;
        private TextWatcher termWatcher;
        private TextWatcher defWatcher;

        public CardInputViewHolder(@NonNull ItemCreateCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(final Flashcard card, int position, OnCardFocusChangeListener focusListener) {
            binding.edtTerm.removeTextChangedListener(termWatcher);
            binding.edtDefinition.removeTextChangedListener(defWatcher);

            binding.edtTerm.setText(card.getTerm());
            binding.edtDefinition.setText(card.getDefinition());

            // Ghi nhận vị trí khi người dùng nhấn chọn vào ô Thuật ngữ
            binding.edtTerm.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && focusListener != null) {
                    focusListener.onFocusChanged(position, true);
                }
            });

            // Ghi nhận vị trí khi người dùng nhấn chọn vào ô Định nghĩa
            binding.edtDefinition.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && focusListener != null) {
                    focusListener.onFocusChanged(position, false);
                }
            });

            termWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { card.setTerm(s.toString()); }
            };
            binding.edtTerm.addTextChangedListener(termWatcher);

            defWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { card.setDefinition(s.toString()); }
            };
            binding.edtDefinition.addTextChangedListener(defWatcher);
        }
    }
}