package dev.lavalink.youtube.plugin;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor.Version;
import java.net.URL;
import java.util.Properties;

public class PluginInfo {
    private static final Logger log = LoggerFactory.getLogger(PluginInfo.class);
    public static final String VERSION = getPluginVersion();

    static {
        try {
            checkForNewRelease();
        } catch (Throwable ignored) {

        }
    }

    public static String getPluginVersion() {
        try (InputStream properties = PluginInfo.class.getClassLoader().getResourceAsStream("lavalink-plugins/youtube-plugin.properties")) {
            Properties props = new Properties();
            props.load(properties);

            if (props.containsKey("version")) {
                return props.getProperty("version");
            }
        } catch (IOException ignored) {

        }

        return "Unknown";
    }

    public static void checkForNewRelease() throws IOException, JsonParserException {
        if ("Unknown".equals(VERSION)) {
            return; // Cannot compare versions.
        }

        Version currentVersion = Version.parse(VERSION);

        URL url = new URL("https://api.github.com/repos/lavalink-devs/youtube-source/releases");

        try (InputStream body = url.openStream()) {
            final JsonArray json = JsonParser.array().from(body);

            Version latestVersion = null;
            JsonObject latestRelease = null;

            for (int i = 0; i < json.size(); i++) {
                JsonObject release = json.getObject(i);

                if (!release.has("tag_name") || release.isNull("tag_name") || release.getBoolean("draft", false)) {
                    continue;
                }

                Version version = Version.parse(release.getString("tag_name"));

                if (latestVersion == null || version.compareTo(latestVersion) > 0) {
                    latestVersion = version;
                    latestRelease = release;
                }
            }

            if (latestVersion != null && latestVersion.compareTo(currentVersion) > 0) {
                log.info("********************************************\n" +
                    "YOUTUBE-SOURCE VERSION {} AVAILABLE\n" +
                    "{}\n" +
                    "Update to ensure the YouTube source remains operational!\n" +
                    "********************************************", latestVersion, latestRelease.getString("html_url"));
            }
        }
    }
}
