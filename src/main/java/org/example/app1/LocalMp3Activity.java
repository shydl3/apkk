package org.example.app1;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.IntentSender;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.provider.MediaStore;
import android.app.PendingIntent;
import android.net.Uri;
import android.content.ContentResolver;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import androidx.appcompat.app.AlertDialog;

public class LocalMp3Activity extends AppCompatActivity {
    private static final String TAG = "LocalMp3Activity";
    private static final int REQUEST_PERMISSION = 1001;
    private static final String LOCAL_TARGET_DIR = "CCDownload";

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView permissionText;
    private TextView titleText;
    private Button grantPermissionButton;
    private TextView emptyText;
    private Button localMp3Button;
    private Button moveToUsbButton;
    private Mp3Adapter adapter;
    private ExecutorService executorService;
    private ActionMode actionMode;
    private ActivityResultLauncher<IntentSenderRequest> deleteLauncher;
    private ActivityResultLauncher<Intent> pickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_mp3);

        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        recyclerView = findViewById(R.id.mp3RecyclerView);
        permissionText = findViewById(R.id.permissionText);
        titleText = findViewById(R.id.titleText);
        grantPermissionButton = findViewById(R.id.grantPermissionButton);
        emptyText = findViewById(R.id.emptyText);
        localMp3Button = findViewById(R.id.btn_local_mp3);
        moveToUsbButton = findViewById(R.id.btn_move_to_usb);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Mp3Adapter();
        adapter.setSelectionListeners(this::onSelectionChanged, this::ensureSelectionMode);
        recyclerView.setAdapter(adapter);

        executorService = Executors.newSingleThreadExecutor();

        deleteLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    refreshMp3List();
                } else {
                    Toast.makeText(this, "Delete cancelled", Toast.LENGTH_SHORT).show();
                }
                exitSelectionMode();
            }
        );

        pickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    if (treeUri != null) {
                        UsbAccessUtil.persistUriPermission(this, treeUri);
                        moveSelectedToUsb();
                        return;
                    }
                }
                Toast.makeText(this, getString(R.string.usb_authorize_first), Toast.LENGTH_SHORT)
                    .show();
            }
        );

        swipeRefreshLayout.setOnRefreshListener(this::refreshMp3List);
        grantPermissionButton.setOnClickListener(v -> requestMediaPermission());
        localMp3Button.setOnClickListener(v -> {
            Log.d(TAG, "Local MP3 button clicked");
            if (!UsbAccessUtil.hasRemovableStorage(this)) {
                showUsbNotConnectedDialog();
                return;
            }
            startActivity(new Intent(this, UsbBrowserActivity.class));
        });
        moveToUsbButton.setOnClickListener(v -> moveSelectedToUsb());

        try {
            titleText.setText(getExistingLocalTargetAbsolutePath());
        } catch (IllegalStateException e) {
            Log.e(TAG, e.getMessage());
            File externalStorageDir = Environment.getExternalStorageDirectory();
            File targetDir = new File(externalStorageDir, LOCAL_TARGET_DIR);
            titleText.setText("路径错误: " + targetDir.getAbsolutePath());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
        ensureMediaPermission();
    }

    private String getExistingLocalTargetAbsolutePath() {
        File externalStorageDir = Environment.getExternalStorageDirectory();
        File targetDir = new File(externalStorageDir, LOCAL_TARGET_DIR);
        if (!targetDir.exists() || !targetDir.isDirectory()) {
            throw new IllegalStateException(
                "目录不存在: " + targetDir.getAbsolutePath()
            );
        }
        return targetDir.getAbsolutePath();
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
        super.onBackPressed();
    }

    private void ensureMediaPermission() {
        if (hasReadPermission()) {
            showListState();
            refreshMp3List();
            return;
        }
        showPermissionState();
        requestMediaPermission();
    }

    private void requestMediaPermission() {
        String permission = getReadPermission();
        Log.d(TAG, "requesting audio permission");
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
                Log.d(TAG, "audio permission granted");
                showListState();
                refreshMp3List();
            } else {
                Log.d(TAG, "audio permission denied");
                showPermissionState();
            }
        }
    }

    private void refreshMp3List() {
        if (!hasReadPermission()) {
            swipeRefreshLayout.setRefreshing(false);
            showPermissionState();
            return;
        }
        showListState();
        swipeRefreshLayout.setRefreshing(true);
        executorService.execute(() -> {
            List<Mp3Item> items;
            try {
                items = MediaStoreMp3Scanner.scan(this);
            } catch (Exception e) {
                Log.d(TAG, "scan error " + e.getMessage());
                items = java.util.Collections.emptyList();
            }
            List<Mp3Item> finalItems = items;
            runOnUiThread(() -> {
                swipeRefreshLayout.setRefreshing(false);
                adapter.setItems(finalItems);
                if (finalItems.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                } else {
                    emptyText.setVisibility(View.GONE);
                }
                exitSelectionMode();
            });
        });
    }

    private void showPermissionState() {
        permissionText.setVisibility(View.VISIBLE);
        grantPermissionButton.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        moveToUsbButton.setVisibility(View.GONE);
    }

    private void showListState() {
        permissionText.setVisibility(View.GONE);
        grantPermissionButton.setVisibility(View.GONE);
        swipeRefreshLayout.setVisibility(View.VISIBLE);
    }

    private void onSelectionChanged(int count) {
        if (actionMode != null) {
            actionMode.setTitle(getString(R.string.selected_count, count));
            if (count == 0) {
                actionMode.finish();
            }
        }
        moveToUsbButton.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
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
                deleteSelectedLocal();
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

    private void deleteSelectedLocal() {
        List<Mp3Item> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ArrayList<Uri> uris = new ArrayList<>();
            for (Mp3Item item : selected) {
                uris.add(item.getContentUri());
            }
            try {
                PendingIntent pi = MediaStore.createDeleteRequest(
                    getContentResolver(),
                    uris
                );
                IntentSenderRequest request = new IntentSenderRequest.Builder(pi).build();
                deleteLauncher.launch(request);
                return;
            } catch (Exception e) {
                Toast.makeText(this, "Unable to request delete permission", Toast.LENGTH_SHORT)
                    .show();
            }
        }
        executorService.execute(() -> {
            int failed = 0;
            for (Mp3Item item : selected) {
                try {
                    int rows = getContentResolver().delete(item.getContentUri(), null, null);
                    if (rows <= 0) {
                        failed++;
                    }
                } catch (SecurityException se) {
                    failed++;
                } catch (Exception e) {
                    failed++;
                }
            }
            int finalFailed = failed;
            runOnUiThread(() -> {
                if (finalFailed > 0) {
                    Toast.makeText(
                        this,
                        "Some files could not be deleted. Check system permission.",
                        Toast.LENGTH_SHORT
                    ).show();
                }
                exitSelectionMode();
                refreshMp3List();
            });
        });
    }

    private void moveSelectedToUsb() {
        List<Mp3Item> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            return;
        }
        Uri stored = UsbAccessUtil.getPersistedUriIfValid(this);
        if (!UsbAccessUtil.hasRemovableStorage(this)) {
            runOnUiThread(this::showUsbNotConnectedDialog);
            return;
        }
        if (stored == null) {
            pickerLauncher.launch(UsbAccessUtil.createOpenDocumentTreeIntent(this));
            return;
        }
        executorService.execute(() -> {
            int copied = 0;
            int failed = 0;
            androidx.documentfile.provider.DocumentFile root =
                androidx.documentfile.provider.DocumentFile.fromTreeUri(this, stored);
            if (root == null || !root.exists()) {
                runOnUiThread(() -> Toast.makeText(
                    this,
                    getString(R.string.usb_authorize_first),
                    Toast.LENGTH_SHORT
                ).show());
                return;
            }
            ContentResolver resolver = getContentResolver();
            for (Mp3Item item : selected) {
                boolean ok = copyToUsb(resolver, item, root);
                if (ok) {
                    copied++;
                } else {
                    failed++;
                }
            }
            int finalCopied = copied;
            int finalFailed = failed;
            runOnUiThread(() -> {
                if (finalFailed > 0) {
                    Toast.makeText(
                        this,
                        getString(R.string.move_failed),
                        Toast.LENGTH_SHORT
                    ).show();
                } else if (finalCopied > 0) {
                    Toast.makeText(
                        this,
                        "Copied " + finalCopied + " file(s)",
                        Toast.LENGTH_SHORT
                    ).show();
                }
                exitSelectionMode();
                refreshMp3List();
            });
        });
    }

    private boolean copyToUsb(
        ContentResolver resolver,
        Mp3Item item,
        androidx.documentfile.provider.DocumentFile targetDir
    ) {
        if (item == null || targetDir == null) {
            return false;
        }
        String displayName = item.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = "audio.mp3";
        }
        androidx.documentfile.provider.DocumentFile outFile =
            createUniqueFile(targetDir, "audio/mpeg", displayName);
        if (outFile == null) {
            return false;
        }
        try (InputStream in = resolver.openInputStream(item.getContentUri());
             OutputStream out = resolver.openOutputStream(outFile.getUri())) {
            if (in == null || out == null) {
                return false;
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private androidx.documentfile.provider.DocumentFile createUniqueFile(
        androidx.documentfile.provider.DocumentFile dir,
        String mimeType,
        String displayName
    ) {
        String base = displayName;
        String ext = "";
        int dot = displayName.lastIndexOf('.');
        if (dot > 0 && dot < displayName.length() - 1) {
            base = displayName.substring(0, dot);
            ext = displayName.substring(dot);
        }
        String candidate = displayName;
        int index = 1;
        while (dir.findFile(candidate) != null && index < 1000) {
            candidate = base + " (" + index + ")" + ext;
            index++;
        }
        return dir.createFile(mimeType, candidate);
    }

    private void showUsbNotConnectedDialog() {
        new AlertDialog.Builder(this)
            .setMessage("请先连接U盘！")
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }
}
