package work.lclpnet.kibu.nbs.network;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import work.lclpnet.kibu.nbs.network.packet.PlaySongS2CPacket;
import work.lclpnet.kibu.nbs.network.packet.RequestSongC2SPacket;

import static net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend;

public class KibuNbsNetworking {

    public static final int MAX_PACKET_BYTES = 0x800000;  // packet size limit imposed by mc

    public static boolean canSendAll(ServerPlayerEntity player) {
        return canSend(player, PlaySongS2CPacket.TYPE);
    }

    public void register() {
        ServerPlayNetworking.registerGlobalReceiver(RequestSongC2SPacket.TYPE, this::onRequestSong);
    }

    private void onRequestSong(RequestSongC2SPacket packet, ServerPlayerEntity player, PacketSender sender) {
        System.out.println("client " + player.getNameForScoreboard() + " requested song " + packet.getSongId());
    }
}
