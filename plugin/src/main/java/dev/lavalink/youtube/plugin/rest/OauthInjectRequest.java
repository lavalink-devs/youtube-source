package dev.lavalink.youtube.plugin.rest;

public class OauthInjectRequest {
    private String identifier = null;
    private String token = null;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
