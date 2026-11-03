package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.lavalink.youtube.CannotBeLoaded;
import dev.lavalink.youtube.OptionDisabledException;
import dev.lavalink.youtube.RemotePoToken;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import dev.lavalink.youtube.track.YoutubeAudioTrack;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TvTcl extends StreamingNonMusicClient {
    private static final Logger log = LoggerFactory.getLogger(TvTcl.class);
    private static final String USER_AGENT = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), TCL_BeyondTV_Android_11/Unknown (TCL, BeyondTV)";

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withClientName("TVHTML5")
        .withUserAgent(USER_AGENT)
        .withClientField("clientVersion", "7.20260707.07.00")
        .withClientField("deviceMake", "TCL")
        .withClientField("deviceModel", "BeyondTV")
        .withClientField("osName", "Android")
        .withClientField("osVersion", "11");

    protected ClientOptions options;
    protected volatile String poToken;
    protected volatile String visitorData;

    public TvTcl() {
        this(ClientOptions.DEFAULT);
    }

    public TvTcl(@NotNull ClientOptions options) {
        this.options = options;
    }

    @Override
    public boolean supportsSabrPlayback() {
        return true;
    }

    @Override
    protected boolean preferSabrPlayback() {
        return true;
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        ClientConfig config = BASE_CONFIG.copy().withUserAgent(USER_AGENT);
        if (visitorData != null) {
            config.withVisitorData(visitorData);
        }
        if (poToken != null) {
            config.putOnceAndJoin(config.getRoot(), "serviceIntegrityDimensions").put("poToken", poToken);
        }
        return config;
    }

    @Override
    public void preparePlayback(@NotNull YoutubeAudioSourceManager source,
                                @NotNull HttpInterface httpInterface,
                                @NotNull String videoId) throws IOException {
        visitorData = source.getVisitorData();
        RemotePoToken.Result result = source.generatePoToken(httpInterface, visitorData != null ? visitorData : videoId);

        if (result != null) {
            poToken = result.getPoToken();
            visitorData = result.getContentBinding();
            log.debug("TV_TCL visitor bound PoToken generated for playback");
        } else {
            poToken = null;
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
        return getOptions().getVideoLoading()
            && !identifier.startsWith(YoutubeAudioSourceManager.SEARCH_PREFIX)
            && !identifier.contains("list=")
            && super.canHandleRequest(identifier);
    }

    @Override
    @Nullable
    public String getPoToken() {
        return poToken;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "TV_TCL";
    }

    @Override
    public AudioItem loadPlaylist(@NotNull YoutubeAudioSourceManager source,
                                  @NotNull HttpInterface httpInterface,
                                  @NotNull String playlistId,
                                  @Nullable String selectedVideoId) {
        throw new FriendlyException("This client cannot load playlists", Severity.COMMON,
            new RuntimeException("TV_TCL cannot be used to load playlists"));
    }

    @Override
    public AudioItem loadVideo(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface, @NotNull String videoId) throws CannotBeLoaded, IOException {
        if (!getOptions().getVideoLoading()) {
            throw new OptionDisabledException("Video loading is disabled for this client");
        }

        AudioItem item = super.loadVideo(source, httpInterface, videoId);
        if (item instanceof YoutubeAudioTrack) {
            YoutubeAudioTrack track = (YoutubeAudioTrack) item;
            if (track.getInfo().title == null || "Unknown artist".equals(track.getInfo().author)) {
                try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=" + videoId + "&format=json"))) {
                    if (HttpClientTools.isSuccessWithContent(response.getStatusLine().getStatusCode())) {
                        JsonBrowser oembed = JsonBrowser.parse(response.getEntity().getContent());
                        String oembedTitle = oembed.get("title").text();
                        String oembedAuthor = oembed.get("author_name").text();
                        if (oembedTitle != null && !oembedTitle.isEmpty()) {
                            AudioTrackInfo old = track.getInfo();
                            AudioTrackInfo updated = new AudioTrackInfo(
                                oembedTitle,
                                oembedAuthor != null ? oembedAuthor : old.author,
                                old.length,
                                old.identifier,
                                old.isStream,
                                old.uri
                            );
                            return source.buildAudioTrack(updated);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to fetch oEmbed metadata for {}: {}", videoId, e.getMessage());
                }
            }
        }
        return item;
    }

    @Override
    public AudioItem loadMix(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface, @NotNull String mixId, @Nullable String selectedVideoId) {
        throw new FriendlyException("This client cannot load mixes", Severity.COMMON,
            new RuntimeException("TV_TCL cannot be used to load mixes"));
    }

    @Override
    public AudioItem loadSearch(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface, @NotNull String searchQuery) {
        throw new FriendlyException("This client cannot search", Severity.COMMON,
            new RuntimeException("TV_TCL cannot be used to search"));
    }
}
