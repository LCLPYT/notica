package work.lclpnet.notica.util;

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
        private final String title;
        private final List<String> songs = new ArrayList<>();
        private final Set<UUID> sharedWith = new HashSet<>();
        private boolean isPublic;

        public PlaylistEntry(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        public List<String> getSongs() {
            return Collections.unmodifiableList(songs);
        }

        public Set<UUID> getSharedWith() {
            return Collections.unmodifiableSet(sharedWith);
        }

        public boolean isPublic() {
            return isPublic;
        }
    }
}
