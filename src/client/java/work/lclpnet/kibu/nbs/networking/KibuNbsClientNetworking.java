package work.lclpnet.kibu.nbs.networking;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import work.lclpnet.kibu.nbs.network.packet.PlaySongS2CPacket;

public class KibuNbsClientNetworking {

    public void register() {
        ClientPlayNetworking.registerGlobalReceiver(PlaySongS2CPacket.TYPE, this::onPlaySong);
    }

    private void onPlaySong(PlaySongS2CPacket packet, ClientPlayerEntity player, PacketSender sender) {
        player.sendMessage(Text.literal("play " + packet.getSongDescriptor().id().toString() + " " + packet.getVolume()));
    }
}
