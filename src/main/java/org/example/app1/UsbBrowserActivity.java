package org.example.app1;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UsbBrowserActivity extends AppCompatActivity {
    private TextView statusText;
    private Button authorizeButton;
    private RecyclerView recyclerView;
    private Button localMp3Button;
    private DocumentFileAdapter adapter;
    private final Deque<DocumentFile> navigationStack = new ArrayDeque<>();
    private DocumentFile rootDocument;
    private DocumentFile currentDocument;
    private ExecutorService executorService;
    private ActivityResultLauncher<Intent> pickerLauncher;
    private boolean suppressAutoOpen;
    private boolean waitingForPickerResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usb_browser);

        statusText = findViewById(R.id.statusText);
        authorizeButton = findViewById(R.id.authorizeButton);
        recyclerView = findViewById(R.id.recyclerView);
        localMp3Button = findViewById(R.id.localMp3Button);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DocumentFileAdapter(this::onDocumentClicked);
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
        localMp3Button.setOnClickListener(
            v -> startActivity(new Intent(this, LocalMp3Activity.class))
        );

        ensureUsbAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForPickerResult) {
            return;
        }
        Uri stored = UsbAccessUtil.getStoredUri(this);
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
        if (rootDocument == null) {
            loadRootAndList();
        } else if (currentDocument != null) {
            loadDirectory(currentDocument);
        }
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
        navigationStack.clear();
        currentDocument = rootDocument;
        loadDirectory(rootDocument);
    }

    private void onDocumentClicked(DocumentFile file) {
        if (file != null && file.isDirectory() && currentDocument != null) {
            navigationStack.push(currentDocument);
            currentDocument = file;
            loadDirectory(file);
        }
    }

    private boolean navigateUp() {
        if (navigationStack.isEmpty()) {
            return false;
        }
        currentDocument = navigationStack.pop();
        loadDirectory(currentDocument);
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
                runOnUiThread(this::showDisconnectedUi);
                return;
            }
            List<DocumentFile> fileList = new ArrayList<>(Arrays.asList(files));
            fileList.sort((a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                String nameA = a.getName() == null ? "" : a.getName();
                String nameB = b.getName() == null ? "" : b.getName();
                return String.CASE_INSENSITIVE_ORDER.compare(nameA, nameB);
            });
            runOnUiThread(() -> adapter.setItems(fileList));
        });
    }

    private void showDisconnectedUi() {
        statusText.setText(R.string.status_not_connected);
        authorizeButton.setVisibility(View.VISIBLE);
        adapter.setItems(Collections.emptyList());
        rootDocument = null;
        currentDocument = null;
        navigationStack.clear();
        updateActionBar();
    }

    private void showConnectedUi(DocumentFile directory) {
        authorizeButton.setVisibility(View.GONE);
        String name = directory.getName();
        if (name == null) {
            name = "";
        }
        statusText.setText(
            name.isEmpty() ? getString(R.string.status_connected)
                : getString(R.string.current_directory, name)
        );
        updateActionBar();
    }

    private void updateActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(!navigationStack.isEmpty());
        }
    }
}
