package work.lclpnet.notica.network.packet;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import work.lclpnet.notica.NoticaInit;

public class StopSongBidiPacket implements FabricPacket {

    public static final PacketType<StopSongBidiPacket> TYPE =
            PacketType.create(NoticaInit.identifier("stop"), StopSongBidiPacket::new);

    private final Identifier songId;

    public StopSongBidiPacket(Identifier songId) {
        this.songId = songId;
    }

    public StopSongBidiPacket(PacketByteBuf buf) {
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
