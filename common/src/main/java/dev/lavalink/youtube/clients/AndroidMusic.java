package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AndroidMusic extends Android {
    private static final Logger log = LoggerFactory.getLogger(AndroidMusic.class);
    public static String CLIENT_VERSION = "7.27.52";

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withClientName("ANDROID_MUSIC")
        .withClientField("clientVersion", CLIENT_VERSION)
        .withUserAgent(String.format("com.google.android.apps.youtube.music/%s (Linux; U; Android %s) gzip", CLIENT_VERSION, ANDROID_VERSION.getOsVersion()));

    public AndroidMusic() {
        this(ClientOptions.DEFAULT);
    }

    public AndroidMusic(@NotNull ClientOptions options) {
        super(options, false);
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
    }

    @Override
    @NotNull
    public String getPlayerParams() {
        return MOBILE_PLAYER_PARAMS;
    }

    @Override
    @NotNull
    protected JsonBrowser extractMixPlaylistData(@NotNull JsonBrowser json) {
        return json.get("contents")
            .get("singleColumnMusicWatchNextResultsRenderer")
            .get("tabbedRenderer")
            .get("watchNextTabbedResultsRenderer")
            .get("tabs")
            .values()
            .stream()
            .filter(tab -> "Up next".equalsIgnoreCase(tab.get("tabRenderer").get("title").text()))
            .findFirst()
            .orElse(json)
            .get("tabRenderer")
            .get("content")
            .get("musicQueueRenderer")
            .get("content")
            .get("playlistPanelRenderer");
    }

    @NotNull
    protected List<AudioTrack> extractSearchResults(@NotNull YoutubeAudioSourceManager source,
                                                    @NotNull JsonBrowser json) {
        return json.get("contents")
            .get("tabbedSearchResultsRenderer")
            .get("tabs")
            .values()
            .stream()
            .flatMap(item -> item.get("tabRenderer").get("content").get("sectionListRenderer").get("contents").values().stream())
            .map(item -> extractAudioTrack(item.get("musicCardShelfRenderer"), source))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    @Nullable
    protected AudioTrack extractAudioTrack(@NotNull JsonBrowser json, @NotNull YoutubeAudioSourceManager source) {
        if (json.isNull() || !json.get("unplayableText").isNull()) return null;

        AudioTrack track = super.extractAudioTrack(json, source);

        if (track != null) {
            return track;
        }

        String videoId = json.get("onTap").get("watchEndpoint").get("videoId").text();

        if (videoId == null) {
            return null;
        }

        JsonBrowser titleJson = json.get("title");
        JsonBrowser secondaryJson = json.get("menu").get("menuRenderer").get("title").get("musicMenuTitleRenderer").get("secondaryText").get("runs");
        String title = DataFormatTools.defaultOnNull(titleJson.get("runs").index(0).get("text").text(), titleJson.get("simpleText").text());
        String author = secondaryJson.index(0).get("text").text();

        if (author == null) {
            log.debug("Author field is null, json: {}", json.format());
            author = "Unknown artist";
        }

        JsonBrowser durationJson = secondaryJson.index(2);
        String durationText = DataFormatTools.defaultOnNull(durationJson.get("text").text(), durationJson.get("runs").index(0).get("text").text());

        long duration = DataFormatTools.durationTextToMillis(durationText);
        return buildAudioTrack(source, json, title, author, duration, videoId, false);
    }

    @Override
    public boolean canHandleRequest(@NotNull String identifier) {
        // loose check to avoid loading playlists.
        // this client does support them, but it seems to be missing fields (i.e. videoId)
        return (!identifier.contains("list=") || identifier.contains("list=RD")) && super.canHandleRequest(identifier);
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }

    @Override
    public AudioItem loadPlaylist(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface, @NotNull String playlistId, @Nullable String selectedVideoId) {
        // It does actually return JSON, but it seems like videoId is missing.
        // Each video JSON contains a "Content is unavailable" message.
        // Theoretically, you could construct an audio track from the JSON as author, duration and title are there.
        // Video ID is included in the thumbnail URL, but I don't think it's worth writing parsing for.
        throw new FriendlyException("This client cannot load playlists", Severity.COMMON,
            new RuntimeException("ANDROID_MUSIC cannot be used to load playlists"));
    }
}
