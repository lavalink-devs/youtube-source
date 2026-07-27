package dev.lavalink.youtube.sabr;

import dev.lavalink.youtube.sabr.protobuf.ProtoReader;
import org.jetbrains.annotations.NotNull;

/**
 * Metadata sent by the server when a format is first initialized. Corresponds to
 * {@code video_streaming.FormatInitializationMetadata}.
 */
public class FormatInitializationMetadata {
    public String videoId;
    public FormatId formatId;
    public long endSegmentNumber;
    public String mimeType;
    public long durationUnits;
    public long durationTimescale;

    @NotNull
    public String formatKey() {
        return formatId == null ? "" : formatId.key();
    }

    public boolean isAudio() {
        return mimeType != null && mimeType.startsWith("audio");
    }

    /**
     * @return The total media duration in milliseconds, or {@code 0} if it cannot be computed.
     */
    public long durationMs() {
        if (durationTimescale <= 0) {
            return 0;
        }

        return (long) (durationUnits / (durationTimescale / 1000.0));
    }

    @NotNull
    public static FormatInitializationMetadata parse(@NotNull ProtoReader reader) {
        FormatInitializationMetadata metadata = new FormatInitializationMetadata();

        int tag;
        while ((tag = reader.readTag()) != -1) {
            switch (reader.getFieldNumber()) {
                case 1:
                    metadata.videoId = reader.readString();
                    break;
                case 2:
                    metadata.formatId = FormatId.parse(reader.readMessage());
                    break;
                case 4:
                    metadata.endSegmentNumber = reader.readVarint();
                    break;
                case 5:
                    metadata.mimeType = reader.readString();
                    break;
                case 9:
                    metadata.durationUnits = reader.readVarint();
                    break;
                case 10:
                    metadata.durationTimescale = reader.readVarint();
                    break;
                default:
                    reader.skip();
                    break;
            }
        }

        return metadata;
    }
}