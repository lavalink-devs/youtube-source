package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;

public class VisionOs extends StreamingNonMusicClient {
    public static String CLIENT_VERSION = "1.02";
    public static String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15";

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withUserAgent(USER_AGENT)
        .withClientName("VISIONOS")
        .withClientField("clientVersion", CLIENT_VERSION)
        .withClientField("deviceMake", "Apple")
        .withClientField("deviceModel", "RealityDevice17,1")
        .withClientField("osName", "visionOS")
        .withClientField("osVersion", "26.5.23O471")
        .withUserField("lockedSafetyMode", false);

    protected ClientOptions options;

    public VisionOs() {
        this(ClientOptions.DEFAULT);
    }

    public VisionOs(@NotNull ClientOptions options) {
        this.options = options;
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
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

    @Override
    public boolean requirePlayerScript() {
        return false;
    }

    @Override
    public boolean supportsSabrPlayback() {
        return false;
    }

    @Override
    @NotNull
    public String getPlayerParams() {
        return WEB_PLAYER_PARAMS;
    }

    @Override
    public boolean canHandleRequest(@NotNull String identifier) {
        return !identifier.contains("list=") && super.canHandleRequest(identifier);
    }
}
