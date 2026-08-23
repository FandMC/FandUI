package cn.fandmc.fandui.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable namespaced identifier used for components, HUD layers, resources, and theme tokens. */
public record UiKey(String namespace, String value) implements Comparable<UiKey> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern VALUE = Pattern.compile("[a-z0-9/._-]+");

    public UiKey {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(value, "value");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid namespace: " + namespace);
        }
        if (!VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }

    public static UiKey of(String namespace, String value) {
        return new UiKey(namespace, value);
    }

    public static UiKey parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator != value.lastIndexOf(':') || separator == value.length() - 1) {
            throw new IllegalArgumentException("Expected namespaced key: " + value);
        }
        return of(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public int compareTo(UiKey other) {
        int namespaceOrder = namespace.compareTo(other.namespace);
        return namespaceOrder != 0 ? namespaceOrder : value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return namespace + ':' + value;
    }
}
