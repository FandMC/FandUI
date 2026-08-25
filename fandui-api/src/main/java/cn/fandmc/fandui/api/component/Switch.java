package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.style.StyleResolver;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/** Compatibility name for {@link ToggleSwitch}; exposes the same sliding-button control. */
public final class Switch extends UiContainer {
    private final ToggleSwitch control;

    private Switch(Builder builder) {
        super(builder.key, 1, 1);
        control = ToggleSwitch.builder()
                .selected(builder.selected)
                .onChange(builder.onChange == null ? () -> { } : builder.onChange)
                .onValueChange(builder.onValueChange == null ? ignored -> { } : builder.onValueChange)
                .build();
        add(control);
        if (builder.style != null) {
            setStyle(builder.style);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Switch of(boolean selected) {
        return builder().selected(selected).build();
    }

    public ToggleSwitch control() {
        return control;
    }

    public boolean selected() {
        return control.selected();
    }

    public void setSelected(boolean selected) {
        control.setSelected(selected);
    }

    public void toggle() {
        control.toggle();
    }

    public EventRegistration onChange(Runnable listener) {
        return control.onChange(listener);
    }

    public EventRegistration onValueChange(Consumer<Boolean> listener) {
        return control.onValueChange(listener);
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        return SingleChildSupport.measure(control, cn.fandmc.fandui.api.layout.Alignment.CENTER, scope, constraints);
    }

    /** Fluent builder for the compatibility switch name. */
    public static final class Builder {
        private @Nullable UiKey key;
        private boolean selected;
        private @Nullable Runnable onChange;
        private @Nullable Consumer<Boolean> onValueChange;
        private @Nullable StyleResolver style;

        private Builder() {
        }

        public Builder key(UiKey value) { key = Objects.requireNonNull(value, "value"); return this; }
        public Builder selected(boolean value) { selected = value; return this; }
        public Builder checked(boolean value) { return selected(value); }
        public Builder onChange(Runnable value) { onChange = Objects.requireNonNull(value, "value"); return this; }
        public Builder onValueChange(Consumer<Boolean> value) { onValueChange = Objects.requireNonNull(value, "value"); return this; }
        public Builder style(StyleResolver value) { style = Objects.requireNonNull(value, "value"); return this; }
        public Switch build() { return new Switch(this); }
    }
}
