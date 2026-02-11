package org.example.app1;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class Mp3Adapter extends RecyclerView.Adapter<Mp3Adapter.ViewHolder> {

    public interface SelectionChangedListener {
        void onSelectionChanged(int count);
    }

    private final List<Mp3Item> items = new ArrayList<>();
    private final SelectionChangedListener selectionChangedListener;

    public Mp3Adapter(SelectionChangedListener selectionChangedListener) {
        this.selectionChangedListener = selectionChangedListener;
    }

    public void setItems(List<Mp3Item> mp3Items) {
        items.clear();
        items.addAll(mp3Items);
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public List<Mp3Item> getSelectedItems() {
        List<Mp3Item> selected = new ArrayList<>();
        for (Mp3Item item : items) {
            if (item.isSelected()) {
                selected.add(item);
            }
        }
        return selected;
    }

    public void clearSelection() {
        for (Mp3Item item : items) {
            item.setSelected(false);
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(getSelectedItems().size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_mp3, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Mp3Item item = items.get(position);
        holder.nameText.setText(item.getDisplayName());
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(item.isSelected());
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setSelected(isChecked);
            notifySelectionChanged();
        });
        holder.itemView.setOnClickListener(v -> {
            holder.checkBox.setChecked(!item.isSelected());
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox checkBox;
        private final TextView nameText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.selectCheckBox);
            nameText = itemView.findViewById(R.id.mp3NameText);
        }
    }
}
