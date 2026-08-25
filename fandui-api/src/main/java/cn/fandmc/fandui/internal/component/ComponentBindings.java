package cn.fandmc.fandui.internal.component;

import cn.fandmc.fandui.api.component.UiComponent;
import cn.fandmc.fandui.api.component.UiContainer;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class ComponentBindings {
    private static final Map<UiComponent, ComponentBinding> BINDINGS = new WeakHashMap<>();

    private ComponentBindings() {
    }

    public static void bind(UiComponent component, ComponentBinding binding) {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(binding, "binding");
        synchronized (BINDINGS) {
            if (BINDINGS.putIfAbsent(component, binding) != null) {
                throw new IllegalStateException("Component is already attached");
            }
        }
    }

    public static void unbind(UiComponent component, ComponentBinding binding) {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(binding, "binding");
        synchronized (BINDINGS) {
            if (!BINDINGS.remove(component, binding)) {
                throw new IllegalStateException("Component is not attached to this binding");
            }
        }
    }

    public static boolean bound(UiComponent component) {
        synchronized (BINDINGS) {
            return BINDINGS.containsKey(component);
        }
    }

    public static void assertMutationAllowed(UiComponent component) {
        ComponentBinding binding = binding(component);
        if (binding != null) {
            binding.assertUiThread();
        }
    }

    public static void invalidateLayout(UiComponent component) {
        ComponentBinding binding = binding(component);
        if (binding != null) {
            binding.assertUiThread();
            binding.invalidateLayout(component);
        }
    }

    public static void invalidatePaint(UiComponent component) {
        ComponentBinding binding = binding(component);
        if (binding != null) {
            binding.assertUiThread();
            binding.invalidatePaint(component);
        }
    }

    public static void interactionChanged(UiComponent component) {
        ComponentBinding binding = binding(component);
        if (binding != null) {
            binding.assertUiThread();
            binding.interactionChanged(component);
        }
    }

    public static void childAdded(UiContainer parent, UiComponent child) {
        ComponentBinding binding = binding(parent);
        if (binding != null) {
            binding.assertUiThread();
            binding.childAdded(parent, child);
        }
    }

    public static void childRemoved(UiContainer parent, UiComponent child) {
        ComponentBinding binding = binding(parent);
        if (binding != null) {
            binding.assertUiThread();
            binding.childRemoved(parent, child);
        }
    }

    public static void childReplaced(
            UiContainer parent,
            UiComponent previous,
            UiComponent replacement) {
        ComponentBinding binding = binding(parent);
        if (binding != null) {
            binding.assertUiThread();
            binding.childReplaced(parent, previous, replacement);
        }
    }

    private static ComponentBinding binding(UiComponent component) {
        synchronized (BINDINGS) {
            return BINDINGS.get(component);
        }
    }
}
