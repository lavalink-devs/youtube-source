package dev.lavalink.youtube.plugin;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.plugin.rest.MinimalConfigResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@RestController
public class YoutubeRestHandler {
    private static final Logger log = LoggerFactory.getLogger(YoutubeRestHandler.class);

    private final AudioPlayerManager playerManager;

    public YoutubeRestHandler(AudioPlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @GetMapping("/youtube")
    public MinimalConfigResponse getYoutubeConfig() {
        YoutubeAudioSourceManager source = playerManager.source(YoutubeAudioSourceManager.class);

        if (source == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The YouTube source manager is not registered.");
        }

        return MinimalConfigResponse.from(source);
    }

    @PostMapping("/youtube")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateOauth(@RequestBody YoutubeOauthConfig config) {
        YoutubeAudioSourceManager source = playerManager.source(YoutubeAudioSourceManager.class);

        if (source == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The YouTube source manager is not registered.");
        }

        source.useOauth2(config.getRefreshToken(), config.getSkipInitialization());
        log.debug("Updated YouTube OAuth2 refresh token to {}", config.getRefreshToken());
    }
}
