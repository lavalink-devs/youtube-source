package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.skeleton.ThumbnailStreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;

public class AndroidWithThumbnail extends ThumbnailStreamingNonMusicClient {
    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return Android.BASE_CONFIG.copy();
    }

    @Override
    @NotNull
    public String getPlayerParams() {
        return MOBILE_PLAYER_PARAMS;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return Android.BASE_CONFIG.getName();
    }
}
