package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Ios extends StreamingNonMusicClient {
    public static String CLIENT_VERSION = "21.32.4";

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withUserAgent(String.format("com.google.ios.youtube/%s (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)", CLIENT_VERSION))
        .withClientName("IOS")
        .withClientField("clientVersion", CLIENT_VERSION)
        .withUserField("lockedSafetyMode", false);

    protected ClientOptions options;

    public Ios() {
        this(ClientOptions.DEFAULT);
    }

    public Ios(@NotNull ClientOptions options) {
        this.options = options;
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
    }

    @Override
    @NotNull
    protected JsonBrowser extractPlaylistVideoList(@NotNull JsonBrowser json) {
        JsonBrowser sections = json.get("contents")
                .get("singleColumnBrowseResultsRenderer")
                .get("tabs")
                .index(0)
                .get("tabRenderer")
                .get("content")
                .get("sectionListRenderer")
                .get("contents");

        JsonBrowser legacyList = sections.index(0)
                .get("itemSectionRenderer")
                .get("contents")
                .index(0)
                .get("playlistVideoListRenderer");

        if (!legacyList.isNull()) {
            return legacyList;
        }

        JsonBrowser videoSection = sections.index(1).get("itemSectionRenderer");

        if (!videoSection.isNull()) {
            return videoSection;
        }

        return sections.index(0).get("itemSectionRenderer");
    }

    @Override
    @NotNull
    protected String extractPlaylistName(@NotNull JsonBrowser json) {
        String title = json.get("header")
                .get("pageHeaderRenderer")
                .get("pageTitle")
                .text();

        if (!DataFormatTools.isNullOrEmpty(title)) {
            return title;
        }

        return super.extractPlaylistName(json);
    }

    @Override
    @NotNull
    protected JsonBrowser extractPlaylistContinuationVideos(@NotNull JsonBrowser continuationJson) {
        JsonBrowser continuationContents = continuationJson.get("continuationContents");
        JsonBrowser itemSection = continuationContents.get("itemSectionContinuation");

        if (!itemSection.isNull()) {
            return itemSection;
        }

        return super.extractPlaylistContinuationVideos(continuationJson);
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

            JsonBrowser element = track.get("elementRenderer");

            if (!element.isNull()) {
                AudioTrack audioTrack = extractElementTrack(element, source);

                if (audioTrack != null) {
                    tracks.add(audioTrack);
                }
            }
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
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }

    @Override
    public boolean requirePlayerScript() {
        return false;
    }
}
