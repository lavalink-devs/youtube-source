package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class AndroidTestsuiteWithThumbnail extends AndroidTestsuite implements NonMusicClientWithThumbnail {
    public AndroidTestsuiteWithThumbnail() {
        super();
    }

    public AndroidTestsuiteWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
