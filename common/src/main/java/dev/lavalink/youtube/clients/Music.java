package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.skeleton.MusicClient;

public class Music extends MusicClient {
    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withApiKey("AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30") // Requires header (Referer music.youtube.com)
        .withClientName("WEB_REMIX")
        .withClientField("clientVersion", "1.20240401.00.00");

    @Override
    public ClientConfig getBaseClientConfig(HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
    }

    @Override
    public String getPlayerParams() {
        // This client is not used for format loading so, we don't have
        // any player parameters attached to it.
        throw new UnsupportedOperationException();
    }

    @Override
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }
}
