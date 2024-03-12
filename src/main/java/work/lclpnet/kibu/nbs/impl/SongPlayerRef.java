package work.lclpnet.kibu.nbs.impl;

import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.nbs.api.PlayerConfig;
import work.lclpnet.kibu.nbs.api.PlayerHolder;

import java.util.Objects;

public class SongPlayerRef implements PlayerHolder {

    private ServerPlayerEntity player;
    private final PlayerConfig config;

    public SongPlayerRef(ServerPlayerEntity player, PlayerConfig config) {
        this.player = Objects.requireNonNull(player, "Player must not be null");
        this.config = config;
    }

    @Override
    public void updatePlayer(ServerPlayerEntity player) {
        this.player = Objects.requireNonNull(player, "New player must not be null");
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

    public PlayerConfig getConfig() {
        return config;
    }
}
