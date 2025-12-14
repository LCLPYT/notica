package work.lclpnet.notica.util;

import net.minecraft.client.sounds.JOrbisAudioStream;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import work.lclpnet.kibu.assets.AssetManager;
import work.lclpnet.notica.impl.mix.SoundRef;
import work.lclpnet.notica.impl.mix.SoundSample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TestSoundRegistry {

    private final AssetManager assetManager;
    private JSONObject soundRegistry = new JSONObject();

    public TestSoundRegistry(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    public void init() throws IOException {
        Path path = assetManager.getAsset("minecraft/sounds.json");
        Objects.requireNonNull(path, "Asset sounds.json not found");

        String json = Files.readString(path, UTF_8);
        soundRegistry = new JSONObject(json);
    }

    public Optional<SoundRef> loadBySoundId(@Nullable String id) {
        if (id == null) {
            return Optional.empty();
        }

        JSONObject cfg = soundRegistry.optJSONObject(id);

        if (cfg == null) {
            return Optional.empty();
        }

        JSONArray sounds = cfg.optJSONArray("sounds");

        if (sounds == null || sounds.isEmpty()) {
            return Optional.empty();
        }

        Object item = sounds.get(0);

        if (item instanceof String s) {
            String assetPath = getSoundAssetPath(s);
            return Optional.of(createRef(assetPath, 1f, 1f));
        }

        if (!(item instanceof JSONObject sound)) {
            return Optional.empty();
        }

        String name = sound.optString("name");

        if (name == null) {
            return Optional.empty();
        }

        String assetPath = getSoundAssetPath(name);

        float volume = sound.optFloat("volume", 1f);
        float pitch = sound.optFloat("pitch", 1f);

        return Optional.of(createRef(assetPath, volume, pitch));
    }

    private static String getSoundAssetPath(String name) {
        int sep = name.indexOf(":");
        String namespace;

        if (sep == -1) {
            namespace = "minecraft";
        } else {
            namespace = name.substring(0, sep);
            name = name.substring(sep + 1);
        }

        return namespace + "/sounds/" + name + ".ogg";
    }

    public boolean has(String name) {
        return soundRegistry.has(name);
    }

    public SoundRef createRef(String assetPath, float volume, float pitch) {
        return new TestRef(assetPath, volume, pitch);
    }

    private class TestRef implements SoundRef {

        private final String assetPath;
        private final float volume;
        private final float pitch;

        private TestRef(String assetPath, float volume, float pitch) {
            this.assetPath = assetPath;
            this.volume = volume;
            this.pitch = pitch;
        }

        @Override
        public CompletableFuture<SoundSample> load() {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return loadSync();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to load sound sample", e);
                }
            });
        }

        private SoundSample loadSync() throws IOException {
            Path path = assetManager.getAsset(assetPath);

            if (path == null) {
                throw new IllegalStateException("Unknown asset: " + assetPath);
            }

            try (var in = new JOrbisAudioStream(Files.newInputStream(path))) {
                return new SoundSample(in.readAll(), in.getFormat());
            }
        }

        @Override
        public float volume() {
            return volume;
        }

        @Override
        public float pitch() {
            return pitch;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (TestRef) obj;
            return Objects.equals(this.assetPath, that.assetPath) &&
                    Float.floatToIntBits(this.volume) == Float.floatToIntBits(that.volume) &&
                    Float.floatToIntBits(this.pitch) == Float.floatToIntBits(that.pitch);
        }

        @Override
        public int hashCode() {
            return Objects.hash(assetPath, volume, pitch);
        }

        @Override
        public String toString() {
            return "Ref[assetPath=%s, volume=%s, pitch=%s]".formatted(assetPath, volume, pitch);
        }
    }
}
