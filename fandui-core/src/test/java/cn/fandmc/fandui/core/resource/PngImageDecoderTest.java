package cn.fandmc.fandui.core.resource;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PngImageDecoderTest {
    @Test
    void decodesStaticPngToTightlyPackedPremultipliedRgba() throws Exception {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x80FF8040);
        image.setRGB(1, 0, 0x00123456);

        PngImageDecoder.DecodedImage decoded = PngImageDecoder.decode(png(image));

        assertEquals(2, decoded.width());
        assertEquals(1, decoded.height());
        assertNotEquals(0L, decoded.textureKey());
        assertEquals(32, decoded.cacheKeySha256().length);
        assertArrayEquals(
                new byte[]{
                        (byte) 128, (byte) 64, (byte) 32, (byte) 128,
                        0, 0, 0, 0
                },
                decoded.pixels());
    }

    @Test
    void producesContentKeysFromCanonicalPixelsRatherThanPngContainerBytes() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF2277CC);
        byte[] first = png(image);
        byte[] second = insertChunk(first, "tEXt", "note\0value".getBytes(StandardCharsets.ISO_8859_1));

        PngImageDecoder.DecodedImage firstDecoded = PngImageDecoder.decode(first);
        PngImageDecoder.DecodedImage secondDecoded = PngImageDecoder.decode(second);

        assertEquals(firstDecoded.textureKey(), secondDecoded.textureKey());
        assertArrayEquals(firstDecoded.cacheKeySha256(), secondDecoded.cacheKeySha256());
        assertArrayEquals(firstDecoded.pixels(), secondDecoded.pixels());
    }

    @Test
    void rejectsWrongSignatureAnimationBadCrcAndTrailingData() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFFFFFF);
        byte[] valid = png(image);
        byte[] wrongSignature = valid.clone();
        wrongSignature[0] = 0;
        byte[] badCrc = valid.clone();
        badCrc[29] ^= 1;
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        byte[] animated = insertChunk(
                valid,
                "acTL",
                ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(1).putInt(0).array());

        assertThrows(IOException.class, () -> PngImageDecoder.decode(wrongSignature));
        assertThrows(IOException.class, () -> PngImageDecoder.decode(animated));
        assertThrows(IOException.class, () -> PngImageDecoder.decode(badCrc));
        assertThrows(IOException.class, () -> PngImageDecoder.decode(trailing));
    }

    @Test
    void rejectsDimensionsOverTheDecodedBudgetBeforeImageIoInflation() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        byte[] oversized = png(image);
        ByteBuffer header = ByteBuffer.wrap(oversized).order(ByteOrder.BIG_ENDIAN);
        header.putInt(16, PngImageDecoder.MAX_DIMENSION + 1);
        rewriteChunkCrc(oversized, 8);

        IOException failure = assertThrows(IOException.class, () -> PngImageDecoder.decode(oversized));

        assertTrue(failure.getMessage().contains("dimensions exceed"));
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }

    private static byte[] insertChunk(byte[] png, String type, byte[] payload) {
        int iendOffset = findChunk(png, "IEND");
        ByteBuffer chunk = ByteBuffer.allocate(payload.length + 12).order(ByteOrder.BIG_ENDIAN);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        chunk.putInt(payload.length);
        chunk.put(typeBytes);
        chunk.put(payload);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(payload);
        chunk.putInt((int) crc.getValue());

        byte[] result = new byte[png.length + chunk.capacity()];
        System.arraycopy(png, 0, result, 0, iendOffset);
        System.arraycopy(chunk.array(), 0, result, iendOffset, chunk.capacity());
        System.arraycopy(png, iendOffset, result, iendOffset + chunk.capacity(), png.length - iendOffset);
        return result;
    }

    private static int findChunk(byte[] png, String expectedType) {
        ByteBuffer input = ByteBuffer.wrap(png).order(ByteOrder.BIG_ENDIAN);
        input.position(8);
        while (input.remaining() >= 12) {
            int start = input.position();
            int length = input.getInt();
            byte[] type = new byte[4];
            input.get(type);
            if (new String(type, StandardCharsets.US_ASCII).equals(expectedType)) {
                return start;
            }
            input.position(input.position() + length + 4);
        }
        throw new AssertionError("Chunk not found: " + expectedType);
    }

    private static void rewriteChunkCrc(byte[] png, int chunkOffset) {
        ByteBuffer input = ByteBuffer.wrap(png).order(ByteOrder.BIG_ENDIAN);
        int length = input.getInt(chunkOffset);
        CRC32 crc = new CRC32();
        crc.update(png, chunkOffset + 4, length + 4);
        input.putInt(chunkOffset + 8 + length, (int) crc.getValue());
    }
}
