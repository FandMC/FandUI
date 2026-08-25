package cn.fandmc.fandui.core.resource;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Shared immutable-pixel and content-key operations for image decoders. */
final class ImageDecodeSupport {
    private ImageDecodeSupport() {
    }

    static byte[] toPremultipliedRgba(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] result = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        int[] row = new int[width];
        int output = 0;
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int argb : row) {
                int alpha = argb >>> 24;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                result[output++] = (byte) premultiply(red, alpha);
                result[output++] = (byte) premultiply(green, alpha);
                result[output++] = (byte) premultiply(blue, alpha);
                result[output++] = (byte) alpha;
            }
        }
        return result;
    }

    static byte[] digest(String version, int width, int height, byte[] pixels) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        byte[] versionBytes = version.getBytes(StandardCharsets.US_ASCII);
        putInt(digest, versionBytes.length);
        digest.update(versionBytes);
        putInt(digest, width);
        putInt(digest, height);
        digest.update(pixels);
        return digest.digest();
    }

    static long textureKey(byte[] digest) {
        ByteBuffer keys = ByteBuffer.wrap(digest).order(ByteOrder.BIG_ENDIAN);
        long first = keys.getLong();
        if (first != 0L) {
            return first;
        }
        long second = keys.getLong();
        return second == 0L ? 1L : second;
    }

    private static void putInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static int premultiply(int channel, int alpha) {
        return alpha == 0 ? 0 : (channel * alpha + 127) / 255;
    }
}
