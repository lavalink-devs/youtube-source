package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class AndroidWithThumbnail extends Android implements NonMusicClientWithThumbnail {
    public AndroidWithThumbnail() {
        super();
    }

    public AndroidWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
