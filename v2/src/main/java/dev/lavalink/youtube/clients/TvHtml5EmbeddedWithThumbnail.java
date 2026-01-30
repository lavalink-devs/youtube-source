package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class TvHtml5EmbeddedWithThumbnail extends TvHtml5Embedded implements NonMusicClientWithThumbnail {
    public TvHtml5EmbeddedWithThumbnail() {
        super();
    }

    public TvHtml5EmbeddedWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
