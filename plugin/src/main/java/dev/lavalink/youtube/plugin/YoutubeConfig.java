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
    private Pot pot = null;
    private String[] clients;
    private Map<String, ClientOptions> clientOptions = new HashMap<>();
    private YoutubeOauthConfig oauth = null;

    public boolean getEnabled() {
        return enabled;
    }

    public boolean getAllowSearch() {
        return allowSearch;
    }

    public boolean getAllowDirectVideoIds() {
        return allowDirectVideoIds;
    }

    public boolean getAllowDirectPlaylistIds() {
        return allowDirectPlaylistIds;
    }

    public Pot getPot() {
        return pot;
    }

    public String[] getClients() {
        return clients;
    }

    public Map<String, ClientOptions> getClientOptions() {
        return clientOptions;
    }

    public YoutubeOauthConfig getOauth() {
        return this.oauth;
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

    public void setPot(Pot pot) {
        this.pot = pot;
    }

    public void setClients(String[] clients) {
        this.clients = clients;
    }

    public void setClientOptions(Map<String, ClientOptions> clientOptions) {
        this.clientOptions = clientOptions;
    }

    public void setOauth(YoutubeOauthConfig oauth) {
        this.oauth = oauth;
    }
}
