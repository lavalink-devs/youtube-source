package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class TvSamsungWithThumbnail extends TvSamsung implements NonMusicClientWithThumbnail {
    public TvSamsungWithThumbnail() {
        super();
    }

    public TvSamsungWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
