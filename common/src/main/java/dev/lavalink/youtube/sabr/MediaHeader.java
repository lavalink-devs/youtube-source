package dev.lavalink.youtube.sabr;

import dev.lavalink.youtube.sabr.protobuf.ProtoReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes an upcoming media segment. Corresponds to {@code video_streaming.MediaHeader}.
 */
public class MediaHeader {
    public int headerId;
    public int itag;
    public String xtags;
    public boolean isInitSeg;
    public int sequenceNumber;
    public long startMs;
    public long durationMs;
    public long contentLength = -1;
    public FormatId formatId;

    // From time_range, used as a duration fallback.
    public long timeRangeDurationTicks;
    public int timeRangeTimescale;

    @NotNull
    public String formatKey() {
        return FormatId.key(itag, xtags);
    }

    /**
     * @return The effective segment number; init segments are always segment 0.
     */
    public int segmentNumber() {
        return isInitSeg ? 0 : sequenceNumber;
    }

    /**
     * @return The duration of this segment in milliseconds, using time_range as a fallback.
     */
    public long effectiveDurationMs() {
        if (durationMs > 0) {
            return durationMs;
        }

        if (timeRangeTimescale > 0) {
            return (long) Math.ceil((double) timeRangeDurationTicks / timeRangeTimescale * 1000);
        }

        return 0;
    }

    @NotNull
    public static MediaHeader parse(@NotNull ProtoReader reader) {
        MediaHeader header = new MediaHeader();

        int tag;
        while ((tag = reader.readTag()) != -1) {
            switch (reader.getFieldNumber()) {
                case 1:
                    header.headerId = (int) reader.readVarint();
                    break;
                case 3:
                    header.itag = (int) reader.readVarint();
                    break;
                case 5:
                    header.xtags = reader.readString();
                    break;
                case 8:
                    header.isInitSeg = reader.readVarint() != 0;
                    break;
                case 9:
                    header.sequenceNumber = (int) reader.readVarint();
                    break;
                case 11:
                    header.startMs = reader.readVarint();
                    break;
                case 12:
                    header.durationMs = reader.readVarint();
                    break;
                case 13:
                    header.formatId = FormatId.parse(reader.readMessage());
                    break;
                case 14:
                    header.contentLength = reader.readVarint();
                    break;
                case 15:
                    parseTimeRange(header, reader.readMessage());
                    break;
                default:
                    reader.skip();
                    break;
            }
        }

        return header;
    }

    private static void parseTimeRange(@NotNull MediaHeader header, @NotNull ProtoReader reader) {
        int tag;
        while ((tag = reader.readTag()) != -1) {
            switch (reader.getFieldNumber()) {
                case 2:
                    header.timeRangeDurationTicks = reader.readVarint();
                    break;
                case 3:
                    header.timeRangeTimescale = (int) reader.readVarint();
                    break;
                default:
                    reader.skip();
                    break;
            }
        }
    }
}