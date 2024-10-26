package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.NonMusicClientWithThumbnail;
import org.jetbrains.annotations.NotNull;

public class AndroidVrWithThumbnail extends AndroidVr implements NonMusicClientWithThumbnail {
    public AndroidVrWithThumbnail() {
        super();
    }

    public AndroidVrWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }
}
