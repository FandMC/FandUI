package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.style.Theme;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Applies a theme to one component subtree without changing the session theme. */
public final class ThemeScope extends UiContainer {
    private Theme theme;
    private boolean inherit;

    private ThemeScope(Builder builder) {
        super(builder.key);
        theme = builder.theme;
        inherit = builder.inherit;
        add(builder.child);
    }

    public static Builder builder(UiComponent child) { return new Builder(child); }
    public static ThemeScope of(Theme theme, UiComponent child) { return builder(child).theme(theme).build(); }
    public UiComponent child() { return children().get(0); }
    public Theme themeOverride() { return theme; }
    public boolean inheritsParent() { return inherit; }

    public void setThemeOverride(Theme value) {
        Theme checked = Objects.requireNonNull(value, "value");
        if (theme != checked) {
            theme = checked;
            invalidateLayout();
        }
    }

    public void setInheritsParent(boolean value) {
        if (inherit != value) {
            inherit = value;
            invalidateLayout();
        }
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        return SingleChildSupport.measure(child(), Alignment.TOP_LEFT, scope, constraints);
    }

    @Override
    protected void validateChildAddition(UiComponent child, int index) {
        if (!children().isEmpty()) {
            throw new IllegalStateException("ThemeScope accepts exactly one child");
        }
    }

    public static final class Builder {
        private final UiComponent child;
        private @Nullable UiKey key;
        private Theme theme = Theme.defaults();
        private boolean inherit = true;

        private Builder(UiComponent child) { this.child = Objects.requireNonNull(child, "child"); }
        public Builder key(UiKey value) { key = Objects.requireNonNull(value, "value"); return this; }
        public Builder theme(Theme value) { theme = Objects.requireNonNull(value, "value"); return this; }
        public Builder inherit(boolean value) { inherit = value; return this; }
        public ThemeScope build() { return new ThemeScope(this); }
    }
}
