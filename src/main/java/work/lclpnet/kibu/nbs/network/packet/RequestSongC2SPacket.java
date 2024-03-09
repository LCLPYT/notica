package work.lclpnet.kibu.nbs.network.packet;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import work.lclpnet.kibu.nbs.KibuNbsInit;

public class RequestSongC2SPacket implements FabricPacket {

    public static final PacketType<RequestSongC2SPacket> TYPE =
            PacketType.create(KibuNbsInit.identifier("request"), RequestSongC2SPacket::new);

    private final Identifier songId;

    public RequestSongC2SPacket(Identifier songId) {
        this.songId = songId;
    }

    public RequestSongC2SPacket(PacketByteBuf buf) {
        this.songId = buf.readIdentifier();
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(songId);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public Identifier getSongId() {
        return songId;
    }
}
