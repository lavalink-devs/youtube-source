package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.ClientConfig.AndroidVersion;
import org.jetbrains.annotations.NotNull;

public class AndroidVr extends Android {
    public static String CLIENT_VERSION = "1.60.19";
    public static AndroidVersion ANDROID_VERSION = AndroidVersion.ANDROID_12L;

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withApiKey(Android.BASE_CONFIG.getApiKey())
        .withUserAgent(String.format("com.google.android.apps.youtube.vr.oculus/%s (Linux; U; Android %s; eureka-user Build/SQ3A.220605.009.A1) gzip", CLIENT_VERSION, ANDROID_VERSION.getOsVersion()))
        .withClientName("ANDROID_VR")
        .withClientField("clientVersion", CLIENT_VERSION)
        .withClientField("androidSdkVersion", ANDROID_VERSION.getSdkVersion());

    protected ClientOptions options;

    public AndroidVr() {
        this(ClientOptions.DEFAULT);
    }

    public AndroidVr(@NotNull ClientOptions options) {
        super(options, false);
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
    }

    @Override
    @NotNull
    public String getPlayerParams() {
        // not needed MOBILE_PLAYER_PARAMS if we pass it returns this video is not availiable
        return null;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }

    @Override
    @NotNull
    protected String extractPlaylistName(@NotNull JsonBrowser json) {
        return json.get("header").get("playlistHeaderRenderer").get("title").get("runs").index(0).get("text").text();
    }
}
