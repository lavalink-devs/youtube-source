package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class WebEmbeddedWithThumbnail extends WebEmbedded implements NonMusicClientWithThumbnail {
    public WebEmbeddedWithThumbnail() {
        super();
    }

    public WebEmbeddedWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
