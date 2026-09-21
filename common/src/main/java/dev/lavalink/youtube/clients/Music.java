package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.RemotePoToken;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.MusicClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.apache.http.client.utils.URIBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class Music extends MusicClient {
    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withClientName("WEB_REMIX")
        .withClientField("clientVersion", "1.20240724.00.00");

    protected ClientOptions options;
    private volatile String poToken;
    public Music() {
        this(ClientOptions.DEFAULT);
    }

    public Music(@NotNull ClientOptions options) {
        this.options = options;
    }

    @Override
    @NotNull
    public ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        ClientConfig config = BASE_CONFIG.copy();
        if (poToken != null) {
            config.putOnceAndJoin(config.getRoot(), "serviceIntegrityDimensions").put("poToken", poToken);
        }
        return config;
    }

    @Override
    public boolean supportsFormatLoading() {
        return getOptions().getPlayback();
    }

    @Override
    public void preparePlayback(@NotNull YoutubeAudioSourceManager source,
                                @NotNull HttpInterface httpInterface,
                                @NotNull String videoId) throws IOException {
        RemotePoToken.Result result = source.generatePoToken(httpInterface, videoId);
        poToken = result == null ? null : result.getPoToken();
    }

    @Override
    @Nullable
    public String getPoToken() {
        return poToken;
    }

    @Override
    public boolean supportsSabrPlayback() {
        return true;
    }

    @Override
    @NotNull
    public URI transformPlaybackUri(@NotNull URI originalUri,
                                    @NotNull URI resolvedPlaybackUri,
                                    @Nullable String token) {
        if (token == null) {
            return resolvedPlaybackUri;
        }

        try {
            return new URIBuilder(resolvedPlaybackUri)
                .addParameter("pot", token)
                .build();
        } catch (URISyntaxException e) {
            return resolvedPlaybackUri;
        }
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
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }
}
