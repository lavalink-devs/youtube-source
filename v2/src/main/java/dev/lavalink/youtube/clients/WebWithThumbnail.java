package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class WebWithThumbnail extends Web implements NonMusicClientWithThumbnail {
    public WebWithThumbnail() {
        super();
    }

    public WebWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
