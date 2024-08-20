package dev.lavalink.youtube.plugin.rest;

import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.jetbrains.annotations.Nullable;

public class MinimalConfigResponse {
    @Nullable
    public String refreshToken;

    private MinimalConfigResponse(@Nullable String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public static MinimalConfigResponse from(YoutubeAudioSourceManager sourceManager) {
        return new MinimalConfigResponse(sourceManager.getOauth2RefreshToken());
    }
}
