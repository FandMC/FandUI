package cn.fandmc.fandui.api.component;

import cn.fandmc.fandui.api.UiKey;
import cn.fandmc.fandui.internal.component.ComponentBindings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Base component that owns an ordered mutable list of child components.
 *
 * <p>{@link #children()} returns an immutable structural snapshot. Mutation of an attached
 * tree is confined to its UI thread; render work must consume immutable frame snapshots rather
 * than read or mutate this list concurrently.</p>
 */
public abstract class UiContainer extends UiComponent {
    /** Serializes structural mutations, including mutations on trees not attached to a session. */
    private static final Object TREE_MUTATION_LOCK = new Object();

    private final List<UiComponent> children = new ArrayList<>();
    private final int minimumChildren;
    private final int maximumChildren;
    private volatile List<UiComponent> childrenSnapshot = List.of();
    /** Reentrancy is thread-local; the global lock provides the structural memory-safety boundary. */
    private final ThreadLocal<Boolean> structureChanging = ThreadLocal.withInitial(() -> false);

    protected UiContainer() {
        this(null);
    }

    protected UiContainer(@Nullable UiKey key) {
        this(key, 0, Integer.MAX_VALUE);
    }

    UiContainer(@Nullable UiKey key, int minimumChildren, int maximumChildren) {
        super(key);
        if (minimumChildren < 0 || maximumChildren < minimumChildren) {
            throw new IllegalArgumentException("Invalid child-count range");
        }
        this.minimumChildren = minimumChildren;
        this.maximumChildren = maximumChildren;
    }

    public final List<UiComponent> children() {
        return childrenSnapshot;
    }

    public final void add(UiComponent child) {
        requireMutationThread();
        synchronized (TREE_MUTATION_LOCK) {
            beginStructureChange();
            try {
                addAt(children.size(), child);
            } finally {
                endStructureChange();
            }
        }
    }

    public final void add(int index, UiComponent child) {
        requireMutationThread();
        synchronized (TREE_MUTATION_LOCK) {
            beginStructureChange();
            try {
                addAt(index, child);
            } finally {
                endStructureChange();
            }
        }
    }

    public final boolean remove(UiComponent child) {
        requireMutationThread();
        synchronized (TREE_MUTATION_LOCK) {
            beginStructureChange();
            try {
                int index = children.indexOf(Objects.requireNonNull(child, "child"));
                if (index < 0) {
                    return false;
                }
                removeAt(index);
                return true;
            } finally {
                endStructureChange();
            }
        }
    }

    public final UiComponent remove(int index) {
        requireMutationThread();
        synchronized (TREE_MUTATION_LOCK) {
            beginStructureChange();
            try {
                return removeAt(index);
            } finally {
                endStructureChange();
            }
        }
    }

    /** Atomically replaces a child, preserving the previous child when validation or attachment fails. */
    public final UiComponent replace(int index, UiComponent replacement) {
        requireMutationThread();
        synchronized (TREE_MUTATION_LOCK) {
            beginStructureChange();
            try {
                UiComponent previous = children.get(index);
                UiComponent checked = Objects.requireNonNull(replacement, "replacement");
                if (previous == checked) {
                    return previous;
                }
                validateNewChild(checked);
                validateChildReplacement(previous, checked, index);

                children.set(index, checked);
                previous.assignParent(null);
                checked.assignParent(this);
                refreshChildrenSnapshot();
                try {
                    ComponentBindings.childReplaced(this, previous, checked);
                } catch (RuntimeException | Error exception) {
                    checked.assignParent(null);
                    previous.assignParent(this);
                    children.set(index, previous);
                    refreshChildrenSnapshot();
                    throw exception;
                }
                invalidateLayout();
                return previous;
            } finally {
                endStructureChange();
            }
        }
    }

    /**
     * Removes every child when the concrete container permits an empty state.
     *
     * <p>Required single-child wrappers reject this operation; use their {@code setChild}
     * method for dynamic content replacement.</p>
     */
    public final void clear() {
        requireMutationThread();
        synchronized (TREE_MUTATION_LOCK) {
            beginStructureChange();
            try {
                if (minimumChildren > 0) {
                    throw new IllegalStateException(childCountMessage()
                            + "; use the component's setChild(...) method for replacement");
                }
                for (int index = children.size() - 1; index >= 0; index--) {
                    removeAt(index);
                }
            } finally {
                endStructureChange();
            }
        }
    }

    protected void validateChildAddition(UiComponent child, int index) {
    }

    protected void validateChildReplacement(
            UiComponent previous,
            UiComponent replacement,
            int index) {
    }

    private void addAt(int index, UiComponent child) {
        Objects.requireNonNull(child, "child");
        if (index < 0 || index > children.size()) {
            throw new IndexOutOfBoundsException(index);
        }
        if (children.size() == maximumChildren) {
            throw new IllegalStateException(childCountMessage());
        }
        validateNewChild(child);
        validateChildAddition(child, index);
        children.add(index, child);
        refreshChildrenSnapshot();
        child.assignParent(this);
        try {
            ComponentBindings.childAdded(this, child);
        } catch (RuntimeException | Error exception) {
            child.assignParent(null);
            children.remove(child);
            refreshChildrenSnapshot();
            throw exception;
        }
        invalidateLayout();
    }

    private UiComponent removeAt(int index) {
        UiComponent child = children.get(index);
        if (children.size() - 1 < minimumChildren) {
            throw new IllegalStateException(childCountMessage());
        }
        ComponentBindings.childRemoved(this, child);
        if (children.get(index) != child) {
            throw new IllegalStateException("Container children changed during removal");
        }
        children.remove(index);
        refreshChildrenSnapshot();
        child.assignParent(null);
        invalidateLayout();
        return child;
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

    private void refreshChildrenSnapshot() {
        childrenSnapshot = List.copyOf(children);
    }

    private void beginStructureChange() {
        if (structureChanging.get()) {
            throw new IllegalStateException("Container structure cannot be changed reentrantly");
        }
        structureChanging.set(true);
    }

    private void endStructureChange() {
        structureChanging.remove();
    }

    private String childCountMessage() {
        return getClass().getSimpleName() + " accepts "
                + (minimumChildren == maximumChildren
                        ? "exactly " + minimumChildren
                        : minimumChildren + " to " + maximumChildren)
                + " children";
    }
}
