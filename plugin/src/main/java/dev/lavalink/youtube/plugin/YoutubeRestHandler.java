package dev.lavalink.youtube.plugin;

import com.grack.nanojson.JsonObject;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.CannotBeLoaded;
import dev.lavalink.youtube.ClientInformation;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import dev.lavalink.youtube.clients.skeleton.Client;
import dev.lavalink.youtube.plugin.rest.MinimalConfigRequest;
import dev.lavalink.youtube.plugin.rest.MinimalConfigResponse;
import dev.lavalink.youtube.track.YoutubePersistentHttpStream;
import dev.lavalink.youtube.track.format.StreamFormat;
import dev.lavalink.youtube.track.format.TrackFormats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;

@Service
@RestController
public class YoutubeRestHandler {
    private static final Logger log = LoggerFactory.getLogger(YoutubeRestHandler.class);

    private final AudioPlayerManager playerManager;

    public YoutubeRestHandler(AudioPlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    private YoutubeAudioSourceManager getYoutubeSource() {
        YoutubeAudioSourceManager source = playerManager.source(YoutubeAudioSourceManager.class);

        if (source == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The YouTube source manager is not registered.");
        }

        return source;
    }

    @GetMapping("/youtube/stream/{videoId}")
    public ResponseEntity<StreamingResponseBody> getYoutubeVideoStream(@PathVariable("videoId") String videoId,
                                                                       @RequestParam(name = "itag", required = false) Integer itag,
                                                                       @RequestParam(name = "withClient", required = false) String clientIdentifier) throws IOException {
        YoutubeAudioSourceManager source = getYoutubeSource();
        Throwable lastException = null;

        if (Arrays.stream(source.getClients()).noneMatch(Client::supportsFormatLoading)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "None of the registered clients supports format loading.");
        }

        boolean foundFormats = false;

        HttpInterface httpInterface = source.getInterface();

        for (Client client : source.getClients()) {
            log.debug("REST streaming {} attempting to use client {}", videoId, client.getIdentifier());

            if (clientIdentifier != null && !client.getIdentifier().equalsIgnoreCase(clientIdentifier)) {
                log.debug("Client identifier specified but does not match, trying next.");
                continue;
            }

            if (!client.supportsFormatLoading()) {
                continue;
            }

            log.debug("Loading formats for {} with client {}", videoId, client.getIdentifier());
            httpInterface.getContext().setAttribute(Client.OAUTH_CLIENT_ATTRIBUTE, client.supportsOAuth());

            TrackFormats formats;

            try {
                formats = client.loadFormats(source, httpInterface, videoId);
            } catch (CannotBeLoaded cbl) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This video cannot be loaded. Reason: " + cbl.getCause().getMessage());
            }  catch (Throwable t) {
                log.debug("Client \"{}\" threw a non-fatal exception, storing and proceeding...", client.getIdentifier());
                t.addSuppressed(ClientInformation.create(client));
                lastException = t;
                continue;
            }

            if (formats == null || formats.getFormats().isEmpty()) {
                log.debug("No formats found for {}", videoId);
                continue;
            }

            foundFormats = true;
            StreamFormat selectedFormat;

            if (itag == null) {
                selectedFormat = formats.getBestFormat();
            } else {
                selectedFormat = formats.getFormats().stream().filter(fmt -> fmt.getItag() == itag).findFirst()
                    .orElse(null);
            }

            if (selectedFormat == null) {
                log.debug("No suitable formats found. (Matching: {})", itag);
                continue;
            }

            log.debug("Selected format {} for {}", selectedFormat.getItag(), videoId);

            URI transformed = selectedFormat.getUrl();
            if (client.requirePlayerScript()) {
                URI resolved = source.getCipherManager().resolveFormatUrl(httpInterface, formats.getPlayerScriptUrl(), selectedFormat);
                transformed = client.transformPlaybackUri(selectedFormat.getUrl(), resolved);
            }

            YoutubePersistentHttpStream httpStream = new YoutubePersistentHttpStream(httpInterface, transformed, selectedFormat.getContentLength());

            boolean streamValidated = false;

            try {
                int statusCode = httpStream.checkStatusCode();
                streamValidated = statusCode == 200;

                if (statusCode != 200) {
                    log.debug("REST streaming with {} for {} returned status code {} when opening video stream", client.getIdentifier(), videoId, statusCode);
                }
            } catch (Throwable t) {
                if ("Not success status code: 403".equals(t.getMessage())) {
                    log.debug("REST streaming with {} for {} returned status code 403 when opening video stream", client.getIdentifier(), videoId);
                } else {
                    IOUtils.closeQuietly(httpStream, httpInterface);
                    throw t;
                }
            }

            if (!streamValidated) {
                IOUtils.closeQuietly(httpStream);
                continue;
            }

            StreamingResponseBody buffer = (os) -> {
              int bytesRead;
              byte[] copy = new byte[1024];

              try (httpStream; httpInterface) {
                  while ((bytesRead = httpStream.read(copy, 0, copy.length)) != -1) {
                      os.write(copy, 0, bytesRead);
                  }
              }
            };

            return ResponseEntity.ok()
                .contentLength(selectedFormat.getContentLength())
                .contentType(MediaType.parseMediaType(selectedFormat.getType().getMimeType()))
                .body(buffer);
        }

        IOUtils.closeQuietly(httpInterface);

        if (foundFormats) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No formats found with the requested itag.");
        }

        if (lastException != null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "This video cannot be loaded", lastException);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not find formats for the requested videoId.");
    }

    @GetMapping("/youtube")
    public MinimalConfigResponse getYoutubeConfig() {
        return MinimalConfigResponse.from(getYoutubeSource());
    }

    @GetMapping("/youtube/oauth/{refreshToken}")
    public JsonObject createNewAccessToken(@PathVariable("refreshToken") String refreshToken) {
        return getYoutubeSource().getOauth2Handler().createNewAccessToken(refreshToken);
    }

    @PostMapping("/youtube")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateYoutubeConfig(@RequestBody MinimalConfigRequest config) {
        YoutubeAudioSourceManager source = getYoutubeSource();
        String refreshToken = config.getRefreshToken();

        if (!"x".equals(refreshToken)) {
            source.useOauth2(refreshToken, config.getSkipInitialization());
            log.debug("Updated YouTube OAuth2 refresh token to \"{}\"", config.getRefreshToken());
        }

        String poToken = config.getPoToken();
        String visitorData = config.getVisitorData();

        if (poToken == null || visitorData == null || (!poToken.isEmpty() && !visitorData.isEmpty())) {
            WebEmbedded.setPoTokenAndVisitorData(poToken, visitorData);
            Web.setPoTokenAndVisitorData(poToken, visitorData);
            log.debug("Updated poToken to \"{}\" and visitorData to \"{}\"", poToken, visitorData);
        }
    }
}
