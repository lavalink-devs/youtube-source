package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.ThumbnailTools;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.jetbrains.annotations.NotNull;

public class MusicWithThumbnail extends Music {
    public MusicWithThumbnail() {
        super();
    }

    public MusicWithThumbnail(@NotNull ClientOptions options) {
        super(options);
    }

    @Override
    @NotNull
    public AudioTrack buildAudioTrack(@NotNull YoutubeAudioSourceManager source,
                                      @NotNull JsonBrowser json,
                                      @NotNull String title,
                                      @NotNull String author,
                                      long duration,
                                      @NotNull String videoId,
                                      boolean isStream) {
        JsonBrowser thumbnailJson = json.get("musicResponsiveListItemRenderer").get("thumbnail").get("musicThumbnailRenderer");
        String thumbnail = ThumbnailTools.getYouTubeMusicThumbnail(thumbnailJson, videoId);
        return source.buildAudioTrack(new AudioTrackInfo(title, author, duration, videoId, isStream, WATCH_URL + videoId, thumbnail, null));
    }
}
