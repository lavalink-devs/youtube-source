package dev.lavalink.youtube.sabr;

import dev.lavalink.youtube.sabr.protobuf.ProtoReader;
import dev.lavalink.youtube.sabr.protobuf.ProtoWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Identifies a media format within a SABR stream. Corresponds to {@code misc.FormatId}.
 */
public class FormatId {
    public final int itag;
    public final long lastModified;
    public final String xtags;

    public FormatId(int itag, long lastModified, @Nullable String xtags) {
        this.itag = itag;
        this.lastModified = lastModified;
        this.xtags = xtags;
    }

    /**
     * @return A key uniquely identifying this format, mirroring googlevideo's {@code createKey}.
     */
    @NotNull
    public String key() {
        return key(itag, xtags);
    }

    @NotNull
    public static String key(int itag, @Nullable String xtags) {
        return (itag == 0 ? "" : Integer.toString(itag)) + ":" + (xtags == null ? "" : xtags);
    }

    public void writeTo(@NotNull ProtoWriter writer, int field) {
        ProtoWriter fmt = new ProtoWriter();
        fmt.writeVarint(1, itag);

        if (lastModified != 0) {
            fmt.writeVarint(2, lastModified);
        }

        if (xtags != null && !xtags.isEmpty()) {
            fmt.writeString(3, xtags);
        }

        writer.writeMessage(field, fmt);
    }

    @NotNull
    public static FormatId parse(@NotNull ProtoReader reader) {
        int itag = 0;
        long lastModified = 0;
        String xtags = null;

        int tag;
        while ((tag = reader.readTag()) != -1) {
            switch (reader.getFieldNumber()) {
                case 1:
                    itag = (int) reader.readVarint();
                    break;
                case 2:
                    lastModified = reader.readVarint();
                    break;
                case 3:
                    xtags = reader.readString();
                    break;
                default:
                    reader.skip();
                    break;
            }
        }

        return new FormatId(itag, lastModified, xtags);
    }
}