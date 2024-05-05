package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.skeleton.ThumbnailMusicClient;
import org.jetbrains.annotations.NotNull;

public class MusicWithThumbnail extends ThumbnailMusicClient {
    @Override
    @NotNull
    public ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return Music.BASE_CONFIG.copy();
    }

    @Override
    @NotNull
    public String getPlayerParams() {
        // This client is not used for format loading so, we don't have
        // any player parameters attached to it.
        throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return Music.BASE_CONFIG.getName();
    }
}
