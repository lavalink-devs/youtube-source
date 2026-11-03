package dev.lavalink.youtube.plugin.rest;

public class MinimalConfigRequest {
    private String refreshToken = "x"; // null is a valid value so we have a default placeholder.
    private boolean skipInitialization = true;

    public String getRefreshToken() {
        return this.refreshToken;
    }

    public boolean getSkipInitialization() {
        return this.skipInitialization;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void setSkipInitialization(boolean skipInitialization) {
        this.skipInitialization = skipInitialization;
    }

}
