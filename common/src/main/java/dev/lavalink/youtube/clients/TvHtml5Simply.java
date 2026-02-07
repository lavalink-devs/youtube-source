package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.*;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TvHtml5Simply extends StreamingNonMusicClient {
    
    public static ClientConfig BASE_CONFIG = new ClientConfig()
            .withClientName("TVHTML5_SIMPLY")
            .withClientField("clientVersion", "1.0")
            .withRootField("attestationRequest", java.util.Map.of("omitBotguardData", true));

    protected ClientOptions options;

    public TvHtml5Simply() {
        this(ClientOptions.DEFAULT);
    }

    public TvHtml5Simply(@NotNull ClientOptions options) {
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
        return super.canHandleRequest(identifier);
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