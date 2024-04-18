package dev.lavalink.youtube.clients;

import com.grack.nanojson.JsonWriter;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.http.YoutubeHttpContextFilter;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class ClientConfig {
    // https://github.com/MShawon/YouTube-Viewer/issues/593
    // root.cpn => content playback nonce, a-zA-Z0-9-_ (16 characters)
    // contextPlaybackContext.refer => url (video watch URL?)

    private String name;
    private String userAgent;
    private String apiKey;
    private final Map<String, Object> root;

    public ClientConfig() {
        this.root = new HashMap<>();
        this.userAgent = null;
        this.name = null;
    }

    private ClientConfig(Map<String, Object> context, String userAgent, String name) {
        this.root = context;
        this.userAgent = userAgent;
        this.name = name;
    }

    public ClientConfig copy() {
        return new ClientConfig(new HashMap<>(this.root), this.userAgent, this.name);
    }

    public ClientConfig withClientName(String name) {
        this.name = name;
        withClientField("clientName", name);
        return this;
    }

    public String getName() {
        return this.name;
    }

    public ClientConfig withUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public String getUserAgent() {
        return this.userAgent;
    }

    public ClientConfig withApiKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public Map<String, Object> putOnceAndJoin(Map<String, Object> on, String key) {
        return (Map<String, Object>) on.computeIfAbsent(key, __ -> new HashMap<String, Object>());
    }

    public ClientConfig withClientDefaultScreenParameters() {
        withClientField("screenDensityFloat", 1);
        withClientField("screenHeightPoints", 1080);
        withClientField("screenPixelDensity", 1);
        return withClientField("screenWidthPoints", 1920);
    }

    public ClientConfig withThirdPartyEmbedUrl(String embedUrl) {
        Map<String, Object> context = putOnceAndJoin(root, "context");
        Map<String, Object> thirdParty = putOnceAndJoin(context, "thirdParty");
        thirdParty.put("embedUrl", embedUrl);
        return this;
    }

    public ClientConfig withPlaybackSignatureTimestamp(String signatureTimestamp) {
        Map<String, Object> playbackContext = putOnceAndJoin(root, "playbackContext");
        Map<String, Object> contentPlaybackContext = putOnceAndJoin(playbackContext, "contentPlaybackContext");
        contentPlaybackContext.put("signatureTimestamp", signatureTimestamp);
        return this;
    }

    public ClientConfig withRootField(String key, Object value) {
        root.put(key, value);
        return this;
    }

    public ClientConfig withClientField(String key, Object value) {
        Map<String, Object> context = putOnceAndJoin(root, "context");
        Map<String, Object> client = putOnceAndJoin(context, "client");
        client.put(key, value);
        return this;
    }

    public ClientConfig withUserField(String key, Object value) {
        Map<String, Object> context = putOnceAndJoin(root, "context");
        Map<String, Object> user = putOnceAndJoin(context, "user");
        user.put(key, value);
        return this;
    }

    public ClientConfig setAttributes(HttpInterface httpInterface) {
        if (userAgent != null) {
            httpInterface.getContext().setAttribute(YoutubeHttpContextFilter.ATTRIBUTE_USER_AGENT_SPECIFIED, userAgent);
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

        AndroidVersion(String osVersion, int sdkVersion) {
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
