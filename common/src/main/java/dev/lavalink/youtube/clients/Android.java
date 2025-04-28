package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.ClientConfig.AndroidVersion;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Android extends StreamingNonMusicClient {
    private static final Logger log = LoggerFactory.getLogger(Android.class);

    public static String CLIENT_VERSION = "19.44.38";
    public static AndroidVersion ANDROID_VERSION = AndroidVersion.ANDROID_11;

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withUserAgent(String.format("com.google.android.youtube/%s (Linux; U; Android %s) gzip", CLIENT_VERSION, ANDROID_VERSION.getOsVersion()))
        .withClientName("ANDROID")
        .withClientField("clientVersion", CLIENT_VERSION)
        .withClientField("androidSdkVersion", ANDROID_VERSION.getSdkVersion())
        .withUserField("lockedSafetyMode", false);

    protected ClientOptions options;

    public Android() {
        this(ClientOptions.DEFAULT);
    }

    public Android(@NotNull ClientOptions options) {
        this(options, true);
    }

    protected Android(@NotNull ClientOptions options, boolean logWarning) {
        this.options = options;

        if (logWarning) {
            log.warn("ANDROID is broken with no known fix. It is no longer advised to use this client.");
        }
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
    }

    @Override
    @Nullable
    public String getPlayerParams() {
        return null;
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
    @NotNull
    protected String extractPlaylistName(@NotNull JsonBrowser json) {
        return json.get("header")
                .get("pageHeaderRenderer")
                .get("content")
                .get("elementRenderer")
                .get("newElement")
                .get("type")
                .get("componentType")
                .get("model")
                .get("youtubeModel")
                .get("viewModel")
                .get("pageHeaderViewModel")
                .get("title")
                .get("dynamicTextViewModel")
                .get("text")
                .get("content")
                .text();
    }

    @Override
    public boolean requirePlayerScript() {
        return false;
    }
}
