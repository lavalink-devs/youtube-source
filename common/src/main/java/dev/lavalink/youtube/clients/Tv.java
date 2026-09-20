package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.CannotBeLoaded;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public class Tv extends StreamingNonMusicClient {
    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withClientName("TVHTML5")
        .withUserAgent("Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15")
        .withClientField("clientVersion", "7.20250319.10.00");

    protected ClientOptions options;

    public Tv() {
        this(ClientOptions.DEFAULT);
    }

    public Tv(@NotNull ClientOptions options) {
        this.options = options;
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
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
        return true;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }

    @Override
    @NotNull
    protected JsonBrowser extractPlaylistVideoList(@NotNull JsonBrowser json) {
        return json.get("contents")
                .get("tvBrowseRenderer")
                .get("content")
                .get("tvSurfaceContentRenderer")
                .get("content")
                .index(0)
                .get("twoColumnRenderer")
                .get("rightColumn")
                .get("playlistVideoListRenderer");
    }

    @Override
    protected String extractPlaylistName(@NotNull JsonBrowser json) {
        return json.get("contents")
                .get("tvBrowseRenderer")
                .get("content")
                .get("tvSurfaceContentRenderer")
                .get("content")
                .index(0)
                .get("twoColumnRenderer")
                .get("leftColumn")
                .get("entityMetadataRenderer")
                .get("title")
                .get("simpleText")
                .text();
    }

    @Override
    protected void extractPlaylistTracks(@NotNull JsonBrowser json,
            @NotNull List<AudioTrack> tracks,
            @NotNull YoutubeAudioSourceManager source) {
        if (!json.get("contents").isNull()) {
            json = json.get("contents");
        }

        if (json.isNull()) {
            return;
        }

        for (JsonBrowser track : json.values()) {
            JsonBrowser item = track.get("tileRenderer");

            if (item.isNull()) {
                continue;
            }

            String videoId = item.get("contentId").text();

            if (videoId == null) {
                videoId = item.get("onSelectCommand").get("watchEndpoint").get("videoId").text();
            }

            if (videoId == null) {
                continue;
            }

            String title = item.get("metadata").get("tileMetadataRenderer").get("title").get("simpleText").text();

            if (title == null) {
                continue;
            }

            String author = DataFormatTools.defaultOnNull(
                    item.get("metadata").get("tileMetadataRenderer").get("lines").index(0)
                            .get("lineRenderer").get("items").index(0)
                            .get("lineItemRenderer").get("text").get("runs").index(0).get("text").text(),
                    "Unknown artist");

            long duration = Units.DURATION_MS_UNKNOWN;

            for (JsonBrowser overlay : item.get("header").get("tileHeaderRenderer").get("thumbnailOverlays").values()) {
                JsonBrowser timeStatus = overlay.get("thumbnailOverlayTimeStatusRenderer");

                if (timeStatus.isNull()) {
                    continue;
                }

                JsonBrowser lengthField = timeStatus.get("text");
                String lengthText = DataFormatTools.defaultOnNull(
                        lengthField.get("runs").index(0).get("text").text(),
                        lengthField.get("simpleText").text());

                if (lengthText != null) {
                    duration = DataFormatTools.durationTextToMillis(lengthText);
                    break;
                }
            }

            tracks.add(buildAudioTrack(source, item, title, author, duration, videoId, false));
        }
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
