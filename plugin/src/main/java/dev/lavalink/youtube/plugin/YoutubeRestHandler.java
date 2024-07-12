package dev.lavalink.youtube.plugin;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Map;

@Service
public class YoutubeRestHandler {
    private static final Logger log = LoggerFactory.getLogger(YoutubeRestHandler.class);

    private final AudioPlayerManager playerManager;

    public YoutubeRestHandler(AudioPlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @GetMapping("/v4/youtube")
    public Map<String, String> getYoutubeConfig() {
        YoutubeAudioSourceManager source = playerManager.source(YoutubeAudioSourceManager.class);

        if (source == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The YouTube source manager is not registered.");
        }

        return Collections.singletonMap("refreshToken", source.getOauth2RefreshToken());
    }

    @PostMapping("/v4/youtube")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateRefreshToken(@RequestBody YoutubeOauthConfig config) {
        YoutubeAudioSourceManager source = playerManager.source(YoutubeAudioSourceManager.class);

        if (source == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The YouTube source manager is not registered.");
        }

        source.useOauth2(config.getRefreshToken(), config.getSkipInitialization());
        log.debug("Updated YouTube OAuth2 refresh token to {}", config.getRefreshToken());
    }
}
