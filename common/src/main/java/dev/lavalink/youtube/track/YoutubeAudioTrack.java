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

        if (userData != null) {
          JsonBrowser jsonUserData = JsonBrowser.parse(userData.toString());

          if (jsonUserData.get("oauth-token") != null) {
            httpInterface.getContext().setAttribute(OAUTH_INJECT_CONTEXT_ATTRIBUTE, jsonUserData.get("oauth-token").text());
          }
        }
      } catch (IOException e) {
        log.debug("Failed to parse token from userData", e);
      }

      List<ClientException> exceptions = new ArrayList<>();

      for (Client client : clients) {
        if (!client.supportsFormatLoading()) {
          continue;
        }

        httpInterface.getContext().setAttribute(Client.OAUTH_CLIENT_ATTRIBUTE, client.supportsOAuth());

        try {
          processWithClient(localExecutor, httpInterface, client, 0);
          return;
        } catch (CannotBeLoaded e) {
          throw e;
        } catch (Exception e) {
          if (e instanceof ScriptExtractionException) {
            // If we're still early in playback, we can try another client
            if (localExecutor.getPosition() >= BAD_STREAM_POSITION_THRESHOLD_MS) {
              throw e;
            }
          } else if ("Not success status code: 403".equals(e.getMessage()) ||
                  "Invalid status code for player api response: 400".equals(e.getMessage())) {
            // As long as the executor position has not surpassed the threshold for which
            // a stream is considered unrecoverable, we can try to renew the playback URL with
            // another client.
            if (localExecutor.getPosition() >= BAD_STREAM_POSITION_THRESHOLD_MS) {
              throw e;
            }
          }
          exceptions.add(new ClientException(e.getMessage(), client, e));
        }
      }

      if (!exceptions.isEmpty()) {
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
    FormatWithUrl augmentedFormat = loadBestFormatWithUrl(httpInterface, client);
    log.debug("Starting track with URL from client {}: {}", client.getIdentifier(), augmentedFormat.signedUrl);

    try {
      if (trackInfo.isStream || augmentedFormat.format.getContentLength() == CONTENT_LENGTH_UNKNOWN) {
        processStream(localExecutor, httpInterface, augmentedFormat);
      } else {
        processStatic(localExecutor, httpInterface, augmentedFormat, streamPosition);
      }
    } catch (StreamExpiredException e) {
      processWithClient(localExecutor, httpInterface, client, e.lastStreamPosition);
    }
  }

  private void processStatic(LocalAudioTrackExecutor localExecutor,
                             HttpInterface httpInterface,
                             FormatWithUrl augmentedFormat,
                             long streamPosition) throws Exception {
    YoutubePersistentHttpStream stream = null;

    try {
      stream = new YoutubePersistentHttpStream(httpInterface, augmentedFormat.signedUrl, augmentedFormat.format.getContentLength());

      if (streamPosition > 0) {
        stream.seek(streamPosition);
      }

      if (augmentedFormat.format.getType().getMimeType().endsWith("/webm")) {
        processDelegate(new MatroskaAudioTrack(trackInfo, stream), localExecutor);
      } else {
        processDelegate(new MpegAudioTrack(trackInfo, stream), localExecutor);
      }
    } catch (RuntimeException e) {
      if ("Not success status code: 403".equals(e.getMessage()) && augmentedFormat.isExpired() && stream != null) {
        throw new StreamExpiredException(stream.getPosition(), e);
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
      throw new FriendlyException("YouTube WebM streams are currently not supported.", Severity.COMMON, null);
    }

    // TODO: Catch 403 and retry? Can't use position though because it's a livestream.
    processDelegate(new YoutubeMpegStreamAudioTrack(trackInfo, httpInterface, augmentedFormat.signedUrl), localExecutor);
  }

  @NotNull
  private FormatWithUrl loadBestFormatWithUrl(@NotNull HttpInterface httpInterface,
                                              @NotNull Client client) throws CannotBeLoaded, Exception {
    if (!client.supportsFormatLoading()) {
      throw new RuntimeException(client.getIdentifier() + " does not support loading of formats!");
    }

    TrackFormats formats = client.loadFormats(sourceManager, httpInterface, getIdentifier());

    if (formats == null) {
      throw new FriendlyException("This video cannot be played", Severity.SUSPICIOUS, null);
    }

    StreamFormat format = formats.getBestFormat();

    URI resolvedUrl = format.getUrl();
    if (client.requirePlayerScript()) {
      resolvedUrl = sourceManager.getCipherManager()
              .resolveFormatUrl(httpInterface, formats.getPlayerScriptUrl(), format);
      resolvedUrl = client.transformPlaybackUri(format.getUrl(), resolvedUrl);
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
