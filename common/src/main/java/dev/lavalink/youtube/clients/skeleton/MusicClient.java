package dev.lavalink.youtube.clients.skeleton;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.*;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.ClientConfig;
import dev.lavalink.youtube.track.format.TrackFormats;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The base class for a client that can be used with music.youtube.com.
 */
public abstract class MusicClient implements Client {
    @NotNull
    protected abstract ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface);

    protected JsonBrowser getMusicSearchResult(@NotNull HttpInterface httpInterface,
                                               @NotNull String searchQuery) {
        ClientConfig config = getBaseClientConfig(httpInterface)
            .withRootField("query", searchQuery)
            .withRootField("params", MUSIC_SEARCH_PARAMS)
            .setAttributes(httpInterface);

        HttpPost request = new HttpPost(MUSIC_SEARCH_URL);
        request.setEntity(new StringEntity(config.toJsonString(), "UTF-8"));
        request.setHeader("Referer", "music.youtube.com");

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            HttpClientTools.assertSuccessWithContent(response, "search music response");
            return JsonBrowser.parse(response.getEntity().getContent());
        } catch (IOException e) {
            throw ExceptionTools.toRuntimeException(e);
        }
    }

    protected JsonBrowser extractSearchResultTrackJson(@NotNull JsonBrowser json) {
        return json.get("contents")
            .get("tabbedSearchResultsRenderer")
            .get("tabs")
            .index(0)
            .get("tabRenderer")
            .get("content")
            .get("sectionListRenderer")
            .get("contents")
            .values()
            .stream()
            .filter(item -> !item.get("musicShelfRenderer").isNull())
            .findFirst()
            .map(item -> item.get("musicShelfRenderer").get("contents"))
            .orElse(JsonBrowser.NULL_BROWSER);
    }

    @NotNull
    protected List<AudioTrack> extractSearchResultTracks(@NotNull YoutubeAudioSourceManager source,
                                                         @NotNull JsonBrowser json) {
        List<AudioTrack> tracks = new ArrayList<>();

        for (JsonBrowser track : json.values()) {
            JsonBrowser columns = track.get("musicResponsiveListItemRenderer").get("flexColumns");

            if (columns.isNull()) {
                continue;
            }

            JsonBrowser metadata = columns.index(0)
                .get("musicResponsiveListItemFlexColumnRenderer")
                .get("text")
                .get("runs")
                .index(0);

            String title = metadata.get("text").text();
            String videoId = metadata.get("navigationEndpoint").get("watchEndpoint").get("videoId").text();

            if (videoId == null) {
                // If the track is not available on YouTube Music, videoId will be empty
                continue;
            }

            List<JsonBrowser> runs = columns.index(1)
                .get("musicResponsiveListItemFlexColumnRenderer")
                .get("text")
                .get("runs")
                .values();

            String author = runs.get(0).get("text").text();
            JsonBrowser lastElement = runs.get(runs.size() - 1);

            if (!lastElement.get("navigationEndpoint").isNull()) {
                // The duration element should not have this key. If it does,
                // then duration is probably missing.
                continue;
            }

            long duration = DataFormatTools.durationTextToMillis(lastElement.get("text").text());
            tracks.add(buildAudioTrack(source, track, title, author, duration, videoId, false));
        }

        return tracks;
    }

    @Override
    public boolean canHandleRequest(@NotNull String identifier) {
        return identifier.startsWith(YoutubeAudioSourceManager.MUSIC_SEARCH_PREFIX) && getOptions().getSearching();
    }

    @Override
    public void setPlaylistPageCount(int count) {
        // nothing to do.
    }

    @Override
    public boolean supportsFormatLoading() {
        return false;
    }

    @Override
    public AudioItem loadSearchMusic(@NotNull YoutubeAudioSourceManager source,
                                     @NotNull HttpInterface httpInterface,
                                     @NotNull String searchQuery) {
        if (!getOptions().getSearching()) {
            // why would you even disable searching for this client lol
            throw new RuntimeException("Searching is disabled for this client");
        }

        JsonBrowser json = getMusicSearchResult(httpInterface, searchQuery);
        JsonBrowser trackJson = extractSearchResultTrackJson(json);
        List<AudioTrack> tracks = extractSearchResultTracks(source, trackJson);

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new BasicAudioPlaylist("Search music results for: " + searchQuery, tracks, null, true);
    }

    @Override
    public TrackFormats loadFormats(@NotNull YoutubeAudioSourceManager source,
                                    @NotNull HttpInterface httpInterface,
                                    @NotNull String videoId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AudioItem loadVideo(@NotNull YoutubeAudioSourceManager source,
                               @NotNull HttpInterface httpInterface,
                               @NotNull String videoId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AudioItem loadSearch(@NotNull YoutubeAudioSourceManager source,
                                @NotNull HttpInterface httpInterface,
                                @NotNull String searchQuery) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AudioItem loadMix(@NotNull YoutubeAudioSourceManager source,
                             @NotNull HttpInterface httpInterface,
                             @NotNull String mixId,
                             @Nullable String selectedVideoId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AudioItem loadPlaylist(@NotNull YoutubeAudioSourceManager source,
                                  @NotNull HttpInterface httpInterface,
                                  @NotNull String playlistId,
                                  @Nullable String selectedVideoId) {
        throw new UnsupportedOperationException();
    }
}
