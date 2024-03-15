package work.lclpnet.notica.util;

import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.notica.config.ConfigAccess;
import work.lclpnet.notica.impl.NoticaImpl;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.util.Formatting.GREEN;
import static net.minecraft.util.Formatting.RED;

public class NoticaServerPackManager {

    private final ConfigAccess configAccess;
    private final TranslationService translations;
    private final Logger logger;
    private final Set<UUID> requesting = new HashSet<>();
    private final Set<UUID> installed = new HashSet<>();
    private UUID packUuid = null;

    public NoticaServerPackManager(ConfigAccess configAccess, TranslationService translations, Logger logger) {
        this.configAccess = configAccess;
        this.translations = translations;
        this.logger = logger;
    }

    public void sendServerPack(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        if (installed.contains(playerUuid) || requesting.contains(playerUuid)) return;

        URL url = configAccess.getConfig().extraNotesPackUrl;

        if (url == null) {
            sendError(player);
            return;
        }

        String urlString = url.toString();

        UUID packUuid = UUID.nameUUIDFromBytes(urlString.getBytes(StandardCharsets.UTF_8));
        var prompt = translations.translateText(player, "notica.music.server_pack_prompt").formatted(GREEN);
        var packet = new ResourcePackSendS2CPacket(packUuid, urlString, "", false, prompt);

        this.packUuid = packUuid;
        player.networkHandler.sendPacket(packet);
    }

    public boolean hasServerPackInstalled(ServerPlayerEntity player) {
        return installed.contains(player.getUuid());
    }

    public void onPlayerQuit(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        requesting.remove(uuid);
        installed.remove(uuid);
    }

    public void onResourcePackStatus(ServerPlayerEntity player, ResourcePackStatusC2SPacket packet) {
        if (packUuid == null || !packUuid.equals(packet.id())) return;

        var status = packet.status();
        logger.debug("Player {} sent server resource pack status {}", player.getNameForScoreboard(), status);

        switch (status) {
            case SUCCESSFULLY_LOADED -> onSuccess(player);
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> onFail(player);
            default -> {}
        }
    }

    private void onSuccess(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        installed.add(uuid);
        requesting.remove(uuid);

        NoticaImpl instance = NoticaImpl.getInstance(player.getServer());
        PlayerConfigContainer configs = instance.getPlayerConfigs();
        configs.get(player).setExtendedRangeSupported(true);

        var msg = translations.translateText(player, "notica.music.server_pack_success").formatted(GREEN);
        player.sendMessage(msg);
    }

    private void onFail(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        installed.remove(uuid);
        requesting.remove(uuid);

        NoticaImpl instance = NoticaImpl.getInstance(player.getServer());
        PlayerConfigContainer configs = instance.getPlayerConfigs();
        configs.get(player).setExtendedRangeSupported(false);

        sendError(player);
    }

    private void sendError(ServerPlayerEntity player) {
        var msg = translations.translateText(player, "notica.music.server_pack_failed").formatted(RED);
        player.sendMessage(msg);
    }

    public boolean isEnabled() {
        return configAccess.getConfig().extraNotesPackUrl != null;
    }
}
