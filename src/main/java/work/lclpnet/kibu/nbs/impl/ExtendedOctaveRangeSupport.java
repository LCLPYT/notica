package work.lclpnet.kibu.nbs.impl;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ExtendedOctaveRangeSupport {

    private final Map<UUID, BoolExtendedOctaveRange> storage = new HashMap<>();

    @NotNull
    public BoolExtendedOctaveRange get(ServerPlayerEntity player) {
        return storage.computeIfAbsent(player.getUuid(), p -> new BoolExtendedOctaveRange());
    }

    public void setSupported(ServerPlayerEntity player, boolean supported) {
        get(player).setSupported(supported);
    }

    public void onPlayerQuit(ServerPlayerEntity player) {
        storage.remove(player.getUuid());
    }
}
