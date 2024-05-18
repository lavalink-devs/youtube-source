package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class IosWithThumbnail extends Ios implements NonMusicClientWithThumbnail {
    public IosWithThumbnail() {
        super();
    }

    public IosWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
