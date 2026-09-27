package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.ClientConfig.AndroidVersion;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Android extends StreamingNonMusicClient {
    private static final Logger log = LoggerFactory.getLogger(Android.class);

    public static String CLIENT_VERSION = "21.02.35";
    public static AndroidVersion ANDROID_VERSION = AndroidVersion.ANDROID_11;

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withUserAgent(String.format("com.google.android.youtube/%s (Linux; U; Android %s) gzip", CLIENT_VERSION, ANDROID_VERSION.getOsVersion()))
        .withClientName("ANDROID")
        .withClientField("clientVersion", CLIENT_VERSION)
        .withClientField("androidSdkVersion", ANDROID_VERSION.getSdkVersion())
        .withUserField("lockedSafetyMode", false);

    protected ClientOptions options;

    public Android() {
        this(ClientOptions.DEFAULT);
    }

    public Android(@NotNull ClientOptions options) {
        this(options, true);
    }

    protected Android(@NotNull ClientOptions options, boolean logWarning) {
        this.options = options;

        if (logWarning) {
            log.warn("ANDROID is broken with no known fix. It is no longer advised to use this client.");
        }
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
    }

    @Override
    @Nullable
    public String getPlayerParams() {
        return null;
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
    @NotNull
    protected String extractPlaylistName(@NotNull JsonBrowser json) {
        JsonBrowser pageHeader = json.get("header").get("pageHeaderRenderer");
        String title = pageHeader.get("pageTitle").text();

        if (!DataFormatTools.isNullOrEmpty(title)) {
            return title;
        }

        title = pageHeader.get("content")
                .get("elementRenderer")
                .get("newElement")
                .get("type")
                .get("componentType")
                .get("model")
                .get("youtubeModel")
                .get("viewModel")
                .get("pageHeaderViewModel")
                .get("title")
                .get("dynamicTextViewModel")
                .get("text")
                .get("content")
                .text();

        if (!DataFormatTools.isNullOrEmpty(title)) {
            return title;
        }

        return super.extractPlaylistName(json);
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
    public boolean requirePlayerScript() {
        return false;
    }
}
