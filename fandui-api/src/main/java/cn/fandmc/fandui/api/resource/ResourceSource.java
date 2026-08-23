package cn.fandmc.fandui.api.resource;

import java.io.IOException;
import java.util.Objects;

/** Repeatable byte source loaded on FandUI's dedicated resource worker. */
@FunctionalInterface
public interface ResourceSource {
    /** Returns newly readable bytes or throws when the source is unavailable. */
    byte[] load() throws IOException;

    /** Creates a defensively copied in-memory source. */
    static ResourceSource bytes(byte[] bytes) {
        byte[] stored = Objects.requireNonNull(bytes, "bytes").clone();
        return () -> stored.clone();
    }
}
