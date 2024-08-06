package dev.lavalink.youtube;

import java.io.IOException;
import java.io.InputStream;

public class YoutubeSource {
    public static String VERSION = "Unknown";

    static {
        try (InputStream versionStream = YoutubeSource.class.getResourceAsStream("/yts-version.txt")) {
            if (versionStream != null) {
                byte[] content = new byte[versionStream.available()];
                versionStream.read(content);

                String versionS = new String(content);

                if (!versionS.startsWith("@")) {
                    VERSION = versionS;
                }
            }
        } catch (IOException ignored) {

        }
    }
}
