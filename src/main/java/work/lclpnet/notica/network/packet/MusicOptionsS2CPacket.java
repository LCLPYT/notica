package work.lclpnet.notica.network.packet;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import work.lclpnet.notica.NoticaInit;
import work.lclpnet.notica.api.PlayerConfig;
import work.lclpnet.notica.util.PlayerConfigEntry;

public class MusicOptionsS2CPacket implements FabricPacket {
    public static final PacketType<MusicOptionsS2CPacket> TYPE =
            PacketType.create(NoticaInit.identifier("options"), MusicOptionsS2CPacket::new);

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
