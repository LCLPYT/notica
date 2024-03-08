package work.lclpnet.kibu.nbs.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class PlayerConfigContainer {

    private final Path directory;
    private final Logger logger;
    private final Map<UUID, PlayerConfigEntry> entries = new HashMap<>();

    public PlayerConfigContainer(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    @NotNull
    public PlayerConfigEntry get(ServerPlayerEntity player) {
        return entries.computeIfAbsent(player.getUuid(), p -> new PlayerConfigEntry());
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
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

            logger.debug("Restored player config of {}", player.getUuid());
        });
    }

    public void onPlayerQuit(ServerPlayerEntity player) {
        PlayerConfigEntry config = entries.remove(player.getUuid());
        if (config == null) return;

        saveConfigAsync(player, config);
    }

    public void saveConfig(ServerPlayerEntity player) {
        PlayerConfigEntry config = get(player);
        saveConfigAsync(player, config);
    }

    private void saveConfigAsync(ServerPlayerEntity player, PlayerConfigEntry config) {
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
                logger.debug("Wrote player config of {}", player.getUuid());
            }
        });
    }

    private NbtCompound loadConfigNbt(ServerPlayerEntity player) throws IOException {
        Path path = getPath(player);

        if (!Files.exists(path)) return null;

        try (var in = Files.newInputStream(path)) {
            return NbtIo.readCompressed(in, NbtSizeTracker.of(16384));
        }
    }

    private void saveConfigNbt(ServerPlayerEntity player, NbtCompound nbt) throws IOException {
        Path path = getPath(player);
        Path dir = path.getParent();

        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        try (var out = Files.newOutputStream(path)) {
            NbtIo.writeCompressed(nbt, out);
        }
    }

    private void loadConfig(ServerPlayerEntity player, NbtCompound rootNbt) {
        PlayerConfigEntry config = get(player);
        config.readNbt(rootNbt);
    }

    private void saveConfig(ServerPlayerEntity player, PlayerConfigEntry config) throws IOException {
        if (!config.isDirty()) return;

        NbtCompound rootNbt = new NbtCompound();
        config.writeNbt(rootNbt);

        config.markClean();

        saveConfigNbt(player, rootNbt);
    }

    private Path getPath(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        return directory.resolve(uuid.toString() + ".dat");
    }
}
