package dev.lavalink.youtube.clients.skeleton;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.ThumbnailTools;
import com.sedmelluq.discord.lavaplayer.track.*;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is deprecated.
 */
public abstract class ThumbnailMusicClient extends MusicClient {
    @Override
    @NotNull
    protected List<AudioTrack> extractSearchResultTracks(@NotNull YoutubeAudioSourceManager source,
                                                         @NotNull JsonBrowser json) {
        List<AudioTrack> tracks = new ArrayList<>();

        for (JsonBrowser track : json.values()) {
            JsonBrowser thumbnail = track.get("musicResponsiveListItemRenderer").get("thumbnail").get("musicThumbnailRenderer");
            JsonBrowser columns = track.get("musicResponsiveListItemRenderer").get("flexColumns");

            if (columns.isNull()) {
                continue;
            }

            JsonBrowser metadata = columns.index(0)
                .get("musicResponsiveListItemFlexColumnRenderer")
                .get("text")
                .get("runs")
                .index(0);

            String title = metadata.get("text").text();
            String videoId = metadata.get("navigationEndpoint").get("watchEndpoint").get("videoId").text();

            if (videoId == null) {
                // If the track is not available on YouTube Music, videoId will be empty
                continue;
            }

            List<JsonBrowser> runs = columns.index(1)
                .get("musicResponsiveListItemFlexColumnRenderer")
                .get("text")
                .get("runs")
                .values();

            String author = runs.get(0).get("text").text();
            JsonBrowser lastElement = runs.get(runs.size() - 1);

            if (!lastElement.get("navigationEndpoint").isNull()) {
                // The duration element should not have this key. If it does,
                // then duration is probably missing.
                continue;
            }

            long duration = DataFormatTools.durationTextToMillis(lastElement.get("text").text());
            String thumbnailUrl = ThumbnailTools.getYouTubeMusicThumbnail(thumbnail, videoId);

            AudioTrackInfo info = new AudioTrackInfo(title, author, duration, videoId, false, WATCH_URL + videoId, thumbnailUrl, null);
            tracks.add(source.buildAudioTrack(info));
        }

        return tracks;
    }
}
