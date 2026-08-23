package cn.fandmc.fandui.api.hud;

import cn.fandmc.fandui.api.FandUI;
import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.style.Theme;

import java.util.Objects;

/** Immutable definition of one keyed, ordered HUD component tree. */
public final class HudLayer {
    private final UiKey key;
    private final UiComponent root;
    private final int order;
    private final Theme theme;
    private final LayoutDirection layoutDirection;
    private final HudInputMode inputMode;

    private HudLayer(Builder builder) {
        this.key = builder.key;
        this.root = builder.root;
        this.order = builder.order;
        this.theme = builder.theme;
        this.layoutDirection = builder.layoutDirection;
        this.inputMode = builder.inputMode;
    }

    public static Builder builder(UiKey key, UiComponent root) {
        return new Builder(key, root);
    }

    public static HudLayer of(UiKey key, UiComponent root) {
        return builder(key, root).build();
    }

    /** Mounts this layer on the UI thread and returns its ownership handle. */
    public HudRegistration mount() {
        return FandUI.runtime().hud().mount(this);
    }

    public UiKey key() {
        return key;
    }

    public UiComponent root() {
        return root;
    }

    public int order() {
        return order;
    }

    public Theme theme() {
        return theme;
    }

    public LayoutDirection layoutDirection() {
        return layoutDirection;
    }

    public HudInputMode inputMode() {
        return inputMode;
    }

    public static final class Builder {
        private final UiKey key;
        private final UiComponent root;
        private int order;
        private Theme theme = Theme.defaults();
        private LayoutDirection layoutDirection = LayoutDirection.LEFT_TO_RIGHT;
        private HudInputMode inputMode = HudInputMode.PASS_THROUGH;

        private Builder(UiKey key, UiComponent root) {
            this.key = Objects.requireNonNull(key, "key");
            this.root = Objects.requireNonNull(root, "root");
        }

        public Builder order(int value) {
            this.order = value;
            return this;
        }

        public Builder theme(Theme value) {
            this.theme = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder layoutDirection(LayoutDirection value) {
            this.layoutDirection = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder inputMode(HudInputMode value) {
            this.inputMode = Objects.requireNonNull(value, "value");
            return this;
        }

        public HudLayer build() {
            return new HudLayer(this);
        }
    }
}
