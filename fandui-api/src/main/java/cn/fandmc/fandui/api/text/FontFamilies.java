package cn.fandmc.fandui.api.text;

import cn.fandmc.fandui.api.UiKey;

/** Built-in stable font-family handles, including the runtime's fallback family. */
public final class FontFamilies {
    public static final FontFamily DEFAULT = new FontFamily(UiKey.of("fandui", "font/default"));

    private FontFamilies() {
    }
}
