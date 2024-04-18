package dev.lavalink.youtube.clients.skeleton;

import com.sedmelluq.discord.lavaplayer.tools.*;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.*;
import dev.lavalink.youtube.CannotBeLoaded;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.NonMusicClient;
import dev.lavalink.youtube.track.TemporalInfo;

import java.io.IOException;
import java.util.List;

/**
 * The base class for a client that is used for everything except music.youtube.com.
 */
public abstract class ThumbnailNonMusicClient extends NonMusicClient {
    protected void extractPlaylistTracks(JsonBrowser json,
                                         List<AudioTrack> tracks,
                                         YoutubeAudioSourceManager source) {
        if (!json.get("contents").isNull()) {
            json = json.get("contents");
        }

        if (json.isNull()) {
            return;
        }

        for (JsonBrowser track : json.values()) {
            JsonBrowser item = track.get("playlistVideoRenderer");
            JsonBrowser authorJson = item.get("shortBylineText");

            // isPlayable is null -> video has been removed/blocked
            // author is null -> video is region blocked
            if (!item.get("isPlayable").isNull() && !authorJson.isNull()) {
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

    protected AudioTrack extractAudioTrack(JsonBrowser json, YoutubeAudioSourceManager source) {
        // Ignore if it's not a track or if it's a livestream
        if (json.isNull() || json.get("lengthText").isNull() || !json.get("unplayableText").isNull()) return null;

        String videoId = json.get("videoId").text();
        JsonBrowser titleJson = json.get("title");
        String title = titleJson.get("runs").index(0).get("text").textOrDefault(titleJson.get("simpleText").text());
        String author = json.get("longBylineText").get("runs").index(0).get("text").text();

        JsonBrowser durationJson = json.get("lengthText");
        String durationText = durationJson.get("runs").index(0).get("text").textOrDefault(durationJson.get("simpleText").text());

        long duration = DataFormatTools.durationTextToMillis(durationText);
        String thumbnailUrl = ThumbnailTools.getYouTubeThumbnail(json, videoId);

        AudioTrackInfo info = new AudioTrackInfo(title, author, duration, videoId, false, WATCH_URL + videoId, thumbnailUrl, null);
        return source.buildAudioTrack(info);
    }

    @Override
    public AudioItem loadVideo(YoutubeAudioSourceManager source,
                               HttpInterface httpInterface,
                               String videoId) throws CannotBeLoaded, IOException {
        JsonBrowser json = loadTrackInfoFromInnertube(source, httpInterface, videoId, null);
        JsonBrowser playabilityStatus = json.get("playabilityStatus");
        JsonBrowser videoDetails = json.get("videoDetails");

        String title = videoDetails.get("title").text();
        String author = videoDetails.get("author").text();

        TemporalInfo temporalInfo = TemporalInfo.fromRawData(
            !playabilityStatus.get("liveStreamability").isNull(),
            videoDetails.get("lengthSeconds"),
            false
        );

        String thumbnailUrl = ThumbnailTools.getYouTubeThumbnail(json, videoId);

        AudioTrackInfo info = new AudioTrackInfo(title, author, temporalInfo.durationMillis, videoId, temporalInfo.isActiveStream, WATCH_URL + videoId, thumbnailUrl, null);
        return source.buildAudioTrack(info);
    }
}
