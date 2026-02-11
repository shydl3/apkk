package org.example.app1;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalMp3Activity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION = 1001;

    private TextView selectedCountText;
    private RecyclerView recyclerView;
    private Button moveToUsbButton;
    private Button deleteButton;
    private ProgressBar progressBar;
    private Mp3Adapter adapter;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_mp3);

        selectedCountText = findViewById(R.id.selectedCountText);
        recyclerView = findViewById(R.id.mp3RecyclerView);
        moveToUsbButton = findViewById(R.id.moveToUsbButton);
        deleteButton = findViewById(R.id.deleteButton);
        progressBar = findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Mp3Adapter(this::updateSelectedCount);
        recyclerView.setAdapter(adapter);

        executorService = Executors.newSingleThreadExecutor();

        moveToUsbButton.setOnClickListener(v -> moveSelectedToUsb());
        deleteButton.setOnClickListener(v -> confirmDeleteSelected());

        updateSelectedCount(0);
        ensureMediaPermission();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private void updateSelectedCount(int count) {
        selectedCountText.setText(getString(R.string.selected_count, count));
    }

    private void ensureMediaPermission() {
        if (hasReadPermission()) {
            loadMp3List();
            return;
        }
        String permission = getReadPermission();
        ActivityCompat.requestPermissions(this, new String[] { permission }, REQUEST_PERMISSION);
    }

    private boolean hasReadPermission() {
        return ContextCompat.checkSelfPermission(this, getReadPermission())
            == PackageManager.PERMISSION_GRANTED;
    }

    private String getReadPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_AUDIO;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode,
        @NonNull String[] permissions,
        @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMp3List();
            } else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadMp3List() {
        progressBar.setVisibility(View.VISIBLE);
        executorService.execute(() -> {
            List<Mp3Item> items = MediaStoreMp3Scanner.scan(this);
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                adapter.setItems(items);
            });
        });
    }

    private void moveSelectedToUsb() {
        List<Mp3Item> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.select_files, Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentFile root = UsbAccessUtil.getRootDocumentFile(this);
        if (root == null) {
            Toast.makeText(this, R.string.usb_authorize_first, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, UsbBrowserActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
            return;
        }

        setBusy(true);
        ContentResolver resolver = getContentResolver();
        executorService.execute(() -> {
            boolean success = true;
            for (Mp3Item item : selected) {
                try {
                    DocumentFile dest = StreamCopyUtil.createUniqueFile(
                        root,
                        "audio/mpeg",
                        item.getDisplayName()
                    );
                    if (dest == null) {
                        success = false;
                        break;
                    }
                    StreamCopyUtil.copyStream(resolver, item.getContentUri(), dest.getUri());
                    int deleted = resolver.delete(item.getContentUri(), null, null);
                    if (deleted <= 0) {
                        success = false;
                        break;
                    }
                } catch (IOException | SecurityException e) {
                    success = false;
                    break;
                }
            }
            boolean finalSuccess = success;
            runOnUiThread(() -> {
                setBusy(false);
                if (!finalSuccess) {
                    Toast.makeText(this, R.string.move_failed, Toast.LENGTH_SHORT).show();
                }
                loadMp3List();
            });
        });
    }

    private void confirmDeleteSelected() {
        List<Mp3Item> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.select_files, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteSelected())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void deleteSelected() {
        List<Mp3Item> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            return;
        }
        setBusy(true);
        ContentResolver resolver = getContentResolver();
        executorService.execute(() -> {
            for (Mp3Item item : selected) {
                try {
                    resolver.delete(item.getContentUri(), null, null);
                } catch (SecurityException e) {
                    break;
                }
            }
            runOnUiThread(() -> {
                setBusy(false);
                loadMp3List();
            });
        });
    }

    private void setBusy(boolean busy) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        moveToUsbButton.setEnabled(!busy);
        deleteButton.setEnabled(!busy);
    }
}
