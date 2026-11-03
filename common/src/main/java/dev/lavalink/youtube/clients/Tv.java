package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
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
    private static final String USER_AGENT = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)";

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withClientName("TVHTML5")
        .withUserAgent(USER_AGENT)
        .withClientField("clientVersion", "7.20260707.07.00");

    protected ClientOptions options;
    protected volatile String visitorData;

    public Tv() {
        this(ClientOptions.DEFAULT);
    }

    public Tv(@NotNull ClientOptions options) {
        this.options = options;
    }

    @Override
    public boolean supportsSabrPlayback() {
        return true;
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        ClientConfig config = BASE_CONFIG.copy().withUserAgent(USER_AGENT);
        if (visitorData != null) {
            config.withVisitorData(visitorData);
        }
        return config;
    }

    @Override
    protected boolean preferSabrPlayback() {
        return false;
    }

    @Override
    public void preparePlayback(@NotNull YoutubeAudioSourceManager source,
                                @NotNull HttpInterface httpInterface,
                                @NotNull String videoId) throws IOException {
        visitorData = source.getVisitorData();
        log.debug("TVHTML5 playback prepared with Cobalt UA (zero POT required)");
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
    public boolean supportsOAuth() {
        return true;
    }

    @Override
    @Nullable
    public String getPoToken() {
        return null;
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
        if (!getOptions().getVideoLoading()) {
            throw new OptionDisabledException("Video loading is disabled for this client");
        }

        AudioItem item = super.loadVideo(source, httpInterface, videoId);
        if (item instanceof YoutubeAudioTrack) {
            YoutubeAudioTrack track = (YoutubeAudioTrack) item;
            //uses https://www.youtube.com/oembed endpoint to load the video title and author explicitly as TVHTML5 does not return them, proposed by https://github.com/szymonwilczek
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
            new RuntimeException("TVHTML5 cannot be used to load mixes"));
    }

    @Override
    public AudioItem loadSearch(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface, @NotNull String searchQuery) {
        throw new FriendlyException("This client cannot search", Severity.COMMON,
            new RuntimeException("TVHTML5 cannot be used to search"));
    }
}
