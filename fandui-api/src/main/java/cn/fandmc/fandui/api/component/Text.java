package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.canvas.CanvasState;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.layout.TextBaseline;
import cn.fandmc.fandui.api.style.StyleResolver;
import cn.fandmc.fandui.api.text.TextAlignment;
import cn.fandmc.fandui.api.text.TextDirection;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.api.text.TextOverflow;
import cn.fandmc.fandui.api.text.TextRequest;
import cn.fandmc.fandui.api.text.TextStyle;
import cn.fandmc.fandui.api.text.TextWrap;
import cn.fandmc.fandui.internal.component.ComponentBindings;
import cn.fandmc.fandui.internal.validation.Utf16;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/** An asynchronously laid out text block backed by the runtime text service. */
public final class Text extends UiComponent {
    private String value;
    private TextStyle textStyle;
    private int maxLines;
    private TextWrap wrap;
    private TextOverflow overflow;
    private TextAlignment alignment;
    private TextDirection direction;

    private @Nullable ComponentContext context;
    private @Nullable RequestKey requestedKey;
    private @Nullable RequestKey layoutKey;
    private @Nullable RequestKey failedKey;
    private @Nullable CompletableFuture<TextLayout> pending;
    private @Nullable TextLayout layout;

    private Text(Builder builder) {
        super(builder.key);
        value = builder.value;
        textStyle = builder.textStyle;
        maxLines = builder.maxLines;
        wrap = builder.wrap;
        overflow = builder.overflow;
        alignment = builder.alignment;
        direction = builder.direction;
        if (builder.style != null) {
            setStyle(builder.style);
        }
    }

    public static Builder builder(String value, TextStyle textStyle) {
        return new Builder(value, textStyle);
    }

    public static Builder builder(String value, float fontSize) {
        return builder(value, TextStyle.of(fontSize));
    }

    public static Text of(String value, TextStyle textStyle) {
        return builder(value, textStyle).build();
    }

    public static Text of(String value, float fontSize) {
        return builder(value, fontSize).build();
    }

    public String text() {
        return value;
    }

    public void setText(String value) {
        ComponentBindings.assertMutationAllowed(this);
        String checked = Utf16.wellFormed(value, "value");
        if (!this.value.equals(checked)) {
            this.value = checked;
            invalidateRequest();
        }
    }

    public TextStyle textStyle() {
        return textStyle;
    }

    public void setTextStyle(TextStyle textStyle) {
        ComponentBindings.assertMutationAllowed(this);
        TextStyle checked = Objects.requireNonNull(textStyle, "textStyle");
        if (this.textStyle != checked) {
            this.textStyle = checked;
            invalidateRequest();
        }
    }

    public int maxLines() {
        return maxLines;
    }

    public void setMaxLines(int maxLines) {
        ComponentBindings.assertMutationAllowed(this);
        if (maxLines < 1) {
            throw new IllegalArgumentException("maxLines must be at least 1");
        }
        if (this.maxLines != maxLines) {
            this.maxLines = maxLines;
            invalidateRequest();
        }
    }

    public TextWrap wrap() {
        return wrap;
    }

    public void setWrap(TextWrap wrap) {
        ComponentBindings.assertMutationAllowed(this);
        TextWrap checked = Objects.requireNonNull(wrap, "wrap");
        if (this.wrap != checked) {
            this.wrap = checked;
            invalidateRequest();
        }
    }

    public TextOverflow overflow() {
        return overflow;
    }

    public void setOverflow(TextOverflow overflow) {
        ComponentBindings.assertMutationAllowed(this);
        TextOverflow checked = Objects.requireNonNull(overflow, "overflow");
        if (this.overflow != checked) {
            this.overflow = checked;
            invalidateRequest();
        }
    }

    public TextAlignment alignment() {
        return alignment;
    }

    public void setAlignment(TextAlignment alignment) {
        ComponentBindings.assertMutationAllowed(this);
        TextAlignment checked = Objects.requireNonNull(alignment, "alignment");
        if (this.alignment != checked) {
            this.alignment = checked;
            invalidateRequest();
        }
    }

    public TextDirection direction() {
        return direction;
    }

    public void setDirection(TextDirection direction) {
        ComponentBindings.assertMutationAllowed(this);
        TextDirection checked = Objects.requireNonNull(direction, "direction");
        if (this.direction != checked) {
            this.direction = checked;
            invalidateRequest();
        }
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        ComponentContext attached = context;
        RequestKey key = new RequestKey(
                value,
                textStyle,
                attached == null ? -1L : attached.resources().generation(),
                constraints.maxWidth(),
                maxLines,
                wrap,
                overflow,
                alignment,
                direction);
        request(key);

        TextLayout current = layout;
        Size desired = current == null ? new Size(0.0f, 0.0f) : current.size();
        Size size = constraints.constrain(desired);
        Map<TextBaseline, Float> baselines = current == null ? Map.of() : baselines(current, size.height());
        return scope.layout(size.width(), size.height(), baselines, placements -> { });
    }

    @Override
    public void paint(PaintScope scope) {
        TextLayout current = layout;
        if (current == null) {
            return;
        }
        CanvasState state = scope.canvas().save();
        try {
            scope.canvas().intersectScissor(scope.bounds());
            scope.canvas().drawText(current, new Point(0.0f, 0.0f));
        } finally {
            state.close();
        }
    }

    @Override
    public void attached(ComponentContext context) {
        if (this.context != null) {
            throw new IllegalStateException("Text is already attached");
        }
        this.context = Objects.requireNonNull(context, "context");
    }

    @Override
    public void detached(ComponentContext context) {
        if (this.context != context) {
            throw new IllegalStateException("Text detached from an unexpected context");
        }
        cancelPending();
        this.context = null;
        requestedKey = null;
        layoutKey = null;
        failedKey = null;
        layout = null;
    }

    private void request(RequestKey key) {
        ComponentContext attached = context;
        if (attached == null
                || key.equals(requestedKey)
                || key.equals(failedKey)
                || key.equals(layoutKey)) {
            return;
        }
        cancelPending();
        TextRequest request = TextRequest.builder(key.value, key.textStyle)
                .maxWidth(key.maxWidth)
                .maxLines(key.maxLines)
                .wrap(key.wrap)
                .overflow(key.overflow)
                .alignment(key.alignment)
                .direction(key.direction)
                .build();
        CompletableFuture<TextLayout> future;
        try {
            future = Objects.requireNonNull(attached.text().layout(request), "TextService.layout()");
        } catch (RuntimeException exception) {
            failedKey = key;
            return;
        }
        requestedKey = key;
        pending = future;
        future.whenComplete((result, failure) -> attached.execute(
                () -> publish(attached, key, future, result, failure)));
    }

    private void publish(
            ComponentContext expectedContext,
            RequestKey key,
            CompletableFuture<TextLayout> future,
            TextLayout result,
            Throwable failure) {
        if (context != expectedContext || pending != future || !key.equals(requestedKey)) {
            return;
        }
        pending = null;
        requestedKey = null;
        if (failure != null || result == null) {
            if (!(failure instanceof CancellationException)) {
                failedKey = key;
            }
            return;
        }
        layout = result;
        layoutKey = key;
        failedKey = null;
        invalidateLayout();
    }

    private void invalidateRequest() {
        cancelPending();
        requestedKey = null;
        layoutKey = null;
        failedKey = null;
        invalidateLayout();
    }

    private void cancelPending() {
        CompletableFuture<TextLayout> current = pending;
        pending = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    private static Map<TextBaseline, Float> baselines(TextLayout layout, float measuredHeight) {
        EnumMap<TextBaseline, Float> result = new EnumMap<>(TextBaseline.class);
        addBaseline(result, TextBaseline.ALPHABETIC, layout.alphabeticBaseline(), measuredHeight);
        addBaseline(result, TextBaseline.IDEOGRAPHIC, layout.ideographicBaseline(), measuredHeight);
        return Map.copyOf(result);
    }

    private static void addBaseline(
            EnumMap<TextBaseline, Float> result,
            TextBaseline baseline,
            float value,
            float measuredHeight) {
        if (Float.isFinite(value) && value >= 0.0f && value <= measuredHeight) {
            result.put(baseline, value);
        }
    }

    private record RequestKey(
            String value,
            TextStyle textStyle,
            long resourceGeneration,
            float maxWidth,
            int maxLines,
            TextWrap wrap,
            TextOverflow overflow,
            TextAlignment alignment,
            TextDirection direction) {
    }

    public static final class Builder {
        private final String value;
        private final TextStyle textStyle;
        private @Nullable UiKey key;
        private int maxLines = Integer.MAX_VALUE;
        private TextWrap wrap = TextWrap.WORD;
        private TextOverflow overflow = TextOverflow.CLIP;
        private TextAlignment alignment = TextAlignment.START;
        private TextDirection direction = TextDirection.AUTO;
        private @Nullable StyleResolver style;

        private Builder(String value, TextStyle textStyle) {
            this.value = Utf16.wellFormed(value, "value");
            this.textStyle = Objects.requireNonNull(textStyle, "textStyle");
        }

        public Builder key(UiKey key) {
            this.key = Objects.requireNonNull(key, "key");
            return this;
        }

        public Builder maxLines(int maxLines) {
            if (maxLines < 1) {
                throw new IllegalArgumentException("maxLines must be at least 1");
            }
            this.maxLines = maxLines;
            return this;
        }

        public Builder wrap(TextWrap wrap) {
            this.wrap = Objects.requireNonNull(wrap, "wrap");
            return this;
        }

        public Builder overflow(TextOverflow overflow) {
            this.overflow = Objects.requireNonNull(overflow, "overflow");
            return this;
        }

        public Builder alignment(TextAlignment alignment) {
            this.alignment = Objects.requireNonNull(alignment, "alignment");
            return this;
        }

        public Builder direction(TextDirection direction) {
            this.direction = Objects.requireNonNull(direction, "direction");
            return this;
        }

        public Builder style(StyleResolver style) {
            this.style = Objects.requireNonNull(style, "style");
            return this;
        }

        public Text build() {
            return new Text(this);
        }
    }
}
