package org.example.app1;

import android.net.Uri;

public class Mp3Item {
    private final String displayName;
    private final Uri contentUri;
    private final long size;

    public Mp3Item(String displayName, Uri contentUri, long size) {
        this.displayName = displayName;
        this.contentUri = contentUri;
        this.size = size;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Uri getContentUri() {
        return contentUri;
    }

    public long getSize() {
        return size;
    }
}
