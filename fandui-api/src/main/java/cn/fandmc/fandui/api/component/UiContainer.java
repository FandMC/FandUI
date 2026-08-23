package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.internal.component.ComponentBindings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Base component that owns an ordered mutable list of child components. */
public abstract class UiContainer extends UiComponent {
    private final List<UiComponent> children = new ArrayList<>();

    protected UiContainer() {
    }

    protected UiContainer(@Nullable UiKey key) {
        super(key);
    }

    public final List<UiComponent> children() {
        return List.copyOf(children);
    }

    public final void add(UiComponent child) {
        add(children.size(), child);
    }

    public final void add(int index, UiComponent child) {
        ComponentBindings.assertMutationAllowed(this);
        Objects.requireNonNull(child, "child");
        if (index < 0 || index > children.size()) {
            throw new IndexOutOfBoundsException(index);
        }
        validateNewChild(child);
        validateChildAddition(child, index);
        children.add(index, child);
        child.assignParent(this);
        try {
            ComponentBindings.childAdded(this, child);
        } catch (RuntimeException | Error exception) {
            child.assignParent(null);
            children.remove(index);
            throw exception;
        }
        invalidateLayout();
    }

    public final boolean remove(UiComponent child) {
        ComponentBindings.assertMutationAllowed(this);
        int index = children.indexOf(Objects.requireNonNull(child, "child"));
        if (index < 0) {
            return false;
        }
        remove(index);
        return true;
    }

    public final UiComponent remove(int index) {
        ComponentBindings.assertMutationAllowed(this);
        UiComponent child = children.get(index);
        ComponentBindings.childRemoved(this, child);
        children.remove(index);
        child.assignParent(null);
        invalidateLayout();
        return child;
    }

    public final void clear() {
        ComponentBindings.assertMutationAllowed(this);
        for (int index = children.size() - 1; index >= 0; index--) {
            remove(index);
        }
    }

    protected void validateChildAddition(UiComponent child, int index) {
    }

    private void validateNewChild(UiComponent child) {
        if (child == this) {
            throw new IllegalArgumentException("A container cannot contain itself");
        }
        if (child.parent().isPresent() || ComponentBindings.bound(child)) {
            throw new IllegalStateException("Component already belongs to a tree");
        }
        UiContainer ancestor = this;
        while (true) {
            if (ancestor == child) {
                throw new IllegalArgumentException("Adding the component would create a cycle");
            }
            ancestor = ancestor.parent().orElse(null);
            if (ancestor == null) {
                return;
            }
        }
    }
}
