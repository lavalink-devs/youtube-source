package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.skeleton.ThumbnailStreamingNonMusicClient;

public class IosWithThumbnail extends ThumbnailStreamingNonMusicClient {
    @Override
    protected ClientConfig getBaseClientConfig(HttpInterface httpInterface) {
        return Ios.BASE_CONFIG.copy();
    }

    @Override
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
            .get("itemSectionRenderer")
            .get("contents")
            .index(0)
            .get("playlistVideoListRenderer");
    }

    @Override
    public String getPlayerParams() {
        return MOBILE_PLAYER_PARAMS;
    }

    @Override
    public String getIdentifier() {
        return Ios.BASE_CONFIG.getName();
    }
}
