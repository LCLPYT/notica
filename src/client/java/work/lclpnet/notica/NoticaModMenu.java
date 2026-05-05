package work.lclpnet.notica;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import work.lclpnet.notica.config.ConfigScreenBuilder;

public class NoticaModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return ModMenuApi.super.getModConfigScreenFactory();
        }

        return parent -> NoticaClientInit.configManager()
                .map(ConfigScreenBuilder::new)
                .map(builder -> builder.create(parent))
                .orElse(null);
    }
}
