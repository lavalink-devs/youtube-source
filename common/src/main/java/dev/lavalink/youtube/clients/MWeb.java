package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import dev.lavalink.youtube.OptionDisabledException;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MWeb extends Web {
    public static ClientConfig BASE_CONFIG = new ClientConfig()
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
