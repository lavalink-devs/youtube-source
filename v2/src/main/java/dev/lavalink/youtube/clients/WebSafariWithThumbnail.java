package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class WebSafariWithThumbnail extends WebSafari implements NonMusicClientWithThumbnail {
    public WebSafariWithThumbnail() {
        super();
    }

    public WebSafariWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
