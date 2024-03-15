package work.lclpnet.notica.impl;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.notica.api.InstrumentSoundProvider;
import work.lclpnet.notica.api.data.CustomInstrument;

public class ClientInstrumentSoundProvider implements InstrumentSoundProvider {

    private DynamicRegistryManager registryManager = null;
    private FabricInstrumentSoundProvider parent = null;

    @Override
    public @Nullable SoundEvent getVanillaInstrumentSound(byte instrument) {
        return parent().getVanillaInstrumentSound(instrument);
    }

    @Override
    public @Nullable SoundEvent getCustomInstrumentSound(CustomInstrument instrument) {
        return parent().getCustomInstrumentSound(instrument);
    }

    @Override
    public @NotNull SoundEvent getExtendedSound(@NotNull SoundEvent sound, byte key, short pitch) {
        return parent().getExtendedSound(sound, key, pitch);
    }

    private FabricInstrumentSoundProvider parent() {
        FabricInstrumentSoundProvider parent = this.parent;

        if (parent == null) {
            throw new IllegalStateException("Cannot access songs registry when not in a world");
        }

        return parent;
    }

    public void setRegistryManager(@Nullable DynamicRegistryManager registryManager) {
        if (this.registryManager == registryManager) return;

        this.registryManager = registryManager;

        if (registryManager == null) {
            this.parent = null;
        } else {
            this.parent = new FabricInstrumentSoundProvider(registryManager);
        }
    }
}
