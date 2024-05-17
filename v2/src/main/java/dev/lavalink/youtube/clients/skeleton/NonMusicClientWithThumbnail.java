package dev.lavalink.youtube.clients.skeleton;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.ThumbnailTools;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.jetbrains.annotations.NotNull;

public interface NonMusicClientWithThumbnail extends Client {
    @Override
    @NotNull
    default AudioTrack buildAudioTrack(@NotNull YoutubeAudioSourceManager source,
                                       @NotNull JsonBrowser json,
                                       @NotNull String title,
                                       @NotNull String author,
                                       long duration,
                                       @NotNull String videoId,
                                       boolean isStream) {
        String thumbnail = ThumbnailTools.getYouTubeThumbnail(json, videoId);
        return source.buildAudioTrack(new AudioTrackInfo(title, author, duration, videoId, isStream, WATCH_URL + videoId, thumbnail, null));
    }
}
