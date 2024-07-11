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

    private boolean useOauth2 = false;
    private String oauth2RefreshToken = null;

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

    public boolean getUseOauth2() {
        return this.useOauth2;
    }

    public String getOauth2RefreshToken() {
        return this.oauth2RefreshToken;
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

    public void setUseOauth2(boolean useOauth2) {
        this.useOauth2 = useOauth2;
    }

    public void setOauth2RefreshToken(String oauth2RefreshToken) {
        this.oauth2RefreshToken = oauth2RefreshToken;
    }
}
