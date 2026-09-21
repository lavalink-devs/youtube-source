package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import dev.lavalink.youtube.CannotBeLoaded;
import dev.lavalink.youtube.RemotePoToken;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tv extends StreamingNonMusicClient {
    private static final Logger log = LoggerFactory.getLogger(Tv.class);
    private static final String USER_AGENT = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15";
    private static final Pattern LR_POT_ID_PATTERN = Pattern.compile("\"LIVING_ROOM_PO_TOKEN_ID\":\\s*\"([^\"]+)\"");
    private static final String TV_URL = "https://www.youtube.com/tv";
    private static final long SESSION_REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(1);

    private static volatile String livingRoomPoTokenId;
    private static volatile long lastSessionUpdate = -1;

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withClientName("TVHTML5")
        .withUserAgent(USER_AGENT)
        .withClientField("clientVersion", "7.20260707.07.00");

    protected ClientOptions options;
    protected volatile String requestPoToken;
    protected volatile String requestVisitorData;
    protected volatile String requestLivingRoomPoTokenId;
    protected volatile boolean oauthPlayback;
    protected final Boolean oauthPlaybackMode;
    protected final boolean legacyPlayback;

    public Tv(@NotNull ClientOptions options) {
        this(options, null);
    }

    public Tv(@NotNull ClientOptions options, Boolean oauthPlaybackMode) {
        this(options, oauthPlaybackMode, false);
    }

    private Tv(@NotNull ClientOptions options, Boolean oauthPlaybackMode, boolean legacyPlayback) {
        this.options = options;
        this.oauthPlaybackMode = oauthPlaybackMode;
        this.legacyPlayback = legacyPlayback;
    }

    @NotNull
    public Tv createLegacyPlaybackClient(boolean useOAuth) {
        return new Tv(options, useOAuth, true);
    }

    public boolean isLegacyPlayback() {
        return legacyPlayback;
    }

    @Override
    public boolean supportsSabrPlayback() {
        return true;
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        ClientConfig config = BASE_CONFIG.copy();
        config.withUserAgent(USER_AGENT);
        if (requestVisitorData != null) {
            config.withVisitorData(requestVisitorData);
        }
        if (requestLivingRoomPoTokenId != null) {
            Map<String, Object> context = config.putOnceAndJoin(config.getRoot(), "context");
            Map<String, Object> client = config.putOnceAndJoin(context, "client");
            client.put("tvAppInfo", Collections.singletonMap("livingRoomPoTokenId", requestLivingRoomPoTokenId));
        }
        if (requestPoToken != null) {
            config.putOnceAndJoin(config.getRoot(), "serviceIntegrityDimensions").put("poToken", requestPoToken);
        }
        return config;
    }

    @Override
    protected boolean preferSabrPlayback() {
        if (legacyPlayback) {
            return false;
        }
        if (oauthPlayback && requestLivingRoomPoTokenId == null) {
            return false;
        }
        return true;
    }

    private static void fetchLivingRoomPoTokenId(@NotNull HttpInterface httpInterface) {
        long now = System.currentTimeMillis();
        if (livingRoomPoTokenId != null && now - lastSessionUpdate < SESSION_REFRESH_INTERVAL) return;

        synchronized (Tv.class) {
            if (livingRoomPoTokenId != null && now - lastSessionUpdate < SESSION_REFRESH_INTERVAL) return;

            HttpGet request = new HttpGet(TV_URL);
            request.setHeader("User-Agent", USER_AGENT);

            try (CloseableHttpResponse response = httpInterface.execute(request)) {
                HttpClientTools.assertSuccessWithContent(response, "living room session fetch");
                String html = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Matcher matcher = LR_POT_ID_PATTERN.matcher(html);

                if (matcher.find()) {
                    livingRoomPoTokenId = matcher.group(1).replace("\\u003d", "=");
                    lastSessionUpdate = now;
                    log.debug("Found living room potoken ID: {}", livingRoomPoTokenId);
                } else {
                    log.warn("Unable to find living room potoken ID in TV page");
                }
            } catch (IOException e) {
                log.error("Failed to fetch living room session", e);
            }
        }
    }

    @Override
    public void preparePlayback(@NotNull YoutubeAudioSourceManager source,
                                @NotNull HttpInterface httpInterface,
                                @NotNull String videoId) throws IOException {

        oauthPlayback = oauthPlaybackMode != null ? oauthPlaybackMode : source.getOauth2Handler().isEnabled();

        log.debug("Preparing TVHTML5{} playback with {}", legacyPlayback ? " legacy" : "", oauthPlayback ? "OAuth" : "visitor data and PoToken");

        if (legacyPlayback) {
            requestPoToken = null;
            requestVisitorData = null;
            requestLivingRoomPoTokenId = null;
            return;
        }

        requestVisitorData = source.getVisitorData();

        if (oauthPlayback) {
            fetchLivingRoomPoTokenId(httpInterface);
            RemotePoToken.Result result = source.generatePoToken(httpInterface, livingRoomPoTokenId);

            if (result != null) {
                requestPoToken = result.getPoToken();
                requestLivingRoomPoTokenId = livingRoomPoTokenId;
                log.debug("TVHTML5 Living Room PoToken generated for playback with binding: {}", livingRoomPoTokenId);
            } else {
                requestPoToken = null;
                requestLivingRoomPoTokenId = null;
                log.debug("TVHTML5 PoToken unavailable continuing without PoToken");
            }
        } else {
            requestLivingRoomPoTokenId = null;
            RemotePoToken.Result result = source.generatePoToken(httpInterface, requestVisitorData);

            if (result != null) {
                requestPoToken = result.getPoToken();
                requestVisitorData = result.getContentBinding();
                log.debug("TVHTML5 visitor bound PoToken generated for playback");
            } else {
                requestPoToken = null;
                log.debug("TVHTML5 visitor bound PoToken unavailable continuing without PoToken");
            }
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
    public boolean canHandleRequest(@NotNull String identifier) {
        return false;
    }

    @Override
    public boolean supportsOAuth() {
        return oauthPlaybackMode == null || oauthPlaybackMode;
    }

    @Override
    @Nullable
    public String getPoToken() {
        return requestPoToken;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }

    @Override
    public AudioItem loadPlaylist(@NotNull YoutubeAudioSourceManager source,
                                  @NotNull HttpInterface httpInterface,
                                  @NotNull String playlistId,
                                  @Nullable String selectedVideoId) {
        throw new FriendlyException("This client cannot load playlists", Severity.COMMON,
            new RuntimeException("TVHTML5 cannot be used to load playlists"));
    }

    @Override
    public AudioItem loadVideo(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface, @NotNull String videoId) throws CannotBeLoaded, IOException {
        throw new FriendlyException("This client cannot load videos", Severity.COMMON,
            new RuntimeException("TVHTML5 cannot be used to load videos"));
    }

    @Override
    public AudioItem loadMix(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface, @NotNull String mixId, @Nullable String selectedVideoId) {
        throw new FriendlyException("This client cannot load mixes", Severity.COMMON,
            new RuntimeException("TVHTML5 cannot be used to load mixes"));
    }

    @Override
    public AudioItem loadSearch(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface, @NotNull String searchQuery) {
        throw new FriendlyException("This client cannot search", Severity.COMMON,
            new RuntimeException("TVHTML5 cannot be used to search"));
    }
}
