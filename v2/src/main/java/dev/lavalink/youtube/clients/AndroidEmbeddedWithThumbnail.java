package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class AndroidEmbeddedWithThumbnail extends AndroidEmbedded implements NonMusicClientWithThumbnail {
    public AndroidEmbeddedWithThumbnail() {
        super();
    }

    public AndroidEmbeddedWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
