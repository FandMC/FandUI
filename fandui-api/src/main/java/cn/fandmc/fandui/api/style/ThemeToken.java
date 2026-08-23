package cn.fandmc.fandui.api.style;

import cn.fandmc.fandui.api.UiKey;

import java.util.Objects;

/** Typed theme lookup key with a non-null fallback value. */
public final class ThemeToken<T> {
    private final UiKey key;
    private final Class<T> type;
    private final T defaultValue;

    private ThemeToken(UiKey key, Class<T> type, T defaultValue) {
        this.key = Objects.requireNonNull(key, "key");
        this.type = Objects.requireNonNull(type, "type");
        this.defaultValue = type.cast(Objects.requireNonNull(defaultValue, "defaultValue"));
    }

    public static <T> ThemeToken<T> of(UiKey key, Class<T> type, T defaultValue) {
        return new ThemeToken<>(key, type, defaultValue);
    }

    public UiKey key() {
        return key;
    }

    public Class<T> type() {
        return type;
    }

    public T defaultValue() {
        return defaultValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ThemeToken<?> token && key.equals(token.key) && type.equals(token.type);
    }

    @Override
    public int hashCode() {
        return 31 * key.hashCode() + type.hashCode();
    }

    @Override
    public String toString() {
        return "ThemeToken[" + key + ", " + type.getName() + ']';
    }
}
