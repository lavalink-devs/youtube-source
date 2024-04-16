package me.devoxin.lpyts.clients.skeleton;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.ThumbnailTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.*;
import me.devoxin.lpyts.CannotBeLoaded;
import me.devoxin.lpyts.YoutubeAudioSourceManager;
import me.devoxin.lpyts.clients.ClientConfig;
import me.devoxin.lpyts.track.format.TrackFormats;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The base class for a client that can be used with music.youtube.com.
 */
public abstract class MusicClient implements Client {
    protected abstract ClientConfig getBaseClientConfig(HttpInterface httpInterface);

    protected JsonBrowser getMusicSearchResult(HttpInterface httpInterface,
                                               String searchQuery) {
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

    protected JsonBrowser extractSearchResultTrackJson(JsonBrowser json) {
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

    protected List<AudioTrack> extractSearchResultTracks(YoutubeAudioSourceManager source,
                                                         JsonBrowser json) {
        List<AudioTrack> tracks = new ArrayList<>();

        for (JsonBrowser track : json.values()) {
            JsonBrowser thumbnail = track.get("musicResponsiveListItemRenderer").get("thumbnail").get("musicThumbnailRenderer");
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
                return null;
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
            String thumbnailUrl = ThumbnailTools.getYouTubeMusicThumbnail(thumbnail, videoId);

            AudioTrackInfo info = new AudioTrackInfo(title, author, duration, videoId, false, WATCH_URL + videoId, thumbnailUrl, null);
            tracks.add(source.buildAudioTrack(info));
        }

        return tracks;
    }

    @Override
    public boolean canHandleRequest(String identifier) {
        return identifier.startsWith(YoutubeAudioSourceManager.MUSIC_SEARCH_PREFIX);
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
    public AudioItem loadSearchMusic(YoutubeAudioSourceManager source, HttpInterface httpInterface, String searchQuery) throws CannotBeLoaded, IOException {
        JsonBrowser json = getMusicSearchResult(httpInterface, searchQuery);
        JsonBrowser trackJson = extractSearchResultTrackJson(json);
        List<AudioTrack> tracks = extractSearchResultTracks(source, trackJson);

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new BasicAudioPlaylist("Search music results for: " + searchQuery, tracks, null, true);
    }

    @Override
    public TrackFormats loadFormats(YoutubeAudioSourceManager source, HttpInterface httpInterface, String videoId) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AudioItem loadVideo(YoutubeAudioSourceManager source, HttpInterface httpInterface, String videoId) throws CannotBeLoaded, IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AudioItem loadSearch(YoutubeAudioSourceManager source, HttpInterface httpInterface, String searchQuery) throws CannotBeLoaded {
        throw new UnsupportedOperationException();
    }

    @Override
    public AudioItem loadMix(YoutubeAudioSourceManager source, HttpInterface httpInterface, String mixId, String selectedVideoId) throws CannotBeLoaded {
        throw new UnsupportedOperationException();
    }

    @Override
    public AudioItem loadPlaylist(YoutubeAudioSourceManager source, HttpInterface httpInterface, String playlistId, String selectedVideoId) throws CannotBeLoaded {
        throw new UnsupportedOperationException();
    }
}
