package dev.lavalink.youtube.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.youtube")
@Component
public class YoutubeConfig {
    private boolean enabled = true;
    private boolean allowSearch = true;
    private boolean allowDirectVideoIds = true;
    private boolean allowDirectPlaylistIds = true;
    private String[] clients;

    public boolean getEnabled() {
        return this.enabled;
    }

    public boolean getAllowSearch() {
        return this.allowSearch;
    }

    public boolean getAllowDirectVideoIds() {
        return this.allowDirectVideoIds;
    }

    public boolean getAllowDirectPlaylistIds() {
        return this.allowDirectPlaylistIds;
    }

    public String[] getClients() {
        return this.clients;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setAllowSearch(boolean allowSearch) {
        this.allowSearch = allowSearch;
    }

    public void setAllowDirectVideoIds(boolean allowDirectVideoIds) {
        this.allowDirectVideoIds = allowDirectVideoIds;
    }

    public void setAllowDirectPlaylistIds(boolean allowDirectPlaylistIds) {
        this.allowDirectPlaylistIds = allowDirectPlaylistIds;
    }

    public void setClients(String[] clients) {
        this.clients = clients;
    }
}
