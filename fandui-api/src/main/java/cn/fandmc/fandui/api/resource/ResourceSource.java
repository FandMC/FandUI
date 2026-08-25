package cn.fandmc.fandui.api.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Repeatable byte source loaded on FandUI's dedicated resource worker. */
@FunctionalInterface
public interface ResourceSource {
    /** Returns newly readable bytes or throws when the source is unavailable. */
    byte[] load() throws IOException;

    /** Returns the optional encoding hint used by the image decoder. */
    default ResourceFormat format() {
        return ResourceFormat.AUTO;
    }

    /** Creates a defensively copied in-memory source. */
    static ResourceSource bytes(byte[] bytes) {
        return new ByteArraySource(bytes, ResourceFormat.AUTO);
    }

    /** Creates a PNG source with an explicit encoding hint. */
    static ResourceSource png(byte[] bytes) {
        return new ByteArraySource(bytes, ResourceFormat.PNG);
    }

    /** Creates an SVG source from UTF-8 encoded bytes. */
    static ResourceSource svg(byte[] bytes) {
        return new ByteArraySource(bytes, ResourceFormat.SVG);
    }

    /** Creates an SVG source from a UTF-8 string. */
    static ResourceSource svg(String source) {
        Objects.requireNonNull(source, "source");
        return svg(source.getBytes(StandardCharsets.UTF_8));
    }

}

/** Package-private repeatable source implementation used by the convenience factories. */
final class ByteArraySource implements ResourceSource {
    private final byte[] bytes;
    private final ResourceFormat format;

    ByteArraySource(byte[] bytes, ResourceFormat format) {
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
        this.format = Objects.requireNonNull(format, "format");
    }

    @Override
    public byte[] load() {
        return bytes.clone();
    }

    @Override
    public ResourceFormat format() {
        return format;
    }
}
