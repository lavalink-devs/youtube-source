package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.RemotePoToken;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.Nullable;

public class TvHtml5Simply extends StreamingNonMusicClient {

    public static ClientConfig BASE_CONFIG = new ClientConfig()
            .withClientName("TVHTML5_SIMPLY")
            .withClientField("clientVersion", "1.0")
            .withUserAgent("Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15")
            .withRootField("attestationRequest", new HashMap<String, Object>() {{ put("omitBotguardData", true); }});

    protected ClientOptions options;
    private volatile String poToken;
    private volatile String visitorData;

    public TvHtml5Simply() {
        this(ClientOptions.DEFAULT);
    }

    public TvHtml5Simply(@NotNull ClientOptions options) {
        this.options = options;
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        ClientConfig config = BASE_CONFIG.copy();
        if (visitorData != null) {
            config.withVisitorData(visitorData);
        }
        if (poToken != null) {
            Map<String, Object> serviceIntegrityDimensions = config.putOnceAndJoin(
                config.getRoot(), "serviceIntegrityDimensions");
            serviceIntegrityDimensions.put("poToken", poToken);
        }
        return config;
    }

    @Override
    public void preparePlayback(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface,
                                @NotNull String videoId) throws java.io.IOException {
        visitorData = source.getVisitorData();
        RemotePoToken.Result result = visitorData == null ? null : source.generatePoToken(httpInterface, visitorData);
        if (result == null) {
            poToken = null;
        } else {
            poToken = result.getPoToken();
            visitorData = result.getContentBinding();
        }
    }

    @Override
    public String getPoToken() {
        return poToken;
    }

    @Override
    public boolean supportsSabrPlayback() {
        return true;
    }

    @Override
    protected boolean preferSabrPlayback() {
        return false;
    }

    @Override
    @NotNull
    public URI transformPlaybackUri(@NotNull URI originalUri, @NotNull URI resolvedPlaybackUri,
                                    @Nullable String poToken) {
        if (poToken == null) {
            return resolvedPlaybackUri;
        }

        try {
            URIBuilder builder = new URIBuilder(resolvedPlaybackUri);
            builder.addParameter("pot", poToken);
            return builder.build();
        } catch (URISyntaxException e) {
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
    public boolean canHandleRequest(@NotNull String identifier) {
        return !identifier.contains("list=") && super.canHandleRequest(identifier);
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }

    @Override
    public boolean isEmbedded() {
        return true;
    }

    @Override
    @NotNull
    protected JsonBrowser extractPlaylistVideoList(@NotNull JsonBrowser json) {
        return json.get("contents")
                .get("sectionListRenderer")
                .get("contents")
                .index(0)
                .get("playlistVideoListRenderer");
    }

    @Override
    protected String extractPlaylistName(@NotNull JsonBrowser json) {
        return json.get("header")
                .get("playlistHeaderRenderer")
                .get("title")
                .get("runs")
                .index(0)
                .get("text")
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
            JsonBrowser item = track.get("videoRenderer");

            if (item.isNull()) {
                continue;
            }

            JsonBrowser authorJson = item.get("shortBylineText");
            if (authorJson.isNull()) {
                authorJson = item.get("longBylineText");
            }

            // author is null -> video is region blocked
            if (!authorJson.isNull()) {
                String videoId = item.get("videoId").text();

                if (videoId == null) {
                    continue;
                }

                JsonBrowser titleField = item.get("title");
                String title = DataFormatTools.defaultOnNull(
                        titleField.get("simpleText").text(),
                        titleField.get("runs").index(0).get("text").text());

                String author = DataFormatTools.defaultOnNull(
                        authorJson.get("runs").index(0).get("text").text(),
                        "Unknown artist");

                long duration = Units.DURATION_MS_UNKNOWN;
                JsonBrowser lengthJson = item.get("lengthText");

                if (!lengthJson.isNull()) {
                    String lengthText = DataFormatTools.defaultOnNull(
                            lengthJson.get("runs").index(0).get("text").text(),
                            lengthJson.get("simpleText").text());

                    if (lengthText != null) {
                        duration = DataFormatTools.durationTextToMillis(lengthText);
                    }
                }

                tracks.add(buildAudioTrack(source, item, title, author, duration, videoId, false));
            }
        }
    }
}
