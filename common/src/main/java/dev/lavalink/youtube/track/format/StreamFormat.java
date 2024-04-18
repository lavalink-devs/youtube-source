package dev.lavalink.youtube.track.format;

import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Describes an available media format for a track
 */
public class StreamFormat {
  private final FormatInfo info;
  private final ContentType type;
  private final long bitrate;
  private final long contentLength;
  private final long audioChannels;
  private final String url;
  private final String nParameter;
  private final String signature;
  private final String signatureKey;
  private final boolean defaultAudioTrack;
  private final boolean isDrc;

  /**
   * @param type Mime type of the format
   * @param bitrate Bitrate of the format
   * @param contentLength Length in bytes of the media
   * @param audioChannels Number of audio channels
   * @param url Base URL for the playback of this format
   * @param nParameter n parameter for this format
   * @param signature Cipher signature for this format
   * @param signatureKey The key to use for deciphered signature in the final playback URL
   * @param isDefaultAudioTrack Whether this format contains an audio track that is used by default.
   * @param isDrc Whether this format has Dynamic Range Compression.
   */
  public StreamFormat(
      ContentType type,
      long bitrate,
      long contentLength,
      long audioChannels,
      String url,
      String nParameter,
      String signature,
      String signatureKey,
      boolean isDefaultAudioTrack,
      boolean isDrc
  ) {
    this.info = FormatInfo.get(type);
    this.type = type;
    this.bitrate = bitrate;
    this.contentLength = contentLength;
    this.audioChannels = audioChannels;
    this.url = url;
    this.nParameter = nParameter;
    this.signature = signature;
    this.signatureKey = signatureKey;
    this.defaultAudioTrack = isDefaultAudioTrack;
    this.isDrc = isDrc;
  }

  /**
   * @return Format container and codec info
   */
  @Nullable
  public FormatInfo getInfo() {
    return info;
  }

  /**
   * @return Mime type of the format
   */
  @NotNull
  public ContentType getType() {
    return type;
  }

  /**
   * @return Bitrate of the format
   */
  public long getBitrate() {
    return bitrate;
  }

  /**
   * @return Count of audio channels in format
   */
  public long getAudioChannels() {
    return audioChannels;
  }

  /**
   * @return Base URL for the playback of this format
   */
  @NotNull
  public URI getUrl() {
    try {
      return new URI(url);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @return Length in bytes of the media
   */
  public long getContentLength() {
    return contentLength;
  }

  /**
   * @return n parameter for this format
   */
  @Nullable
  public String getNParameter() {
    return nParameter;
  }

  /**
   * @return Cipher signature for this format
   */
  @Nullable
  public String getSignature() {
    return signature;
  }

  /**
   * @return The key to use for deciphered signature in the final playback URL
   */
  @Nullable
  public String getSignatureKey() {
    return signatureKey;
  }

  /**
   * @return Whether this format contains an audio track that is used by default.
   */
  public boolean isDefaultAudioTrack() {
    return defaultAudioTrack;
  }

  /**
   * @return Whether this format has Dynamic Range Compression.
   */
  public boolean isDrc() {
    return isDrc;
  }

  @Override
  public String toString() {
    return "YoutubeStreamFormat{" +
        "type=" + type +
        ", bitrate=" + bitrate +
        ", audioChannels=" + audioChannels +
        ", isDrc=" + isDrc +
        '}';
  }
}
