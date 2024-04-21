package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.ThumbnailTools;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.ThumbnailStreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TvHtml5EmbeddedWithThumbnail extends ThumbnailStreamingNonMusicClient {
    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return TvHtml5Embedded.BASE_CONFIG.copy();
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
            JsonBrowser authorJson = item.get("shortBylineText");

            // this client doesn't appear to receive "isPlayable" fields.
            // author is null -> video is region blocked
            if (!authorJson.isNull()) {
                String videoId = item.get("videoId").text();
                JsonBrowser titleField = item.get("title");
                String title = titleField.get("simpleText").textOrDefault(titleField.get("runs").index(0).get("text").text());
                String author = authorJson.get("runs").index(0).get("text").textOrDefault("Unknown artist");
                long duration = Units.secondsToMillis(item.get("lengthSeconds").asLong(Units.DURATION_SEC_UNKNOWN));
                String thumbnailUrl = ThumbnailTools.getYouTubeThumbnail(item, videoId);

                AudioTrackInfo info = new AudioTrackInfo(title, author, duration, videoId, false, WATCH_URL + videoId, thumbnailUrl, null);
                tracks.add(source.buildAudioTrack(info));
            }
        }
    }

    @Override
    @NotNull
    public String getPlayerParams() {
        return WEB_PLAYER_PARAMS;
    }

    @Override
    public boolean canHandleRequest(@NotNull String identifier) {
        // loose check to avoid loading playlists.
        // this client does support them, but it seems to be missing fields
        // that could be the difference between playable and unplayable --
        // notably the "isPlayable" field.
        // I'm also cautious of routing a lot of traffic through this client.
        // There is overridden code above but that's mostly just for reference.
        return (!identifier.contains("list=") || identifier.contains("list=RD")) && super.canHandleRequest(identifier);
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return TvHtml5Embedded.BASE_CONFIG.getName();
    }

    @Override
    public AudioItem loadPlaylist(@NotNull YoutubeAudioSourceManager source,
                                  @NotNull HttpInterface httpInterface,
                                  @NotNull String playlistId,
                                  @Nullable String selectedVideoId) {
        throw new UnsupportedOperationException();
    }
}
