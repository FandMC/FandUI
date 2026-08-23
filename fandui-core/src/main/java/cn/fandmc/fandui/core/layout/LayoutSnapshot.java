package cn.fandmc.fandui.core.layout;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.component.HitTestBehavior;
import cn.fandmc.fandui.api.layout.Point;
import cn.fandmc.fandui.api.style.ClipMode;
import cn.fandmc.fandui.api.style.CornerRadii;
import cn.fandmc.fandui.api.style.Transform2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class LayoutSnapshot {
    private final LayoutNode root;
    private final List<LayoutNode> paintOrder;
    private final Map<UiComponent, LayoutNode> nodes;
    private final Map<LayoutNode, LayoutNode> parents;

    LayoutSnapshot(LayoutNode root) {
        this.root = Objects.requireNonNull(root, "root");
        List<LayoutNode> flattened = new ArrayList<>();
        flatten(root, flattened);
        this.paintOrder = List.copyOf(flattened);
        Map<UiComponent, LayoutNode> indexed = new IdentityHashMap<>();
        Map<LayoutNode, LayoutNode> parentIndex = new IdentityHashMap<>();
        for (LayoutNode node : flattened) {
            indexed.put(node.component(), node);
            for (LayoutNode child : node.children()) {
                parentIndex.put(child, node);
            }
        }
        this.nodes = Collections.unmodifiableMap(indexed);
        this.parents = Collections.unmodifiableMap(parentIndex);
    }

    public LayoutNode root() {
        return root;
    }

    public List<LayoutNode> paintOrder() {
        return paintOrder;
    }

    public Optional<LayoutNode> node(UiComponent component) {
        return Optional.ofNullable(nodes.get(Objects.requireNonNull(component, "component")));
    }

    public Optional<Point> sceneToLocal(UiComponent component, Point scenePosition) {
        Objects.requireNonNull(scenePosition, "scenePosition");
        return node(component).flatMap(layoutNode -> layoutNode.sceneToLocalTransform()
                .map(transform -> transform.map(scenePosition)));
    }

    public Optional<Point> localToScene(UiComponent component, Point localPosition) {
        Objects.requireNonNull(localPosition, "localPosition");
        return node(component).map(layoutNode -> layoutNode.localToSceneTransform().map(localPosition));
    }

    public Optional<UiComponent> hitTest(Point scenePosition) {
        Objects.requireNonNull(scenePosition, "scenePosition");
        for (int index = paintOrder.size() - 1; index >= 0; index--) {
            LayoutNode node = paintOrder.get(index);
            if (node.visible()
                    && node.hitTestBehavior() == HitTestBehavior.OPAQUE
                    && !ignoredByAncestor(node)
                    && containsBounds(node, scenePosition)
                    && insideOwnClip(node, scenePosition)
                    && insideAncestorClips(node, scenePosition)) {
                return Optional.of(node.component());
            }
        }
        return Optional.empty();
    }

    private boolean ignoredByAncestor(LayoutNode node) {
        LayoutNode current = node;
        while (current != null) {
            if (current.hitTestBehavior() == HitTestBehavior.IGNORE_SUBTREE) {
                return true;
            }
            current = parents.get(current);
        }
        return false;
    }

    private boolean insideAncestorClips(LayoutNode node, Point point) {
        LayoutNode current = parents.get(node);
        while (current != null) {
            if (current.clipMode() != ClipMode.NONE && !insideClip(current, point)) {
                return false;
            }
            current = parents.get(current);
        }
        return true;
    }

    private static void flatten(LayoutNode node, List<LayoutNode> output) {
        if (!node.visible()) {
            return;
        }
        output.add(node);
        for (LayoutNode child : node.children()) {
            flatten(child, output);
        }
    }

    private static boolean containsBounds(LayoutNode node, Point point) {
        Transform2D transform = node.sceneToLocalTransform().orElse(null);
        return transform != null && containsLocalRect(node, transform.map(point));
    }

    private static boolean insideOwnClip(LayoutNode node, Point point) {
        return node.clipMode() != ClipMode.ROUNDED_BOUNDS || insideClip(node, point);
    }

    private static boolean insideClip(LayoutNode node, Point point) {
        Transform2D transform = node.sceneToLocalTransform().orElse(null);
        if (transform == null) {
            return false;
        }
        Point local = transform.map(point);
        if (!containsLocalRect(node, local)) {
            return false;
        }
        return node.clipMode() != ClipMode.ROUNDED_BOUNDS
                || insideRoundedRect(local, node.size().width(), node.size().height(), node.style().cornerRadii());
    }

    private static boolean containsLocalRect(LayoutNode node, Point point) {
        return point.x() >= 0.0f
                && point.x() < node.size().width()
                && point.y() >= 0.0f
                && point.y() < node.size().height();
    }

    private static boolean insideRoundedRect(Point point, float width, float height, CornerRadii radii) {
        float maximumRadius = Math.min(width, height) * 0.5f;
        float topLeft = Math.min(radii.topLeft(), maximumRadius);
        float topRight = Math.min(radii.topRight(), maximumRadius);
        float bottomRight = Math.min(radii.bottomRight(), maximumRadius);
        float bottomLeft = Math.min(radii.bottomLeft(), maximumRadius);
        if (point.x() < topLeft && point.y() < topLeft) {
            return insideCircle(point.x(), point.y(), topLeft, topLeft, topLeft);
        }
        if (point.x() > width - topRight && point.y() < topRight) {
            return insideCircle(point.x(), point.y(), width - topRight, topRight, topRight);
        }
        if (point.x() > width - bottomRight && point.y() > height - bottomRight) {
            return insideCircle(point.x(), point.y(), width - bottomRight, height - bottomRight, bottomRight);
        }
        if (point.x() < bottomLeft && point.y() > height - bottomLeft) {
            return insideCircle(point.x(), point.y(), bottomLeft, height - bottomLeft, bottomLeft);
        }
        return true;
    }

    private static boolean insideCircle(float x, float y, float centerX, float centerY, float radius) {
        float deltaX = x - centerX;
        float deltaY = y - centerY;
        return deltaX * deltaX + deltaY * deltaY <= radius * radius;
    }
}
