package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.animation.AnimationSpec;
import cn.fandmc.fandui.api.animation.Easings;
import cn.fandmc.fandui.api.canvas.LineCap;
import cn.fandmc.fandui.api.canvas.LineJoin;
import cn.fandmc.fandui.api.canvas.Path;
import cn.fandmc.fandui.api.canvas.StrokeStyle;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.event.EventRoute;
import cn.fandmc.fandui.api.event.KeyAction;
import cn.fandmc.fandui.api.event.KeyEvent;
import cn.fandmc.fandui.api.event.Keys;
import cn.fandmc.fandui.api.event.PointerAction;
import cn.fandmc.fandui.api.event.PointerButton;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.Insets;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Placeable;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.style.Border;
import cn.fandmc.fandui.api.style.ClipMode;
import cn.fandmc.fandui.api.style.Color;
import cn.fandmc.fandui.api.style.CornerRadii;
import cn.fandmc.fandui.api.style.Paint;
import cn.fandmc.fandui.api.style.SolidPaint;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.StyleResolver;
import cn.fandmc.fandui.api.style.Theme;
import cn.fandmc.fandui.api.style.ThemeToken;
import cn.fandmc.fandui.api.style.VisualState;
import cn.fandmc.fandui.api.text.TextStyle;
import cn.fandmc.fandui.internal.control.ScalarTransition;
import cn.fandmc.fandui.internal.control.ValueChangeListeners;
import cn.fandmc.fandui.internal.validation.Utf16;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A single-component, keyboard-accessible select control with an inline menu.
 *
 * <p>The menu is measured below the trigger while expanded. Pointer capture keeps
 * outside-click dismissal deterministic without requiring a version-specific overlay
 * service. Options are immutable; selection and expanded state are UI-thread mutations.</p>
 */
public final class Dropdown<T> extends UiContainer implements ContentClipProvider {
    private static final AnimationSpec MENU_TRANSITION = AnimationSpec.duration(Duration.ofMillis(160))
            .easing(Easings.EASE_OUT)
            .build();

    public static final ThemeToken<Paint> TRIGGER_BACKGROUND = paintToken("dropdown/trigger", 0x34383f);
    public static final ThemeToken<Paint> TRIGGER_HOVERED = paintToken("dropdown/trigger_hovered", 0x454b54);
    public static final ThemeToken<Paint> MENU_BACKGROUND = paintToken("dropdown/menu", 0x252a31);
    public static final ThemeToken<Paint> OPTION_HOVERED = paintToken("dropdown/option_hovered", 0x2e6074);
    public static final ThemeToken<Paint> OPTION_DISABLED = paintToken("dropdown/option_disabled", 0x59616a);
    public static final ThemeToken<Paint> BORDER_PAINT = paintToken("dropdown/border", 0x707780);
    public static final ThemeToken<Paint> FOCUS_RING = paintToken("dropdown/focus_ring", 0x8edcff);
    public static final ThemeToken<Insets> PADDING = ThemeToken.of(
            UiKey.of("fandui", "dropdown/padding"), Insets.class, Insets.symmetric(8.0f, 6.0f));
    public static final ThemeToken<Double> MIN_WIDTH = ThemeToken.of(
            UiKey.of("fandui", "dropdown/min_width"), Double.class, 160.0);
    public static final ThemeToken<Double> TRIGGER_HEIGHT = ThemeToken.of(
            UiKey.of("fandui", "dropdown/trigger_height"), Double.class, 30.0);
    public static final ThemeToken<Double> OPTION_HEIGHT = ThemeToken.of(
            UiKey.of("fandui", "dropdown/option_height"), Double.class, 26.0);
    public static final ThemeToken<Double> ARROW_SIZE = ThemeToken.of(
            UiKey.of("fandui", "dropdown/arrow_size"), Double.class, 7.0);
    public static final ThemeToken<CornerRadii> CORNER_RADII = ThemeToken.of(
            UiKey.of("fandui", "dropdown/corner_radii"), CornerRadii.class, CornerRadii.all(4.0f));

    private final List<Option<T>> options;
    private final List<Text> optionTexts;
    private final Text selectedText;
    private final TextStyle textStyle;
    private final ValueChangeListeners<T> valueChanges = new ValueChangeListeners<>();
    private final ValueChangeListeners<Option<T>> selectionChanges = new ValueChangeListeners<>();
    private final ScalarTransition expansionTransition;
    private int selectedIndex;
    private boolean expanded;
    private int hoveredOption = -1;
    private int armedOption = Integer.MIN_VALUE;
    private float triggerHeight;
    private float optionHeight;
    private float menuTop;
    private float menuHeight;
    private float menuWidth;
    private Size measuredSize = new Size(0.0f, 0.0f);

    private Dropdown(Builder<T> builder) {
        super(builder.key);
        options = List.copyOf(builder.options);
        textStyle = builder.textStyle;
        selectedIndex = initialIndex(options, builder.selectedIndex);
        expansionTransition = new ScalarTransition(
                0.0,
                MENU_TRANSITION,
                ignored -> invalidateLayout());
        selectedText = Text.of(selectedLabel(), textStyle);
        selectedText.setHitTestBehavior(HitTestBehavior.PASS_THROUGH);
        add(selectedText);
        optionTexts = new ArrayList<>(options.size());
        for (Option<T> option : options) {
            Text text = Text.of(option.label(), textStyle);
            text.setHitTestBehavior(HitTestBehavior.PASS_THROUGH);
            optionTexts.add(text);
            add(text);
        }
        setFocusable(true);
        setStyle(builder.style == null ? this::defaultStyle : builder.style);
        on(PointerEvent.class, EventRoute.BUBBLE, this::handlePointer);
        on(KeyEvent.class, EventRoute.BUBBLE, this::handleKey);
        if (builder.onChange != null) {
            onValueChange(builder.onChange);
        }
    }

    public static <T> Builder<T> builder(List<Option<T>> options) {
        return new Builder<>(options);
    }

    @SafeVarargs
    public static <T> Builder<T> builder(Option<T>... options) {
        Objects.requireNonNull(options, "options");
        List<Option<T>> copy = new ArrayList<>(options.length);
        for (Option<T> option : options) {
            copy.add(Objects.requireNonNull(option, "option"));
        }
        return builder(copy);
    }

    public static <T> Dropdown<T> of(List<Option<T>> options) {
        return builder(options).build();
    }

    public List<Option<T>> options() {
        return options;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public Optional<Option<T>> selectedOption() {
        return selectedIndex < 0 ? Optional.empty() : Optional.of(options.get(selectedIndex));
    }

    public Optional<T> value() {
        return selectedOption().map(Option::value);
    }

    public boolean expanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        requireMutationThread();
        if (this.expanded == expanded) {
            return;
        }
        this.expanded = expanded;
        hoveredOption = expanded ? selectedIndex : -1;
        armedOption = Integer.MIN_VALUE;
        expansionTransition.setTarget(expanded ? 1.0 : 0.0);
    }

    public void toggleExpanded() {
        setExpanded(!expanded);
    }

    public void setSelectedIndex(int index) {
        requireMutationThread();
        if (index < 0 || index >= options.size()) {
            throw new IndexOutOfBoundsException("selected index: " + index);
        }
        if (!options.get(index).enabled()) {
            throw new IllegalArgumentException("Cannot select a disabled dropdown option");
        }
        if (selectedIndex == index) {
            return;
        }
        selectedIndex = index;
        selectedText.setText(selectedLabel());
        invalidateLayout();
        Option<T> selected = options.get(index);
        valueChanges.notifyListeners(selected.value());
        selectionChanges.notifyListeners(selected);
    }

    public void setValue(T value) {
        requireMutationThread();
        Objects.requireNonNull(value, "value");
        for (int index = 0; index < options.size(); index++) {
            if (Objects.equals(options.get(index).value(), value)) {
                setSelectedIndex(index);
                return;
            }
        }
        throw new IllegalArgumentException("No dropdown option has value " + value);
    }

    public EventRegistration onValueChange(Consumer<? super T> listener) {
        requireMutationThread();
        return valueChanges.add(listener);
    }

    public EventRegistration onSelectionChange(Consumer<? super Option<T>> listener) {
        requireMutationThread();
        return selectionChanges.add(listener);
    }

    @Override
    public ClipMode contentClip() {
        return ClipMode.BOUNDS;
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        Insets padding = scope.style().padding();
        float minimumWidth = positive(scope.theme().value(MIN_WIDTH), "minimum width");
        triggerHeight = positive(scope.theme().value(TRIGGER_HEIGHT), "trigger height");
        optionHeight = positive(scope.theme().value(OPTION_HEIGHT), "option height");
        float arrow = positive(scope.theme().value(ARROW_SIZE), "arrow size");
        float availableText = maxTextWidth(constraints, padding, arrow);
        Placeable selected = scope.measure(selectedText, Constraints.loose(availableText, triggerHeight));
        float width = Math.max(minimumWidth,
                selected.size().width() + padding.left() + padding.right() + arrow + 10.0f);
        float expansion = (float) Math.max(0.0, Math.min(1.0, expansionTransition.value()));
        boolean menuVisible = expanded || expansion > 0.0f;
        List<Placeable> optionPlaceables = new ArrayList<>();
        if (menuVisible) {
            for (Text text : optionTexts) {
                optionPlaceables.add(scope.measure(text, Constraints.loose(availableText, optionHeight)));
                width = Math.max(width,
                        optionPlaceables.get(optionPlaceables.size() - 1).size().width()
                                + padding.left() + padding.right() + arrow + 10.0f);
            }
        }
        menuWidth = width;
        menuTop = triggerHeight;
        float desiredMenuHeight = menuVisible ? optionHeight * options.size() * expansion : 0.0f;
        Size size = constraints.constrain(new Size(width, triggerHeight + desiredMenuHeight));
        menuHeight = Math.max(0.0f, size.height() - menuTop);
        measuredSize = size;
        return scope.layout(size.width(), size.height(), placements -> {
            float textY = padding.top() + Math.max(0.0f,
                    (triggerHeight - padding.top() - padding.bottom() - selected.size().height()) * 0.5f);
            placements.place(selected, padding.left(), textY);
            if (!menuVisible) {
                return;
            }
            for (int index = 0; index < optionPlaceables.size(); index++) {
                Placeable option = optionPlaceables.get(index);
                float y = menuTop + index * optionHeight + Math.max(0.0f,
                        (optionHeight - option.size().height()) * 0.5f);
                placements.place(option, padding.left(), y, 1);
            }
        });
    }

    @Override
    public void paint(PaintScope scope) {
        var trigger = new cn.fandmc.fandui.api.layout.Rect(
                0.0f, 0.0f, scope.bounds().width(), triggerHeight);
        Paint triggerPaint = scope.theme().value(TRIGGER_BACKGROUND);
        scope.canvas().fillRoundedRect(trigger, scope.theme().value(CORNER_RADII), triggerPaint);
        if (menuHeight > 0.0f) {
            var menu = new cn.fandmc.fandui.api.layout.Rect(
                    0.0f, menuTop, scope.bounds().width(), menuHeight);
            scope.canvas().fillRect(menu, scope.theme().value(MENU_BACKGROUND));
            for (int index = 0; index < options.size(); index++) {
                if (index == hoveredOption && options.get(index).enabled()) {
                    scope.canvas().fillRect(
                            new cn.fandmc.fandui.api.layout.Rect(0.0f, menuTop + index * optionHeight,
                                    scope.bounds().width(), optionHeight),
                            scope.theme().value(OPTION_HOVERED));
                }
            }
        }
        Border border = scope.style().border();
        if (border.width() > 0.0f) {
            scope.canvas().stroke(
                    Path.builder().roundedRect(trigger, scope.theme().value(CORNER_RADII)).build(),
                    border.paint(),
                    StrokeStyle.width(border.width()).build());
            if (menuHeight > 0.0f) {
                scope.canvas().stroke(
                        Path.builder().rect(new cn.fandmc.fandui.api.layout.Rect(
                                0.0f, menuTop, scope.bounds().width(),
                                menuHeight)).build(),
                        border.paint(),
                        StrokeStyle.width(border.width()).build());
            }
        }
        float arrowSize = positive(scope.theme().value(ARROW_SIZE), "arrow size");
        float centerX = scope.bounds().width() - scope.style().padding().right() - arrowSize;
        float centerY = triggerHeight * 0.5f;
        float expansion = (float) Math.max(0.0, Math.min(1.0, expansionTransition.value()));
        float edgeOffset = arrowSize * (-0.25f + 0.5f * expansion);
        float centerOffset = arrowSize * (0.35f - 0.7f * expansion);
        Path arrow = Path.builder().moveTo(centerX - arrowSize, centerY + edgeOffset)
                .lineTo(centerX, centerY + centerOffset)
                .lineTo(centerX + arrowSize, centerY + edgeOffset).build();
        scope.canvas().stroke(arrow, scope.theme().value(BORDER_PAINT),
                StrokeStyle.width(1.6f).cap(LineCap.ROUND).join(LineJoin.ROUND).build());
    }

    @Override
    public void attached(ComponentContext context) {
        expansionTransition.attach(context);
    }

    @Override
    public void detached(ComponentContext context) {
        expansionTransition.detach(context);
    }

    private void handlePointer(PointerEvent event, cn.fandmc.fandui.api.event.EventContext context) {
        if (!enabled()) {
            return;
        }
        Point local = context.sceneToLocal(event.scenePosition()).orElse(null);
        if (event.action() == PointerAction.DOWN
                && event.changedButton().filter(PointerButton.PRIMARY::equals).isPresent()) {
            if (local == null) {
                return;
            }
            int option = optionAt(local);
            if (inTrigger(local)) {
                armedOption = Integer.MIN_VALUE;
            } else if (expanded && option >= 0 && options.get(option).enabled()) {
                armedOption = option;
            } else {
                setExpanded(false);
                context.releasePointer();
                context.consume();
                return;
            }
            context.requestFocus();
            context.capturePointer();
            context.consume();
            return;
        }
        if (event.action() == PointerAction.MOVE) {
            if (expanded) {
                int option = local == null ? -1 : optionAt(local);
                if (hoveredOption != option) {
                    hoveredOption = option;
                    invalidatePaint();
                }
            }
            if (armedOption != Integer.MIN_VALUE) {
                context.consume();
            }
            return;
        }
        if (event.action() == PointerAction.UP
                && event.changedButton().filter(PointerButton.PRIMARY::equals).isPresent()) {
            int armed = armedOption;
            int option = local == null ? -1 : optionAt(local);
            boolean triggerClick = armed == Integer.MIN_VALUE && inTrigger(local);
            boolean optionClick = armed >= 0 && armed == option && options.get(armed).enabled();
            armedOption = Integer.MIN_VALUE;
            context.releasePointer();
            if (triggerClick) {
                setExpanded(!expanded);
            } else if (optionClick) {
                setSelectedIndex(armed);
                setExpanded(false);
            } else if (expanded && !inTrigger(local) && option < 0) {
                setExpanded(false);
            }
            context.consume();
            return;
        }
        if (event.action() == PointerAction.CANCEL) {
            armedOption = Integer.MIN_VALUE;
            setExpanded(false);
        }
    }

    private void handleKey(KeyEvent event, cn.fandmc.fandui.api.event.EventContext context) {
        if (!enabled() || (event.action() != KeyAction.PRESS && event.action() != KeyAction.REPEAT)) {
            return;
        }
        if (event.key().equals(Keys.ESCAPE)) {
            if (expanded) {
                setExpanded(false);
                context.consume();
            }
            return;
        }
        if (event.key().equals(Keys.ENTER) || event.key().equals(Keys.SPACE)) {
            if (expanded && hoveredOption >= 0 && options.get(hoveredOption).enabled()) {
                setSelectedIndex(hoveredOption);
                setExpanded(false);
            } else {
                setExpanded(!expanded);
            }
            context.consume();
            return;
        }
        int direction = event.key().equals(Keys.DOWN) || event.key().equals(Keys.RIGHT) ? 1
                : event.key().equals(Keys.UP) || event.key().equals(Keys.LEFT) ? -1 : 0;
        if (direction != 0) {
            if (!expanded) {
                setExpanded(true);
            }
            moveHighlight(direction);
            context.consume();
        } else if (event.key().equals(Keys.HOME) || event.key().equals(Keys.END)) {
            if (!expanded) {
                setExpanded(true);
            }
            hoveredOption = event.key().equals(Keys.HOME)
                    ? nextEnabled(-1, 1)
                    : nextEnabled(options.size(), -1);
            invalidatePaint();
            context.consume();
        }
    }

    private void moveHighlight(int direction) {
        int start = hoveredOption < 0 ? selectedIndex : hoveredOption;
        int next = nextEnabled(start + direction, direction);
        if (next >= 0 && next < options.size() && next != hoveredOption) {
            hoveredOption = next;
            invalidatePaint();
        }
    }

    private int nextEnabled(int start, int direction) {
        for (int index = start; index >= 0 && index < options.size(); index += direction) {
            if (options.get(index).enabled()) {
                return index;
            }
        }
        return -1;
    }

    private boolean inTrigger(@Nullable Point point) {
        return point != null && point.x() >= 0.0f && point.x() < measuredSize.width()
                && point.y() >= 0.0f && point.y() < triggerHeight;
    }

    private int optionAt(@Nullable Point point) {
        if (!expanded || point == null || point.x() < 0.0f || point.x() >= measuredSize.width()
                || point.y() < menuTop || point.y() >= menuTop + menuHeight) {
            return -1;
        }
        int index = (int) ((point.y() - menuTop) / optionHeight);
        return index >= 0 && index < options.size() ? index : -1;
    }

    private String selectedLabel() {
        return selectedIndex < 0 ? "" : options.get(selectedIndex).label();
    }

    private Style defaultStyle(Theme theme, VisualState state) {
        return Style.builder()
                .padding(theme.value(PADDING))
                .border(new Border(1.0f, state.focused() ? theme.value(FOCUS_RING) : theme.value(BORDER_PAINT)))
                .cornerRadii(theme.value(CORNER_RADII))
                .build();
    }

    private static int initialIndex(List<? extends Option<?>> options, int requested) {
        if (requested >= 0) {
            if (requested >= options.size()) {
                throw new IndexOutOfBoundsException("selected index: " + requested);
            }
            if (!options.get(requested).enabled()) {
                throw new IllegalArgumentException("Initial dropdown option is disabled");
            }
            return requested;
        }
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).enabled()) {
                return index;
            }
        }
        return -1;
    }

    private static float maxTextWidth(Constraints constraints, Insets padding, float arrow) {
        if (!Float.isFinite(constraints.maxWidth())) {
            return Float.POSITIVE_INFINITY;
        }
        return Math.max(0.0f, constraints.maxWidth() - padding.left() - padding.right() - arrow - 10.0f);
    }

    private static float positive(Double value, String name) {
        double checked = Objects.requireNonNull(value, name);
        if (!Double.isFinite(checked) || checked <= 0.0 || checked > Float.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
        return (float) checked;
    }

    private static ThemeToken<Paint> paintToken(String value, int rgb) {
        return ThemeToken.of(UiKey.of("fandui", value), Paint.class, new SolidPaint(Color.rgb(rgb)));
    }

    /** Immutable option value and display label. */
    public record Option<T>(T value, String label, boolean enabled) {
        public Option {
            Objects.requireNonNull(value, "value");
            label = Utf16.wellFormed(label, "label");
        }

        public Option(T value, String label) {
            this(value, label, true);
        }

        public static <T> Option<T> of(T value, String label) {
            return new Option<>(value, label, true);
        }

        public static <T> Option<T> disabled(T value, String label) {
            return new Option<>(value, label, false);
        }
    }

    /** Fluent builder for a typed dropdown. */
    public static final class Builder<T> {
        private final List<Option<T>> options;
        private @Nullable UiKey key;
        private TextStyle textStyle = TextStyle.of(14.0f);
        private int selectedIndex = -1;
        private @Nullable Consumer<? super T> onChange;
        private @Nullable StyleResolver style;

        private Builder(List<Option<T>> options) {
            Objects.requireNonNull(options, "options");
            List<Option<T>> copy = new ArrayList<>(options.size());
            for (Option<T> option : options) {
                copy.add(Objects.requireNonNull(option, "option"));
            }
            this.options = List.copyOf(copy);
        }

        public Builder<T> key(UiKey value) {
            key = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder<T> textStyle(TextStyle value) {
            textStyle = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder<T> selectedIndex(int value) {
            selectedIndex = value;
            return this;
        }

        public Builder<T> value(T value) {
            Objects.requireNonNull(value, "value");
            for (int index = 0; index < options.size(); index++) {
                if (Objects.equals(options.get(index).value(), value)) {
                    selectedIndex = index;
                    return this;
                }
            }
            throw new IllegalArgumentException("No dropdown option has value " + value);
        }

        public Builder<T> onChange(Consumer<? super T> value) {
            onChange = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder<T> style(StyleResolver value) {
            style = Objects.requireNonNull(value, "value");
            return this;
        }

        public Dropdown<T> build() {
            return new Dropdown<>(this);
        }
    }
}
