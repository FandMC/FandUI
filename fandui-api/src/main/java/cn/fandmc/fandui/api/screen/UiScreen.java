package cn.fandmc.fandui.api.screen;

import cn.fandmc.fandui.api.FandUI;
import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.style.Theme;

import java.util.Objects;

/**
 * Immutable definition used to open a FandUI-backed Minecraft Screen.
 * The root component tree must not already belong to another active session.
 */
public final class UiScreen {
    private final String title;
    private final UiComponent root;
    private final boolean pausesGame;
    private final boolean closesOnEscape;
    private final ScreenBackground background;
    private final Theme theme;
    private final LayoutDirection layoutDirection;

    private UiScreen(Builder builder) {
        this.title = builder.title;
        this.root = builder.root;
        this.pausesGame = builder.pausesGame;
        this.closesOnEscape = builder.closesOnEscape;
        this.background = builder.background;
        this.theme = builder.theme;
        this.layoutDirection = builder.layoutDirection;
    }

    public static Builder builder(String title, UiComponent root) {
        return new Builder(title, root);
    }

    public static UiScreen of(String title, UiComponent root) {
        return builder(title, root).build();
    }

    /** Opens this definition on the UI thread and returns the owning live session. */
    public ScreenSession open() {
        return FandUI.runtime().screens().open(this);
    }

    public String title() {
        return title;
    }

    public UiComponent root() {
        return root;
    }

    public boolean pausesGame() {
        return pausesGame;
    }

    public boolean closesOnEscape() {
        return closesOnEscape;
    }

    public ScreenBackground background() {
        return background;
    }

    public Theme theme() {
        return theme;
    }

    public LayoutDirection layoutDirection() {
        return layoutDirection;
    }

    public static final class Builder {
        private final String title;
        private final UiComponent root;
        private boolean pausesGame;
        private boolean closesOnEscape = true;
        private ScreenBackground background = ScreenBackground.DEFAULT;
        private Theme theme = Theme.defaults();
        private LayoutDirection layoutDirection = LayoutDirection.LEFT_TO_RIGHT;

        private Builder(String title, UiComponent root) {
            this.title = Objects.requireNonNull(title, "title");
            this.root = Objects.requireNonNull(root, "root");
        }

        public Builder pausesGame(boolean value) {
            this.pausesGame = value;
            return this;
        }

        public Builder closesOnEscape(boolean value) {
            this.closesOnEscape = value;
            return this;
        }

        public Builder background(ScreenBackground value) {
            this.background = Objects.requireNonNull(value, "value");
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

        public UiScreen build() {
            return new UiScreen(this);
        }
    }
}
