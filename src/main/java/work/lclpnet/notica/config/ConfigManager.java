package work.lclpnet.notica.config;

import org.slf4j.Logger;
import work.lclpnet.config.json.ConfigHandler;
import work.lclpnet.config.json.FileConfigSerializer;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class ConfigManager implements ConfigAccess {

    private final ConfigHandler<NoticaConfig> handler;

    public ConfigManager(Path configPath, Logger logger) {
        var serializer = new FileConfigSerializer<>(NoticaConfig.FACTORY, logger);

        handler = new ConfigHandler<>(configPath, serializer, logger);
        handler.setConfig(new NoticaConfig());  // set default config for immediate access, actual config is loaded async
    }

    @Override
    public NoticaConfig getConfig() {
        return handler.getConfig();
    }

    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(handler::loadConfig);
    }
}
