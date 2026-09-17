package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.RemotePoToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.net.URISyntaxException;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;

public class WebEmbedded extends Web {
    private static final Logger log = LoggerFactory.getLogger(WebEmbedded.class);

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withClientName("WEB_EMBEDDED_PLAYER")
        .withClientField("clientVersion", "2.20260908.01.00")
        .withUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .withUserField("lockedSafetyMode", false);

    public WebEmbedded() {
        super(ClientOptions.DEFAULT);
    }

    public WebEmbedded(@NotNull ClientOptions options) {
        super(options);
    }

    @Override
    public boolean isEmbedded() {
        return true;
    }

    @Override
    public void preparePlayback(@NotNull YoutubeAudioSourceManager source,
                                @NotNull HttpInterface httpInterface,
                                @NotNull String videoId) throws java.io.IOException {
        requestVisitorData = source.getVisitorData();
        RemotePoToken.Result result = source.generatePoToken(httpInterface, videoId);
        if (result != null) {
            requestPoToken = result.getPoToken();
        }
    }

    @Override
    @NotNull
    public ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        ClientConfig config = BASE_CONFIG.copy();
        if (requestVisitorData != null) {
            config.withVisitorData(requestVisitorData);
        }
        if (requestPoToken != null) {
            config.putOnceAndJoin(config.getRoot(), "serviceIntegrityDimensions").put("poToken", requestPoToken);
        }
        return config;
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
            new RuntimeException("WEBEMBEDDED cannot be used to load searches"));
    }

    @Override
    public AudioItem loadPlaylist(@NotNull YoutubeAudioSourceManager source,
                                  @NotNull HttpInterface httpInterface,
                                  @NotNull String playlistId,
                                  @Nullable String selectedVideoId) {
        throw new FriendlyException("This client cannot load playlists", Severity.COMMON,
            new RuntimeException("WEBEMBEDDED cannot be used to load playlists"));
    }

    @Override
    public AudioItem loadMix(@NotNull YoutubeAudioSourceManager source,
                             @NotNull HttpInterface httpInterface,
                             @NotNull String mixId,
                             @Nullable String selectedVideoId) {
        throw new FriendlyException("This client cannot load mixes", Severity.COMMON,
            new RuntimeException("WEBEMBEDDED cannot be used to load mixes"));
    }
}
