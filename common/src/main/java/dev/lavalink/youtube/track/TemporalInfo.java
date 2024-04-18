package dev.lavalink.youtube.track;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import org.jetbrains.annotations.NotNull;

import static com.sedmelluq.discord.lavaplayer.tools.Units.DURATION_MS_UNKNOWN;

public class TemporalInfo {
    public final boolean isActiveStream;
    public final long durationMillis;

    private TemporalInfo(boolean isActiveStream, long durationMillis) {
        this.isActiveStream = isActiveStream;
        this.durationMillis = durationMillis;
    }

    @NotNull
    public static TemporalInfo fromRawData(boolean wasLiveStream, JsonBrowser durationSecondsField, boolean legacy) {
        long durationValue = durationSecondsField.asLong(0L);

        if (wasLiveStream && !legacy) {
            // Premieres have duration information, but act as a normal stream. When we play it, we don't know the
            // current position of it since YouTube doesn't provide such information, so assume duration is unknown.
            durationValue = 0;
        }

        // VODs are not really live streams, even though the response JSON indicates that it is.
        // If it is actually live, then duration is also missing or 0.
        boolean isActiveStream = wasLiveStream && durationValue == 0;

        return new TemporalInfo(
            isActiveStream,
            durationValue == 0 ? DURATION_MS_UNKNOWN : Units.secondsToMillis(durationValue)
        );
    }
}
