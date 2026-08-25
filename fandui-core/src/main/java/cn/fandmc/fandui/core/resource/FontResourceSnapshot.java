package cn.fandmc.fandui.core.resource;

import cn.fandmc.fandui.api.UiKey;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable internal snapshot of all custom font bytes in one resource generation. */
public final class FontResourceSnapshot {
    private final long generation;
    private final Map<UiKey, byte[]> fonts;
    private final String contentFingerprint;

    static FontResourceSnapshot create(long generation, Map<UiKey, byte[]> fonts) {
        return new FontResourceSnapshot(generation, fonts);
    }

    public static FontResourceSnapshot empty(long generation) {
        return new FontResourceSnapshot(generation, Map.of());
    }

    private FontResourceSnapshot(long generation, Map<UiKey, byte[]> fonts) {
        if (generation < 0L) {
            throw new IllegalArgumentException("generation must not be negative");
        }
        Objects.requireNonNull(fonts, "fonts");
        TreeMap<UiKey, byte[]> sorted = new TreeMap<>();
        fonts.forEach((key, bytes) -> sorted.put(
                Objects.requireNonNull(key, "font key"),
                Objects.requireNonNull(bytes, "font bytes").clone()));
        this.generation = generation;
        this.fonts = Map.copyOf(sorted);
        this.contentFingerprint = fingerprint(sorted);
    }

    public long generation() {
        return generation;
    }

    public String contentFingerprint() {
        return contentFingerprint;
    }

    /** Returns a deep copy for the native text implementation that owns the resulting buffers. */
    public Map<UiKey, byte[]> copyFonts() {
        Map<UiKey, byte[]> result = new LinkedHashMap<>();
        fonts.forEach((key, bytes) -> result.put(key, bytes.clone()));
        return Map.copyOf(result);
    }

    private static String fingerprint(Map<UiKey, byte[]> fonts) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        putInt(digest, fonts.size());
        fonts.forEach((key, bytes) -> {
            putBytes(digest, key.namespace().getBytes(StandardCharsets.UTF_8));
            putBytes(digest, key.value().getBytes(StandardCharsets.UTF_8));
            putBytes(digest, bytes);
        });
        return HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    private static void putBytes(MessageDigest digest, byte[] value) {
        putInt(digest, value.length);
        digest.update(value);
    }

    private static void putInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }
}
