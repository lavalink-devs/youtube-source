package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class MediaConnectWithThumbnail extends MediaConnect implements NonMusicClientWithThumbnail {
    public MediaConnectWithThumbnail() {
        super();
    }

    public MediaConnectWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
