package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TvHtml5Simply extends StreamingNonMusicClient {
    public static ClientConfig BASE_CONFIG = new ClientConfig()
            .withClientName("TVHTML5_SIMPLY")
            .withClientField("clientVersion", "1.0")
            .withRootField("attestationRequest", java.util.Map.of("omitBotguardData", true));

    protected ClientOptions options;

    public TvHtml5Simply() {
        this(ClientOptions.DEFAULT);
    }

    public TvHtml5Simply(@NotNull ClientOptions options) {
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
        return WEB_PLAYER_PARAMS;
    }

    @Override
    @NotNull
    public ClientOptions getOptions() {
        return this.options;
    }

    @Override
    public boolean canHandleRequest(@NotNull String identifier) {
        return !identifier.contains("list=") && super.canHandleRequest(identifier);
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }

    @Override
    public boolean isEmbedded() {
        return true;
    }

    @Override
    public AudioItem loadPlaylist(@NotNull YoutubeAudioSourceManager source,
            @NotNull HttpInterface httpInterface,
            @NotNull String playlistId,
            @Nullable String selectedVideoId) {
        throw new FriendlyException("This client cannot load playlists", Severity.COMMON,
                new RuntimeException("TVHTML5_SIMPLY cannot be used to load playlists"));
    }

    @Override
    public AudioItem loadMix(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface,
            @NotNull String mixId, @Nullable String selectedVideoId) {
        throw new FriendlyException("This client cannot load mixes", Severity.COMMON,
                new RuntimeException("TVHTML5_SIMPLY cannot be used to load mixes"));
    }
}