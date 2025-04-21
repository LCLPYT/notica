package work.lclpnet.notica;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import work.lclpnet.notica.cmd.TestCommand;

public class NoticaTestInit implements ModInitializer {

    @Override
    public void onInitialize() {
        NoticaInit.LOGGER.info("Notica test mod loaded.");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env)
                -> new TestCommand().register(dispatcher));
    }
}
