package cn.fandmc.fandui.api.event;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/** Stable key identity independent of GLFW, LWJGL, and Minecraft key classes. */
public final class KeyCode {
    private static final Pattern NAME = Pattern.compile("[a-z0-9]+(?:\\.[a-z0-9]+)*");
    private static final ConcurrentMap<String, KeyCode> INTERNED = new ConcurrentHashMap<>();

    private final String canonicalName;

    private KeyCode(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    public static KeyCode of(String canonicalName) {
        Objects.requireNonNull(canonicalName, "canonicalName");
        if (!NAME.matcher(canonicalName).matches()) {
            throw new IllegalArgumentException("Invalid canonical key name: " + canonicalName);
        }
        return INTERNED.computeIfAbsent(canonicalName, KeyCode::new);
    }

    public String canonicalName() {
        return canonicalName;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof KeyCode code && canonicalName.equals(code.canonicalName);
    }

    @Override
    public int hashCode() {
        return canonicalName.hashCode();
    }

    @Override
    public String toString() {
        return canonicalName;
    }
}
