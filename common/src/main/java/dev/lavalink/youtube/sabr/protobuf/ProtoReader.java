package dev.lavalink.youtube.sabr.protobuf;

import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * A minimal protocol buffers decoder that iterates over the fields of a single message.
 * Only the subset of functionality required to parse SABR responses is implemented.
 *
 * <p>Usage: repeatedly call {@link #readTag()} until it returns {@code -1}, inspecting
 * {@link #getFieldNumber()} and {@link #getWireType()}, then pulling the value with the
 * relevant {@code read*} method (or {@link #skip()} to ignore the field).</p>
 */
public class ProtoReader {
    private static final int WIRETYPE_START_GROUP = 3;
    private static final int WIRETYPE_END_GROUP = 4;

    private final byte[] data;
    private final int limit;
    private int pos;

    private int fieldNumber;
    private int wireType;

    public ProtoReader(byte[] data) {
        this(data, 0, data.length);
    }

    public ProtoReader(byte[] data, int offset, int length) {
        this.data = data;
        this.pos = offset;
        this.limit = offset + length;
    }

    /**
     * @return The tag of the next field, or {@code -1} if the end of the message was reached.
     */
    public int readTag() {
        if (pos >= limit) {
            return -1;
        }

        long tag = readRawVarint();
        fieldNumber = (int) (tag >>> 3);
        wireType = (int) (tag & 0x7);
        return (int) tag;
    }

    public int getFieldNumber() {
        return fieldNumber;
    }

    public int getWireType() {
        return wireType;
    }

    public long readVarint() {
        return readRawVarint();
    }

    public int readSInt32() {
        long value = readRawVarint();
        return (int) ((value >>> 1) ^ -(value & 1));
    }

    public long readSInt64() {
        long value = readRawVarint();
        return (value >>> 1) ^ -(value & 1);
    }

    public byte[] readBytes() {
        int length = (int) readRawVarint();

        if (length < 0 || pos + length > limit) {
            throw new IllegalStateException("Invalid length-delimited field length: " + length);
        }

        byte[] result = new byte[length];
        System.arraycopy(data, pos, result, 0, length);
        pos += length;
        return result;
    }

    public String readString() {
        return new String(readBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Reads a length-delimited field as a nested message reader.
     */
    public ProtoReader readMessage() {
        int length = (int) readRawVarint();

        if (length < 0 || pos + length > limit) {
            throw new IllegalStateException("Invalid nested message length: " + length);
        }

        ProtoReader reader = new ProtoReader(data, pos, length);
        pos += length;
        return reader;
    }

    /**
     * Skips the value of the current field, based on its wire type.
     */
    public void skip() {
        switch (wireType) {
            case ProtoWriter.WIRETYPE_VARINT:
                readRawVarint();
                break;
            case ProtoWriter.WIRETYPE_FIXED64:
                pos += 8;
                break;
            case ProtoWriter.WIRETYPE_LENGTH_DELIMITED: {
                // Note: readRawVarint() advances pos as a side effect, so it must be evaluated
                // into a local before adding to pos (a `pos += readRawVarint()` would discard
                // the length-byte advance and desync the reader).
                int length = (int) readRawVarint();
                pos += length;
                break;
            }
            case ProtoWriter.WIRETYPE_FIXED32:
                pos += 4;
                break;
            case WIRETYPE_START_GROUP:
                skipGroup(fieldNumber);
                break;
            case WIRETYPE_END_GROUP:
                // A stray end-group with no matching start; nothing to consume.
                break;
            default:
                throw new IllegalStateException("Unsupported wire type: " + wireType);
        }

        if (pos > limit) {
            throw new IllegalStateException("Field extends past end of message");
        }
    }

    private void skipGroup(int groupFieldNumber) {
        while (readTag() != -1) {
            if (wireType == WIRETYPE_END_GROUP) {
                return;
            }

            skip();
        }

        throw new IllegalStateException("Truncated group (field " + groupFieldNumber + ")");
    }

    private long readRawVarint() {
        long result = 0;
        int shift = 0;

        while (shift < 64) {
            if (pos >= limit) {
                throw new IllegalStateException("Truncated varint");
            }

            byte b = data[pos++];
            result |= (long) (b & 0x7F) << shift;

            if ((b & 0x80) == 0) {
                return result;
            }

            shift += 7;
        }

        throw new IllegalStateException("Malformed varint");
    }

    @Nullable
    public static byte[] concat(@Nullable byte[] a, @Nullable byte[] b) {
        if (a == null) return b;
        if (b == null) return a;

        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}