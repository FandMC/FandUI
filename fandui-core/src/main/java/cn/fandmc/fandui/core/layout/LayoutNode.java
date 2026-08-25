package cn.fandmc.fandui.core.layout;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.component.HitTestBehavior;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.layout.LayoutDirection;
import cn.fandmc.fandui.api.layout.Rect;
import cn.fandmc.fandui.api.layout.Size;
import cn.fandmc.fandui.api.layout.TextBaseline;
import cn.fandmc.fandui.api.style.Style;
import cn.fandmc.fandui.api.style.Theme;
import cn.fandmc.fandui.api.style.ClipMode;
import cn.fandmc.fandui.api.style.Transform2D;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Optional;

public final class LayoutNode {
    private final UiComponent component;
    private final Point position;
    private final Size size;
    private final Rect localBounds;
    private final Rect sceneBounds;
    private final int zIndex;
    private final boolean visible;
    private final boolean enabled;
    private final boolean focusable;
    private final int tabIndex;
    private final HitTestBehavior hitTestBehavior;
    private final ClipMode clipMode;
    private final Map<TextBaseline, Float> baselines;
    private final Style style;
    private final Theme theme;
    private final LayoutDirection direction;
    private final Transform2D localToScene;
    private final Optional<Transform2D> sceneToLocal;
    private final List<LayoutNode> children;

    LayoutNode(
            UiComponent component,
            Point position,
            Size size,
            Rect sceneBounds,
            int zIndex,
            ClipMode clipMode,
            Map<TextBaseline, Float> baselines,
            Style style,
            Theme theme,
            LayoutDirection direction,
            Transform2D localToScene,
            List<LayoutNode> children) {
        this.component = Objects.requireNonNull(component, "component");
        this.position = Objects.requireNonNull(position, "position");
        this.size = Objects.requireNonNull(size, "size");
        this.localBounds = new Rect(0.0f, 0.0f, size.width(), size.height());
        this.sceneBounds = Objects.requireNonNull(sceneBounds, "sceneBounds");
        this.zIndex = zIndex;
        this.visible = component.visible();
        this.enabled = component.enabled();
        this.focusable = component.focusable();
        this.tabIndex = component.tabIndex();
        this.hitTestBehavior = component.hitTestBehavior();
        this.clipMode = Objects.requireNonNull(clipMode, "clipMode");
        this.baselines = Map.copyOf(Objects.requireNonNull(baselines, "baselines"));
        this.style = Objects.requireNonNull(style, "style");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.localToScene = Objects.requireNonNull(localToScene, "localToScene");
        this.sceneToLocal = localToScene.inverse();
        this.children = List.copyOf(children);
    }

    public UiComponent component() {
        return component;
    }

    public Point position() {
        return position;
    }

    public Size size() {
        return size;
    }

    public Rect localBounds() {
        return localBounds;
    }

    public Rect sceneBounds() {
        return sceneBounds;
    }

    public int zIndex() {
        return zIndex;
    }

    public boolean visible() {
        return visible;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean focusable() {
        return focusable;
    }

    public int tabIndex() {
        return tabIndex;
    }

    public HitTestBehavior hitTestBehavior() {
        return hitTestBehavior;
    }

    ClipMode clipMode() {
        return clipMode;
    }

    public Style style() {
        return style;
    }

    public Theme theme() {
        return theme;
    }

    public LayoutDirection direction() {
        return direction;
    }

    public Transform2D localToSceneTransform() {
        return localToScene;
    }

    public Optional<Transform2D> sceneToLocalTransform() {
        return sceneToLocal;
    }

    public List<LayoutNode> children() {
        return children;
    }

    public OptionalDouble baseline(TextBaseline baseline) {
        Objects.requireNonNull(baseline, "baseline");
        Float value = baselines.get(baseline);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }
}
