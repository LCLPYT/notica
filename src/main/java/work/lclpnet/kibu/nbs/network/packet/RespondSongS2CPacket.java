package work.lclpnet.kibu.nbs.network.packet;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import work.lclpnet.kibu.nbs.KibuNbsInit;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.network.SongSlicer;

public class RespondSongS2CPacket implements FabricPacket {

    public static final PacketType<RespondSongS2CPacket> TYPE =
            PacketType.create(KibuNbsInit.identifier("respond"), RespondSongS2CPacket::new);

    private final Identifier songId;
    private final SongSlice slice;

    public RespondSongS2CPacket(Identifier songId, SongSlice slice) {
        this.songId = songId;
        this.slice = slice;
    }

    public RespondSongS2CPacket(PacketByteBuf buf) {
        this.songId = buf.readIdentifier();
        this.slice = SongSlicer.readSlice(buf);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(songId);
        SongSlicer.writeSlice(buf, slice);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public Identifier getSongId() {
        return songId;
    }

    public SongSlice getSlice() {
        return slice;
    }
}
