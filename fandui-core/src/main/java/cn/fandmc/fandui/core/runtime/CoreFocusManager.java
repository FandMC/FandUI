package cn.fandmc.fandui.core.runtime;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.focus.FocusDirection;
import cn.fandmc.fandui.api.focus.FocusManager;
import cn.fandmc.fandui.api.event.FocusCause;
import cn.fandmc.fandui.api.session.SessionCloseReason;
import cn.fandmc.fandui.core.layout.LayoutNode;
import cn.fandmc.fandui.core.layout.LayoutSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class CoreFocusManager implements FocusManager {
    private final AbstractCoreSession session;
    private UiComponent focused;

    CoreFocusManager(AbstractCoreSession session) {
        this.session = session;
    }

    @Override
    public Optional<UiComponent> focused() {
        session.runtime().assertUiThread();
        return Optional.ofNullable(focused);
    }

    @Override
    public boolean request(UiComponent component) {
        Objects.requireNonNull(component, "component");
        session.requireActiveOperation();
        if (!eligible(component)) {
            return false;
        }
        change(component, FocusCause.PROGRAMMATIC, session.runtime().now());
        return true;
    }

    @Override
    public boolean move(FocusDirection direction) {
        Objects.requireNonNull(direction, "direction");
        session.requireActiveOperation();
        LayoutSnapshot layout = session.layoutSnapshot();
        if (layout == null) {
            return false;
        }
        List<LayoutNode> candidates = focusableNodes(layout);
        if (candidates.isEmpty()) {
            return false;
        }

        LayoutNode next = switch (direction) {
            case FORWARD -> traverse(candidates, 1);
            case BACKWARD -> traverse(candidates, -1);
            case UP, DOWN, LEFT, RIGHT -> directional(candidates, direction);
        };
        if (next == null) {
            return false;
        }
        change(next.component(), FocusCause.KEYBOARD, session.runtime().now());
        return true;
    }

    @Override
    public void clear() {
        session.requireActiveOperation();
        change(null, FocusCause.CLEAR, session.runtime().now());
    }

    boolean request(UiComponent component, FocusCause cause, long timestampNanos) {
        if (!eligible(component)) {
            return false;
        }
        change(component, cause, timestampNanos);
        return true;
    }

    void clearDetached() {
        change(null, FocusCause.DETACH, session.runtime().now());
    }

    void clearForSessionClose() {
        change(null, FocusCause.SESSION_CLOSED, session.runtime().now());
    }

    void reconcileEligibility() {
        if (focused != null && !eligible(focused)) {
            change(null, FocusCause.INELIGIBLE, session.runtime().now());
        }
    }

    UiComponent current() {
        return focused;
    }

    private boolean eligible(UiComponent component) {
        if (!session.contains(component) || !component.focusable()) {
            return false;
        }
        UiComponent current = component;
        while (current != null) {
            if (!current.visible() || !current.enabled()) {
                return false;
            }
            current = current.parent().orElse(null);
        }
        LayoutSnapshot layout = session.layoutSnapshot();
        if (layout == null) {
            return true;
        }
        return layout.node(component).map(LayoutNode::visible).orElse(false);
    }

    private void change(UiComponent next, FocusCause cause, long timestampNanos) {
        UiComponent previous = focused;
        if (previous == next) {
            return;
        }
        focused = next;
        session.visualStateChanged();
        session.enterCallback();
        try {
            session.events().dispatchFocus(previous, next, cause, timestampNanos);
        } catch (RuntimeException | Error exception) {
            session.requestClose(SessionCloseReason.FAILED, true);
            throw exception;
        } finally {
            session.exitCallback();
        }
    }

    private List<LayoutNode> focusableNodes(LayoutSnapshot layout) {
        List<LayoutNode> result = new ArrayList<>();
        for (LayoutNode node : layout.paintOrder()) {
            if (node.visible() && node.enabled() && node.focusable() && node.tabIndex() >= 0) {
                result.add(node);
            }
        }
        result.sort(Comparator.comparingInt(LayoutNode::tabIndex)
                .thenComparingInt(layout.paintOrder()::indexOf));
        return result;
    }

    private LayoutNode traverse(List<LayoutNode> candidates, int direction) {
        if (focused == null) {
            return direction > 0 ? candidates.get(0) : candidates.get(candidates.size() - 1);
        }
        int currentIndex = -1;
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).component() == focused) {
                currentIndex = index;
                break;
            }
        }
        if (currentIndex < 0) {
            return direction > 0 ? candidates.get(0) : candidates.get(candidates.size() - 1);
        }
        return candidates.get(Math.floorMod(currentIndex + direction, candidates.size()));
    }

    private LayoutNode directional(List<LayoutNode> candidates, FocusDirection direction) {
        LayoutSnapshot layout = session.layoutSnapshot();
        LayoutNode origin = focused == null || layout == null ? null : layout.node(focused).orElse(null);
        if (origin == null) {
            return candidates.get(0);
        }

        float originX = origin.sceneBounds().x() + origin.sceneBounds().width() * 0.5f;
        float originY = origin.sceneBounds().y() + origin.sceneBounds().height() * 0.5f;
        LayoutNode best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (LayoutNode candidate : candidates) {
            if (candidate == origin) {
                continue;
            }
            float candidateX = candidate.sceneBounds().x() + candidate.sceneBounds().width() * 0.5f;
            float candidateY = candidate.sceneBounds().y() + candidate.sceneBounds().height() * 0.5f;
            float deltaX = candidateX - originX;
            float deltaY = candidateY - originY;
            double primary;
            double secondary;
            switch (direction) {
                case UP -> {
                    primary = -deltaY;
                    secondary = Math.abs(deltaX);
                }
                case DOWN -> {
                    primary = deltaY;
                    secondary = Math.abs(deltaX);
                }
                case LEFT -> {
                    primary = -deltaX;
                    secondary = Math.abs(deltaY);
                }
                case RIGHT -> {
                    primary = deltaX;
                    secondary = Math.abs(deltaY);
                }
                default -> throw new IllegalArgumentException("Not a directional focus move: " + direction);
            }
            if (primary <= 0.0) {
                continue;
            }
            double score = primary * primary + secondary * secondary * 4.0;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
}
