package work.lclpnet.notica.config;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import work.lclpnet.config.json.JsonConfig;
import work.lclpnet.config.json.JsonConfigFactory;

import java.net.MalformedURLException;
import java.net.URL;

public class NoticaConfig implements JsonConfig {

    @Nullable
    public URL extraNotesPackUrl = null;

    public NoticaConfig() {
        setDefaultUrl();
    }

    public NoticaConfig(JSONObject json) {
        setDefaultUrl();

        if (json.has("extra-notes-pack-url")) {
            String urlString = json.getString("extra-notes-pack-url");

            try {
                this.extraNotesPackUrl = new URL(urlString);
            } catch (MalformedURLException e) {
                this.extraNotesPackUrl = null;
            }
        }
    }

    private void setDefaultUrl() {
        try {
            this.extraNotesPackUrl = new URL("https://github.com/LCLPYT/notica/releases/download/1.0.0%2B1.20.4/extranotes.zip");
        } catch (MalformedURLException e) {
            this.extraNotesPackUrl = null;
        }
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();

        json.put("extra-notes-pack-url", extraNotesPackUrl == null ? JSONObject.NULL : extraNotesPackUrl.toString());

        return json;
    }

    public static final JsonConfigFactory<NoticaConfig> FACTORY = new JsonConfigFactory<>() {
        @Override
        public NoticaConfig createDefaultConfig() {
            return new NoticaConfig();
        }

        @Override
        public NoticaConfig createConfig(JSONObject json) {
            return new NoticaConfig(json);
        }
    };
}
