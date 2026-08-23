package cn.fandmc.fandui.api.style;

import cn.fandmc.fandui.api.UiKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable typed map of theme-token overrides with defensive copy semantics. */
public final class Theme {
    private static final Theme DEFAULTS = new Theme(Map.of());

    private final Map<ThemeToken<?>, Object> values;

    private Theme(Map<ThemeToken<?>, Object> values) {
        this.values = Map.copyOf(values);
    }

    public static Theme defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder(Map.of());
    }

    public static Builder builder(Theme base) {
        Objects.requireNonNull(base, "base");
        return new Builder(base.values);
    }

    public <T> T value(ThemeToken<T> token) {
        Objects.requireNonNull(token, "token");
        Object value = values.get(token);
        return value == null ? token.defaultValue() : token.type().cast(value);
    }

    /** Returns this theme with every explicitly stored value from {@code overrides} applied. */
    public Theme mergedWith(Theme overrides) {
        Objects.requireNonNull(overrides, "overrides");
        if (overrides.values.isEmpty()) {
            return this;
        }
        if (values.isEmpty()) {
            return overrides;
        }
        Map<ThemeToken<?>, Object> merged = new HashMap<>(values);
        for (Map.Entry<ThemeToken<?>, Object> entry : overrides.values.entrySet()) {
            rejectConflictingType(merged, entry.getKey().key(), entry.getKey().type());
            merged.put(entry.getKey(), entry.getValue());
        }
        return new Theme(merged);
    }

    public static final class Builder {
        private final Map<ThemeToken<?>, Object> values;

        private Builder(Map<ThemeToken<?>, Object> values) {
            this.values = new HashMap<>(values);
        }

        public <T> Builder value(ThemeToken<T> token, T value) {
            Objects.requireNonNull(token, "token");
            T checked = token.type().cast(Objects.requireNonNull(value, "value"));
            rejectConflictingType(token.key(), token.type());
            values.put(token, checked);
            return this;
        }

        public Theme build() {
            if (values.isEmpty()) {
                return DEFAULTS;
            }
            return new Theme(values);
        }

        private void rejectConflictingType(UiKey key, Class<?> type) {
            Theme.rejectConflictingType(values, key, type);
        }
    }

    private static void rejectConflictingType(
            Map<ThemeToken<?>, ?> values,
            UiKey key,
            Class<?> type) {
        for (ThemeToken<?> existing : values.keySet()) {
            if (existing.key().equals(key) && !existing.type().equals(type)) {
                throw new IllegalArgumentException(
                        "Theme key " + key + " already uses type " + existing.type().getName());
            }
        }
    }
}
