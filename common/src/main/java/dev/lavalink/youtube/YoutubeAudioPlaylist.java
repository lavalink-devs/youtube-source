package dev.lavalink.youtube;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class YoutubeAudioPlaylist extends BasicAudioPlaylist {

    @Nullable
    protected final String url;

    public YoutubeAudioPlaylist(String name, List<AudioTrack> tracks, AudioTrack selectedTrack, boolean isSearchResult, @Nullable String url) {
        super(name, tracks, selectedTrack, isSearchResult);
        this.url = url;
    }

    @Nullable
    public String getUrl() {
        return url;
    }

}
