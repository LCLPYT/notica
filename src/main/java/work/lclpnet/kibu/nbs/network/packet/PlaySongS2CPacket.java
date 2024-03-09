package work.lclpnet.kibu.nbs.network.packet;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import work.lclpnet.kibu.nbs.KibuNbsInit;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;
import work.lclpnet.kibu.nbs.network.SongHeader;

public class PlaySongS2CPacket implements FabricPacket {

    public static final PacketType<PlaySongS2CPacket> TYPE =
            PacketType.create(KibuNbsInit.identifier("play"), PlaySongS2CPacket::new);

    private final SongDescriptor songDescriptor;
    private final float volume;
    private final SongHeader header;
    private final SongSlice slice;

    public PlaySongS2CPacket(SongDescriptor songDescriptor, float volume, SongHeader header, SongSlice slice) {
        this.songDescriptor = songDescriptor;
        this.volume = volume;
        this.header = header;
        this.slice = null;
    }

    public PlaySongS2CPacket(PacketByteBuf buf) {
        this.songDescriptor = SongDescriptor.read(buf);
        this.volume = buf.readFloat();
        this.header = new SongHeader(buf);
        slice = null;
    }

    @Override
    public void write(PacketByteBuf buf) {
        songDescriptor.write(buf);
        buf.writeFloat(volume);
        header.write(buf);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public SongDescriptor getSongDescriptor() {
        return songDescriptor;
    }

    public float getVolume() {
        return volume;
    }
}
