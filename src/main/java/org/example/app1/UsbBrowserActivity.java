package org.example.app1;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UsbBrowserActivity extends AppCompatActivity {
    private static final String TAG = "UsbBrowserActivity";
    private static final int REQUEST_PERMISSION = 2002;

    private TextView statusText;
    private TextView permissionHintText;
    private Button authorizeButton;
    private RecyclerView recyclerView;
    private Button deleteButton;
    private Button localMp3Button;
    private DocumentFileAdapter adapter;
    private final Deque<String> pathSegments = new ArrayDeque<>();
    private DocumentFile rootDocument;
    private DocumentFile currentDocument;
    private ExecutorService executorService;
    private ActivityResultLauncher<Intent> pickerLauncher;
    private boolean suppressAutoOpen;
    private boolean waitingForPickerResult;
    private ActionMode actionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usb_browser);

        statusText = findViewById(R.id.statusText);
        permissionHintText = findViewById(R.id.permissionHintText);
        authorizeButton = findViewById(R.id.authorizeButton);
        recyclerView = findViewById(R.id.recyclerView);
        deleteButton = findViewById(R.id.deleteButton);
        localMp3Button = findViewById(R.id.btn_local_mp3);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DocumentFileAdapter(
            this::onDocumentClicked,
            this::onSelectionChanged,
            this::ensureSelectionMode
        );
        recyclerView.setAdapter(adapter);

        executorService = Executors.newSingleThreadExecutor();

        pickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    if (treeUri != null) {
                        UsbAccessUtil.persistUriPermission(this, treeUri);
                        waitingForPickerResult = false;
                        suppressAutoOpen = false;
                        loadRootAndList();
                        return;
                    }
                }
                waitingForPickerResult = false;
                suppressAutoOpen = true;
                showDisconnectedUi();
            }
        );

        authorizeButton.setOnClickListener(v -> openUsbPicker());
        deleteButton.setOnClickListener(v -> confirmExternalDelete());
        localMp3Button.setOnClickListener(v -> {
            Log.d(TAG, "Local MP3 button clicked");
            finish();
        });

        ensureAudioPermission();
        ensureUsbAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForPickerResult) {
            return;
        }
        Uri stored = UsbAccessUtil.getStoredUri(this);
        Log.d(TAG, "onResume storedUri=" + stored);
        if (stored == null) {
            showDisconnectedUi();
            if (!suppressAutoOpen) {
                openUsbPicker();
            }
            return;
        }
        if (!UsbAccessUtil.hasPersistedUriPermission(this, stored)) {
            UsbAccessUtil.clearStoredUri(this);
            showDisconnectedUi();
            openUsbPicker();
            return;
        }
        refreshExternalListing();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Override
    public void onBackPressed() {
        if (actionMode != null) {
            actionMode.finish();
            return;
        }
        if (!navigateUp()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            return navigateUp();
        }
        return super.onOptionsItemSelected(item);
    }

    private void ensureUsbAccess() {
        Uri stored = UsbAccessUtil.getStoredUri(this);
        if (stored == null) {
            showDisconnectedUi();
            openUsbPicker();
            return;
        }
        if (!UsbAccessUtil.hasPersistedUriPermission(this, stored)) {
            UsbAccessUtil.clearStoredUri(this);
            showDisconnectedUi();
            openUsbPicker();
        } else {
            loadRootAndList();
        }
    }

    private void openUsbPicker() {
        waitingForPickerResult = true;
        pickerLauncher.launch(UsbAccessUtil.createOpenDocumentTreeIntent(this));
    }

    private void loadRootAndList() {
        rootDocument = UsbAccessUtil.getRootDocumentFile(this);
        if (rootDocument == null) {
            showDisconnectedUi();
            return;
        }
        pathSegments.clear();
        currentDocument = rootDocument;
        loadDirectory(rootDocument);
    }

    private void onDocumentClicked(DocumentFile file) {
        if (file != null && file.isDirectory() && currentDocument != null) {
            String name = file.getName();
            if (name != null) {
                pathSegments.addLast(name);
            }
            currentDocument = file;
            loadDirectory(file);
        }
    }

    private boolean navigateUp() {
        if (pathSegments.isEmpty()) {
            return false;
        }
        pathSegments.removeLast();
        refreshExternalListing();
        return true;
    }

    private void loadDirectory(DocumentFile directory) {
        if (directory == null) {
            showDisconnectedUi();
            return;
        }
        showConnectedUi(directory);
        executorService.execute(() -> {
            DocumentFile[] files;
            try {
                if (!directory.exists() || !directory.canRead()) {
                    throw new IllegalStateException("Directory unavailable");
                }
                files = directory.listFiles();
            } catch (Exception e) {
                files = null;
            }
            if (files == null) {
                Log.d(TAG, "loadDirectory listFiles failed");
                runOnUiThread(this::showDisconnectedUi);
                return;
            }
            List<DocumentFile> fileList = new ArrayList<>(Arrays.asList(files));
            List<DocumentFile> mp3Only = new ArrayList<>();
            for (DocumentFile file : fileList) {
                if (file == null || file.isDirectory()) {
                    continue;
                }
                String name = file.getName();
                if (name != null && name.toLowerCase(java.util.Locale.US).endsWith(".mp3")) {
                    mp3Only.add(file);
                }
            }
            mp3Only.sort((a, b) -> {
                String nameA = a.getName() == null ? "" : a.getName();
                String nameB = b.getName() == null ? "" : b.getName();
                return String.CASE_INSENSITIVE_ORDER.compare(nameA, nameB);
            });
            runOnUiThread(() -> {
                adapter.setItems(mp3Only);
                onSelectionChanged(0);
                exitSelectionMode();
                Log.d(TAG, "loadDirectory count=" + mp3Only.size());
            });
        });
    }

    private void showDisconnectedUi() {
        boolean removableDetected = UsbAccessUtil.hasRemovableStorage(this);
        statusText.setText(
            removableDetected ? R.string.status_detected_needs_auth
                : R.string.status_not_connected
        );
        authorizeButton.setVisibility(View.VISIBLE);
        adapter.setItems(Collections.emptyList());
        rootDocument = null;
        currentDocument = null;
        pathSegments.clear();
        deleteButton.setVisibility(View.GONE);
        exitSelectionMode();
        updateActionBar();
    }

    private void showConnectedUi(DocumentFile directory) {
        authorizeButton.setVisibility(View.GONE);
        statusText.setText(getString(R.string.external_location));
        updateActionBar();
    }

    private void updateActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(!pathSegments.isEmpty());
        }
    }

    private void onSelectionChanged(int count) {
        Log.d(TAG, "selectionCount=" + count);
        if (actionMode != null) {
            actionMode.setTitle(getString(R.string.selected_count, count));
            if (count == 0) {
                actionMode.finish();
            }
        }
        deleteButton.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private void confirmExternalDelete() {
        if (!adapter.hasSelection()) {
            return;
        }
        new AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> confirmExternalDeleteSecond())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void confirmExternalDeleteSecond() {
        new AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete_second)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteSelectedExternal())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void deleteSelectedExternal() {
        List<DocumentFile> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            return;
        }
        executorService.execute(() -> {
            String failedName = null;
            for (DocumentFile file : selected) {
                try {
                    boolean deleted = file.delete();
                    Log.d(TAG, "external delete " + file.getUri() + " result=" + deleted);
                    if (!deleted && failedName == null) {
                        failedName = file.getName();
                    }
                } catch (Exception e) {
                    Log.d(TAG, "external delete error " + e.getMessage());
                    if (failedName == null) {
                        failedName = file.getName();
                    }
                }
            }
            String finalFailedName = failedName;
            runOnUiThread(() -> {
                if (finalFailedName != null) {
                    String display = finalFailedName == null ? "" : finalFailedName;
                    Toast.makeText(
                        this,
                        getString(R.string.external_delete_failed, display),
                        Toast.LENGTH_SHORT
                    ).show();
                }
                exitSelectionMode();
                refreshExternalListing();
            });
        });
    }

    private void refreshExternalListing() {
        DocumentFile directory = rebuildCurrentDirectory();
        if (directory == null) {
            showDisconnectedUi();
            return;
        }
        loadDirectory(directory);
    }

    private DocumentFile rebuildCurrentDirectory() {
        Uri stored = UsbAccessUtil.getPersistedUriIfValid(this);
        if (stored == null) {
            return null;
        }
        DocumentFile root = DocumentFile.fromTreeUri(this, stored);
        if (root == null || !root.exists()) {
            return null;
        }
        DocumentFile dir = root;
        for (String segment : pathSegments) {
            DocumentFile next = dir.findFile(segment);
            if (next == null || !next.isDirectory()) {
                pathSegments.clear();
                dir = root;
                break;
            }
            dir = next;
        }
        rootDocument = root;
        currentDocument = dir;
        return dir;
    }

    private void ensureSelectionMode() {
        if (actionMode != null) {
            return;
        }
        actionMode = startSupportActionMode(actionModeCallback);
        if (actionMode != null) {
            actionMode.setTitle(getString(R.string.selected_count, 0));
        }
    }

    private void exitSelectionMode() {
        if (actionMode != null) {
            actionMode.finish();
        }
    }

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.selection_menu, menu);
            adapter.setSelectionMode(true);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.action_delete) {
                confirmExternalDelete();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            adapter.setSelectionMode(false);
            onSelectionChanged(0);
        }
    };

    private void ensureAudioPermission() {
        String permission = getReadPermission();
        if (ContextCompat.checkSelfPermission(this, permission)
            == PackageManager.PERMISSION_GRANTED) {
            permissionHintText.setVisibility(View.GONE);
            Log.d(TAG, "audio permission granted");
            return;
        }
        Log.d(TAG, "requesting audio permission");
        ActivityCompat.requestPermissions(this, new String[] { permission }, REQUEST_PERMISSION);
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
                permissionHintText.setVisibility(View.GONE);
                Log.d(TAG, "audio permission granted");
            } else {
                permissionHintText.setVisibility(View.VISIBLE);
                Log.d(TAG, "audio permission denied");
            }
        }
    }
}



