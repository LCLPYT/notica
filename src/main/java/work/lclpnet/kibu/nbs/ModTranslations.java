package work.lclpnet.kibu.nbs;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import work.lclpnet.kibu.translate.TranslationService;
import work.lclpnet.translations.DefaultLanguageTranslator;
import work.lclpnet.translations.loader.language.UrlLanguageLoader;
import work.lclpnet.translations.loader.translation.DirectTranslationLoader;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

class ModTranslations {

    private ModTranslations() {}

    public static Result load(Logger logger) {
        var locations = FabricLoader.getInstance()
                .getModContainer(KibuNbsInit.MOD_ID)
                .orElseThrow(() -> new NoSuchElementException("Failed to find mod container"))
                .getRootPaths()
                .stream()
                .map(path -> {
                    try {
                        return path.toUri().toURL();
                    } catch (MalformedURLException e) {
                        logger.error("Failed to convert path {} to url", path, e);
                        return null;
                    }
                }).filter(Objects::nonNull)
                .toArray(URL[]::new);

        var langLoader = new UrlLanguageLoader(locations, List.of("assets/" + KibuNbsInit.MOD_ID + "/lang/"), logger);
        var translationLoader = new DirectTranslationLoader(langLoader);

        DefaultLanguageTranslator translator = new DefaultLanguageTranslator(translationLoader);

        TranslationService translationService = new TranslationService(translator, player -> Optional.empty());
        var whenLoaded = translator.reload();

        return new Result(translationService, whenLoaded);
    }

    public record Result(TranslationService translations, CompletableFuture<Void> whenLoaded) {}
}
