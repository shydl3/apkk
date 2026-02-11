package org.example.app1;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import androidx.documentfile.provider.DocumentFile;

import java.util.List;

public class UsbAccessUtil {
    private static final String PREFS_NAME = "usb_prefs";
    public static final String KEY_USB_TREE_URI = "usb_tree_uri";

    public static Intent createOpenDocumentTreeIntent(Context context) {
        Intent intent = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            StorageManager storageManager =
                (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (storageManager != null) {
                for (StorageVolume volume : storageManager.getStorageVolumes()) {
                    if (volume.isRemovable()) {
                        intent = volume.createOpenDocumentTreeIntent();
                        break;
                    }
                }
            }
        }
        if (intent == null) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        return intent;
    }

    public static void persistUriPermission(Context context, Uri uri) {
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        context.getContentResolver().takePersistableUriPermission(uri, flags);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_USB_TREE_URI, uri.toString()).apply();
    }

    public static Uri getStoredUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriString = prefs.getString(KEY_USB_TREE_URI, null);
        if (uriString == null) {
            return null;
        }
        return Uri.parse(uriString);
    }

    public static void clearStoredUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_USB_TREE_URI).apply();
    }

    public static boolean hasPersistedUriPermission(Context context, Uri uri) {
        List<UriPermission> permissions =
            context.getContentResolver().getPersistedUriPermissions();
        for (UriPermission permission : permissions) {
            if (permission.getUri().equals(uri)
                && permission.isReadPermission()
                && permission.isWritePermission()) {
                return true;
            }
        }
        return false;
    }

    public static Uri getPersistedUriIfValid(Context context) {
        Uri stored = getStoredUri(context);
        if (stored == null) {
            return null;
        }
        if (!hasPersistedUriPermission(context, stored)) {
            clearStoredUri(context);
            return null;
        }
        return stored;
    }

    public static DocumentFile getRootDocumentFile(Context context) {
        Uri uri = getPersistedUriIfValid(context);
        if (uri == null) {
            return null;
        }
        return DocumentFile.fromTreeUri(context, uri);
    }
}
