package work.lclpnet.notica.impl;

import net.minecraft.server.level.ServerPlayer;
import work.lclpnet.notica.api.PlayerConfig;
import work.lclpnet.notica.api.PlayerHolder;

import java.util.Objects;

public class SongPlayerRef implements PlayerHolder {

    private ServerPlayer player;
    private final PlayerConfig config;

    public SongPlayerRef(ServerPlayer player, PlayerConfig config) {
        this.player = Objects.requireNonNull(player, "Player must not be null");
        this.config = config;
    }

    @Override
    public void updatePlayer(ServerPlayer player) {
        this.player = Objects.requireNonNull(player, "New player must not be null");
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public PlayerConfig getConfig() {
        return config;
    }
}
