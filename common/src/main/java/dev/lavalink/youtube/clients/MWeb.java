package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import org.jetbrains.annotations.NotNull;

public class MWeb extends Web {
    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withApiKey("AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8")
        .withClientName("MWEB")
        .withClientField("clientVersion", "2.20240726.11.00");

    public MWeb() {
        super();
    }

    public MWeb(@NotNull ClientOptions options) {
        super(options);
    }

    @Override
    @NotNull
    public ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
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
        return json.get("header")
            .get("pageHeaderRenderer")
            .get("pageTitle")
            .text();
    }

    @Override
    @NotNull
    protected JsonBrowser extractPlaylistVideoList(@NotNull JsonBrowser json) {
        return json.get("contents")
            .get("singleColumnBrowseResultsRenderer")
            .get("tabs")
            .index(0)
            .get("tabRenderer")
            .get("content")
            .get("sectionListRenderer")
            .get("contents")
            .index(0)
            .get("itemSectionRenderer")
            .get("contents")
            .index(0)
            .get("playlistVideoListRenderer");
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }
}
