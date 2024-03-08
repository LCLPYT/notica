package work.lclpnet.kibu.nbs.util;

import net.minecraft.nbt.NbtCompound;
import work.lclpnet.kibu.nbs.api.MutablePlayerConfig;
import work.lclpnet.kibu.nbs.api.PlayerConfig;

public class PlayerConfigEntry implements PlayerConfig, MutablePlayerConfig {

    private static final String
            EXTENDED_RANGE_KEY = "extendedRange",
            VOLUME_KEY = "volume";

    private float volume = 1f;
    private boolean extendedRangeSupported = false;
    private boolean dirty = true;

    @Override
    public void setExtendedRangeSupported(boolean supported) {
        if (extendedRangeSupported == supported) return;
        this.extendedRangeSupported = supported;
        markDirty();
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
        nbt.putBoolean(EXTENDED_RANGE_KEY, extendedRangeSupported);
    }

    public void readNbt(NbtCompound nbt) {
        if (nbt.contains(VOLUME_KEY)) {
            this.volume = nbt.getFloat(VOLUME_KEY);
        }

        if (nbt.contains(EXTENDED_RANGE_KEY)) {
            this.extendedRangeSupported = nbt.getBoolean(EXTENDED_RANGE_KEY);
        }
    }
}
