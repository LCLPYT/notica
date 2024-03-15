package work.lclpnet.notica.network.packet;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import work.lclpnet.notica.NoticaInit;

public class RequestSongC2SPacket implements FabricPacket {

    public static final PacketType<RequestSongC2SPacket> TYPE =
            PacketType.create(NoticaInit.identifier("request"), RequestSongC2SPacket::new);

    private final Identifier songId;
    private final int tickOffset, layerOffset;

    public RequestSongC2SPacket(Identifier songId, int tickOffset, int layerOffset) {
        this.songId = songId;
        this.tickOffset = tickOffset;
        this.layerOffset = layerOffset;
    }

    public RequestSongC2SPacket(PacketByteBuf buf) {
        this.songId = buf.readIdentifier();
        this.tickOffset = buf.readInt();
        this.layerOffset = buf.readInt();
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(songId);
        buf.writeInt(tickOffset);
        buf.writeInt(layerOffset);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public Identifier getSongId() {
        return songId;
    }

    public int getTickOffset() {
        return tickOffset;
    }

    public int getLayerOffset() {
        return layerOffset;
    }
}
