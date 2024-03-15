package work.lclpnet.notica.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.NbtCompound;
import work.lclpnet.notica.api.MutablePlayerConfig;
import work.lclpnet.notica.api.PlayerConfig;

public class PlayerConfigEntry implements PlayerConfig, MutablePlayerConfig {

    private static final String VOLUME_KEY = "volume";

    private float volume = 1f;
    private boolean extendedRangeSupported = false;
    private boolean dirty = true;

    @Override
    public void setExtendedRangeSupported(boolean supported) {
        this.extendedRangeSupported = supported;
    }

    @Override
    public void setVolume(float volume) {
        if (this.volume == volume) return;
        this.volume = Math.max(0f, Math.min(1f, volume));
        markDirty();
    }

    @Override
    public boolean isExtendedRangeSupported() {
        return extendedRangeSupported;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    public boolean isDirty() {
        return dirty;
    }

    protected void markDirty() {
        dirty = true;
    }

    void markClean() {
        dirty = false;
    }

    public void writeNbt(NbtCompound nbt) {
        nbt.putFloat(VOLUME_KEY, volume);
    }

    public void readNbt(NbtCompound nbt) {
        if (nbt.contains(VOLUME_KEY)) {
            this.volume = nbt.getFloat(VOLUME_KEY);
        }
    }

    @Environment(EnvType.CLIENT)
    public void copyClient(PlayerConfig config) {
        // copy config on the client
        this.volume = config.getVolume();
    }
}
