package work.lclpnet.notica.util;

import lombok.Getter;

import java.util.*;

/**
 * In-memory playlist store. All public methods are synchronized.
 * Persistence hook points for iteration 2: add load(Path) and save(Path) methods,
 * and wire them to server start/stop events in NoticaInit.
 */
public class PlaylistManager {

    private final Map<UUID, Map<String, PlaylistEntry>> playlists = new HashMap<>();

    public synchronized boolean createPlaylist(UUID owner, String title) {
        String key = normalize(title);
        Map<String, PlaylistEntry> owned = playlists.computeIfAbsent(owner, k -> new LinkedHashMap<>());

        if (owned.containsKey(key)) return false;

        owned.put(key, new PlaylistEntry(title));
        return true;
    }

    public synchronized boolean addSong(UUID owner, String key, String songPath) {
        PlaylistEntry entry = findEntry(owner, key);
        if (entry == null) return false;

        entry.songs.add(songPath);
        return true;
    }

    public synchronized boolean removeSong(UUID owner, String key, String songPath) {
        PlaylistEntry entry = findEntry(owner, key);
        if (entry == null) return false;

        entry.songs.remove(songPath);
        return true;
    }

    public synchronized boolean deletePlaylist(UUID owner, String key) {
        Map<String, PlaylistEntry> owned = playlists.get(owner);
        if (owned == null) return false;

        return owned.remove(normalize(key)) != null;
    }

    public synchronized boolean shareWith(UUID owner, String key, Set<UUID> targets) {
        PlaylistEntry entry = findEntry(owner, key);
        if (entry == null) return false;

        entry.sharedWith.addAll(targets);
        return true;
    }

    public synchronized boolean makePublic(UUID owner, String key) {
        PlaylistEntry entry = findEntry(owner, key);
        if (entry == null) return false;

        entry.sharedWith.clear();
        entry.isPublic = true;
        return true;
    }

    public synchronized boolean unshareAll(UUID owner, String key) {
        PlaylistEntry entry = findEntry(owner, key);
        if (entry == null) return false;

        entry.sharedWith.clear();
        entry.isPublic = false;
        return true;
    }

    public synchronized boolean unshareFrom(UUID owner, String key, Set<UUID> targets) {
        PlaylistEntry entry = findEntry(owner, key);
        if (entry == null) return false;

        entry.sharedWith.removeAll(targets);
        return true;
    }

    public synchronized Optional<PlaylistEntry> getPlaylist(UUID owner, String key) {
        return Optional.ofNullable(findEntry(owner, normalize(key)));
    }

    public synchronized Map<String, PlaylistEntry> getOwnPlaylists(UUID owner) {
        Map<String, PlaylistEntry> owned = playlists.get(owner);
        if (owned == null) return Collections.emptyMap();

        return Collections.unmodifiableMap(owned);
    }

    /**
     * Returns all playlists accessible to {@code accessor} that are owned by someone else.
     * The outer key is the owner UUID; the inner key is the normalized playlist name.
     */
    public synchronized Map<UUID, Map<String, PlaylistEntry>> getSharedWithMe(UUID accessor) {
        Map<UUID, Map<String, PlaylistEntry>> result = new LinkedHashMap<>();

        for (Map.Entry<UUID, Map<String, PlaylistEntry>> ownerEntry : playlists.entrySet()) {
            UUID owner = ownerEntry.getKey();
            if (owner.equals(accessor)) continue;

            Map<String, PlaylistEntry> accessible = new LinkedHashMap<>();
            for (Map.Entry<String, PlaylistEntry> playlistEntry : ownerEntry.getValue().entrySet()) {
                PlaylistEntry entry = playlistEntry.getValue();
                if (entry.isPublic || entry.sharedWith.contains(accessor)) {
                    accessible.put(playlistEntry.getKey(), entry);
                }
            }

            if (!accessible.isEmpty()) {
                result.put(owner, Collections.unmodifiableMap(accessible));
            }
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Finds the first playlist named {@code name} accessible to {@code accessor}.
     * Own playlists take priority over shared ones.
     */
    public synchronized Optional<PlaylistEntry> getAccessiblePlaylist(UUID accessor, String name) {
        String key = normalize(name);

        // own playlist first
        PlaylistEntry own = findEntry(accessor, key);
        if (own != null) return Optional.of(own);

        // then search playlists from other owners
        for (Map.Entry<UUID, Map<String, PlaylistEntry>> ownerEntry : playlists.entrySet()) {
            if (ownerEntry.getKey().equals(accessor)) continue;

            PlaylistEntry entry = ownerEntry.getValue().get(key);
            if (entry != null && (entry.isPublic || entry.sharedWith.contains(accessor))) {
                return Optional.of(entry);
            }
        }

        return Optional.empty();
    }

    public synchronized boolean canAccess(UUID owner, String key, UUID accessor) {
        PlaylistEntry entry = findEntry(owner, key);
        if (entry == null) return false;

        return owner.equals(accessor) || entry.isPublic || entry.sharedWith.contains(accessor);
    }

    private PlaylistEntry findEntry(UUID owner, String key) {
        Map<String, PlaylistEntry> owned = playlists.get(owner);
        if (owned == null) return null;

        return owned.get(normalize(key));
    }

    public static String normalize(String title) {
        return title.toLowerCase(Locale.ROOT);
    }

    public static final class PlaylistEntry {
        @Getter
        private final String title;
        private final List<String> songs = new ArrayList<>();
        private final Set<UUID> sharedWith = new HashSet<>();
        @Getter
        private boolean isPublic;

        public PlaylistEntry(String title) {
            this.title = title;
        }

        public List<String> getSongs() {
            return Collections.unmodifiableList(songs);
        }

        public Set<UUID> getSharedWith() {
            return Collections.unmodifiableSet(sharedWith);
        }
    }
}
