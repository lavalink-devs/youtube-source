package dev.lavalink.youtube;

public class YoutubeSourceOptions {
    private boolean allowSearch = true;
    private boolean allowDirectVideoIds = true;
    private boolean allowDirectPlaylistIds = true;
    private String cipherProxyUrl;
    private String cipherProxyPass;

    public boolean isAllowSearch() {
        return allowSearch;
    }

    public boolean isAllowDirectVideoIds() {
        return allowDirectVideoIds;
    }

    public boolean isAllowDirectPlaylistIds() {
        return allowDirectPlaylistIds;
    }

    public YoutubeSourceOptions setAllowSearch(boolean allowSearch) {
        this.allowSearch = allowSearch;
        return this;
    }

    public YoutubeSourceOptions setAllowDirectVideoIds(boolean allowDirectVideoIds) {
        this.allowDirectVideoIds = allowDirectVideoIds;
        return this;
    }

    public YoutubeSourceOptions setAllowDirectPlaylistIds(boolean allowDirectPlaylistIds) {
        this.allowDirectPlaylistIds = allowDirectPlaylistIds;
        return this;
    }

    public String getCipherProxyUrl() {
        return cipherProxyUrl;
    }

    public YoutubeSourceOptions setCipherProxyUrl(String cipherProxyUrl) {
        this.cipherProxyUrl = cipherProxyUrl;
        return this;
    }

    public String getCipherProxyPass() {
        return cipherProxyPass;
    }

    public YoutubeSourceOptions setCipherProxyPass(String cipherProxyPass) {
        this.cipherProxyPass = cipherProxyPass;
        return this;
    }
}
