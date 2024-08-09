package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebEmbedded extends Web {
    private static final Logger log = LoggerFactory.getLogger(WebEmbedded.class);

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withApiKey("AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8")
        .withClientName("WEB_EMBEDDED_PLAYER")
        .withClientField("clientVersion", "1.20240806.01.00")
        .withUserField("lockedSafetyMode", false);

    public WebEmbedded() {
        super(ClientOptions.DEFAULT);
    }

    public WebEmbedded(@NotNull ClientOptions options) {
        super(options);
    }

    @Override
    @NotNull
    public ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
    }
    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }

    @Override
    public boolean canHandleRequest(@NotNull String identifier) {
        return !identifier.startsWith(YoutubeAudioSourceManager.SEARCH_PREFIX) && !identifier.contains("list=") && super.canHandleRequest(identifier);
    }

    @Override
    public AudioItem loadSearch(@NotNull YoutubeAudioSourceManager source,
                                @NotNull HttpInterface httpInterface,
                                @NotNull String searchQuery) {
        throw new FriendlyException("This client cannot load searches", Severity.COMMON,
            new RuntimeException("WEB_EMBEDDED cannot be used to load searches"));
    }

    @Override
    public AudioItem loadPlaylist(@NotNull YoutubeAudioSourceManager source,
                                  @NotNull HttpInterface httpInterface,
                                  @NotNull String playlistId,
                                  @Nullable String selectedVideoId) {
        throw new FriendlyException("This client cannot load playlists", Severity.COMMON,
            new RuntimeException("WEB_EMBEDDED cannot be used to load playlists"));
    }

    @Override
    public AudioItem loadMix(@NotNull YoutubeAudioSourceManager source,
                             @NotNull HttpInterface httpInterface,
                             @NotNull String mixId,
                             @Nullable String selectedVideoId) {
        throw new FriendlyException("This client cannot load mixes", Severity.COMMON,
            new RuntimeException("WEB_EMBEDDED cannot be used to load mixes"));
    }
}