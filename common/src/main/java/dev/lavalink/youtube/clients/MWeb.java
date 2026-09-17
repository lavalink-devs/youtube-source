package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.RemotePoToken;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.http.client.utils.URIBuilder;

public class MWeb extends StreamingNonMusicClient {
    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withClientName("MWEB")
        .withClientField("clientVersion", "2.20260904.01.00")
        .withUserAgent("Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)");

    protected ClientOptions options;
    private volatile String poToken;

    public MWeb() {
        this(ClientOptions.DEFAULT);
    }

    public MWeb(@NotNull ClientOptions options) {
        this.options = options;
    }

    @Override
    public boolean supportsFormatLoading() {
        return getOptions().getPlayback();
    }

    @Override
    public boolean supportsSabrPlayback() {
        return true;
    }

    @Override
    public boolean canHandleRequest(@NotNull String identifier) {
        return !identifier.startsWith(YoutubeAudioSourceManager.MUSIC_SEARCH_PREFIX);
    }

    @Override
    @NotNull
    public ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        ClientConfig config = BASE_CONFIG.copy();
        if (poToken != null) {
            config.putOnceAndJoin(config.getRoot(), "serviceIntegrityDimensions").put("poToken", poToken);
        }
        return config;
    }

    @Override
    public void preparePlayback(@NotNull YoutubeAudioSourceManager source,
                                @NotNull HttpInterface httpInterface,
                                @NotNull String videoId) throws IOException {
        RemotePoToken.Result result = source.generatePoToken(httpInterface, videoId);
        if (result != null) {
            poToken = result.getPoToken();
        }
    }

    @Override
    @Nullable
    public String getPoToken() {
        return poToken;
    }

    @Override
    @NotNull
    public URI transformPlaybackUri(@NotNull URI originalUri,
                                    @NotNull URI resolvedPlaybackUri,
                                    @Nullable String token) {
        if (token == null) {
            return resolvedPlaybackUri;
        }

        try {
            return new URIBuilder(resolvedPlaybackUri)
                .addParameter("pot", token)
                .build();
        } catch (URISyntaxException e) {
            return resolvedPlaybackUri;
        }
    }

    @Override
    @NotNull
    public String getPlayerParams() {
        return MOBILE_PLAYER_PARAMS;
    }

    @Override
    @NotNull
    public ClientOptions getOptions() {
        return options;
    }

    @Override
    @NotNull
    protected List<AudioTrack> extractSearchResults(@NotNull YoutubeAudioSourceManager source,
                                                    @NotNull JsonBrowser json) {
        return json.get("contents")
            .get("sectionListRenderer")
            .get("contents")
            .values() // .index(0)
            .stream()
            .flatMap(item -> item.get("itemSectionRenderer").get("contents").values().stream()) // actual results
            .map(item -> extractAudioTrack(item.get("videoWithContextRenderer"), source))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    @NotNull
    protected JsonBrowser extractMixPlaylistData(@NotNull JsonBrowser json) {
        return json.get("contents")
            .get("singleColumnWatchNextResults")
            .get("playlist")
            .get("playlist");
    }

    @Override
    protected String extractPlaylistName(@NotNull JsonBrowser json) {
        JsonBrowser pageHeader = json.get("header").get("pageHeaderRenderer");
        String title = pageHeader.get("pageTitle").text();

        if (!DataFormatTools.isNullOrEmpty(title)) {
            return title;
        }

        title = pageHeader.get("content").get("pageHeaderViewModel").get("title")
            .get("dynamicTextViewModel").get("text").get("content").text();

        if (!DataFormatTools.isNullOrEmpty(title)) {
            return title;
        }

        return super.extractPlaylistName(json);
    }

    @Override
    @NotNull
    protected JsonBrowser extractPlaylistVideoList(@NotNull JsonBrowser json) {
        JsonBrowser itemSectionContents = json.get("contents")
            .get("singleColumnBrowseResultsRenderer")
            .get("tabs")
            .index(0)
            .get("tabRenderer")
            .get("content")
            .get("sectionListRenderer")
            .get("contents")
            .index(0)
            .get("itemSectionRenderer")
            .get("contents");

        JsonBrowser playlistVideoList = itemSectionContents
            .index(0)
            .get("playlistVideoListRenderer");

        if (!playlistVideoList.isNull()) {
            return playlistVideoList;
        }

        return itemSectionContents;
    }

    @Override
    @Nullable
    protected String extractPlaylistContinuationToken(@NotNull JsonBrowser videoList) {
        JsonBrowser contents = videoList.get("contents");

        if (!contents.isNull()) {
            videoList = contents;
        }

        return videoList.values()
            .stream()
            .filter(item -> !item.get("continuationItemRenderer").isNull() || !item.get("continuationItemViewModel").isNull())
            .findFirst()
            .map(item -> {
                JsonBrowser continuationItem = item.get("continuationItemRenderer");

                if (!continuationItem.isNull()) {
                    JsonBrowser continuationEndpoint = continuationItem.get("continuationEndpoint");
                    String token = continuationEndpoint.get("continuationCommand").get("token").text();

                    if (!DataFormatTools.isNullOrEmpty(token)) {
                        return token;
                    }

                    return continuationEndpoint.get("commandExecutorCommand").get("commands").index(1)
                        .get("continuationCommand").get("token").text();
                }

                return item.get("continuationItemViewModel")
                    .get("continuationCommand")
                    .get("innertubeCommand")
                    .get("continuationCommand")
                    .get("token")
                    .text();
            })
            .orElse(null);
    }

    @Override
    @NotNull
    protected JsonBrowser extractPlaylistContinuationVideos(@NotNull JsonBrowser continuationJson) {
        return continuationJson.get("onResponseReceivedActions")
            .index(0)
            .get("appendContinuationItemsAction")
            .get("continuationItems");
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
            JsonBrowser item = track.get("playlistVideoRenderer");

            if (!item.isNull()) {
                super.extractPlaylistTracks(track, tracks, source);
                continue;
            }

            JsonBrowser lockup = track.get("lockupViewModel");

            if (!lockup.isNull()) {
                AudioTrack audioTrack = extractLockupTrack(lockup, source);

                if (audioTrack != null) {
                    tracks.add(audioTrack);
                }
            }
        }
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }
}
