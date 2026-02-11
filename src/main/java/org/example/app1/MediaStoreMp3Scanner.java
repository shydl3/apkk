package org.example.app1;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MediaStoreMp3Scanner {
    private static final String RELATIVE_PATH = "CCDownload/";
    private static final String LEGACY_PATH_PREFIX = "/storage/emulated/0/CCDownload/";

    public static List<Mp3Item> scan(Context context) {
        ContentResolver resolver = context.getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[] {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATA
        };

        List<Mp3Item> results = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            results = queryWithSelection(
                resolver,
                uri,
                projection,
                MediaStore.Audio.Media.RELATIVE_PATH + "=?",
                new String[] { RELATIVE_PATH }
            );
            if (results.isEmpty()) {
                results = queryWithSelection(
                    resolver,
                    uri,
                    projection,
                    MediaStore.Audio.Media.DATA + " LIKE ?",
                    new String[] { LEGACY_PATH_PREFIX + "%" }
                );
            }
        } else {
            results = queryWithSelection(
                resolver,
                uri,
                projection,
                MediaStore.Audio.Media.DATA + " LIKE ?",
                new String[] { LEGACY_PATH_PREFIX + "%" }
            );
        }
        return results;
    }

    private static List<Mp3Item> queryWithSelection(
        ContentResolver resolver,
        Uri uri,
        String[] projection,
        String selection,
        String[] selectionArgs
    ) {
        List<Mp3Item> results = new ArrayList<>();
        try (Cursor cursor = resolver.query(uri, projection, selection, selectionArgs, null)) {
            if (cursor == null) {
                return results;
            }
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            while (cursor.moveToNext()) {
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
                results.add(new Mp3Item(name, contentUri, size));
            }
        }
        return results;
    }
}
