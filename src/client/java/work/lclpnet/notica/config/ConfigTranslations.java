package work.lclpnet.notica.config;

import java.util.Locale;

import static java.lang.String.join;
import static work.lclpnet.notica.NoticaInit.MOD_ID;

public class ConfigTranslations {

    public static final String
            TITLE = MOD_ID + ".config.title",
            DESC = MOD_ID + ".config.desc",
            ENUM = MOD_ID + ".config.enum",
            ENUM_DESC = MOD_ID + ".config.enum_desc";

    public static String optionTitleKey(String path) {
        return join(".", TITLE, path);
    }

    public static String optionDescKey(String path) {
        return join(".", DESC, path);
    }

    public static String enumNameKey(Enum<?> enumVal, String path) {
        return join(".", ENUM, path, enumVal.name().toLowerCase(Locale.ROOT));
    }

    public static String enumDescKey(Enum<?> enumVal, String path) {
        return join(".", ENUM_DESC, path, enumVal.name().toLowerCase(Locale.ROOT));
    }
}
