package work.lclpnet.kibu.nbs;

import net.minecraft.server.MinecraftServer;
import work.lclpnet.kibu.nbs.impl.KibuNbsApiImpl;

public interface KibuNbsAPI {

    static KibuNbsAPI getInstance(MinecraftServer server) {
        return KibuNbsApiImpl.getInstance(server);
    }
}
