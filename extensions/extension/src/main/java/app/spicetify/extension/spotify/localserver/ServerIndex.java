package app.spicetify.extension.spotify.localserver;

import android.media.MediaMetadataRetriever;
import android.os.ParcelFileDescriptor;
import java.util.*;
import java.util.concurrent.*;

public final class ServerIndex {
    private static volatile Index index = new Index(null, Collections.emptyList());
    private static final class Index {
        final ServerConfig.Snapshot snapshot; final List<RemoteTrack> tracks;
        Index(ServerConfig.Snapshot snapshot, List<RemoteTrack> tracks) { this.snapshot = snapshot; this.tracks = tracks; }
    }
    private static volatile String status = "Not scanned";
    private static final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), runnable -> { Thread t = new Thread(runnable, "spicetify-server-scan"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private static Future<?> pending;
    private ServerIndex() {}

    public static String status() { return ServerConfig.snapshot().enabled ? status : "Disabled"; }
    public static List<RemoteTrack> tracks() { return tracks(ServerConfig.snapshot()); }
    static List<RemoteTrack> tracks(ServerConfig.Snapshot snapshot) {
        Index saved = index;
        return saved.snapshot == snapshot && ServerConfig.isCurrent(snapshot) ? saved.tracks : Collections.emptyList();
    }
    static RemoteTrack byId(ServerConfig.Snapshot snapshot, String id) { for (RemoteTrack track : tracks(snapshot)) if (track.id.equals(id)) return track; return null; }
    static void invalidate() {
        index = new Index(null, Collections.emptyList()); status = "Not scanned";
        synchronized (worker) { if (pending != null) pending.cancel(true); worker.getQueue().clear(); }
        LocalServerHook.requestRescan();
    }

    public static void scanAsync() {
        if (android.os.Build.VERSION.SDK_INT < 26) return;
        ServerConfig.Snapshot snapshot = ServerConfig.snapshot();
        if (!snapshot.enabled || snapshot.connection() == null) return;
        synchronized (worker) {
            if (pending != null) pending.cancel(true);
            worker.getQueue().clear();
            status = "Scanning…";
            pending = worker.submit(() -> scan(snapshot));
        }
    }

    private static void scan(ServerConfig.Snapshot snapshot) {
        try {
            WebDav dav = new WebDav(snapshot.connection(), () -> ServerConfig.isCurrent(snapshot));
            List<RemoteTrack> found = dav.scan();
            List<RemoteTrack> completed = new ArrayList<>();
            long deadline = System.nanoTime() + 120_000_000_000L;
            for (RemoteTrack track : found) {
                if (!ServerConfig.isCurrent(snapshot) || Thread.currentThread().isInterrupted()) return;
                if (System.nanoTime() > deadline) throw new java.io.IOException("Metadata scanning exceeded two minutes. Choose a smaller folder.");
                ServerConfig.publish(snapshot, () -> status = "Reading tags: " + (completed.size() + 1) + " / " + found.size());
                completed.add(readTags(snapshot, track));
            }
            if (Thread.currentThread().isInterrupted()) return;
            ServerConfig.publish(snapshot, () -> {
                index = new Index(snapshot, Collections.unmodifiableList(completed));
                status = completed.size() + " tracks ready";
                LocalServerHook.requestRescan();
            });
        } catch (Exception ex) {
            ServerConfig.publish(snapshot, () -> status = "Scan failed. Check the HTTPS WebDAV folder, credentials, and byte-range support.");
        }
    }

    private static RemoteTrack readTags(ServerConfig.Snapshot snapshot, RemoteTrack track) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        long deadline = System.nanoTime() + 15_000_000_000L;
        try (ParcelFileDescriptor file = ServerFileProvider.openTrack(ServerConfig.context(), snapshot, track,
                () -> System.nanoTime() < deadline, 8L * 1024 * 1024)) {
            retriever.setDataSource(file.getFileDescriptor());
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            int seconds = duration == null ? 0 : (int) Math.min(Integer.MAX_VALUE, Long.parseLong(duration) / 1000L);
            return track.withMetadata(snapshot.connection(), retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST), seconds);
        } finally { retriever.release(); }
    }
}
