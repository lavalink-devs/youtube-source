package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class TvHtml5Kids extends StreamingNonMusicClient {
    private static final Logger log = LoggerFactory.getLogger(TvHtml5Kids.class);

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withClientName("TVHTML5_KIDS")
        .withUserAgent("Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)")
        .withClientField("clientVersion", "3.20220918");

    public static String poToken;

    protected ClientOptions options;

    public TvHtml5Kids() {
        this(ClientOptions.DEFAULT);
    }

    public TvHtml5Kids(@NotNull ClientOptions options) {
        this.options = options;
    }

    public static void setPoTokenAndVisitorData(String poToken, String visitorData) {
        TvHtml5Kids.poToken = poToken;

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
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
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

    @Override
    @NotNull
    public String getPlayerParams() {
        return WEB_PLAYER_PARAMS;
    }

    @Override
    @NotNull
    public ClientOptions getOptions() {
        return this.options;
    }

    @Override
    public boolean supportsOAuth() {
        return true;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }
}
