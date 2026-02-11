package org.example.app1;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DocumentFileAdapter extends RecyclerView.Adapter<DocumentFileAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(DocumentFile file);
    }

    private final List<DocumentFile> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public DocumentFileAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<DocumentFile> files) {
        items.clear();
        items.addAll(files);
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
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(file);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final TextView typeText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.nameText);
            typeText = itemView.findViewById(R.id.typeText);
        }
    }
}
