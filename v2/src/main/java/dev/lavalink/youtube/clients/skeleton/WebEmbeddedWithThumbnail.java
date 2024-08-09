package dev.lavalink.youtube.clients.skeleton;

import dev.lavalink.youtube.clients.ClientOptions;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import org.jetbrains.annotations.NotNull;

public class WebEmbeddedWithThumbnail extends WebEmbedded implements NonMusicClientWithThumbnail {
    public WebEmbeddedWithThumbnail() {
        super();
    }

    public WebEmbeddedWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
