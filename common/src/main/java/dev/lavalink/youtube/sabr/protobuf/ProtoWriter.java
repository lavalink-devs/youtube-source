package dev.lavalink.youtube.sabr.protobuf;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * A minimal protocol buffers (proto2/proto3 wire-compatible) encoder.
 * Only the subset of functionality required to build SABR requests is implemented.
 */
public class ProtoWriter {
    static final int WIRETYPE_VARINT = 0;
    static final int WIRETYPE_FIXED64 = 1;
    static final int WIRETYPE_LENGTH_DELIMITED = 2;
    static final int WIRETYPE_FIXED32 = 5;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private void writeTag(int field, int wireType) {
        writeRawVarint(((long) field << 3) | wireType);
    }

    private void writeRawVarint(long value) {
        // LEB128 unsigned varint encoding.
        while (true) {
            int bits = (int) (value & 0x7F);
            value >>>= 7;

            if (value != 0) {
                out.write(bits | 0x80);
            } else {
                out.write(bits);
                return;
            }
        }
    }

    @NotNull
    public ProtoWriter writeVarint(int field, long value) {
        writeTag(field, WIRETYPE_VARINT);
        writeRawVarint(value);
        return this;
    }

    @NotNull
    public ProtoWriter writeBool(int field, boolean value) {
        return writeVarint(field, value ? 1 : 0);
    }

    @NotNull
    public ProtoWriter writeSInt(int field, long value) {
        // zigzag encoding for signed varints.
        return writeVarint(field, (value << 1) ^ (value >> 63));
    }

    @NotNull
    public ProtoWriter writeFloat(int field, float value) {
        writeTag(field, WIRETYPE_FIXED32);
        int bits = Float.floatToIntBits(value);
        out.write(bits & 0xFF);
        out.write((bits >>> 8) & 0xFF);
        out.write((bits >>> 16) & 0xFF);
        out.write((bits >>> 24) & 0xFF);
        return this;
    }

    @NotNull
    public ProtoWriter writeBytes(int field, @NotNull byte[] value) {
        writeTag(field, WIRETYPE_LENGTH_DELIMITED);
        writeRawVarint(value.length);
        out.write(value, 0, value.length);
        return this;
    }

    @NotNull
    public ProtoWriter writeString(int field, @NotNull String value) {
        return writeBytes(field, value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes a nested message as a length-delimited field.
     */
    @NotNull
    public ProtoWriter writeMessage(int field, @NotNull ProtoWriter message) {
        return writeBytes(field, message.toByteArray());
    }

    @NotNull
    public byte[] toByteArray() {
        return out.toByteArray();
    }
}