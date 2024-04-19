package dev.lavalink.youtube.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.youtube")
@Component
public class YoutubeConfig {
    private boolean enabled = true;
    private boolean allowSearch = true;
    private String[] clients;

    public boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean getAllowSearch() {
        return this.allowSearch;
    }

    public void setAllowSearch(boolean allowSearch) {
        this.allowSearch = allowSearch;
    }

    public String[] getClients() {
        return this.clients;
    }

    public void setClients(String[] clients) {
        this.clients = clients;
    }
}
