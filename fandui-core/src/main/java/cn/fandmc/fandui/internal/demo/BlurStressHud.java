package cn.fandmc.fandui.internal.demo;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.component.Row;
import cn.fandmc.fandui.api.component.Spacer;
import cn.fandmc.fandui.api.hud.HudLayer;
import cn.fandmc.fandui.api.layout.CrossAxisAlignment;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.core.runtime.CoreUiRuntime;

import java.util.Objects;

public final class BlurStressHud {
    public static final String ENABLE_PROPERTY = "fandui.demo.blurStress";

    static final float BLUR_RADIUS = 18.0f;

    private static final UiKey KEY = UiKey.of("fandui", "demo/blur-stress");

    private BlurStressHud() {
    }

    public static boolean mountIfEnabled(CoreUiRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || !runtime.availability().available()) {
            return false;
        }
        runtime.hud().mount(createLayer());
        return true;
    }

    static HudLayer createLayer() {
        Spacer leftHalf = Spacer.expanded();
        Spacer blurredHalf = Spacer.expanded();
        blurredHalf.setStyle(Style.builder().backdropBlur(BLUR_RADIUS).build());

        Row root = Row.builder(leftHalf, blurredHalf)
                .crossAxisAlignment(CrossAxisAlignment.STRETCH)
                .build();
        return HudLayer.of(KEY, root);
    }
}
