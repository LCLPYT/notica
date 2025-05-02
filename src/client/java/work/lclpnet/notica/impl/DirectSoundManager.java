package work.lclpnet.notica.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.floatprovider.ConstantFloatProvider;
import net.minecraft.util.math.floatprovider.FloatProvider;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Injects custom {@link WeightedSoundSet}s for sound id's referencing direct sounds.
 * Normally, multiple direct sounds are registered under a sound id.
 * A lot of note block songs using custom sounds reference sounds directly.
 * This class makes it possible to play those sounds directly.
 */
public class DirectSoundManager {

    private static final FloatProvider ONE = ConstantFloatProvider.create(1.0F);
    private final Map<Identifier, WeightedSoundSet> soundSetOverrides = new HashMap<>();

    @Nullable
    public synchronized WeightedSoundSet getSoundSet(Identifier id) {
        return soundSetOverrides.computeIfAbsent(id, this::tryParseDirect);
    }

    @Nullable
    private WeightedSoundSet tryParseDirect(Identifier id) {
        if (!"minecraft".equals(id.getNamespace())) {
            return null;
        }

        Identifier directId = parseAsPathSegment(id.getPath());

        if (directId == null) {
            return null;
        }

        Sound sound = fetchSound(directId);

        var set = new WeightedSoundSet(id, null);
        set.add(sound);

        return set;
    }

    @Nullable
    private Sound fetchSound(Identifier id) {
        Identifier resource = Sound.FINDER.toResourcePath(id);

        ResourceManager resourceManager = MinecraftClient.getInstance().getResourceManager();

        if (resourceManager.getResource(resource).isEmpty()) {
            // sound resource doesn't exist
            return null;
        }

        return new Sound(id, ONE, ONE, 1, Sound.RegistrationType.FILE, false, false, 16);
    }

    private @Nullable Identifier parseAsPathSegment(String str) {
        int firstSlash = str.indexOf('/');

        if (firstSlash == -1 || firstSlash >= str.length() - 1) return null;

        String namespace = str.substring(0, firstSlash);
        String path = str.substring(firstSlash + 1);

        return Identifier.tryParse(namespace, path);
    }
}
