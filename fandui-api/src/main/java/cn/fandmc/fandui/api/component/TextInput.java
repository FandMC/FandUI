package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.api.canvas.CanvasState;
import cn.fandmc.fandui.api.component.control.TextController;
import cn.fandmc.fandui.api.component.control.TextSelection;
import cn.fandmc.fandui.api.event.EventContext;
import cn.fandmc.fandui.api.event.EventRegistration;
import cn.fandmc.fandui.api.event.EventRoute;
import cn.fandmc.fandui.api.event.FocusAction;
import cn.fandmc.fandui.api.event.FocusEvent;
import cn.fandmc.fandui.api.event.KeyAction;
import cn.fandmc.fandui.api.event.KeyEvent;
import cn.fandmc.fandui.api.event.KeyModifier;
import cn.fandmc.fandui.api.event.Keys;
import cn.fandmc.fandui.api.event.PointerAction;
import cn.fandmc.fandui.api.event.PointerButton;
import cn.fandmc.fandui.api.event.PointerEvent;
import cn.fandmc.fandui.api.event.TextCompositionEvent;
import cn.fandmc.fandui.api.event.TextInputEvent;
import cn.fandmc.fandui.api.layout.Constraints;
import cn.fandmc.fandui.api.layout.Insets;
import cn.fandmc.fandui.api.layout.MeasureResult;
import cn.fandmc.fandui.api.layout.MeasureScope;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.layout.TextBaseline;
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
import cn.fandmc.fandui.api.text.TextAffinity;
import cn.fandmc.fandui.api.text.TextAlignment;
import cn.fandmc.fandui.api.text.TextDirection;
import cn.fandmc.fandui.api.text.TextGeometry;
import cn.fandmc.fandui.api.text.TextLayout;
import cn.fandmc.fandui.api.text.TextOverflow;
import cn.fandmc.fandui.api.text.TextPosition;
import cn.fandmc.fandui.api.text.TextRange;
import cn.fandmc.fandui.api.text.TextRangeGeometry;
import cn.fandmc.fandui.api.text.TextRequest;
import cn.fandmc.fandui.api.text.TextStyle;
import cn.fandmc.fandui.api.text.TextWrap;
import cn.fandmc.fandui.internal.component.ComponentBindings;
import cn.fandmc.fandui.internal.control.TextControllerState;
import cn.fandmc.fandui.internal.control.TextControllers;
import cn.fandmc.fandui.internal.validation.Utf16;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** A focusable single-line editor with UTF-16 selection and IME composition preview. */
public final class TextInput extends UiComponent {
    public static final ThemeToken<Paint> BACKGROUND = paintToken("text_input/background", 0x181b20);
    public static final ThemeToken<Paint> DISABLED_BACKGROUND =
            paintToken("text_input/background_disabled", 0x24272c);
    public static final ThemeToken<Border> BORDER = ThemeToken.of(
            UiKey.of("fandui", "text_input/border"),
            Border.class,
            new Border(1.0f, new SolidPaint(Color.rgb(0x626973))));
    public static final ThemeToken<Border> FOCUSED_BORDER = ThemeToken.of(
            UiKey.of("fandui", "text_input/border_focused"),
            Border.class,
            new Border(1.0f, new SolidPaint(Color.rgb(0x4f8ee8))));
    public static final ThemeToken<Insets> PADDING = ThemeToken.of(
            UiKey.of("fandui", "text_input/padding"),
            Insets.class,
            new Insets(8.0f, 6.0f, 8.0f, 6.0f));
    public static final ThemeToken<CornerRadii> CORNER_RADII = ThemeToken.of(
            UiKey.of("fandui", "text_input/corner_radii"),
            CornerRadii.class,
            new CornerRadii(4.0f, 4.0f, 4.0f, 4.0f));
    public static final ThemeToken<Paint> SELECTION = ThemeToken.of(
            UiKey.of("fandui", "text_input/selection"),
            Paint.class,
            new SolidPaint(Color.argb(0x804f8ee8)));
    public static final ThemeToken<Paint> CARET = paintToken("text_input/caret", 0xf4f6f8);
    public static final ThemeToken<Paint> COMPOSITION =
            paintToken("text_input/composition", 0xaeb5bf);
    public static final ThemeToken<Paint> FOCUSED_COMPOSITION =
            paintToken("text_input/composition_focused", 0x65a2f3);
    public static final ThemeToken<Double> CARET_WIDTH = ThemeToken.of(
            UiKey.of("fandui", "text_input/caret_width"),
            Double.class,
            1.0);

    private final TextController controller;
    private final TextControllerState controllerState;
    private TextStyle textStyle;
    private TextDirection direction;
    private Consumer<String> onSubmit;
    private String placeholder;
    private TextStyle placeholderStyle;
    private boolean readOnly;
    private boolean password;
    private int maxLength;
    private UnaryOperator<String> filter;
    private Predicate<String> validator;

    private @Nullable ComponentContext context;
    private @Nullable EventRegistration controllerChanges;
    private boolean controllerBound;
    private boolean mutatingController;
    private String observedText;
    private TextSelection observedSelection;
    private TextAffinity caretAffinity = TextAffinity.DOWNSTREAM;
    private boolean focused;
    private @Nullable Composition composition;

    private @Nullable VisualKey requestedKey;
    private @Nullable VisualKey visualKey;
    private @Nullable VisualKey failedKey;
    private @Nullable PendingVisual pendingVisual;
    private @Nullable EditorVisual visual;

    private Size measuredSize = new Size(0.0f, 0.0f);
    private float textOriginX;
    private float textOriginY;
    private float contentWidth;
    private float horizontalOffset;

    private boolean pointerTracking;
    private boolean pointerAnchorReady;
    private int pointerAnchorUtf16;
    private @Nullable Point pointerLatestLocal;
    private @Nullable DeferredHit deferredHit;
    private @Nullable CompletableFuture<TextPosition> pendingHit;
    private long hitSequence;

    private TextInput(Builder builder) {
        super(builder.key);
        controller = builder.controller;
        controllerState = TextControllers.state(controller);
        textStyle = builder.textStyle;
        direction = builder.direction;
        onSubmit = builder.onSubmit;
        placeholder = builder.placeholder;
        placeholderStyle = builder.placeholderStyle == null
                ? defaultPlaceholderStyle(textStyle)
                : builder.placeholderStyle;
        readOnly = builder.readOnly;
        password = builder.password;
        maxLength = builder.maxLength;
        filter = builder.filter;
        validator = builder.validator;
        observedText = controller.text();
        observedSelection = controller.selection();
        setFocusable(true);
        setStyle(builder.style == null ? TextInput::defaultStyle : builder.style);
        on(PointerEvent.class, EventRoute.BUBBLE, this::handlePointer);
        on(KeyEvent.class, EventRoute.BUBBLE, this::handleKey);
        on(TextInputEvent.class, EventRoute.BUBBLE, this::handleTextInput);
        on(TextCompositionEvent.class, EventRoute.BUBBLE, this::handleComposition);
        on(FocusEvent.class, EventRoute.BUBBLE, this::handleFocus);
    }

    public static Builder builder(TextController controller, TextStyle textStyle) {
        return new Builder(controller, textStyle);
    }

    public static Builder builder(TextController controller, float fontSize) {
        return builder(controller, TextStyle.of(fontSize));
    }

    public static TextInput of(TextController controller, TextStyle textStyle) {
        return builder(controller, textStyle).build();
    }

    public static TextInput of(TextController controller, float fontSize) {
        return builder(controller, fontSize).build();
    }

    public TextController controller() {
        return controller;
    }

    public TextStyle textStyle() {
        return textStyle;
    }

    public void setTextStyle(TextStyle textStyle) {
        ComponentBindings.assertMutationAllowed(this);
        TextStyle checked = Objects.requireNonNull(textStyle, "textStyle");
        if (this.textStyle != checked) {
            this.textStyle = checked;
            invalidateVisual();
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
            invalidateVisual();
        }
    }

    public void setOnSubmit(Consumer<String> onSubmit) {
        ComponentBindings.assertMutationAllowed(this);
        this.onSubmit = Objects.requireNonNull(onSubmit, "onSubmit");
    }

    public String placeholder() {
        return placeholder;
    }

    public void setPlaceholder(String value) {
        ComponentBindings.assertMutationAllowed(this);
        String checked = Utf16.wellFormed(value, "value");
        if (!placeholder.equals(checked)) {
            placeholder = checked;
            invalidateVisual();
        }
    }

    public TextStyle placeholderStyle() {
        return placeholderStyle;
    }

    public void setPlaceholderStyle(TextStyle value) {
        ComponentBindings.assertMutationAllowed(this);
        TextStyle checked = Objects.requireNonNull(value, "value");
        if (placeholderStyle != checked) {
            placeholderStyle = checked;
            invalidateVisual();
        }
    }

    public boolean readOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean value) {
        ComponentBindings.assertMutationAllowed(this);
        if (readOnly != value) {
            readOnly = value;
            if (value && composition != null) {
                composition = null;
                invalidateVisual();
            }
        }
    }

    public boolean password() {
        return password;
    }

    public void setPassword(boolean value) {
        ComponentBindings.assertMutationAllowed(this);
        if (password != value) {
            password = value;
            invalidateVisual();
        }
    }

    /** Maximum accepted Unicode code-point count. */
    public int maxLength() {
        return maxLength;
    }

    public void setMaxLength(int value) {
        ComponentBindings.assertMutationAllowed(this);
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative");
        }
        maxLength = value;
    }

    public void setFilter(UnaryOperator<String> value) {
        ComponentBindings.assertMutationAllowed(this);
        filter = Objects.requireNonNull(value, "value");
    }

    public void setValidator(Predicate<String> value) {
        ComponentBindings.assertMutationAllowed(this);
        validator = Objects.requireNonNull(value, "value");
    }

    @Override
    public MeasureResult measure(MeasureScope scope, Constraints constraints) {
        VisualKey key = currentVisualKey();
        requestVisual(key);

        EditorVisual current = visual;
        float estimatedHeight = textStyle.lineHeight() > 0.0f
                ? textStyle.lineHeight()
                : textStyle.fontSize() * 1.2f;
        Size textSize = current == null
                ? new Size(0.0f, estimatedHeight)
                : current.layout().size();
        Insets padding = scope.style().padding();
        Size size = constraints.constrain(new Size(
                textSize.width() + horizontal(padding),
                textSize.height() + vertical(padding)));
        measuredSize = size;
        contentWidth = Math.max(0.0f, size.width() - horizontal(padding));
        float contentHeight = Math.max(0.0f, size.height() - vertical(padding));
        textOriginX = padding.left();
        textOriginY = padding.top() + Math.max(0.0f, contentHeight - textSize.height()) * 0.5f;
        updateHorizontalOffset(current, contentWidth);
        startDeferredHitIfReady();

        Map<TextBaseline, Float> baselines = current == null
                ? Map.of()
                : baselines(current.layout(), textOriginY, size.height());
        return scope.layout(size.width(), size.height(), baselines, placements -> { });
    }

    @Override
    public void paint(PaintScope scope) {
        SingleChildSupport.paintBackground(scope);
        EditorVisual current = visual;
        if (current == null || contentWidth <= 0.0f) {
            return;
        }

        Style style = scope.style();
        Insets padding = style.padding();
        Rect content = new Rect(
                padding.left(),
                padding.top(),
                Math.max(0.0f, measuredSize.width() - horizontal(padding)),
                Math.max(0.0f, measuredSize.height() - vertical(padding)));
        CanvasState state = scope.canvas().save();
        try {
            scope.canvas().intersectScissor(content);
            scope.canvas().translate(textOriginX - horizontalOffset, textOriginY);
            if (focused && enabled()) {
                paintSelection(scope, current);
            }
            scope.canvas().drawText(current.layout(), new Point(0.0f, 0.0f));
            if (focused && enabled()) {
                paintComposition(scope, current);
                paintCaret(scope, current);
            }
        } finally {
            state.close();
        }
    }

    @Override
    public void attached(ComponentContext context) {
        if (this.context != null) {
            throw new IllegalStateException("TextInput is already attached");
        }
        this.context = Objects.requireNonNull(context, "context");
        try {
            controllerState.bind(this);
            controllerBound = true;
            controllerChanges = controller.onChange(this::controllerChanged);
            observedText = controller.text();
            observedSelection = controller.selection();
        } catch (RuntimeException | Error exception) {
            if (controllerBound) {
                controllerState.unbind(this);
                controllerBound = false;
            }
            this.context = null;
            throw exception;
        }
    }

    @Override
    public void detached(ComponentContext context) {
        if (this.context != context) {
            throw new IllegalStateException("TextInput detached from an unexpected context");
        }
        cancelPendingVisual();
        cancelPointerInteraction();
        EventRegistration changes = controllerChanges;
        controllerChanges = null;
        if (changes != null) {
            changes.close();
        }
        if (controllerBound) {
            controllerState.unbind(this);
            controllerBound = false;
        }
        this.context = null;
        focused = false;
        composition = null;
        requestedKey = null;
        visualKey = null;
        failedKey = null;
        visual = null;
        horizontalOffset = 0.0f;
    }

    private void controllerChanged() {
        String text = controller.text();
        TextSelection selection = controller.selection();
        boolean changed = !observedText.equals(text) || !observedSelection.equals(selection);
        observedText = text;
        observedSelection = selection;
        if (!changed) {
            return;
        }
        if (!mutatingController) {
            caretAffinity = TextAffinity.DOWNSTREAM;
            composition = null;
            cancelPointerInteraction();
        }
        invalidateVisual();
    }

    private void handlePointer(PointerEvent event, EventContext eventContext) {
        if (event.action() == PointerAction.DOWN
                && event.changedButton().filter(PointerButton.PRIMARY::equals).isPresent()) {
            if (!enabled()) {
                return;
            }
            Point local = eventContext.sceneToLocal(event.scenePosition()).orElse(null);
            if (local == null || !inside(local)) {
                return;
            }
            eventContext.requestFocus();
            eventContext.capturePointer();
            beginPointerInteraction(local);
            if (composition != null) {
                composition = null;
                invalidateVisual();
            }
            requestPointerHit(local, HitMode.ANCHOR);
            eventContext.consume();
            return;
        }
        if (event.action() == PointerAction.MOVE && pointerTracking) {
            Point local = eventContext.sceneToLocal(event.scenePosition()).orElse(null);
            if (local != null) {
                pointerLatestLocal = local;
                if (pointerAnchorReady) {
                    requestPointerHit(local, HitMode.DRAG);
                }
            }
            eventContext.consume();
            return;
        }
        if (event.action() == PointerAction.UP
                && event.changedButton().filter(PointerButton.PRIMARY::equals).isPresent()
                && pointerTracking) {
            Point local = eventContext.sceneToLocal(event.scenePosition()).orElse(pointerLatestLocal);
            pointerTracking = false;
            if (local != null) {
                pointerLatestLocal = local;
                if (pointerAnchorReady) {
                    requestPointerHit(local, HitMode.FINAL);
                }
            }
            eventContext.releasePointer();
            eventContext.consume();
            return;
        }
        if (event.action() == PointerAction.CANCEL) {
            cancelPointerInteraction();
        }
    }

    private void handleKey(KeyEvent event, EventContext eventContext) {
        if (!enabled() || event.action() == KeyAction.RELEASE) {
            return;
        }
        boolean command = event.modifiers().contains(KeyModifier.CONTROL)
                || event.modifiers().contains(KeyModifier.SUPER);
        if (command) {
            if (event.key().equals(Keys.letter('a'))) {
                caretAffinity = TextAffinity.DOWNSTREAM;
                mutateController(controller::selectAll);
            } else if (event.key().equals(Keys.letter('c'))) {
                copySelection();
            } else if (event.key().equals(Keys.letter('x'))) {
                cutSelection();
            } else if (event.key().equals(Keys.letter('v'))) {
                pasteClipboard();
            } else {
                return;
            }
            eventContext.consume();
            return;
        }
        if (composition != null && isEditingKey(event)) {
            eventContext.consume();
            return;
        }
        if (event.key().equals(Keys.LEFT)) {
            moveCaret(false, event.modifiers().contains(KeyModifier.SHIFT));
        } else if (event.key().equals(Keys.RIGHT)) {
            moveCaret(true, event.modifiers().contains(KeyModifier.SHIFT));
        } else if (event.key().equals(Keys.HOME)) {
            moveCaretTo(0, event.modifiers().contains(KeyModifier.SHIFT), TextAffinity.DOWNSTREAM);
        } else if (event.key().equals(Keys.END)) {
            moveCaretTo(
                    controller.text().length(),
                    event.modifiers().contains(KeyModifier.SHIFT),
                    TextAffinity.UPSTREAM);
        } else if (event.key().equals(Keys.BACKSPACE)) {
            if (!readOnly) {
                delete(false);
            }
        } else if (event.key().equals(Keys.DELETE)) {
            if (!readOnly) {
                delete(true);
            }
        } else if (event.key().equals(Keys.ENTER) && event.action() == KeyAction.PRESS) {
            onSubmit.accept(controller.text());
        } else {
            return;
        }
        eventContext.consume();
    }

    private void handleTextInput(TextInputEvent event, EventContext eventContext) {
        if (!enabled()) {
            return;
        }
        if (readOnly) {
            eventContext.consume();
            return;
        }
        if (event.text().isEmpty()) {
            if (composition != null) {
                composition = null;
                invalidateVisual();
                eventContext.consume();
            }
            return;
        }
        composition = null;
        caretAffinity = TextAffinity.DOWNSTREAM;
        replace(controller.selection(), event.text());
        eventContext.consume();
    }

    private void handleComposition(TextCompositionEvent event, EventContext eventContext) {
        if (!enabled()) {
            return;
        }
        if (readOnly) {
            eventContext.consume();
            return;
        }
        if (!event.active()) {
            if (composition != null) {
                composition = null;
                invalidateVisual();
            }
            eventContext.consume();
            return;
        }
        TextSelection replacement = composition == null
                ? controller.selection()
                : composition.replacement();
        composition = new Composition(
                replacement,
                event.fullText(),
                event.caretUtf16(),
                event.blocks(),
                event.focusedBlock().orElse(-1));
        caretAffinity = TextAffinity.DOWNSTREAM;
        invalidateVisual();
        eventContext.consume();
    }

    private void handleFocus(FocusEvent event, EventContext eventContext) {
        boolean nextFocused = event.action() == FocusAction.GAINED;
        if (focused == nextFocused) {
            return;
        }
        focused = nextFocused;
        if (!focused) {
            cancelPointerInteraction();
            if (composition != null) {
                composition = null;
                invalidateVisual();
                return;
            }
        }
        invalidatePaint();
    }

    private void moveCaret(boolean forward, boolean extend) {
        TextSelection selection = controller.selection();
        int target;
        if (!extend && !selection.collapsed()) {
            target = forward ? selection.endUtf16() : selection.startUtf16();
        } else {
            target = forward
                    ? nextBoundary(controller.text(), selection.focusUtf16())
                    : previousBoundary(controller.text(), selection.focusUtf16());
        }
        moveCaretTo(target, extend, forward ? TextAffinity.DOWNSTREAM : TextAffinity.UPSTREAM);
    }

    private void moveCaretTo(int target, boolean extend, TextAffinity affinity) {
        TextSelection current = controller.selection();
        TextSelection next = new TextSelection(extend ? current.anchorUtf16() : target, target);
        caretAffinity = affinity;
        mutateController(() -> controller.setSelection(next));
    }

    private void delete(boolean forward) {
        TextSelection selection = controller.selection();
        TextSelection range = selection;
        if (selection.collapsed()) {
            int caret = selection.focusUtf16();
            int boundary = forward
                    ? nextBoundary(controller.text(), caret)
                    : previousBoundary(controller.text(), caret);
            range = forward
                    ? new TextSelection(caret, boundary)
                    : new TextSelection(boundary, caret);
        }
        caretAffinity = TextAffinity.DOWNSTREAM;
        TextSelection replacementRange = range;
        replace(replacementRange, "");
    }

    private void copySelection() {
        if (password) {
            return;
        }
        TextSelection selection = controller.selection();
        if (selection.collapsed()) {
            return;
        }
        ComponentContext attached = context;
        if (attached != null) {
            attached.clipboard().setText(controller.text().substring(
                    selection.startUtf16(), selection.endUtf16()));
        }
    }

    private void cutSelection() {
        TextSelection selection = controller.selection();
        copySelection();
        if (!readOnly && !selection.collapsed()) {
            replace(selection, "");
        }
    }

    private void pasteClipboard() {
        ComponentContext attached = context;
        if (!readOnly && attached != null) {
            replace(controller.selection(), attached.clipboard().getText());
        }
    }

    private boolean replace(TextSelection range, String replacement) {
        String filtered = Utf16.wellFormed(
                Objects.requireNonNull(filter.apply(replacement), "Text input filter result"),
                "filtered replacement");
        String text = controller.text();
        int outsideCodePoints = text.codePointCount(0, range.startUtf16())
                + text.codePointCount(range.endUtf16(), text.length());
        int allowed = Math.max(0, maxLength - outsideCodePoints);
        String limited = limitCodePoints(filtered, allowed);
        String candidate = text.substring(0, range.startUtf16())
                + limited
                + text.substring(range.endUtf16());
        if (!validator.test(candidate)) {
            return false;
        }
        mutateController(() -> controllerState.replace(range, limited));
        return true;
    }

    private static String limitCodePoints(String value, int maximum) {
        int count = value.codePointCount(0, value.length());
        if (count <= maximum) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximum));
    }

    private void mutateController(Runnable mutation) {
        mutatingController = true;
        try {
            mutation.run();
        } finally {
            mutatingController = false;
        }
    }

    private VisualKey currentVisualKey() {
        String text = controller.text();
        TextSelection selection = controller.selection();
        long resourceGeneration = context == null ? -1L : context.resources().generation();
        Composition currentComposition = composition;
        if (currentComposition == null) {
            boolean showPlaceholder = text.isEmpty() && !placeholder.isEmpty();
            String displayText = showPlaceholder ? placeholder : displayText(text);
            TextStyle displayStyle = showPlaceholder ? placeholderStyle : textStyle;
            List<TextRange> ranges = selection.collapsed()
                    ? List.of()
                    : List.of(new TextRange(selection.startUtf16(), selection.endUtf16()));
            return new VisualKey(
                    new LayoutKey(displayText, displayStyle, direction, resourceGeneration),
                    new TextPosition(selection.focusUtf16(), caretAffinity),
                    ranges,
                    ranges.isEmpty() ? -1 : 0,
                    0,
                    -1);
        }

        TextSelection replacement = currentComposition.replacement();
        String baseDisplayText = displayText(text);
        String displayText = baseDisplayText.substring(0, replacement.startUtf16())
                + currentComposition.text()
                + baseDisplayText.substring(replacement.endUtf16());
        List<TextRange> ranges = new ArrayList<>(currentComposition.blocks().size());
        int cursor = replacement.startUtf16();
        for (String block : currentComposition.blocks()) {
            int end = cursor + block.length();
            ranges.add(new TextRange(cursor, end));
            cursor = end;
        }
        return new VisualKey(
                new LayoutKey(displayText, textStyle, direction, resourceGeneration),
                TextPosition.downstream(replacement.startUtf16() + currentComposition.caretUtf16()),
                ranges,
                -1,
                ranges.size(),
                currentComposition.focusedBlock());
    }

    private String displayText(String value) {
        return password ? "\u2022".repeat(value.length()) : value;
    }

    private void requestVisual(VisualKey key) {
        ComponentContext attached = context;
        if (attached == null
                || key.equals(requestedKey)
                || key.equals(visualKey)
                || key.equals(failedKey)) {
            return;
        }
        cancelPendingVisual();
        TextRequest request = TextRequest.builder(key.layout().text(), key.layout().style())
                .maxLines(1)
                .wrap(TextWrap.NONE)
                .overflow(TextOverflow.CLIP)
                .alignment(TextAlignment.START)
                .direction(key.layout().direction())
                .build();
        CompletableFuture<TextLayout> layoutFuture;
        EditorVisual current = visual;
        if (current != null && current.key().layout().equals(key.layout())) {
            layoutFuture = CompletableFuture.completedFuture(current.layout());
        } else {
            try {
                layoutFuture = Objects.requireNonNull(attached.text().layout(request), "TextService.layout()");
            } catch (RuntimeException exception) {
                failedKey = key;
                return;
            }
        }

        AtomicReference<CompletableFuture<?>> activeStage = new AtomicReference<>(layoutFuture);
        CompletableFuture<EditorVisual> result = layoutFuture.thenCompose(layout -> {
            CompletableFuture<TextGeometry> geometry = Objects.requireNonNull(
                    attached.text().geometry(layout, key.caret(), key.ranges()),
                    "TextService.geometry()");
            activeStage.set(geometry);
            return geometry.thenApply(value -> new EditorVisual(key, layout, value));
        });
        PendingVisual requestState = new PendingVisual(layoutFuture, activeStage, result);
        requestedKey = key;
        pendingVisual = requestState;
        result.whenComplete((value, failure) -> attached.execute(
                () -> publishVisual(attached, key, requestState, value, failure)));
    }

    private void publishVisual(
            ComponentContext expectedContext,
            VisualKey key,
            PendingVisual request,
            EditorVisual result,
            Throwable failure) {
        if (context != expectedContext || pendingVisual != request || !key.equals(requestedKey)) {
            return;
        }
        pendingVisual = null;
        requestedKey = null;
        if (failure != null || result == null) {
            if (!(failure instanceof CancellationException)) {
                failedKey = key;
            }
            return;
        }
        visual = result;
        visualKey = key;
        failedKey = null;
        invalidateLayout();
    }

    private void invalidateVisual() {
        cancelPendingVisual();
        requestedKey = null;
        failedKey = null;
        invalidateLayout();
    }

    private void cancelPendingVisual() {
        PendingVisual current = pendingVisual;
        pendingVisual = null;
        if (current != null) {
            current.cancel();
        }
    }

    private void beginPointerInteraction(Point local) {
        cancelPointerInteraction();
        pointerTracking = true;
        pointerLatestLocal = local;
    }

    private void requestPointerHit(Point local, HitMode mode) {
        pointerLatestLocal = local;
        EditorVisual current = visual;
        if (current == null || !current.key().layout().equals(currentVisualKey().layout())) {
            deferredHit = new DeferredHit(local, mode);
            return;
        }
        deferredHit = null;
        CompletableFuture<TextPosition> previous = pendingHit;
        if (previous != null) {
            previous.cancel(false);
        }
        Point textPoint = new Point(
                local.x() - textOriginX + horizontalOffset,
                local.y() - textOriginY);
        CompletableFuture<TextPosition> future;
        try {
            future = Objects.requireNonNull(
                    context.text().hitTest(current.layout(), textPoint),
                    "TextService.hitTest()");
        } catch (RuntimeException exception) {
            return;
        }
        long sequence = ++hitSequence;
        pendingHit = future;
        ComponentContext attached = context;
        LayoutKey layoutKey = current.key().layout();
        future.whenComplete((position, failure) -> attached.execute(
                () -> publishHit(attached, layoutKey, local, mode, sequence, future, position, failure)));
    }

    private void publishHit(
            ComponentContext expectedContext,
            LayoutKey layoutKey,
            Point requestedLocal,
            HitMode mode,
            long sequence,
            CompletableFuture<TextPosition> future,
            TextPosition position,
            Throwable failure) {
        if (context != expectedContext
                || pendingHit != future
                || sequence != hitSequence
                || failure != null
                || position == null
                || !layoutKey.equals(currentVisualKey().layout())) {
            return;
        }
        pendingHit = null;
        if (mode == HitMode.ANCHOR) {
            pointerAnchorUtf16 = position.offsetUtf16();
            pointerAnchorReady = true;
            caretAffinity = position.affinity();
            mutateController(() -> controller.setSelection(
                    new TextSelection(position.offsetUtf16(), position.offsetUtf16())));
        } else {
            caretAffinity = position.affinity();
            mutateController(() -> controller.setSelection(
                    new TextSelection(pointerAnchorUtf16, position.offsetUtf16())));
        }

        Point latest = pointerLatestLocal;
        if (latest != null && !latest.equals(requestedLocal)) {
            requestPointerHit(latest, pointerTracking ? HitMode.DRAG : HitMode.FINAL);
        }
    }

    private void startDeferredHitIfReady() {
        DeferredHit deferred = deferredHit;
        if (deferred != null && pendingHit == null) {
            requestPointerHit(deferred.local(), deferred.mode());
        }
    }

    private void cancelPointerInteraction() {
        pointerTracking = false;
        pointerAnchorReady = false;
        pointerLatestLocal = null;
        deferredHit = null;
        hitSequence++;
        CompletableFuture<TextPosition> current = pendingHit;
        pendingHit = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    private void updateHorizontalOffset(EditorVisual current, float viewportWidth) {
        if (current == null || viewportWidth <= 0.0f) {
            horizontalOffset = 0.0f;
            return;
        }
        double caretWidthValue = context == null
                ? CARET_WIDTH.defaultValue()
                : context.theme().value(CARET_WIDTH);
        float caretWidth = validPositiveFloat(caretWidthValue, "Caret width");
        Rect caret = current.geometry().caretBounds();
        float contentExtent = Math.max(current.layout().size().width(), caret.x() + caretWidth);
        float maximum = Math.max(0.0f, contentExtent - viewportWidth);
        if (caret.x() < horizontalOffset) {
            horizontalOffset = caret.x();
        } else if (caret.x() + caretWidth > horizontalOffset + viewportWidth) {
            horizontalOffset = caret.x() + caretWidth - viewportWidth;
        }
        horizontalOffset = Math.max(0.0f, Math.min(horizontalOffset, maximum));
    }

    private void paintSelection(PaintScope scope, EditorVisual current) {
        int index = current.key().selectionRangeIndex();
        if (index < 0 || index >= current.geometry().ranges().size()) {
            return;
        }
        Paint paint = scope.theme().value(SELECTION);
        for (Rect bounds : current.geometry().ranges().get(index).bounds()) {
            scope.canvas().fillRect(bounds, paint);
        }
    }

    private void paintComposition(PaintScope scope, EditorVisual current) {
        int count = Math.min(
                current.key().compositionRangeCount(),
                current.geometry().ranges().size());
        for (int index = 0; index < count; index++) {
            TextRangeGeometry range = current.geometry().ranges().get(index);
            boolean focusedRange = index == current.key().focusedCompositionRangeIndex();
            Paint paint = scope.theme().value(focusedRange ? FOCUSED_COMPOSITION : COMPOSITION);
            float thickness = focusedRange ? 2.0f : 1.0f;
            for (Rect bounds : range.bounds()) {
                scope.canvas().fillRect(new Rect(
                        bounds.x(),
                        Math.max(bounds.y(), bounds.y() + bounds.height() - thickness),
                        bounds.width(),
                        Math.min(thickness, bounds.height())), paint);
            }
        }
    }

    private void paintCaret(PaintScope scope, EditorVisual current) {
        float width = validPositiveFloat(scope.theme().value(CARET_WIDTH), "Caret width");
        Rect bounds = current.geometry().caretBounds();
        float height = bounds.height() > 0.0f ? bounds.height() : current.layout().size().height();
        if (height <= 0.0f) {
            return;
        }
        scope.canvas().fillRect(
                new Rect(bounds.x(), bounds.y(), width, height),
                scope.theme().value(CARET));
    }

    private boolean inside(Point point) {
        return point.x() >= 0.0f
                && point.y() >= 0.0f
                && point.x() < measuredSize.width()
                && point.y() < measuredSize.height();
    }

    private static boolean isEditingKey(KeyEvent event) {
        return event.key().equals(Keys.LEFT)
                || event.key().equals(Keys.RIGHT)
                || event.key().equals(Keys.HOME)
                || event.key().equals(Keys.END)
                || event.key().equals(Keys.BACKSPACE)
                || event.key().equals(Keys.DELETE)
                || event.key().equals(Keys.ENTER);
    }

    private static int previousBoundary(String text, int offset) {
        return offset == 0 ? 0 : offset - Character.charCount(text.codePointBefore(offset));
    }

    private static int nextBoundary(String text, int offset) {
        return offset == text.length()
                ? offset
                : offset + Character.charCount(text.codePointAt(offset));
    }

    private static Map<TextBaseline, Float> baselines(
            TextLayout layout,
            float originY,
            float measuredHeight) {
        EnumMap<TextBaseline, Float> result = new EnumMap<>(TextBaseline.class);
        addBaseline(result, TextBaseline.ALPHABETIC, originY + layout.alphabeticBaseline(), measuredHeight);
        addBaseline(result, TextBaseline.IDEOGRAPHIC, originY + layout.ideographicBaseline(), measuredHeight);
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

    private static Style defaultStyle(Theme theme, VisualState state) {
        return Style.builder()
                .padding(theme.value(PADDING))
                .background(theme.value(state.disabled() ? DISABLED_BACKGROUND : BACKGROUND))
                .border(theme.value(state.focused() ? FOCUSED_BORDER : BORDER))
                .cornerRadii(theme.value(CORNER_RADII))
                .clip(ClipMode.BOUNDS)
                .build();
    }

    private static TextStyle defaultPlaceholderStyle(TextStyle style) {
        Color color = style.color();
        return TextStyle.builder(style)
                .color(color.withAlpha(color.alpha() * 0.55f))
                .build();
    }

    private static ThemeToken<Paint> paintToken(String value, int rgb) {
        return ThemeToken.of(
                UiKey.of("fandui", value),
                Paint.class,
                new SolidPaint(Color.rgb(rgb)));
    }

    private static float horizontal(Insets padding) {
        return padding.left() + padding.right();
    }

    private static float vertical(Insets padding) {
        return padding.top() + padding.bottom();
    }

    private static float validPositiveFloat(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0 || value > Float.MAX_VALUE) {
            throw new IllegalStateException(name + " must be finite and positive");
        }
        return (float) value;
    }

    private record LayoutKey(
            String text,
            TextStyle style,
            TextDirection direction,
            long resourceGeneration) {
        private LayoutKey {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(style, "style");
            Objects.requireNonNull(direction, "direction");
        }
    }

    private record VisualKey(
            LayoutKey layout,
            TextPosition caret,
            List<TextRange> ranges,
            int selectionRangeIndex,
            int compositionRangeCount,
            int focusedCompositionRangeIndex) {
        private VisualKey {
            Objects.requireNonNull(layout, "layout");
            Objects.requireNonNull(caret, "caret");
            ranges = List.copyOf(Objects.requireNonNull(ranges, "ranges"));
            if (selectionRangeIndex < -1 || selectionRangeIndex >= ranges.size()) {
                throw new IllegalArgumentException("selectionRangeIndex is outside ranges");
            }
            if (compositionRangeCount < 0 || compositionRangeCount > ranges.size()) {
                throw new IllegalArgumentException("compositionRangeCount is outside ranges");
            }
            if (focusedCompositionRangeIndex < -1
                    || focusedCompositionRangeIndex >= compositionRangeCount) {
                throw new IllegalArgumentException("focusedCompositionRangeIndex is outside composition ranges");
            }
        }
    }

    private record EditorVisual(VisualKey key, TextLayout layout, TextGeometry geometry) {
        private EditorVisual {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(layout, "layout");
            Objects.requireNonNull(geometry, "geometry");
            if (geometry.ranges().size() != key.ranges().size()) {
                throw new IllegalArgumentException("Text geometry range count does not match the request");
            }
        }
    }

    private record Composition(
            TextSelection replacement,
            String text,
            int caretUtf16,
            List<String> blocks,
            int focusedBlock) {
        private Composition {
            Objects.requireNonNull(replacement, "replacement");
            Objects.requireNonNull(text, "text");
            blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
        }
    }

    private static final class PendingVisual {
        private final CompletableFuture<TextLayout> layout;
        private final AtomicReference<CompletableFuture<?>> activeStage;
        private final CompletableFuture<EditorVisual> result;

        private PendingVisual(
                CompletableFuture<TextLayout> layout,
                AtomicReference<CompletableFuture<?>> activeStage,
                CompletableFuture<EditorVisual> result) {
            this.layout = layout;
            this.activeStage = activeStage;
            this.result = result;
        }

        private void cancel() {
            result.cancel(false);
            activeStage.get().cancel(false);
            layout.cancel(false);
        }
    }

    private enum HitMode {
        ANCHOR,
        DRAG,
        FINAL
    }

    private record DeferredHit(Point local, HitMode mode) {
    }

    public static final class Builder {
        private final TextController controller;
        private final TextStyle textStyle;
        private @Nullable UiKey key;
        private TextDirection direction = TextDirection.AUTO;
        private Consumer<String> onSubmit = ignored -> { };
        private String placeholder = "";
        private @Nullable TextStyle placeholderStyle;
        private boolean readOnly;
        private boolean password;
        private int maxLength = Integer.MAX_VALUE;
        private UnaryOperator<String> filter = UnaryOperator.identity();
        private Predicate<String> validator = ignored -> true;
        private @Nullable StyleResolver style;

        private Builder(TextController controller, TextStyle textStyle) {
            this.controller = Objects.requireNonNull(controller, "controller");
            this.textStyle = Objects.requireNonNull(textStyle, "textStyle");
        }

        public Builder key(UiKey key) {
            this.key = Objects.requireNonNull(key, "key");
            return this;
        }

        public Builder direction(TextDirection direction) {
            this.direction = Objects.requireNonNull(direction, "direction");
            return this;
        }

        public Builder onSubmit(Consumer<String> onSubmit) {
            this.onSubmit = Objects.requireNonNull(onSubmit, "onSubmit");
            return this;
        }

        public Builder placeholder(String value) {
            placeholder = Utf16.wellFormed(value, "value");
            return this;
        }

        public Builder placeholderStyle(TextStyle value) {
            placeholderStyle = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder readOnly(boolean value) {
            readOnly = value;
            return this;
        }

        public Builder password(boolean value) {
            password = value;
            return this;
        }

        /** Limits accepted content by Unicode code points. */
        public Builder maxLength(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("value must not be negative");
            }
            maxLength = value;
            return this;
        }

        /** Filters each incoming replacement before length limiting and validation. */
        public Builder filter(UnaryOperator<String> value) {
            filter = Objects.requireNonNull(value, "value");
            return this;
        }

        /** Accepts or rejects the complete candidate value after filtering and length limiting. */
        public Builder validator(Predicate<String> value) {
            validator = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder style(StyleResolver style) {
            this.style = Objects.requireNonNull(style, "style");
            return this;
        }

        public TextInput build() {
            return new TextInput(this);
        }
    }
}
