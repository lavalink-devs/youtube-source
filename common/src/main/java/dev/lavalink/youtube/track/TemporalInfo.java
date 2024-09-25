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

    // normal video? but has liveStreamability: PRRBJOn_n-Y
    // livestream: jfKfPfyJRdk

    @NotNull
    public static TemporalInfo fromRawData(JsonBrowser playabilityStatus, JsonBrowser videoDetails) {
        JsonBrowser durationField = videoDetails.get("lengthSeconds");
        long durationValue = durationField.asLong(0L);

        boolean hasLivestreamability = !playabilityStatus.get("liveStreamability").isNull();
        boolean isLive = videoDetails.get("isLive").asBoolean(false)
            || videoDetails.get("isLiveContent").asBoolean(false);

        if (hasLivestreamability) {
            // Premieres have duration information, but act as a normal stream. When we play it, we don't know the
            // current position of it since YouTube doesn't provide such information, so assume duration is unknown.
            durationValue = 0;
        }

        return new TemporalInfo(
            (isLive || hasLivestreamability) && durationValue == 0,
            durationValue == 0 ? DURATION_MS_UNKNOWN : Units.secondsToMillis(durationValue)
        );
    }
}
