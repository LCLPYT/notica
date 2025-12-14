package work.lclpnet.notica.util;

import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import work.lclpnet.kibu.translate.Translations;
import work.lclpnet.notica.config.ConfigAccess;
import work.lclpnet.notica.impl.NoticaImpl;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.ChatFormatting.GREEN;
import static net.minecraft.ChatFormatting.RED;

public class NoticaServerPackManager {

    private final ConfigAccess configAccess;
    private final Translations translations;
    private final Logger logger;
    private final Set<UUID> requesting = new HashSet<>();
    private final Set<UUID> installed = new HashSet<>();
    private UUID packUuid = null;

    public NoticaServerPackManager(ConfigAccess configAccess, Translations translations, Logger logger) {
        this.configAccess = configAccess;
        this.translations = translations;
        this.logger = logger;
    }

    public void sendServerPack(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        if (installed.contains(playerUuid) || !requesting.add(playerUuid)) return;

        URL url = configAccess.getConfig().extraNotesPackUrl;

        if (url == null) {
            sendError(player);
            return;
        }

        String urlString = url.toString();

        UUID packUuid = UUID.nameUUIDFromBytes(urlString.getBytes(StandardCharsets.UTF_8));
        var prompt = translations.translateText(player, "notica.music.server_pack_prompt").formatted(GREEN);
        var packet = new ClientboundResourcePackPushPacket(packUuid, urlString, "", false, Optional.of(prompt));

        this.packUuid = packUuid;
        player.connection.send(packet);
    }

    public boolean hasServerPackInstalled(ServerPlayer player) {
        return installed.contains(player.getUUID());
    }

    public void onPlayerQuit(ServerPlayer player) {
        UUID uuid = player.getUUID();
        requesting.remove(uuid);
        installed.remove(uuid);
    }

    public void onResourcePackStatus(ServerPlayer player, ServerboundResourcePackPacket packet) {
        if (packUuid == null || !packUuid.equals(packet.id())) return;

        var status = packet.action();
        logger.debug("Player {} sent server resource pack status {}", player.getScoreboardName(), status);

        switch (status) {
            case SUCCESSFULLY_LOADED -> onSuccess(player);
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> onFail(player);
            default -> {}
        }
    }

    private void onSuccess(ServerPlayer player) {
        UUID uuid = player.getUUID();
        installed.add(uuid);
        requesting.remove(uuid);

        NoticaImpl instance = NoticaImpl.getInstance(player.level().getServer());
        PlayerConfigContainer configs = instance.getPlayerConfigs();
        configs.get(player).setExtendedRangeSupported(true);

        var msg = translations.translateText(player, "notica.music.server_pack_success").formatted(GREEN);
        player.sendSystemMessage(msg);
    }

    private void onFail(ServerPlayer player) {
        UUID uuid = player.getUUID();
        installed.remove(uuid);
        requesting.remove(uuid);

        NoticaImpl instance = NoticaImpl.getInstance(player.level().getServer());
        PlayerConfigContainer configs = instance.getPlayerConfigs();
        configs.get(player).setExtendedRangeSupported(false);

        sendError(player);
    }

    private void sendError(ServerPlayer player) {
        var msg = translations.translateText(player, "notica.music.server_pack_failed").formatted(RED);
        player.sendSystemMessage(msg);
    }

    public boolean isEnabled() {
        return configAccess.getConfig().extraNotesPackUrl != null;
    }
}
