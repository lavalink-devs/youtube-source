package dev.lavalink.youtube;

import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public class YoutubeSource {
    private static final Logger log = LoggerFactory.getLogger(YoutubeSource.class);

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

    /**
     * Sets the given PoToken and VisitorData pair on all POT-supporting clients.
     * This is a convenience method to allow for setting this from one method call.
     * @param poToken The poToken to use. This must be paired to the specified visitorData.
     *                You may specify {@code null} to unset.
     * @param visitorData The visitorData to use. This must be paired to the specified poToken.
     *                    You may specify {@code null} to unset.
     */
    public static void setPoTokenAndVisitorData(String poToken, String visitorData) {
        log.debug("Applying pot: {} vd: {} to WEB, WEBEMBEDDED", poToken, visitorData);
        Web.setPoTokenAndVisitorData(poToken, visitorData);
        WebEmbedded.setPoTokenAndVisitorData(poToken, visitorData);
    }
}
