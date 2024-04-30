package dev.lavalink.youtube.track;

import com.sedmelluq.discord.lavaplayer.container.matroska.MatroskaAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import dev.lavalink.youtube.CannotBeLoaded;
import dev.lavalink.youtube.UrlTools;
import dev.lavalink.youtube.UrlTools.UrlInfo;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.skeleton.Client;
import dev.lavalink.youtube.track.format.StreamFormat;
import dev.lavalink.youtube.track.format.TrackFormats;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;

import static com.sedmelluq.discord.lavaplayer.container.Formats.MIME_AUDIO_WEBM;
import static com.sedmelluq.discord.lavaplayer.tools.DataFormatTools.decodeUrlEncodedItems;
import static com.sedmelluq.discord.lavaplayer.tools.Units.CONTENT_LENGTH_UNKNOWN;

/**
 * Audio track that handles processing Youtube videos as audio tracks.
 */
public class YoutubeAudioTrack extends DelegatedAudioTrack {
  private static final Logger log = LoggerFactory.getLogger(YoutubeAudioTrack.class);

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
      Exception lastException = null;

      for (Client client : clients) {
        if (!client.supportsFormatLoading()) {
          continue;
        }

        try {
          processWithClient(localExecutor, httpInterface, client, 0);
          return; // stream played through successfully, short-circuit.
        } catch (FriendlyException e) {
          // usually thrown by getPlayabilityStatus when loading formats.
          // these aren't considered fatal, so we just store them and continue.
          lastException = e;
        } catch (RuntimeException e) {
          // store exception so it can be thrown if we run out of clients to
          // load formats with.
          lastException = e;
          String message = e.getMessage();

          if ("Not success status code: 403".equals(message) ||
              "Invalid status code for player api response: 400".equals(message) ||
              message.contains("No supported audio streams available")) {
            continue; // try next client
          }

          throw e; // Unhandled exception, just throw.
        }
      }

      if (lastException != null) {
        if (lastException instanceof FriendlyException) {
          throw lastException;
        }

        throw ExceptionTools.toRuntimeException(lastException);
      }
    } catch (CannotBeLoaded e) {
      throw ExceptionTools.wrapUnfriendlyExceptions("This video is unavailable", Severity.COMMON, e.getCause());
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
    } catch (RuntimeException e) {
      if ("Not success status code: 403".equals(e.getMessage())) {
        if (localExecutor.getPosition() < 3000) {
          throw e; // bad stream URL, try the next client.
        }
      }

      // contains("No route to host") || contains("Read timed out")
      // augmentedFormat.getFallback()

      throw e;
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
      throw new FriendlyException("This video cannot be played", Severity.COMMON, null);
    }

    StreamFormat format = formats.getBestFormat();

    URI signedUrl = sourceManager.getCipherManager()
        .resolveFormatUrl(httpInterface, formats.getPlayerScriptUrl(), format);

    return new FormatWithUrl(format, signedUrl);
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
                                   @NotNull Exception cause) {
      super(cause);
      this.lastStreamPosition = lastStreamPosition;
    }
  }
}
