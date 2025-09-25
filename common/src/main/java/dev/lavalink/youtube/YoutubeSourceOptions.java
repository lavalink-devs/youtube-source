package dev.lavalink.youtube;

public class YoutubeSourceOptions {
    private boolean allowSearch = true;
    private boolean allowDirectVideoIds = true;
    private boolean allowDirectPlaylistIds = true;
    private String remoteCipherUrl;
    private String remoteCipherPass;

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

    public String getRemoteCipherUrl() {
        return remoteCipherUrl;
    }

    public YoutubeSourceOptions setRemoteCipherUrl(String remoteCipherUrl) {
        this.remoteCipherUrl = remoteCipherUrl;
        return this;
    }

    public String getRemoteCipherPass() {
        return remoteCipherPass;
    }

    public YoutubeSourceOptions setRemoteCipherPass(String remoteCipherPass) {
        this.remoteCipherPass = remoteCipherPass;
        return this;
    }
}
