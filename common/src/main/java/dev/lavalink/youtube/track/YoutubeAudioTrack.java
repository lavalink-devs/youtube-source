package dev.lavalink.youtube.track;

import com.sedmelluq.discord.lavaplayer.container.matroska.MatroskaAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import dev.lavalink.youtube.CannotBeLoaded;
import dev.lavalink.youtube.AllClientsFailedException;
import dev.lavalink.youtube.ClientInformation;
import dev.lavalink.youtube.*;
import dev.lavalink.youtube.UrlTools.UrlInfo;
import dev.lavalink.youtube.cipher.ScriptExtractionException;
import dev.lavalink.youtube.clients.skeleton.Client;
import dev.lavalink.youtube.track.format.StreamFormat;
import dev.lavalink.youtube.track.format.TrackFormats;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.sedmelluq.discord.lavaplayer.container.Formats.MIME_AUDIO_WEBM;
import static com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.decodeUrlEncodedItems;
import static com.sedmelluq.discord.lavaplayer.tools.Units.CONTENT_LENGTH_UNKNOWN;
import static dev.lavalink.youtube.http.YoutubeOauth2Handler.OAUTH_INJECT_CONTEXT_ATTRIBUTE;

/**
 * Audio track that handles processing Youtube videos as audio tracks.
 */
public class YoutubeAudioTrack extends DelegatedAudioTrack {
  private static final Logger log = LoggerFactory.getLogger(YoutubeAudioTrack.class);

  // This field is used to determine at what point should a stream be discarded.
  // If an error is thrown and the executor's position is larger than this number,
  // the stream URL will not be renewed.
  public static long BAD_STREAM_POSITION_THRESHOLD_MS = 3000;

  private final YoutubeAudioSourceManager sourceManager;

  /**
   * @param trackInfo Track info
   * @param sourceManager Source manager which was used to find this track
   */
  public YoutubeAudioTrack(@NotNull AudioTrackInfo trackInfo,
                           @NotNull YoutubeAudioSourceManager sourceManager) {
    super(trackInfo);
    this.sourceManager = sourceManager;
  }

  @Override
  public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
    Client[] clients = sourceManager.getClients();

    if (Arrays.stream(clients).noneMatch(Client::supportsFormatLoading)) {
      throw new FriendlyException("This video cannot be played", Severity.COMMON,
          new RuntimeException("None of the registered clients supports loading of formats"));
    }

    try (HttpInterface httpInterface = sourceManager.getInterface()) {
      try {
        Object userData = getUserData();
        log.debug("Processing track {}, userData type: {}, userData value: {}", 
            getIdentifier(), 
            userData != null ? userData.getClass().getName() : "null",
            userData != null ? userData.toString() : "null");

        if (userData != null) {
          String userDataString = userData.toString();
          log.debug("Attempting to parse userData as JSON: {}", userDataString);
          
          JsonBrowser jsonUserData = JsonBrowser.parse(userDataString);

          if (jsonUserData.get("oauth-token") != null) {
            String oauthToken = jsonUserData.get("oauth-token").text();
            log.debug("Found oauth-token in userData, setting context attribute");
            httpInterface.getContext().setAttribute(OAUTH_INJECT_CONTEXT_ATTRIBUTE, oauthToken);
          } else {
            log.debug("No oauth-token found in userData JSON");
          }
        }
      } catch (IOException e) {
        log.debug("Failed to parse token from userData", e);
      } catch (Exception e) {
        log.debug("Unexpected error while processing userData: {}", e.getMessage(), e);
      }

      List<ClientException> exceptions = new ArrayList<>();
      log.debug("Starting client iteration for track {}. Available clients: {}", 
          getIdentifier(), 
          Arrays.stream(clients)
              .filter(Client::supportsFormatLoading)
              .map(Client::getIdentifier)
              .toArray(String[]::new));

      for (Client client : clients) {
        if (!client.supportsFormatLoading()) {
          log.debug("Skipping client {} as it does not support format loading", client.getIdentifier());
          continue;
        }

        log.debug("Attempting to load track {} with client {}", getIdentifier(), client.getIdentifier());
        httpInterface.getContext().setAttribute(Client.OAUTH_CLIENT_ATTRIBUTE, client.supportsOAuth());
        log.debug("Client {} OAuth support: {}", client.getIdentifier(), client.supportsOAuth());

        try {
          processWithClient(localExecutor, httpInterface, client, 0);
          log.debug("Successfully loaded track {} with client {}", getIdentifier(), client.getIdentifier());
          return;
        } catch (CannotBeLoaded e) {
          log.debug("Client {} cannot load track {}: {}", client.getIdentifier(), getIdentifier(), e.getMessage(), e);
          throw e;
        } catch (Exception e) {
          log.debug("Client {} failed to load track {}: {} (exception type: {})", 
              client.getIdentifier(), 
              getIdentifier(), 
              e.getMessage(), 
              e.getClass().getName(), 
              e);
          
          if (e instanceof ScriptExtractionException) {
            // If we're still early in playback, we can try another client
            long position = localExecutor.getPosition();
            log.debug("ScriptExtractionException at position {}ms (threshold: {}ms)", 
                position, BAD_STREAM_POSITION_THRESHOLD_MS);
            if (position >= BAD_STREAM_POSITION_THRESHOLD_MS) {
              throw e;
            }
          } else if ("Not success status code: 403".equals(e.getMessage()) ||
                  "Invalid status code for player api response: 400".equals(e.getMessage())) {
            // As long as the executor position has not surpassed the threshold for which
            // a stream is considered unrecoverable, we can try to renew the playback URL with
            // another client.
            long position = localExecutor.getPosition();
            log.debug("HTTP error {} at position {}ms (threshold: {}ms)", 
                e.getMessage(), position, BAD_STREAM_POSITION_THRESHOLD_MS);
            if (position >= BAD_STREAM_POSITION_THRESHOLD_MS) {
              throw e;
            }
          }
          exceptions.add(new ClientException(e.getMessage(), client, e));
        }
      }

      if (!exceptions.isEmpty()) {
        log.warn("All clients failed to load track {}. Failed clients: {}", 
            getIdentifier(),
            exceptions.stream()
                .map(e -> String.format("%s (%s)", e.getClient().getIdentifier(), e.getMessage()))
                .toArray(String[]::new));
        throw new AllClientsFailedException(exceptions);
      }
    } catch (CannotBeLoaded e) {
      throw ExceptionTools.wrapUnfriendlyExceptions("This video is unavailable", Severity.SUSPICIOUS, e.getCause());
    }
  }

  private void processWithClient(LocalAudioTrackExecutor localExecutor,
                                 HttpInterface httpInterface,
                                 Client client,
                                 long streamPosition) throws CannotBeLoaded, Exception {
    log.debug("Loading best format for track {} with client {}", getIdentifier(), client.getIdentifier());
    FormatWithUrl augmentedFormat = loadBestFormatWithUrl(httpInterface, client);
    log.debug("Starting track {} with URL from client {}: {} (format: {}, contentLength: {})", 
        getIdentifier(),
        client.getIdentifier(), 
        augmentedFormat.signedUrl,
        augmentedFormat.format.getType(),
        augmentedFormat.format.getContentLength());

    try {
      if (trackInfo.isStream || augmentedFormat.format.getContentLength() == CONTENT_LENGTH_UNKNOWN) {
        log.debug("Processing track {} as stream (isStream: {}, contentLength: {})", 
            getIdentifier(), trackInfo.isStream, augmentedFormat.format.getContentLength());
        processStream(localExecutor, httpInterface, augmentedFormat);
      } else {
        log.debug("Processing track {} as static file (contentLength: {}, streamPosition: {})", 
            getIdentifier(), augmentedFormat.format.getContentLength(), streamPosition);
        processStatic(localExecutor, httpInterface, augmentedFormat, streamPosition);
      }
    } catch (StreamExpiredException e) {
      log.debug("Stream expired for track {} at position {}ms, retrying with same client", 
          getIdentifier(), e.lastStreamPosition);
      processWithClient(localExecutor, httpInterface, client, e.lastStreamPosition);
    }
  }

  private void processStatic(LocalAudioTrackExecutor localExecutor,
                             HttpInterface httpInterface,
                             FormatWithUrl augmentedFormat,
                             long streamPosition) throws Exception {
    YoutubePersistentHttpStream stream = null;

    try {
      log.debug("Creating persistent HTTP stream for track {} with URL: {}, contentLength: {}", 
          getIdentifier(), augmentedFormat.signedUrl, augmentedFormat.format.getContentLength());
      stream = new YoutubePersistentHttpStream(httpInterface, augmentedFormat.signedUrl, augmentedFormat.format.getContentLength());

      if (streamPosition > 0) {
        log.debug("Seeking to position {}ms for track {}", streamPosition, getIdentifier());
        stream.seek(streamPosition);
      }

      String mimeType = augmentedFormat.format.getType().getMimeType();
      log.debug("Processing static track {} with mimeType: {}", getIdentifier(), mimeType);
      if (mimeType.endsWith("/webm")) {
        log.debug("Delegating to MatroskaAudioTrack for track {}", getIdentifier());
        processDelegate(new MatroskaAudioTrack(trackInfo, stream), localExecutor);
      } else {
        log.debug("Delegating to MpegAudioTrack for track {}", getIdentifier());
        processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
      }
    } catch (RuntimeException e) {
      log.debug("RuntimeException while processing static track {}: {}", getIdentifier(), e.getMessage(), e);
      if ("Not success status code: 403".equals(e.getMessage()) && augmentedFormat.isExpired() && stream != null) {
        long position = stream.getPosition();
        log.debug("Stream expired for track {} at position {}, throwing StreamExpiredException", getIdentifier(), position);
        throw new StreamExpiredException(position, e);
      }

      throw e;
    } finally {
      if (stream != null) {
        stream.close();
      }
    }
  }

  private void processStream(LocalAudioTrackExecutor localExecutor,
                             HttpInterface httpInterface,
                             FormatWithUrl augmentedFormat) throws Exception {
    if (MIME_AUDIO_WEBM.equals(augmentedFormat.format.getType().getMimeType())) {
      log.warn("Track {} requested WebM stream format which is not supported", getIdentifier());
      throw new FriendlyException("YouTube WebM streams are currently not supported.", Severity.COMMON, null);
    }

    log.debug("Delegating stream processing for track {} to YoutubeMpegStreamAudioTrack with URL: {}", 
        getIdentifier(), augmentedFormat.signedUrl);
    // TODO: Catch 403 and retry? Can't use position though because it's a livestream.
    processDelegate(new YoutubeMpegStreamAudioTrack(trackInfo, httpInterface, augmentedFormat.signedUrl), localExecutor);
  }

  @NotNull
  private FormatWithUrl loadBestFormatWithUrl(@NotNull HttpInterface httpInterface,
                                              @NotNull Client client) throws CannotBeLoaded, Exception {
    if (!client.supportsFormatLoading()) {
      throw new RuntimeException(client.getIdentifier() + " does not support loading of formats!");
    }

    log.debug("Requesting formats for track {} from client {}", getIdentifier(), client.getIdentifier());
    TrackFormats formats = client.loadFormats(sourceManager, httpInterface, getIdentifier());

    if (formats == null) {
      log.debug("Client {} returned null formats for track {}", client.getIdentifier(), getIdentifier());
      throw new FriendlyException("This video cannot be played", Severity.SUSPICIOUS, null);
    }

    log.debug("Client {} returned {} formats for track {}, player script URL: {}", 
        client.getIdentifier(), 
        formats.getFormats().size(),
        getIdentifier(),
        formats.getPlayerScriptUrl());

    StreamFormat format = formats.getBestFormat();
    log.debug("Selected best format for track {}: itag={}, mimeType={}, bitrate={}, contentLength={}, url={}", 
        getIdentifier(),
        format.getItag(),
        format.getType().getMimeType(),
        format.getBitrate(),
        format.getContentLength(),
        format.getUrl());

    URI resolvedUrl = format.getUrl();
    if (client.requirePlayerScript()) {
      log.debug("Client {} requires player script, resolving format URL with cipher manager", client.getIdentifier());
      resolvedUrl = sourceManager.getCipherManager()
              .resolveFormatUrl(httpInterface, formats.getPlayerScriptUrl(), format);
      log.debug("Resolved format URL after cipher: {}", resolvedUrl);
      resolvedUrl = client.transformPlaybackUri(format.getUrl(), resolvedUrl);
      log.debug("Transformed playback URI: {}", resolvedUrl);
    }

    return new FormatWithUrl(format, resolvedUrl);
  }

  @Override
  protected AudioTrack makeShallowClone() {
    return new YoutubeAudioTrack(trackInfo, sourceManager);
  }

  @Override
  public AudioSourceManager getSourceManager() {
    return sourceManager;
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  private static class FormatWithUrl {
    private final StreamFormat format;
    private final URI signedUrl;

    private FormatWithUrl(@NotNull StreamFormat format,
                          @NotNull URI signedUrl) {
      this.format = format;
      this.signedUrl = signedUrl;
    }

    public boolean isExpired() {
      UrlInfo urlInfo = UrlTools.getUrlInfo(signedUrl.toString(), true);
      String expire = urlInfo.parameters.get("expire");

      if (expire == null) {
        return false;
      }

      long expiresAbsMillis = Long.parseLong(expire) * 1000;
      return System.currentTimeMillis() >= expiresAbsMillis;
    }

    @Nullable
    public FormatWithUrl getFallback() {
      String signedString = signedUrl.toString();
      Map<String, String> urlParameters = decodeUrlEncodedItems(signedString, false);

      String mn = urlParameters.get("mn");

      if (mn == null) {
        return null;
      }

      String[] hosts = mn.split(",");

      if (hosts.length < 2) {
        log.warn("Cannot fallback, available hosts: {}", String.join(", ", hosts));
        return null;
      }

      String newUrl = signedString.replaceFirst(hosts[0], hosts[1]);

      try {
        URI uri = new URI(newUrl);
        return new FormatWithUrl(format, uri);
      } catch (URISyntaxException e) {
        return null;
      }
    }
  }

  private static class StreamExpiredException extends RuntimeException {
    private final long lastStreamPosition;

    private StreamExpiredException(long lastStreamPosition,
                                   @NotNull Throwable cause) {
      super(null, cause, true, false);
      this.lastStreamPosition = lastStreamPosition;
    }
  }
}
