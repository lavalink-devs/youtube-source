package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import dev.lavalink.youtube.CannotBeLoaded;
import dev.lavalink.youtube.YoutubeAudioSourceManager;

import java.io.IOException;

public class AndroidTestsuiteWithThumbnail extends AndroidWithThumbnail {
    @Override
    protected ClientConfig getBaseClientConfig(HttpInterface httpInterface) {
        return AndroidTestsuite.BASE_CONFIG.copy();
    }

    @Override
    public String getIdentifier() {
        return AndroidTestsuite.BASE_CONFIG.getName();
    }

    @Override
    public boolean canHandleRequest(String identifier) {
        // loose check to avoid loading mixes/playlists.
        return !identifier.contains("list=") && super.canHandleRequest(identifier);
    }

    @Override
    public AudioItem loadMix(YoutubeAudioSourceManager source, HttpInterface httpInterface, String mixId, String selectedVideoId) throws CannotBeLoaded, IOException {
        // Considered returning null but an exception makes it clearer as to why a mix couldn't be loaded,
        // assuming someone tries to only register this client with the source manager.
        // Also, an exception will halt further loading so other source managers won't be queried.
        // N.B. This client genuinely cannot load mixes for whatever reason. You can get the mix metadata
        // but there are no videos in the response JSON. Weird.
        throw new FriendlyException("This client cannot load mixes", Severity.COMMON,
            new RuntimeException("ANDROID_TESTSUITE cannot be used to load mixes"));
    }

    @Override
    public AudioItem loadPlaylist(YoutubeAudioSourceManager source, HttpInterface httpInterface, String playlistId, String selectedVideoId) throws CannotBeLoaded {
        // Similar to mixes except server returns status code 500 when trying to load playlists.
        throw new FriendlyException("This client cannot load playlists", Severity.COMMON,
            new RuntimeException("ANDROID_TESTSUITE cannot be used to load playlists"));
    }
}
