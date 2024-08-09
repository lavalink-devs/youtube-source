package dev.lavalink.youtube.clients;

import com.grack.nanojson.JsonWriter;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.http.YoutubeHttpContextFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class ClientConfig {
    // https://github.com/MShawon/YouTube-Viewer/issues/593
    // root.cpn => content playback nonce, a-zA-Z0-9-_ (16 characters)
    // contextPlaybackContext.refer => url (video watch URL?)

    private String name;
    private String userAgent;
    private String visitorData;
    private String apiKey;
    private final Map<String, Object> root;

    public ClientConfig() {
        this.name = null;
        this.userAgent = null;
        this.visitorData = null;
        this.root = new HashMap<>();
    }

    private ClientConfig(@NotNull Map<String, Object> context,
                         @NotNull String userAgent,
                         @NotNull String visitorData,
                         @NotNull String name) {
        this.name = name;
        this.userAgent = userAgent;
        this.visitorData = visitorData;
        this.root = context;
    }

    public String getName() {
        return this.name;
    }

    public String getUserAgent() {
        return this.userAgent;
    }

    public String getVisitorData() {
        return this.visitorData;
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public Map<String, Object> getRoot() {
        return this.root;
    }

    public ClientConfig copy() {
        return new ClientConfig(new HashMap<>(this.root), this.userAgent, this.visitorData, this.name);
    }

    public ClientConfig withClientName(@NotNull String name) {
        this.name = name;
        withClientField("clientName", name);
        return this;
    }

    public ClientConfig withUserAgent(@NotNull String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public ClientConfig withVisitorData(@Nullable String visitorData) {
        this.visitorData = visitorData;

        if (visitorData != null) {
            withClientField("visitorData", visitorData);
        } else {
            Map<String, Object> context = (Map<String, Object>) root.get("context");

            if (context != null) {
                Map<String, Object> client = (Map<String, Object>) context.get("client");

                if (client != null) {
                    client.remove("visitorData");

                    if (client.isEmpty()) {
                        context.remove("client");
                    }
                }

                if (context.isEmpty()) {
                    root.remove("context");
                }
            }
        }

        return this;
    }

    public ClientConfig withApiKey(@NotNull String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public Map<String, Object> putOnceAndJoin(@NotNull Map<String, Object> on,
                                              @NotNull String key) {
        return (Map<String, Object>) on.computeIfAbsent(key, __ -> new HashMap<String, Object>());
    }

    public ClientConfig withClientDefaultScreenParameters() {
        withClientField("screenDensityFloat", 1);
        withClientField("screenHeightPoints", 1080);
        withClientField("screenPixelDensity", 1);
        return withClientField("screenWidthPoints", 1920);
    }

    public ClientConfig withThirdPartyEmbedUrl(@NotNull String embedUrl) {
        Map<String, Object> context = putOnceAndJoin(root, "context");
        Map<String, Object> thirdParty = putOnceAndJoin(context, "thirdParty");
        thirdParty.put("embedUrl", embedUrl);
        return this;
    }

    public ClientConfig withPlaybackSignatureTimestamp(@NotNull String signatureTimestamp) {
        Map<String, Object> playbackContext = putOnceAndJoin(root, "playbackContext");
        Map<String, Object> contentPlaybackContext = putOnceAndJoin(playbackContext, "contentPlaybackContext");
        contentPlaybackContext.put("signatureTimestamp", signatureTimestamp);
        return this;
    }

    public ClientConfig withRootField(@NotNull String key,
                                      @Nullable Object value) {
        root.put(key, value);
        return this;
    }

    public ClientConfig withClientField(@NotNull String key,
                                        @Nullable Object value) {
        Map<String, Object> context = putOnceAndJoin(root, "context");
        Map<String, Object> client = putOnceAndJoin(context, "client");
        client.put(key, value);
        return this;
    }

    public ClientConfig withUserField(@NotNull String key,
                                      @Nullable Object value) {
        Map<String, Object> context = putOnceAndJoin(root, "context");
        Map<String, Object> user = putOnceAndJoin(context, "user");
        user.put(key, value);
        return this;
    }

    public ClientConfig setAttributes(@NotNull HttpInterface httpInterface) {
        if (userAgent != null) {
            httpInterface.getContext().setAttribute(YoutubeHttpContextFilter.ATTRIBUTE_USER_AGENT_SPECIFIED, userAgent);

            if (visitorData != null) {
                httpInterface.getContext().setAttribute(YoutubeHttpContextFilter.ATTRIBUTE_VISITOR_DATA_SPECIFIED, visitorData);
            }
        }

        return this;
    }

    public String toJsonString() {
        return JsonWriter.string().object(root).done();
    }

    public enum AndroidVersion {
        // https://apilevels.com/
        ANDROID_13("13", 33),
        ANDROID_12("12", 31), // 12L => 32
        ANDROID_11("11", 30);

        private final String osVersion;
        private final int sdkVersion;

        AndroidVersion(@NotNull String osVersion, int sdkVersion) {
            this.osVersion = osVersion;
            this.sdkVersion = sdkVersion;
        }

        public String getOsVersion() {
            return this.osVersion;
        }

        public int getSdkVersion() {
            return this.sdkVersion;
        }
    }
}
