package org.example.app1;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Mp3Adapter extends RecyclerView.Adapter<Mp3Adapter.ViewHolder> {
    private final List<Mp3Item> items = new ArrayList<>();
    private final Set<String> selectedUris = new HashSet<>();
    private SelectionChangedListener selectionChangedListener;
    private SelectionModeListener selectionModeListener;
    private boolean selectionMode;

    public interface SelectionChangedListener {
        void onSelectionChanged(int count);
    }

    public interface SelectionModeListener {
        void onRequestSelectionMode();
    }

    public Mp3Adapter() {}

    public void setSelectionListeners(
        SelectionChangedListener selectionChangedListener,
        SelectionModeListener selectionModeListener
    ) {
        this.selectionChangedListener = selectionChangedListener;
        this.selectionModeListener = selectionModeListener;
    }

    public void setItems(List<Mp3Item> mp3Items) {
        items.clear();
        items.addAll(mp3Items);
        selectedUris.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public boolean hasSelection() {
        return !selectedUris.isEmpty();
    }

    public List<Mp3Item> getSelectedItems() {
        List<Mp3Item> selected = new ArrayList<>();
        for (Mp3Item item : items) {
            if (item != null && selectedUris.contains(item.getContentUri().toString())) {
                selected.add(item);
            }
        }
        return selected;
    }

    public void setSelectionMode(boolean enabled) {
        if (selectionMode == enabled) {
            return;
        }
        selectionMode = enabled;
        if (!selectionMode) {
            selectedUris.clear();
            notifySelectionChanged();
        }
        notifyDataSetChanged();
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
        holder.sizeText.setText(formatSize(item.getSize()));
        String uriKey = item.getContentUri().toString();
        boolean selected = selectedUris.contains(uriKey);
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(selected);
        holder.checkBox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.itemView.setSelected(selected);
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                selectedUris.add(uriKey);
            } else {
                selectedUris.remove(uriKey);
            }
            notifySelectionChanged();
        });
        holder.itemView.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) {
                return;
            }
            if (selectionMode) {
                toggleSelection(adapterPosition);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) {
                return true;
            }
            if (!selectionMode && selectionModeListener != null) {
                selectionModeListener.onRequestSelectionMode();
            }
            toggleSelection(adapterPosition);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox checkBox;
        private final TextView nameText;
        private final TextView sizeText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.mp3SelectCheckBox);
            nameText = itemView.findViewById(R.id.mp3NameText);
            sizeText = itemView.findViewById(R.id.mp3SizeText);
        }
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        double kb = sizeBytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format(Locale.US, "%.1f MB", mb);
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedUris.size());
        }
    }

    private void toggleSelection(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        Mp3Item item = items.get(position);
        if (item == null) {
            return;
        }
        String uriKey = item.getContentUri().toString();
        if (selectedUris.contains(uriKey)) {
            selectedUris.remove(uriKey);
        } else {
            selectedUris.add(uriKey);
        }
        notifyItemChanged(position);
        notifySelectionChanged();
    }
}
