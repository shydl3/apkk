package org.example.app1;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DocumentFileAdapter extends RecyclerView.Adapter<DocumentFileAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(DocumentFile file);
    }

    public interface SelectionChangedListener {
        void onSelectionChanged(int count);
    }

    public interface SelectionModeListener {
        void onRequestSelectionMode();
    }

    private final List<DocumentFile> items = new ArrayList<>();
    private final Set<String> selectedUris = new HashSet<>();
    private final OnItemClickListener listener;
    private final SelectionChangedListener selectionChangedListener;
    private final SelectionModeListener selectionModeListener;
    private boolean selectionMode;

    public DocumentFileAdapter(
        OnItemClickListener listener,
        SelectionChangedListener selectionChangedListener,
        SelectionModeListener selectionModeListener
    ) {
        this.listener = listener;
        this.selectionChangedListener = selectionChangedListener;
        this.selectionModeListener = selectionModeListener;
    }

    public void setItems(List<DocumentFile> files) {
        items.clear();
        items.addAll(files);
        selectedUris.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public List<DocumentFile> getSelectedItems() {
        List<DocumentFile> selected = new ArrayList<>();
        for (DocumentFile file : items) {
            if (file != null && selectedUris.contains(file.getUri().toString())) {
                selected.add(file);
            }
        }
        return selected;
    }

    public boolean hasSelection() {
        return !selectedUris.isEmpty();
    }

    public boolean isSelectionMode() {
        return selectionMode;
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
            .inflate(R.layout.item_document_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DocumentFile file = items.get(position);
        String name = file.getName();
        holder.nameText.setText(name == null ? "" : name);
        holder.typeText.setText(
            file.isDirectory() ? R.string.type_directory : R.string.type_file
        );
        String uriKey = file.getUri().toString();
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
                return;
            }
            if (file.isDirectory() && listener != null) {
                listener.onItemClick(file);
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
        private final TextView typeText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.selectCheckBox);
            nameText = itemView.findViewById(R.id.nameText);
            typeText = itemView.findViewById(R.id.typeText);
        }
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
        DocumentFile file = items.get(position);
        if (file == null) {
            return;
        }
        String uriKey = file.getUri().toString();
        if (selectedUris.contains(uriKey)) {
            selectedUris.remove(uriKey);
        } else {
            selectedUris.add(uriKey);
        }
        notifyItemChanged(position);
        notifySelectionChanged();
    }
}
