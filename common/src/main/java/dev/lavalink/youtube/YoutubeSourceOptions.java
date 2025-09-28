package dev.lavalink.youtube;

import org.jetbrains.annotations.Nullable;

public class YoutubeSourceOptions {
    private boolean allowSearch = true;
    private boolean allowDirectVideoIds = true;
    private boolean allowDirectPlaylistIds = true;
    private String remoteCipherUrl;
    private String remoteCipherPassword;

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

    public YoutubeSourceOptions setRemoteCipherUrl(String remoteCipherUrl, @Nullable String remoteCipherPassword) {
        this.remoteCipherUrl = remoteCipherUrl;
        this.remoteCipherPassword = remoteCipherPassword;
        return this;
    }

    public String getRemoteCipherPassword() {
        return remoteCipherPassword;
    }

}
