package cn.fandmc.fandui.api.text;

import cn.fandmc.fandui.api.UiKey;

import java.util.Objects;

/** Stable platform-neutral font-family handle identified by a FandUI resource key. */
public record FontFamily(UiKey key) {
    public FontFamily {
        Objects.requireNonNull(key, "key");
    }
}
