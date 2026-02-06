package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class TvHtml5SimplyWithThumbnail extends TvHtml5Simply implements NonMusicClientWithThumbnail {
    public TvHtml5SimplyWithThumbnail() {
        super();
    }

    public TvHtml5SimplyWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}