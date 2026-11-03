package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class TvTclWithThumbnail extends TvTcl implements NonMusicClientWithThumbnail {
    public TvTclWithThumbnail() {
        super();
    }

    public TvTclWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
