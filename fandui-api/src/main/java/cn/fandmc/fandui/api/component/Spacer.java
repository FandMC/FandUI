package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.internal.validation.Preconditions;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Non-painting leaf with fixed, constrained, or legacy expanded sizing. */
public final class Spacer extends UiComponent {
    private float width;
    private float height;
    private boolean expandWidth;
    private boolean expandHeight;

    private Spacer(Builder builder) {
        super(builder.key);
        this.width = builder.width;
        this.height = builder.height;
        this.expandWidth = builder.expandWidth;
        this.expandHeight = builder.expandHeight;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Spacer of(float width, float height) {
        return builder().width(width).height(height).build();
    }

    public static Spacer width(float width) {
        return of(width, 0.0f);
    }

    public static Spacer height(float height) {
        return of(0.0f, height);
    }

    public static Spacer expanded() {
        return builder().expandWidth(true).expandHeight(true).build();
    }

    public Size preferredSize() {
        return new Size(width, height);
    }

    public void setPreferredSize(Size size) {
        requireMutationThread();
        Objects.requireNonNull(size, "size");
        if (Float.compare(width, size.width()) != 0 || Float.compare(height, size.height()) != 0) {
            width = size.width();
            height = size.height();
            invalidateLayout();
        }
    }

    public boolean expandWidth() {
        return expandWidth;
    }

    public void setExpandWidth(boolean expandWidth) {
        requireMutationThread();
        if (this.expandWidth != expandWidth) {
            this.expandWidth = expandWidth;
            invalidateLayout();
        }
    }

    public boolean expandHeight() {
        return expandHeight;
    }

    public void setExpandHeight(boolean expandHeight) {
        requireMutationThread();
        if (this.expandHeight != expandHeight) {
            this.expandHeight = expandHeight;
            invalidateLayout();
        }
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        float measuredWidth = expandWidth && Float.isFinite(constraints.maxWidth())
                ? constraints.maxWidth()
                : width;
        float measuredHeight = expandHeight && Float.isFinite(constraints.maxHeight())
                ? constraints.maxHeight()
                : height;
        Size size = constraints.constrain(new Size(measuredWidth, measuredHeight));
        return scope.layout(size.width(), size.height(), placements -> { });
    }

    public static final class Builder {
        private @Nullable UiKey key;
        private float width;
        private float height;
        private boolean expandWidth;
        private boolean expandHeight;

        private Builder() {
        }

        public Builder key(UiKey key) {
            this.key = Objects.requireNonNull(key, "key");
            return this;
        }

        public Builder width(float width) {
            this.width = Preconditions.nonNegative(width, "width");
            return this;
        }

        public Builder height(float height) {
            this.height = Preconditions.nonNegative(height, "height");
            return this;
        }

        public Builder size(Size size) {
            Objects.requireNonNull(size, "size");
            this.width = size.width();
            this.height = size.height();
            return this;
        }

        public Builder expandWidth(boolean value) {
            this.expandWidth = value;
            return this;
        }

        public Builder expandHeight(boolean value) {
            this.expandHeight = value;
            return this;
        }

        public Spacer build() {
            return new Spacer(this);
        }
    }
}
