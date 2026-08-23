package cn.fandmc.fandui.internal.demo;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.component.ConstrainedBox;
import cn.fandmc.fandui.api.component.Spacer;
import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.hud.HudLayer;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.core.runtime.CoreUiRuntime;

import java.util.Objects;

public final class BlurDemoHud {
    public static final String ENABLE_PROPERTY = "fandui.demo.blur";

    static final float SIZE = 50.0f;
    static final float EDGE_INSET = 12.0f;
    static final float BLUR_RADIUS = 18.0f;

    private static final UiKey KEY = UiKey.of("fandui", "demo/blur");

    private BlurDemoHud() {
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
        Spacer blur = Spacer.of(SIZE, SIZE);
        blur.setStyle(Style.builder().backdropBlur(BLUR_RADIUS).build());

        float insetBoxSize = SIZE + EDGE_INSET * 2.0f;
        UiComponent insetBox = ConstrainedBox.builder(blur, Constraints.tight(SIZE, SIZE))
                .alignment(Alignment.CENTER)
                .build();
        UiComponent root = ConstrainedBox.builder(
                        insetBox,
                        Constraints.tight(insetBoxSize, insetBoxSize))
                .alignment(Alignment.BOTTOM_RIGHT)
                .build();
        return HudLayer.of(KEY, root);
    }
}
