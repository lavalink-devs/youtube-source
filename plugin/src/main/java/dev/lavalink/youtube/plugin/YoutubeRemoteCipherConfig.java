package dev.lavalink.youtube.plugin;

public class YoutubeRemoteCipherConfig {
    private String url;
    private String password;
    private String userAgent = "yt-source";
    private boolean useResolveEndpoint = false;

    public String getUrl() {
        return url;
    }

    public String getPassword() {
        return password;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public boolean useResolveEndpoint() {
        return useResolveEndpoint;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public void setUseResolveEndpoint(boolean resolveUrl) {
        this.useResolveEndpoint = resolveUrl;
    }

}
