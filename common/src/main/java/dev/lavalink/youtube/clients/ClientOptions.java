package dev.lavalink.youtube.clients;

public class ClientOptions {
    public static final ClientOptions DEFAULT = new ClientOptions();

    private boolean playback = true;
    private boolean playlistLoading = true;
    private boolean videoLoading = true;
    private boolean searching = true;

    public boolean getPlayback() {
        return this.playback;
    }

    public boolean getPlaylistLoading() {
        return this.playlistLoading;
    }

    public boolean getVideoLoading() {
        return this.videoLoading;
    }

    public boolean getSearching() {
        return this.searching;
    }

    public void setPlayback(boolean playback) {
        this.playback = playback;
    }

    public void setPlaylistLoading(boolean playlistLoading) {
        this.playlistLoading = playlistLoading;
    }

    public void setVideoLoading(boolean videoLoading) {
        this.videoLoading = videoLoading;
    }

    public void setSearching(boolean searching) {
        this.searching = searching;
    }

    public ClientOptions copy() {
        ClientOptions options = new ClientOptions();
        options.setPlayback(this.playback);
        options.setPlaylistLoading(this.playlistLoading);
        options.setVideoLoading(this.videoLoading);
        options.setSearching(this.searching);
        return options;
    }
}
