package org.example.app1;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamCopyUtil {

    public static DocumentFile createUniqueFile(
        DocumentFile directory,
        String mimeType,
        String displayName
    ) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }
        String baseName = displayName;
        String extension = "";
        int dot = displayName.lastIndexOf('.');
        if (dot > 0) {
            baseName = displayName.substring(0, dot);
            extension = displayName.substring(dot);
        }
        String candidate = displayName;
        int index = 1;
        while (directory.findFile(candidate) != null) {
            candidate = baseName + " (" + index + ")" + extension;
            index++;
        }
        return directory.createFile(mimeType, candidate);
    }

    public static void copyStream(ContentResolver resolver, Uri source, Uri dest) throws IOException {
        try (InputStream inputStream = resolver.openInputStream(source);
             OutputStream outputStream = resolver.openOutputStream(dest)) {
            if (inputStream == null || outputStream == null) {
                throw new IOException("Stream unavailable");
            }
            byte[] buffer = new byte[4 * 1024 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }
}
