package work.lclpnet.notica.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Injects custom {@link WeighedSoundEvents}s for sound id's referencing direct sounds.
 * Normally, multiple direct sounds are registered under a sound id.
 * A lot of note block songs using custom sounds reference sounds directly.
 * This class makes it possible to play those sounds directly.
 */
public class DirectSoundManager {

    private static final FloatProvider ONE = ConstantFloat.of(1.0F);
    private final Map<Identifier, WeighedSoundEvents> soundSetOverrides = new HashMap<>();

    @Nullable
    public synchronized WeighedSoundEvents getSoundSet(Identifier id) {
        return soundSetOverrides.computeIfAbsent(id, this::tryParseDirect);
    }

    @Nullable
    private WeighedSoundEvents tryParseDirect(Identifier id) {
        if (!"minecraft".equals(id.getNamespace())) {
            return null;
        }

        Identifier directId = parseAsPathSegment(id.getPath());

        if (directId == null) {
            return null;
        }

        Sound sound = fetchSound(directId);

        var set = new WeighedSoundEvents(id, null);
        set.addSound(sound);

        return set;
    }

    @Nullable
    private Sound fetchSound(Identifier id) {
        Identifier resource = Sound.SOUND_LISTER.idToFile(id);

        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();

        if (resourceManager.getResource(resource).isEmpty()) {
            // sound resource doesn't exist
            return null;
        }

        return new Sound(id, ONE, ONE, 1, Sound.Type.FILE, false, false, 16);
    }

    private @Nullable Identifier parseAsPathSegment(String str) {
        int firstSlash = str.indexOf('/');

        if (firstSlash == -1 || firstSlash >= str.length() - 1) return null;

        String namespace = str.substring(0, firstSlash);
        String path = str.substring(firstSlash + 1);

        return Identifier.tryBuild(namespace, path);
    }
}
