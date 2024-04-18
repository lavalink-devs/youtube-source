package dev.lavalink.youtube.clients.skeleton;

import com.sedmelluq.discord.lavaplayer.tools.*;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.*;
import me.devoxin.lpyts.CannotBeLoaded;
import me.devoxin.lpyts.YoutubeAudioSourceManager;
import me.devoxin.lpyts.cipher.SignatureCipher;
import me.devoxin.lpyts.cipher.SignatureCipherManager;
import me.devoxin.lpyts.cipher.SignatureCipherManager.CachedPlayerScript;
import dev.lavalink.youtube.clients.ClientConfig;
import me.devoxin.lpyts.track.TemporalInfo;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * The base class for a client that is used for everything except music.youtube.com.
 */
public abstract class NonMusicClient implements Client {
    private static final Logger log = LoggerFactory.getLogger(NonMusicClient.class);

    protected static String WEB_PLAYER_PARAMS = "ygUEbmF0dA%3D%3D";
    protected static String MOBILE_PLAYER_PARAMS = "CgIIAdgDAQ%3D%3D";

    protected int playlistPageCount = 6;

    //<editor-fold desc="Class-Specific Methods">
    /**
     * Retrieves a base client config payload to be used for requests.
     * @param httpInterface The HTTP interface to use for fetching a config,
     *                      if applicable.
     * @return A client configuration.
     */
    protected abstract ClientConfig getBaseClientConfig(HttpInterface httpInterface);

    protected  JsonBrowser loadJsonResponse(HttpInterface httpInterface,
                                            HttpPost request,
                                            String context) throws IOException {
        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            HttpClientTools.assertSuccessWithContent(response, context);
            // todo: flag for checking json content type?
            //       from my testing, json is always returned so might not be necessary.
            HttpClientTools.assertJsonContentType(response);

            return JsonBrowser.parse(response.getEntity().getContent());
        }
    }

    protected JsonBrowser loadTrackInfoFromInnertube(YoutubeAudioSourceManager source,
                                                     HttpInterface httpInterface,
                                                     String videoId,
                                                     PlayabilityStatus status) throws CannotBeLoaded, IOException {
        SignatureCipherManager cipherManager = source.getCipherManager();
        CachedPlayerScript playerScript = cipherManager.getCachedPlayerScript(httpInterface);
        SignatureCipher signatureCipher = cipherManager.getCipherScript(httpInterface, playerScript.url);

        ClientConfig config = getBaseClientConfig(httpInterface);

        if (status == null) {
            // Only add embed info if the status is not NON_EMBEDDABLE.
            config.withClientField("clientScreen", "EMBED")
                .withThirdPartyEmbedUrl("https://google.com");
        }

        String payload = config.withRootField("videoId", videoId)
            .withRootField("racyCheckOk", true)
            .withRootField("contentCheckOk", true)
            .withRootField("params", getPlayerParams())
            .withPlaybackSignatureTimestamp(signatureCipher.scriptTimestamp)
            .toJsonString();

        log.debug("Requesting {} with payload {}", PLAYER_URL, payload);

        HttpPost request = new HttpPost(PLAYER_URL);
        request.setEntity(new StringEntity(payload, "UTF-8"));

        JsonBrowser json = loadJsonResponse(httpInterface, request, "player api response");
        JsonBrowser playabilityJson = json.get("playabilityStatus");
        PlayabilityStatus playabilityStatus = getPlayabilityStatus(playabilityJson, false);

        // All other branches should've been caught by getPlayabilityStatus().
        // An exception will be thrown if we can't handle it.
        if (playabilityStatus == PlayabilityStatus.NON_EMBEDDABLE) {
            json = loadTrackInfoFromInnertube(source, httpInterface, videoId, status);
            getPlayabilityStatus(json.get("playabilityStatus"), true);
        }

        JsonBrowser videoDetails = json.get("videoDetails");

        if (videoDetails.isNull()) {
            throw new FriendlyException("Loading information for for video failed", Severity.SUSPICIOUS,
                new RuntimeException("Missing videoDetails block, JSON: " + json.format()));
        }

        if (!videoId.equals(videoDetails.get("videoId").text())) {
            throw new FriendlyException(
                "The video returned is not what was requested.",
                Severity.SUSPICIOUS,
                new RuntimeException("Incorrect video response, JSON: " + json.format())
            );
        }

        return json;
    }

    protected JsonBrowser loadSearchResults(HttpInterface httpInterface,
                                            String searchQuery) {
        ClientConfig clientConfig = getBaseClientConfig(httpInterface)
            .withRootField("query", searchQuery)
            .withRootField("params", SEARCH_PARAMS);

        HttpPost request = new HttpPost(SEARCH_URL); // This *had* a key parameter. Doesn't seem needed though.
        request.setEntity(new StringEntity(clientConfig.toJsonString(), "UTF-8"));

        try {
            return loadJsonResponse(httpInterface, request, "search response");
        } catch (IOException e) {
            throw ExceptionTools.toRuntimeException(e);
        }
    }

    protected List<AudioTrack> extractSearchResults(YoutubeAudioSourceManager source,
                                                    JsonBrowser json) {
        return json.get("contents")
            .get("sectionListRenderer")
            .get("contents")
            .values()
            .stream()
            .flatMap(item -> item.get("itemSectionRenderer").get("contents").values().stream())
            .map(item -> extractAudioTrack(item.get("compactVideoRenderer"), source))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    protected JsonBrowser loadMixResult(HttpInterface httpInterface,
                                        String mixId,
                                        String selectedVideoId) {
        ClientConfig clientConfig = getBaseClientConfig(httpInterface)
            .withRootField("videoId", selectedVideoId)
            .withRootField("playlistId", mixId)
            .setAttributes(httpInterface);

        HttpPost request = new HttpPost(NEXT_URL);
        request.setEntity(new StringEntity(clientConfig.toJsonString(), "UTF-8"));

        try {
            return loadJsonResponse(httpInterface, request, "mix response");
        } catch (IOException e) {
            throw new FriendlyException("Could not read mix page.", SUSPICIOUS, e);
        }
    }

    protected JsonBrowser extractMixPlaylistData(JsonBrowser json) {
        return json.get("contents")
            .get("singleColumnWatchNextResults")
            .get("playlist") // this doesn't exist if mix is not found
            .get("playlist");
    }

    protected JsonBrowser loadPlaylistResult(HttpInterface httpInterface,
                                             String playlistId) {
        ClientConfig clientConfig = getBaseClientConfig(httpInterface)
            .withRootField("browseId", "VL" + playlistId)
            .setAttributes(httpInterface);

        HttpPost request = new HttpPost(BROWSE_URL);
        request.setEntity(new StringEntity(clientConfig.toJsonString(), "UTF-8"));

        try {
            return loadJsonResponse(httpInterface, request, "playlist response");
        } catch (IOException e) {
            throw ExceptionTools.toRuntimeException(e);
        }
    }

    protected String extractPlaylistError(JsonBrowser json) {
        JsonBrowser alerts = json.get("alerts");

        if (!alerts.isNull()) {
            for (JsonBrowser alert : alerts.values()) {
                JsonBrowser alertInner = alert.get("alertRenderer");
                String type = alertInner.get("type").text();

                if ("ERROR".equals(type)) {
                    JsonBrowser textObject = alertInner.get("text");

                    return textObject.get("simpleText")
                        .textOrDefault(textObject.get("runs").values().stream()
                            .map(run -> run.get("text").text())
                            .collect(Collectors.joining())
                        );
                }
            }
        }

        return null;
    }

    protected String extractPlaylistName(JsonBrowser json) {
        return json.get("header").get("playlistHeaderRenderer").get("title").get("runs").index(0).get("text").text();
    }

    protected JsonBrowser extractPlaylistVideoList(JsonBrowser json) {
        return json.get("contents")
            .get("singleColumnBrowseResultsRenderer")
            .get("tabs")
            .index(0)
            .get("tabRenderer")
            .get("content")
            .get("sectionListRenderer")
            .get("contents")
            .index(0)
            .get("playlistVideoListRenderer");
    }

    protected String extractPlaylistContinuationToken(JsonBrowser videoList) {
        return videoList.get("continuations").index(0).get("nextContinuationData").get("continuation").text();
    }

    protected JsonBrowser extractPlaylistContinuationVideos(JsonBrowser continuationJson) {
        return continuationJson.get("continuationContents").get("playlistVideoListContinuation");
    }

    protected void extractPlaylistTracks(JsonBrowser json,
                                         List<AudioTrack> tracks,
                                         YoutubeAudioSourceManager source) {
        if (!json.get("contents").isNull()) {
            json = json.get("contents");
        }

        if (json.isNull()) {
            return;
        }

        for (JsonBrowser track : json.values()) {
            JsonBrowser item = track.get("playlistVideoRenderer");
            JsonBrowser authorJson = item.get("shortBylineText");

            // isPlayable is null -> video has been removed/blocked
            // author is null -> video is region blocked
            if (!item.get("isPlayable").isNull() && !authorJson.isNull()) {
                String videoId = item.get("videoId").text();
                JsonBrowser titleField = item.get("title");
                String title = titleField.get("simpleText").textOrDefault(titleField.get("runs").index(0).get("text").text());
                String author = authorJson.get("runs").index(0).get("text").textOrDefault("Unknown artist");
                long duration = Units.secondsToMillis(item.get("lengthSeconds").asLong(Units.DURATION_SEC_UNKNOWN));

                AudioTrackInfo info = new AudioTrackInfo(title, author, duration, videoId, false, WATCH_URL + videoId);
                tracks.add(source.buildAudioTrack(info));
            }
        }
    }

    protected AudioTrack extractAudioTrack(JsonBrowser json, YoutubeAudioSourceManager source) {
        // Ignore if it's not a track or if it's a livestream
        if (json.isNull() || json.get("lengthText").isNull() || !json.get("unplayableText").isNull()) return null;

        String videoId = json.get("videoId").text();
        JsonBrowser titleJson = json.get("title");
        String title = titleJson.get("runs").index(0).get("text").textOrDefault(titleJson.get("simpleText").text());
        String author = json.get("longBylineText").get("runs").index(0).get("text").text();

        JsonBrowser durationJson = json.get("lengthText");
        String durationText = durationJson.get("runs").index(0).get("text").textOrDefault(durationJson.get("simpleText").text());

        long duration = DataFormatTools.durationTextToMillis(durationText);

        AudioTrackInfo info = new AudioTrackInfo(title, author, duration, videoId, false, WATCH_URL + videoId);
        return source.buildAudioTrack(info);
    }
    //</editor-fold>

    @Override
    public boolean canHandleRequest(String identifier) {
        return !identifier.startsWith(YoutubeAudioSourceManager.MUSIC_SEARCH_PREFIX);
    }

    @Override
    public void setPlaylistPageCount(int playlistPageCount) {
        this.playlistPageCount = playlistPageCount;
    }

    @Override
    public AudioItem loadVideo(YoutubeAudioSourceManager source,
                               HttpInterface httpInterface,
                               String videoId) throws CannotBeLoaded, IOException {
        JsonBrowser json = loadTrackInfoFromInnertube(source, httpInterface, videoId, null);
        JsonBrowser playabilityStatus = json.get("playabilityStatus");
        JsonBrowser videoDetails = json.get("videoDetails");

        String title = videoDetails.get("title").text();
        String author = videoDetails.get("author").text();

        TemporalInfo temporalInfo = TemporalInfo.fromRawData(
            !playabilityStatus.get("liveStreamability").isNull(),
            videoDetails.get("lengthSeconds"),
            false
        );

        AudioTrackInfo info = new AudioTrackInfo(title, author, temporalInfo.durationMillis, videoId, temporalInfo.isActiveStream, WATCH_URL + videoId);
        return source.buildAudioTrack(info);
    }

    @Override
    public AudioItem loadSearch(YoutubeAudioSourceManager source, HttpInterface httpInterface, String searchQuery) {
        JsonBrowser json = loadSearchResults(httpInterface, searchQuery);
        List<AudioTrack> tracks = extractSearchResults(source, json);

        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new BasicAudioPlaylist("Search results for: " + searchQuery, tracks, null, true);
    }

    @Override
    public AudioItem loadMix(YoutubeAudioSourceManager source, HttpInterface httpInterface, String mixId, String selectedVideoId) throws CannotBeLoaded, IOException {
        JsonBrowser json = loadMixResult(httpInterface, mixId, selectedVideoId);
        JsonBrowser playlist = extractMixPlaylistData(json);

        JsonBrowser titleElement = playlist.get("title");
        String title = titleElement.isNull() ? "YouTube mix" : titleElement.text();

        List<AudioTrack> tracks = playlist.get("contents").values().stream()
            .map(item -> extractAudioTrack(item.get("playlistPanelVideoRenderer"), source))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (tracks.isEmpty()) {
            // This isn't a CannotBeLoaded exception as if the response JSON changes, another
            // client that receives the expected response format may still be able to load it.
            throw new FriendlyException("Could not find tracks from mix.", SUSPICIOUS, null);
        }

        return new BasicAudioPlaylist(title, tracks, findSelectedTrack(tracks, selectedVideoId), false);
    }

    @Override
    public AudioItem loadPlaylist(YoutubeAudioSourceManager source, HttpInterface httpInterface, String playlistId, String selectedVideoId) throws CannotBeLoaded {
        JsonBrowser json = loadPlaylistResult(httpInterface, playlistId);
        String error = extractPlaylistError(json);

        if (error != null) {
            throw new FriendlyException(error, COMMON, null);
        }

        String playlistName = extractPlaylistName(json);
        JsonBrowser playlistVideoList = extractPlaylistVideoList(json);

        List<AudioTrack> tracks = new ArrayList<>();
        extractPlaylistTracks(playlistVideoList, tracks, source);

        String continuationsToken = extractPlaylistContinuationToken(playlistVideoList);
        int currentPageCount = 0;

        while (continuationsToken != null && ++currentPageCount < playlistPageCount) {
            ClientConfig clientConfig = getBaseClientConfig(httpInterface)
                .withRootField("continuation", continuationsToken)
                .setAttributes(httpInterface);

            HttpPost request = new HttpPost(BROWSE_URL);
            request.setEntity(new StringEntity(clientConfig.toJsonString(), "UTF-8"));

            try {
                JsonBrowser continuationJson = loadJsonResponse(httpInterface, request, "playlist response");
                playlistVideoList = extractPlaylistContinuationVideos(continuationJson);
                continuationsToken = extractPlaylistContinuationToken(playlistVideoList);
                extractPlaylistTracks(playlistVideoList, tracks, source);
            } catch (IOException e) {
                throw ExceptionTools.toRuntimeException(e);
            }
        }

        if (tracks.isEmpty()) {
            // This isn't a CannotBeLoaded exception as if the response JSON changes, another
            // client that receives the expected response format may still be able to load it.
            throw new FriendlyException("Could not find tracks from playlist.", SUSPICIOUS, null);
        }

        return new BasicAudioPlaylist(playlistName, tracks, findSelectedTrack(tracks, selectedVideoId), false);
    }

    @Override
    public AudioItem loadSearchMusic(YoutubeAudioSourceManager source, HttpInterface httpInterface, String searchQuery) {
        throw new UnsupportedOperationException();
    }
}
