package app.spicetify.extension.spotify.localserver;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class RemoteTrack {
    public final URI url;
    public final String name;
    public final long size;
    public final String etag;
    public final String title;
    public final String album;
    public final String artist;
    public final int durationSeconds;
    public final String id;

    RemoteTrack(ServerConnection connection, URI url, long size, String etag) {
        this(connection, url, size, etag, "", "", "", 0);
    }

    private RemoteTrack(ServerConnection connection, URI url, long size, String etag,
            String title, String album, String artist, int durationSeconds) {
        if (size <= 0 || size > 2L * 1024 * 1024 * 1024) throw new IllegalArgumentException("Unsupported audio file size.");
        this.url = connection.resolve(connection.root, url.toASCIIString());
        this.name = url.getPath().substring(url.getPath().lastIndexOf('/') + 1);
        this.size = size;
        this.etag = etag;
        this.title = title;
        this.album = album;
        this.artist = artist;
        this.durationSeconds = durationSeconds;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((connection.root + "\n" + connection.username
                    + "\n" + url + "\n" + size + "\n" + etag).getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder();
            for (byte value : digest) text.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            id = text.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    RemoteTrack withMetadata(ServerConnection connection, String title, String album, String artist, int duration) {
        return new RemoteTrack(connection, url, size, etag, bounded(title), bounded(album), bounded(artist), Math.max(0, duration));
    }
    private static String bounded(String value) { return value == null ? "" : value.substring(0, Math.min(value.length(), 512)); }
    public String displayTitle() {
        if (!title.isEmpty()) return title;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
