package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.skeleton.MusicClient;
import org.jetbrains.annotations.NotNull;

public class Music extends MusicClient {
    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withApiKey("AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30") // Requires header (Referer music.youtube.com)
        .withClientName("WEB_REMIX")
        .withClientField("clientVersion", "1.20240401.00.00");

    protected ClientOptions options;

    public Music() {
        this(ClientOptions.DEFAULT);
    }

    public Music(@NotNull ClientOptions options) {
        this.options = options;
    }

    @Override
    @NotNull
    public ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
    }

    @Override
    @NotNull
    public String getPlayerParams() {
        // This client is not used for format loading so, we don't have
        // any player parameters attached to it.
        throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public ClientOptions getOptions() {
        return this.options;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }
}
