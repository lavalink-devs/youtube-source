package dev.lavalink.youtube.track.format;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.StringJoiner;

import static com.sedmelluq.discord.lavaplayer.container.Formats.MIME_AUDIO_WEBM;

public class TrackFormats {
    private final List<StreamFormat> formats;
    private final String playerScriptUrl;
    private final String serverAbrStreamingUrl;
    private final String videoPlaybackUstreamerConfig;
    private final String poToken;

    public TrackFormats(@NotNull List<StreamFormat> formats,
                        @NotNull String playerScriptUrl) {
        this(formats, playerScriptUrl, null, null);
    }

    public TrackFormats(@NotNull List<StreamFormat> formats,
                        @NotNull String playerScriptUrl,
                        @Nullable String serverAbrStreamingUrl,
                        @Nullable String videoPlaybackUstreamerConfig) {
        this(formats, playerScriptUrl, serverAbrStreamingUrl, videoPlaybackUstreamerConfig, null);
    }

    public TrackFormats(@NotNull List<StreamFormat> formats,
                        @NotNull String playerScriptUrl,
                        @Nullable String serverAbrStreamingUrl,
                        @Nullable String videoPlaybackUstreamerConfig,
                        @Nullable String poToken) {
        this.formats = formats;
        this.playerScriptUrl = playerScriptUrl;
        this.serverAbrStreamingUrl = serverAbrStreamingUrl;
        this.videoPlaybackUstreamerConfig = videoPlaybackUstreamerConfig;
        this.poToken = poToken;
    }

    @NotNull
    public List<StreamFormat> getFormats() {
        return this.formats;
    }

    @NotNull
    public String getPlayerScriptUrl() {
        return playerScriptUrl;
    }

    /**
     * @return The server ABR streaming URL used for SABR playback, or {@code null} if unavailable.
     */
    @Nullable
    public String getServerAbrStreamingUrl() {
        return serverAbrStreamingUrl;
    }

    /**
     * @return The base64 videoPlaybackUstreamerConfig required for SABR requests, or {@code null}.
     */
    @Nullable
    public String getVideoPlaybackUstreamerConfig() {
        return videoPlaybackUstreamerConfig;
    }

    @Nullable
    public String getPoToken() {
        return poToken;
    }

    @Nullable
    public StreamFormat getFormatByItag(int itag) {
        for (StreamFormat format : formats) {
            if (format.getItag() == itag && !format.isSabr()) {
                return format;
            }
        }

        return null;
    }

    @NotNull
    public StreamFormat getBestFormat() {
        StreamFormat bestFormat = null;

        for (StreamFormat format : formats) {
            if (!format.isDefaultAudioTrack()) {
                continue;
            }

            if (isBetterFormat(format, bestFormat)) {
                bestFormat = format;
            }
        }

        if (bestFormat == null) {
            StringJoiner joiner = new StringJoiner(", ");
            formats.forEach(format -> joiner.add(format.getType().toString()));
            throw new RuntimeException("No supported audio streams available, available types: " + joiner);
        }

        return bestFormat;
    }

    private static boolean isBetterFormat(StreamFormat format, StreamFormat other) {
        FormatInfo info = format.getInfo();

        if (info == null) {
            return false;
        } else if (other == null) {
            return true;
        } else if (MIME_AUDIO_WEBM.equals(info.mimeType) && format.getAudioChannels() > 2) {
            // Opus with more than 2 audio channels is unsupported by LavaPlayer currently.
            return false;
        } else if (info.ordinal() != other.getInfo().ordinal()) {
            return info.ordinal() < other.getInfo().ordinal();
        } else if (format.isDrc() && !other.isDrc()) {
            // prefer non-drc formats
            // IF ANYTHING BREAKS/SOUNDS BAD, REMOVE THIS
            return false;
        } else {
            return format.getBitrate() > other.getBitrate();
        }
    }
}
