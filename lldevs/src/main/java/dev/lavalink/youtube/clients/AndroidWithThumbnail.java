package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.skeleton.ThumbnailStreamingNonMusicClient;

public class AndroidWithThumbnail extends ThumbnailStreamingNonMusicClient {
    @Override
    protected ClientConfig getBaseClientConfig(HttpInterface httpInterface) {
        return Android.BASE_CONFIG.copy();
    }

    @Override
    public String getPlayerParams() {
        return MOBILE_PLAYER_PARAMS;
    }

    @Override
    public String getIdentifier() {
        return Android.BASE_CONFIG.getName();
    }
}
