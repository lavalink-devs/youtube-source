package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.net.URISyntaxException;
import java.util.Map;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;

public class WebEmbedded extends Web {
    private static final Logger log = LoggerFactory.getLogger(WebEmbedded.class);

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withClientName("WEB_EMBEDDED_PLAYER")
        .withClientField("clientVersion", "1.20250401.01.00")
        .withUserField("lockedSafetyMode", false);

    public WebEmbedded() {
        super(ClientOptions.DEFAULT);
    }

    public static void setPoTokenAndVisitorData(String poToken, String visitorData) {
        WebEmbedded.poToken = poToken;

        if (poToken == null || visitorData == null) {
            BASE_CONFIG.getRoot().remove("serviceIntegrityDimensions");
            BASE_CONFIG.withVisitorData(null);
            return;
        }

        Map<String, Object> sid = BASE_CONFIG.putOnceAndJoin(BASE_CONFIG.getRoot(), "serviceIntegrityDimensions");
        sid.put("poToken", poToken);
        BASE_CONFIG.withVisitorData(visitorData);
    }

    @Override
    public boolean isEmbedded() {
        return true;
    }

    @Override
    @NotNull
    public URI transformPlaybackUri(@NotNull URI originalUri, @NotNull URI resolvedPlaybackUri) {
        if (poToken == null) {
            return resolvedPlaybackUri;
        }

        log.debug("Applying 'pot' parameter on playback URI: {}", resolvedPlaybackUri);
        URIBuilder builder = new URIBuilder(resolvedPlaybackUri);
        builder.addParameter("pot", poToken);

        try {
            return builder.build();
        } catch (URISyntaxException e) {
            log.debug("Failed to apply 'pot' parameter.", e);
            return resolvedPlaybackUri;
        }
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
