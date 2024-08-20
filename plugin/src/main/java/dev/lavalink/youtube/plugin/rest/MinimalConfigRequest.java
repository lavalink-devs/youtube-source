package dev.lavalink.youtube.plugin.rest;

public class MinimalConfigRequest {
    private String refreshToken = "x"; // null is a valid value so we have a default placeholder.
    private boolean skipInitialization = true;
    private String poToken = null;
    private String visitorData = null;

    public String getRefreshToken() {
        return this.refreshToken;
    }

    public boolean getSkipInitialization() {
        return this.skipInitialization;
    }

    public String getPoToken() {
        return this.poToken;
    }

    public String getVisitorData() {
        return this.visitorData;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void setSkipInitialization(boolean skipInitialization) {
        this.skipInitialization = skipInitialization;
    }

    public void setPoToken(String poToken) {
        this.poToken = poToken;
    }

    public void setVisitorData(String visitorData) {
        this.visitorData = visitorData;
    }
}
