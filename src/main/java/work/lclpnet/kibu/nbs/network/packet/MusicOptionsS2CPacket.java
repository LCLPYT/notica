package work.lclpnet.kibu.nbs.network.packet;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import work.lclpnet.kibu.nbs.KibuNbsInit;
import work.lclpnet.kibu.nbs.api.PlayerConfig;
import work.lclpnet.kibu.nbs.util.PlayerConfigEntry;

public class MusicOptionsS2CPacket implements FabricPacket {
    public static final PacketType<MusicOptionsS2CPacket> TYPE =
            PacketType.create(KibuNbsInit.identifier("options"), MusicOptionsS2CPacket::new);

    private final PlayerConfig config;

    public MusicOptionsS2CPacket(PlayerConfig config) {
        this.config = config;
    }

    public MusicOptionsS2CPacket(PacketByteBuf buf) {
        this.config = readConfig(buf);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeFloat(config.getVolume());
        // no need to write extended range support as clients automatically support it
    }

    private static PlayerConfig readConfig(PacketByteBuf buf) {
        PlayerConfigEntry config = new PlayerConfigEntry();

        float volume = buf.readFloat();
        config.setVolume(volume);

        return config;
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public PlayerConfig getConfig() {
        return config;
    }
}
