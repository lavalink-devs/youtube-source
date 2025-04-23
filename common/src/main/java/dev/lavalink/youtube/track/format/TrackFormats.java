package dev.lavalink.youtube.track.format;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.StringJoiner;

import static com.sedmelluq.discord.lavaplayer.container.Formats.MIME_AUDIO_WEBM;

public class TrackFormats {
    private final List<StreamFormat> formats;
    private final String playerScriptUrl;

    public TrackFormats(@NotNull List<StreamFormat> formats,
                        @NotNull String playerScriptUrl) {
        this.formats = formats;
        this.playerScriptUrl = playerScriptUrl;
    }

    @NotNull
    public List<StreamFormat> getFormats() {
        return this.formats;
    }

    @NotNull
    public String getPlayerScriptUrl() {
        return playerScriptUrl;
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
