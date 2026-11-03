package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.RemotePoToken;
import org.jetbrains.annotations.NotNull;
import java.util.Map;

public class WebSafari extends Web {

    public static final String SAFARI_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15,gzip(gfe)";

    public static ClientConfig BASE_CONFIG = new ClientConfig()
            .withClientName("WEB")
            .withClientField("clientVersion", "2.20261016.01.00")
            .withUserAgent(SAFARI_USER_AGENT)
            .withUserField("lockedSafetyMode", false);

    protected volatile String poToken;
    protected volatile String visitorData;

    public WebSafari() {
        this(ClientOptions.DEFAULT);
    }

    public WebSafari(@NotNull ClientOptions options) {
        super(options);
    }

    @Override
    @NotNull
    public ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        ClientConfig config = BASE_CONFIG.copy();
        if (visitorData != null) {
            config.withVisitorData(visitorData);
        }
        if (poToken != null) {
            Map<String, Object> serviceIntegrityDimensions = config.putOnceAndJoin(
                config.getRoot(), "serviceIntegrityDimensions");
            serviceIntegrityDimensions.put("poToken", poToken);
        }
        return config;
    }

    @Override
    public void preparePlayback(@NotNull YoutubeAudioSourceManager source, @NotNull HttpInterface httpInterface,
                                @NotNull String videoId) throws java.io.IOException {
        visitorData = source.getVisitorData();
        RemotePoToken.Result result = source.generatePoToken(httpInterface, visitorData != null ? visitorData : videoId);
        if (result == null) {
            poToken = null;
        } else {
            poToken = result.getPoToken();
            visitorData = result.getContentBinding();
        }
    }

    @Override
    public String getPoToken() {
        return poToken;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "WEB_SAFARI";
    }
}
