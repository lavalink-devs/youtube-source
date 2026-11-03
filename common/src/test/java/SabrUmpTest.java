import dev.lavalink.youtube.sabr.UmpReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class SabrUmpTest {

    @Test
    public void testVarint1Byte() {
        // Values < 128 are encoded as 1 byte
        byte[] data = new byte[]{0x00, 0x15, 0x7F}; // 0, 21 (MEDIA), 127
        
        long[] res0 = UmpReader.readVarint(data, 0);
        Assertions.assertNotNull(res0);
        Assertions.assertEquals(0, res0[0]);
        Assertions.assertEquals(1, res0[1]);

        long[] res21 = UmpReader.readVarint(data, 1);
        Assertions.assertNotNull(res21);
        Assertions.assertEquals(21, res21[0]);
        Assertions.assertEquals(1, res21[1]);

        long[] res127 = UmpReader.readVarint(data, 2);
        Assertions.assertNotNull(res127);
        Assertions.assertEquals(127, res127[0]);
        Assertions.assertEquals(1, res127[1]);
    }

    @Test
    public void testVarint2Bytes() {
        // Values >= 128 and < 16512 (first byte 128..191)
        // val = (b0 & 0x3F) + 64 * b1
        // Example: headerId = 128 -> (128 & 0x3F) + 64 * 2 -> 0 + 128 = 128
        // b0 = 0x80 (128), b1 = 0x02
        byte[] data = new byte[]{(byte) 0x80, 0x02};
        long[] res = UmpReader.readVarint(data, 0);
        Assertions.assertNotNull(res);
        Assertions.assertEquals(128, res[0]);
        Assertions.assertEquals(2, res[1]);

        // Example: 300 -> (300 % 64) | 0x80 = 44 | 0x80 = 0xAC, b1 = 300 / 64 = 4
        byte[] data300 = new byte[]{(byte) 0xAC, 0x04};
        long[] res300 = UmpReader.readVarint(data300, 0);
        Assertions.assertNotNull(res300);
        Assertions.assertEquals(300, res300[0]);
        Assertions.assertEquals(2, res300[1]);
    }

    @Test
    public void testVarint3Bytes() {
        // First byte in 192..223 (0xC0..0xDF)
        // val = (b0 & 0x1F) + 32 * (b1 + 256 * b2)
        // For b0 = 0xC0 (192), b1 = 0x00, b2 = 0x01 -> 0 + 32 * 256 = 8192
        byte[] data = new byte[]{(byte) 0xC0, 0x00, 0x01};
        long[] res = UmpReader.readVarint(data, 0);
        Assertions.assertNotNull(res);
        Assertions.assertEquals(8192, res[0]);
        Assertions.assertEquals(3, res[1]);
    }

    @Test
    public void testVarint5Bytes() {
        // First byte >= 240 (0xF0)
        // Remaining 4 bytes are little-endian uint32
        // E.g. [0xF0, 0x78, 0x56, 0x34, 0x12] -> 0x12345678 (305419896)
        byte[] data = new byte[]{(byte) 0xF0, 0x78, 0x56, 0x34, 0x12};
        long[] res = UmpReader.readVarint(data, 0);
        Assertions.assertNotNull(res);
        Assertions.assertEquals(0x12345678L, res[0]);
        Assertions.assertEquals(5, res[1]);
    }

    @Test
    public void testUmpReaderPartExtraction() throws Exception {
        // Construct a simulated UMP stream with 2 parts:
        // Part 1: Type 20 (MEDIA_HEADER), length 4, payload: [1, 2, 3, 4]
        // Part 2: Type 21 (MEDIA), length 5, payload: [varint 128 (2 bytes: 0x80, 0x02), audio bytes: 0xAA, 0xBB, 0xCC]
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Part 1: Type 20, len 4
        out.write(20);
        out.write(4);
        out.write(new byte[]{1, 2, 3, 4}, 0, 4);

        // Part 2: Type 21, len 5
        out.write(21);
        out.write(5);
        out.write(new byte[]{(byte) 0x80, 0x02, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC}, 0, 5);

        byte[] umpBytes = out.toByteArray();

        List<Integer> parsedTypes = new ArrayList<>();
        List<byte[]> parsedPayloads = new ArrayList<>();

        new UmpReader(umpBytes).read((type, payload) -> {
            parsedTypes.add(type);
            parsedPayloads.add(payload);
        });

        Assertions.assertEquals(2, parsedTypes.size());
        Assertions.assertEquals(20, parsedTypes.get(0));
        Assertions.assertArrayEquals(new byte[]{1, 2, 3, 4}, parsedPayloads.get(0));

        Assertions.assertEquals(21, parsedTypes.get(1));
        // Verify we can read the varint headerId from part 2
        long[] headerIdResult = UmpReader.readVarint(parsedPayloads.get(1), 0);
        Assertions.assertNotNull(headerIdResult);
        Assertions.assertEquals(128, headerIdResult[0]);
        Assertions.assertEquals(2, headerIdResult[1]); // consumed 2 bytes

        // Media payload after stripping varint:
        int varintLen = (int) headerIdResult[1];
        int mediaLen = parsedPayloads.get(1).length - varintLen;
        byte[] mediaBytes = new byte[mediaLen];
        System.arraycopy(parsedPayloads.get(1), varintLen, mediaBytes, 0, mediaLen);

        Assertions.assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC}, mediaBytes);
    }
}
