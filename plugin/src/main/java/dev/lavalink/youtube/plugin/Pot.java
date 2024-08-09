package dev.lavalink.youtube.plugin;

public class Pot {
    private String token;
    private String visitorData;

    public String getToken() {
        return token != null && !token.isEmpty() ? token : null;
    }

    public String getVisitorData() {
        return visitorData != null && !visitorData.isEmpty() ? visitorData : null;
    }

    public void setPoToken(String token) {
        this.token = token;
    }

    public void setVisitorData(String visitorData) {
        this.visitorData = visitorData;
    }
}
