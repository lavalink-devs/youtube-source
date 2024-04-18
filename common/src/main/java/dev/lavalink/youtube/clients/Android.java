package dev.lavalink.youtube.clients;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.ClientConfig.AndroidVersion;
import dev.lavalink.youtube.clients.skeleton.StreamingNonMusicClient;

public class Android extends StreamingNonMusicClient {
    public static String CLIENT_VERSION = "19.07.39";
    public static AndroidVersion ANDROID_VERSION = AndroidVersion.ANDROID_11;

    public static ClientConfig BASE_CONFIG = new ClientConfig()
        .withApiKey("AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w")
        .withUserAgent(String.format("com.google.android.youtube/%s (Linux; U; Android %s) gzip", CLIENT_VERSION, ANDROID_VERSION.getOsVersion()))
        .withClientName("ANDROID")
        .withClientField("clientVersion", CLIENT_VERSION)
        .withClientField("androidSdkVersion", ANDROID_VERSION.getSdkVersion())
        .withUserField("lockedSafetyMode", false);

    @Override
    protected ClientConfig getBaseClientConfig(HttpInterface httpInterface) {
        return BASE_CONFIG.copy();
    }

    @Override
    public String getPlayerParams() {
        return MOBILE_PLAYER_PARAMS;
    }

    @Override
    public String getIdentifier() {
        return BASE_CONFIG.getName();
    }
}
