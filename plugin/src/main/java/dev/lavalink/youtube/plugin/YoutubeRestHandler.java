package dev.lavalink.youtube.plugin;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.plugin.rest.MinimalConfigRequest;
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
    public void updateYoutubeConfig(@RequestBody MinimalConfigRequest config) {
        YoutubeAudioSourceManager source = playerManager.source(YoutubeAudioSourceManager.class);

        if (source == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The YouTube source manager is not registered.");
        }

        String refreshToken = config.getRefreshToken();

        if (!"x".equals(refreshToken)) {
            source.useOauth2(refreshToken, config.getSkipInitialization());
            log.debug("Updated YouTube OAuth2 refresh token to \"{}\"", config.getRefreshToken());
        }

        String poToken = config.getPoToken();
        String visitorData = config.getVisitorData();

        if (poToken == null || visitorData == null || (!poToken.isEmpty() && !visitorData.isEmpty())) {
            Web.setPoTokenAndVisitorData(poToken, visitorData);
            log.debug("Updated poToken to \"{}\" and visitorData to \"{}\"", poToken, visitorData);
        }
    }
}
