package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class AndroidMusicWithThumbnail extends AndroidMusic implements NonMusicClientWithThumbnail {
    public AndroidMusicWithThumbnail() {
        super();
    }

    public AndroidMusicWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
