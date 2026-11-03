package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class VisionOsWithThumbnail extends VisionOs implements NonMusicClientWithThumbnail {
    public VisionOsWithThumbnail() {
        super();
    }

    public VisionOsWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
