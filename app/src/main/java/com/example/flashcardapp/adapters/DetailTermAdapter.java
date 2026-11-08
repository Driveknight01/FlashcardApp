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

public class DetailTermAdapter extends RecyclerView.Adapter<DetailTermAdapter.ViewHolder> {

    private final List<Map<String, String>> termList;

    public DetailTermAdapter(List<Map<String, String>> termList) {
        this.termList = termList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_detail_term, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, String> card = termList.get(position);
        if (card != null) {
            String term = card.get("term");
            String definition = card.get("definition");

            holder.txtTerm.setText(term != null ? term : "");
            holder.txtDefinition.setText(definition != null ? definition : "");
        }
    }

    @Override
    public int getItemCount() {
        return termList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtTerm, txtDefinition;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTerm = itemView.findViewById(R.id.txtDetailTermText);
            txtDefinition = itemView.findViewById(R.id.txtDetailDefText);
        }
    }
}
