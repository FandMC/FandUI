package cn.fandmc.fandui.core.resource;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.CRC32;

final class PngImageDecoder {
    static final int MAX_ENCODED_BYTES = 64 * 1024 * 1024;
    static final int MAX_DIMENSION = 16_384;
    static final long MAX_DECODED_BYTES = 64L * 1024L * 1024L;

    private static final byte[] SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final String CACHE_KEY_VERSION =
            "FandUI static PNG RGBA8 premultiplied v1";
    private static final Set<String> CRITICAL_CHUNKS = Set.of("IHDR", "PLTE", "IDAT", "IEND");
    private static final Set<String> ANIMATION_CHUNKS = Set.of("acTL", "fcTL", "fdAT");

    private PngImageDecoder() {
    }

    static DecodedImage decode(byte[] encoded) throws IOException {
        if (encoded == null) {
            throw new IOException("ResourceSource returned null bytes");
        }
        if (encoded.length > MAX_ENCODED_BYTES) {
            throw new IOException("PNG source exceeds the encoded byte limit");
        }
        PngHeader header = validateContainer(encoded);
        long decodedBytes = Math.multiplyExact(Math.multiplyExact((long) header.width, header.height), 4L);
        if (header.width > MAX_DIMENSION
                || header.height > MAX_DIMENSION
                || decodedBytes > MAX_DECODED_BYTES) {
            throw new IOException("PNG dimensions exceed the decoded image limit");
        }

        BufferedImage image = readImage(encoded, header);
        byte[] pixels = ImageDecodeSupport.toPremultipliedRgba(image);
        byte[] digest = ImageDecodeSupport.digest(CACHE_KEY_VERSION, header.width, header.height, pixels);
        return new DecodedImage(
                ImageDecodeSupport.textureKey(digest),
                digest,
                header.width,
                header.height,
                pixels);
    }

    private static PngHeader validateContainer(byte[] encoded) throws IOException {
        if (encoded.length < SIGNATURE.length || !Arrays.equals(
                SIGNATURE,
                Arrays.copyOf(encoded, SIGNATURE.length))) {
            throw new IOException("Resource is not a PNG file");
        }

        ByteBuffer input = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        input.position(SIGNATURE.length);
        boolean sawHeader = false;
        boolean sawImageData = false;
        boolean imageDataEnded = false;
        boolean sawEnd = false;
        int width = 0;
        int height = 0;
        while (input.hasRemaining()) {
            if (input.remaining() < 12) {
                throw new IOException("PNG chunk header is truncated");
            }
            long unsignedLength = Integer.toUnsignedLong(input.getInt());
            if (unsignedLength > Integer.MAX_VALUE || unsignedLength + 8L > input.remaining()) {
                throw new IOException("PNG chunk payload is truncated or too large");
            }
            int length = (int) unsignedLength;
            byte[] typeBytes = new byte[4];
            input.get(typeBytes);
            String type = new String(typeBytes, StandardCharsets.US_ASCII);
            requireChunkType(typeBytes);
            int dataOffset = input.position();
            input.position(Math.addExact(dataOffset, length));
            long expectedCrc = Integer.toUnsignedLong(input.getInt());
            CRC32 crc = new CRC32();
            crc.update(typeBytes);
            crc.update(encoded, dataOffset, length);
            if (crc.getValue() != expectedCrc) {
                throw new IOException("PNG chunk CRC mismatch for " + type);
            }
            if (ANIMATION_CHUNKS.contains(type)) {
                throw new IOException("Animated PNG is not supported");
            }
            if (Character.isUpperCase(type.charAt(0)) && !CRITICAL_CHUNKS.contains(type)) {
                throw new IOException("Unsupported critical PNG chunk " + type);
            }
            if (!sawHeader && !type.equals("IHDR")) {
                throw new IOException("IHDR must be the first PNG chunk");
            }
            switch (type) {
                case "IHDR" -> {
                    if (sawHeader || length != 13) {
                        throw new IOException("PNG must contain one 13-byte IHDR chunk");
                    }
                    ByteBuffer header = ByteBuffer.wrap(encoded, dataOffset, length).order(ByteOrder.BIG_ENDIAN);
                    width = header.getInt();
                    height = header.getInt();
                    int bitDepth = Byte.toUnsignedInt(header.get());
                    int colorType = Byte.toUnsignedInt(header.get());
                    int compression = Byte.toUnsignedInt(header.get());
                    int filter = Byte.toUnsignedInt(header.get());
                    int interlace = Byte.toUnsignedInt(header.get());
                    if (width <= 0 || height <= 0) {
                        throw new IOException("PNG dimensions must be positive");
                    }
                    if (!validBitDepth(bitDepth, colorType)
                            || compression != 0
                            || filter != 0
                            || (interlace != 0 && interlace != 1)) {
                        throw new IOException("PNG IHDR uses an unsupported encoding");
                    }
                    sawHeader = true;
                }
                case "IDAT" -> {
                    if (imageDataEnded) {
                        throw new IOException("PNG IDAT chunks must be consecutive");
                    }
                    sawImageData = true;
                }
                case "IEND" -> {
                    if (length != 0 || !sawImageData) {
                        throw new IOException("PNG has an invalid IEND chunk");
                    }
                    sawEnd = true;
                    if (input.hasRemaining()) {
                        throw new IOException("PNG contains data after IEND");
                    }
                }
                default -> {
                    if (sawImageData) {
                        imageDataEnded = true;
                    }
                }
            }
            if (sawEnd) {
                break;
            }
        }
        if (!sawHeader || !sawImageData || !sawEnd) {
            throw new IOException("PNG is missing a required chunk");
        }
        return new PngHeader(width, height);
    }

    private static BufferedImage readImage(byte[] encoded, PngHeader header) throws IOException {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(encoded))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new IOException("No PNG ImageIO reader is available");
            }
            ImageReader reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase("png")) {
                    throw new IOException("PNG signature resolved to a non-PNG ImageIO reader");
                }
                reader.setInput(stream, true, true);
                if (reader.getWidth(0) != header.width || reader.getHeight(0) != header.height) {
                    throw new IOException("ImageIO dimensions do not match PNG IHDR");
                }
                BufferedImage image = reader.read(0);
                if (image == null
                        || image.getWidth() != header.width
                        || image.getHeight() != header.height) {
                    throw new IOException("ImageIO returned an invalid PNG image");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private static boolean validBitDepth(int bitDepth, int colorType) {
        return switch (colorType) {
            case 0 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16;
            case 2, 4, 6 -> bitDepth == 8 || bitDepth == 16;
            case 3 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8;
            default -> false;
        };
    }

    private static void requireChunkType(byte[] type) throws IOException {
        for (byte value : type) {
            if ((value < 'A' || value > 'Z') && (value < 'a' || value > 'z')) {
                throw new IOException("PNG chunk type contains a non-letter byte");
            }
        }
    }

    record DecodedImage(
            long textureKey,
            byte[] cacheKeySha256,
            int width,
            int height,
            byte[] pixels) {
        DecodedImage {
            cacheKeySha256 = cacheKeySha256.clone();
            pixels = pixels.clone();
        }

        @Override
        public byte[] cacheKeySha256() {
            return cacheKeySha256.clone();
        }

        @Override
        public byte[] pixels() {
            return pixels.clone();
        }
    }

    private record PngHeader(int width, int height) {
    }
}
