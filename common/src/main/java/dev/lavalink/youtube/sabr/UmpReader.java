package dev.lavalink.youtube.sabr;

import org.jetbrains.annotations.NotNull;

/**
 * Parser for YouTube's UMP (Universal/Ultra Media Playback) container format, which wraps
 * SABR streaming responses. A UMP payload is a sequence of parts, each encoded as
 * {@code [varint partType][varint partSize][partSize bytes of data]}.
 *
 * <p>Note: UMP uses its own variable-length integer encoding which differs from the LEB128
 * varint used by protocol buffers. The number of bytes is derived from the high bits of the
 * first byte, and the value is little-endian.</p>
 */
public class UmpReader {
    // Relevant UMP part types (see UMPPartId proto enum).
    public static final int MEDIA_HEADER = 20;
    public static final int MEDIA = 21;
    public static final int MEDIA_END = 22;
    public static final int NEXT_REQUEST_POLICY = 35;
    public static final int FORMAT_INITIALIZATION_METADATA = 42;
    public static final int SABR_REDIRECT = 43;
    public static final int SABR_ERROR = 44;
    public static final int RELOAD_PLAYER_RESPONSE = 46;
    public static final int SABR_CONTEXT_UPDATE = 57;
    public static final int STREAM_PROTECTION_STATUS = 58;
    public static final int SABR_CONTEXT_SENDING_POLICY = 59;

    private final byte[] data;
    private int pos;

    public UmpReader(@NotNull byte[] data) {
        this.data = data;
    }

    public interface PartHandler {
        void handle(int type, @NotNull byte[] partData) throws Exception;
    }

    /**
     * Reads every complete part from the buffer, invoking the handler for each.
     */
    public void read(@NotNull PartHandler handler) throws Exception {
        while (pos < data.length) {
            long partType = readVarint();
            long partSize = readVarint();

            if (partType < 0 || partSize < 0) {
                break;
            }

            if (pos + partSize > data.length) {
                // Truncated final part; nothing more can be safely read.
                break;
            }

            byte[] partData = new byte[(int) partSize];
            System.arraycopy(data, pos, partData, 0, (int) partSize);
            pos += (int) partSize;

            handler.handle((int) partType, partData);
        }
    }

    private long readVarint() {
        if (pos >= data.length) {
            return -1;
        }

        int firstByte = data[pos] & 0xFF;
        int byteLength = firstByte < 128 ? 1
            : firstByte < 192 ? 2
            : firstByte < 224 ? 3
            : firstByte < 240 ? 4
            : 5;

        if (pos + byteLength > data.length) {
            return -1;
        }

        long value;

        switch (byteLength) {
            case 1:
                value = data[pos] & 0xFF;
                pos += 1;
                break;
            case 2: {
                int b1 = data[pos] & 0xFF;
                int b2 = data[pos + 1] & 0xFF;
                value = (b1 & 0x3F) + 64L * b2;
                pos += 2;
                break;
            }
            case 3: {
                int b1 = data[pos] & 0xFF;
                int b2 = data[pos + 1] & 0xFF;
                int b3 = data[pos + 2] & 0xFF;
                value = (b1 & 0x1F) + 32L * (b2 + 256L * b3);
                pos += 3;
                break;
            }
            case 4: {
                int b1 = data[pos] & 0xFF;
                int b2 = data[pos + 1] & 0xFF;
                int b3 = data[pos + 2] & 0xFF;
                int b4 = data[pos + 3] & 0xFF;
                value = (b1 & 0x0F) + 16L * (b2 + 256L * (b3 + 256L * b4));
                pos += 4;
                break;
            }
            default: {
                // 5-byte: the low bits of the first byte are ignored; the remaining 4 bytes
                // are a little-endian uint32.
                int b2 = data[pos + 1] & 0xFF;
                int b3 = data[pos + 2] & 0xFF;
                int b4 = data[pos + 3] & 0xFF;
                int b5 = data[pos + 4] & 0xFF;
                value = (b2 + 256L * (b3 + 256L * (b4 + 256L * b5))) & 0xFFFFFFFFL;
                pos += 5;
                break;
            }
        }

        return value;
    }
}