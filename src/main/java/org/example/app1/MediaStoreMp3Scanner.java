package org.example.app1;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MediaStoreMp3Scanner {
    private static final String TAG = "MediaStoreMp3Scanner";
    private static final String RELATIVE_PATH = "CCDownload/";
    private static final String LEGACY_PATH_PREFIX = "/storage/emulated/0/CCDownload/";

    public static List<Mp3Item> scan(Context context) {
        ContentResolver resolver = context.getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DATA
            };
        } else {
            projection = new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATA
            };
        }

        int totalRows = countTotalRows(resolver, uri);
        Log.d(TAG, "total audio rows=" + totalRows);

        QueryStats stats;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            stats = queryWithSelection(
                resolver,
                uri,
                projection,
                MediaStore.Audio.Media.RELATIVE_PATH + "=?",
                new String[] { RELATIVE_PATH },
                "RELATIVE_PATH="
            );
            if (stats.items.isEmpty()) {
                stats = queryWithSelection(
                    resolver,
                    uri,
                    projection,
                    MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?",
                    new String[] { "%/CCDownload/" },
                    "RELATIVE_PATH_LIKE"
                );
            }
            if (stats.items.isEmpty()) {
                stats = queryWithSelection(
                    resolver,
                    uri,
                    projection,
                    MediaStore.Audio.Media.DATA + " LIKE ?",
                    new String[] { LEGACY_PATH_PREFIX + "%" },
                    "DATA_LIKE"
                );
            }
        } else {
            stats = queryWithSelection(
                resolver,
                uri,
                projection,
                MediaStore.Audio.Media.DATA + " LIKE ?",
                new String[] { LEGACY_PATH_PREFIX + "%" },
                "DATA_LIKE"
            );
        }
        if (stats.items.isEmpty()) {
            Log.d(TAG, "scan result empty");
        }
        return stats.items;
    }

    private static QueryStats queryWithSelection(
        ContentResolver resolver,
        Uri uri,
        String[] projection,
        String selection,
        String[] selectionArgs,
        String label
    ) {
        QueryStats stats = new QueryStats();
        try (Cursor cursor = resolver.query(uri, projection, selection, selectionArgs, null)) {
            if (cursor == null) {
                Log.d(TAG, label + " cursor null");
                return stats;
            }
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            while (cursor.moveToNext()) {
                stats.totalRows++;
                String name = cursor.getString(nameIndex);
                if (name == null) {
                    continue;
                }
                if (!name.toLowerCase(Locale.US).endsWith(".mp3")) {
                    continue;
                }
                long id = cursor.getLong(idIndex);
                long size = cursor.getLong(sizeIndex);
                Uri contentUri = ContentUris.withAppendedId(uri, id);
                stats.items.add(new Mp3Item(name, contentUri, size));
                stats.mp3Rows++;
            }
        } catch (Exception e) {
            Log.d(TAG, label + " query error " + e.getMessage());
        }
        Log.d(TAG, label + " totalRows=" + stats.totalRows + " mp3Rows=" + stats.mp3Rows);
        return stats;
    }

    private static int countTotalRows(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[] { MediaStore.Audio.Media._ID },
            null, null, null)) {
            if (cursor == null) {
                return 0;
            }
            return cursor.getCount();
        } catch (Exception e) {
            Log.d(TAG, "countTotalRows error " + e.getMessage());
            return 0;
        }
    }

    private static class QueryStats {
        private final List<Mp3Item> items = new ArrayList<>();
        private int totalRows = 0;
        private int mp3Rows = 0;
    }
}
