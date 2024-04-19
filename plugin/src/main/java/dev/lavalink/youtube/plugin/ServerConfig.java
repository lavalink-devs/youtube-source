package dev.lavalink.youtube.plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "lavalink.server.ratelimit")
@Component
public class ServerConfig {
    private int youtubePlaylistLoadLimit = 6;

    public int getYoutubePlaylistLoadLimit() {
        return this.youtubePlaylistLoadLimit;
    }

    public void setYoutubePlaylistLoadLimit(int youtubePlaylistLoadLimit) {
        this.youtubePlaylistLoadLimit = youtubePlaylistLoadLimit;
    }
}
