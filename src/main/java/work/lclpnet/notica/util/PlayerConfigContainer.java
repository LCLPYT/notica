package work.lclpnet.notica.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class PlayerConfigContainer {

    private final Path directory;
    private final Logger logger;
    private final Map<UUID, PlayerConfigEntry> entries = Collections.synchronizedMap(new HashMap<>());

    public PlayerConfigContainer(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    @NotNull
    public PlayerConfigEntry get(ServerPlayer player) {
        return entries.computeIfAbsent(player.getUUID(), p -> new PlayerConfigEntry());
    }

    public void onPlayerJoin(ServerPlayer player) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return loadConfigNbt(player);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }).exceptionally(error -> {
            logger.error("Failed to load player config", error);
            return null;
        }).thenAccept(nbt -> {
            if (nbt == null) return;

            loadConfig(player, nbt);

            logger.debug("Restored player config of {}", player.getUUID());
        });
    }

    public void onPlayerQuit(ServerPlayer player) {
        PlayerConfigEntry config = entries.remove(player.getUUID());
        if (config == null) return;

        saveConfigAsync(player, config);
    }

    public void saveConfig(ServerPlayer player) {
        PlayerConfigEntry config = get(player);
        saveConfigAsync(player, config);
    }

    private void saveConfigAsync(ServerPlayer player, PlayerConfigEntry config) {
        CompletableFuture.runAsync(() -> {
            try {
                saveConfig(player, config);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((res, err) -> {
            if (err != null) {
                logger.error("Failed to save player config", err);
            } else {
                logger.debug("Wrote player config of {}", player.getUUID());
            }
        });
    }

    private CompoundTag loadConfigNbt(ServerPlayer player) throws IOException {
        Path path = getPath(player);

        if (!Files.exists(path)) return null;

        try (var in = Files.newInputStream(path)) {
            return NbtIo.readCompressed(in, NbtAccounter.create(16384));
        }
    }

    private void saveConfigNbt(ServerPlayer player, CompoundTag nbt) throws IOException {
        Path path = getPath(player);
        Path dir = path.getParent();

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        try (var out = Files.newOutputStream(path)) {
            NbtIo.writeCompressed(nbt, out);
        }
    }

    private void loadConfig(ServerPlayer player, CompoundTag rootNbt) {
        PlayerConfigEntry config = get(player);
        config.readNbt(rootNbt);
    }

    private void saveConfig(ServerPlayer player, PlayerConfigEntry config) throws IOException {
        if (!config.isDirty()) return;

        CompoundTag rootNbt = new CompoundTag();
        config.writeNbt(rootNbt);

        config.markClean();

        saveConfigNbt(player, rootNbt);
    }

    private Path getPath(ServerPlayer player) {
        UUID uuid = player.getUUID();
        return directory.resolve(uuid.toString() + ".dat");
    }
}
