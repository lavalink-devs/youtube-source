package dev.lavalink.youtube.plugin;

import dev.lavalink.youtube.clients.ClientOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "plugins.youtube")
@Component
public class YoutubeConfig {
    private boolean enabled = true;
    private boolean allowSearch = true;
    private boolean allowDirectVideoIds = true;
    private boolean allowDirectPlaylistIds = true;
    private String[] clients;
    private Map<String, ClientOptions> clientOptions = new HashMap<>();
    private YoutubeOauthConfig oauthConfig = null;

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

    public Map<String, ClientOptions> getClientOptions() {
        return this.clientOptions;
    }

    public YoutubeOauthConfig getOauthConfig() {
        return this.oauthConfig;
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

    public void setClientOptions(Map<String, ClientOptions> clientOptions) {
        this.clientOptions = clientOptions;
    }

    public void setOauthConfig(YoutubeOauthConfig oauthConfig) {
        this.oauthConfig = oauthConfig;
    }
}
