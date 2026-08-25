package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.icon.IconDefinition;
import cn.fandmc.fandui.api.layout.Alignment;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.style.Paint;
import cn.fandmc.fandui.api.style.StyleResolver;
import cn.fandmc.fandui.internal.component.ComponentBindings;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Convenience component that parses and renders a bounded SVG as vector paths. */
public final class SvgIcon extends UiContainer {
    private final Icon icon;
    private final Alignment alignment;

    private SvgIcon(Builder builder) {
        super(builder.key, 1, 1);
        alignment = builder.alignment;
        icon = Icon.builder(IconDefinition.fromSvg(builder.svg))
                .size(builder.size)
                .alignment(builder.alignment)
                .tint(builder.tint)
                .build();
        add(icon);
        if (builder.style != null) {
            setStyle(builder.style);
        }
    }

    public static Builder builder(String svg) {
        return new Builder(svg);
    }

    public static SvgIcon of(String svg) {
        return builder(svg).build();
    }

    public Icon icon() {
        return icon;
    }

    public IconDefinition definition() {
        return icon.definition();
    }

    public void setTint(@Nullable Paint tint) {
        ComponentBindings.assertMutationAllowed(this);
        icon.setTint(tint);
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        return SingleChildSupport.measure(icon, alignment, scope, constraints);
    }

    /** Fluent SVG component builder. */
    public static final class Builder {
        private final String svg;
        private @Nullable UiKey key;
        private Size size = new Size(24.0f, 24.0f);
        private Alignment alignment = Alignment.CENTER;
        private @Nullable Paint tint;
        private @Nullable StyleResolver style;

        private Builder(String svg) {
            this.svg = Objects.requireNonNull(svg, "svg");
        }

        public Builder key(UiKey value) {
            key = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder size(float width, float height) {
            size = new Size(width, height);
            return this;
        }

        public Builder size(Size value) {
            size = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder alignment(Alignment value) {
            alignment = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder tint(@Nullable Paint value) {
            tint = value;
            return this;
        }

        public Builder style(StyleResolver value) {
            style = Objects.requireNonNull(value, "value");
            return this;
        }

        public SvgIcon build() {
            return new SvgIcon(this);
        }
    }
}
