package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.skeleton.ThumbnailStreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;

public class IosWithThumbnail extends ThumbnailStreamingNonMusicClient {
    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return Ios.BASE_CONFIG.copy();
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
    public String getPlayerParams() {
        return MOBILE_PLAYER_PARAMS;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return Ios.BASE_CONFIG.getName();
    }
}
