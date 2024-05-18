package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class AndroidLiteWithThumbnail extends AndroidLite implements NonMusicClientWithThumbnail {
    public AndroidLiteWithThumbnail() {
        super();
    }

    public AndroidLiteWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
