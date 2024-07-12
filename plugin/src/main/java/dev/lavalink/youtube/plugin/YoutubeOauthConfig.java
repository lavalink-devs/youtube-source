package dev.lavalink.youtube.plugin;

public class YoutubeOauthConfig {
    private boolean enabled = false;
    private String refreshToken;
    private boolean skipInitialization = false;

    public boolean getEnabled() {
        return enabled;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public boolean getSkipInitialization() {
        return skipInitialization;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void setSkipInitialization(boolean skipInitialization) {
        this.skipInitialization = skipInitialization;
    }
}
