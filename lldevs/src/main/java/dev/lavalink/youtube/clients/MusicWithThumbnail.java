package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.skeleton.ThumbnailMusicClient;

public class MusicWithThumbnail extends ThumbnailMusicClient {
    @Override
    public ClientConfig getBaseClientConfig(HttpInterface httpInterface) {
        return Music.BASE_CONFIG.copy();
    }

    @Override
    public String getPlayerParams() {
        // This client is not used for format loading so, we don't have
        // any player parameters attached to it.
        throw new UnsupportedOperationException();
    }

    @Override
    public String getIdentifier() {
        return Music.BASE_CONFIG.getName();
    }
}
