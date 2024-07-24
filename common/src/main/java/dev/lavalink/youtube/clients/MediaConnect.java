package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MediaConnect extends StreamingNonMusicClient {
    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withClientName("MEDIA_CONNECT_FRONTEND")
        .withClientField("clientVersion", "0.1");

    protected ClientOptions options;

    public MediaConnect() {
        this(ClientOptions.DEFAULT);
    }

    public MediaConnect(@NotNull ClientOptions options) {
        this.options = options;
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
    }

    @Override
    @NotNull
    public String getPlayerParams() {
        return MOBILE_PLAYER_PARAMS;
    }

    @Override
    @NotNull
    public ClientOptions getOptions() {
        return this.options;
    }

    @Override
    public boolean canHandleRequest(@NotNull String identifier) {
        // This client appears to be able to load livestreams and videos, but will
        // receive 400 bad request when loading playlists. mixes do return JSON, but does not contain mix videos.
        return !identifier.startsWith(YoutubeAudioSourceManager.SEARCH_PREFIX) && !identifier.contains("list=") && super.canHandleRequest(identifier);
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }

    @Override
    public AudioItem loadSearch(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface, @NotNull String searchQuery) {
        throw new FriendlyException("This client cannot load searches", Severity.COMMON,
            new RuntimeException("MEDIA_CONNECT cannot be used to load searches"));
    }

    @Override
    public AudioItem loadPlaylist(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface, @NotNull String playlistId, @Nullable String selectedVideoId) {
        throw new FriendlyException("This client cannot load playlists", Severity.COMMON,
            new RuntimeException("MEDIA_CONNECT cannot be used to load playlists"));
    }

    @Override
    public AudioItem loadMix(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface, @NotNull String mixId, @Nullable String selectedVideoId) {
        throw new FriendlyException("This client cannot load mixes", Severity.COMMON,
            new RuntimeException("MEDIA_CONNECT cannot be used to load mixes"));
    }
}
